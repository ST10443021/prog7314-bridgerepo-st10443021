using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
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

    public NotificationsController(DevLedgerDbContext db) => _db = db;

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
}
