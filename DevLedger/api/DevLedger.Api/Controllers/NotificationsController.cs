using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>Registers a device's Firebase Cloud Messaging token so the server can push
/// real-time alerts — "/api/notifications/register" in the Part 1 endpoint spec.</summary>
[ApiController]
[Route("api/notifications")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.; Firebase, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class NotificationsController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly NotificationService _push;

    public NotificationsController(DevLedgerDbContext db, NotificationService push)
    {
        _db = db;
        _push = push;
    }

    private Guid CurrentUserId => Guid.Parse(User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpPost("register")]
    public async Task<IActionResult> RegisterDevice([FromBody] RegisterDeviceRequest request)
    {
        var existing = await _db.NotificationTokens
            .FirstOrDefaultAsync(t => t.UserId == CurrentUserId && t.DeviceId == request.DeviceId);

        if (existing is null)
        {
            _db.NotificationTokens.Add(new NotificationToken
            {
                UserId = CurrentUserId,
                FcmToken = request.FcmToken,
                DeviceId = request.DeviceId,
            });
        }
        else
        {
            existing.FcmToken = request.FcmToken;
            existing.RegisteredAt = DateTime.UtcNow;
        }

        await _db.SaveChangesAsync();
        return Ok();
    }

    /// <summary>
    /// Sends a real FCM push to every device registered by the signed-in user. Used to demonstrate
    /// real-time notifications (and to verify the whole chain) without having to cross a budget
    /// threshold. Returns 404 when no device is registered and 503 when Firebase is not configured.
    /// </summary>
    [HttpPost("test")]
    public async Task<IActionResult> SendTest()
    {
        if (!_push.IsConfigured)
            return StatusCode(StatusCodes.Status503ServiceUnavailable, new { message = "Push service is not configured." });

        var userId = CurrentUserId;
        var tokens = await _db.NotificationTokens.AsNoTracking()
            .Where(t => t.UserId == userId)
            .Select(t => t.FcmToken)
            .Distinct()
            .ToListAsync();
        tokens = tokens.Where(t => !string.IsNullOrWhiteSpace(t)).ToList();
        if (tokens.Count == 0)
            return NotFound(new { message = "No device is registered for this account yet." });

        var language = await _db.Users.AsNoTracking()
            .Where(u => u.UserId == userId)
            .Select(u => u.PreferredLanguage)
            .SingleOrDefaultAsync();
        var (title, body) = TestPushText.For(language);
        await _push.SendToTokensAsync(tokens, title, body, "SYSTEM");
        return Ok(new { sent = tokens.Count });
    }
}

/// <summary>Localised text of the test push (English, Afrikaans, isiXhosa).</summary>
public static class TestPushText
{
    public static (string Title, string Body) For(string? language) => (language ?? "en").ToLowerInvariant() switch
    {
        "af" => ("DevLedger-toets", "Stootkennisgewings werk. Hierdie boodskap het regstreeks vanaf die bediener gekom."),
        "xh" => ("Uvavanyo lwe-DevLedger", "Izaziso ziyasebenza. Lo myalezo uvela ngqo kwiseva."),
        _ => ("DevLedger test", "Push notifications are working. This message came live from the server."),
    };
}
