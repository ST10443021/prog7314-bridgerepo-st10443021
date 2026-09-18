using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Reads and saves the authenticated user's settings in PostgreSQL.
/// Supports /api/settings and the existing /api/users/me/settings route.
/// </summary>
[ApiController]
[Route("api/settings")]
[Route("api/users/me/settings")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class UsersController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    public UsersController(DevLedgerDbContext db) => _db = db;

    private Guid CurrentUserId => Guid.Parse(
        User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpGet]
    public async Task<ActionResult<UserSettingsDto>> GetSettings()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();

        return Ok(new UserSettingsDto(
            user.PreferredLanguage,
            user.BaseCurrency,
            user.BiometricEnabled,
            user.SyncOnWifiOnly,
            user.MonthlyBudget));
    }

    [HttpPut]
    public async Task<ActionResult<UserSettingsDto>> UpdateSettings(
        [FromBody] UserSettingsDto settings)
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();

        user.PreferredLanguage = settings.Language;
        user.BaseCurrency = settings.BaseCurrency;
        user.BiometricEnabled = settings.BiometricEnabled;
        user.SyncOnWifiOnly = settings.SyncOnWifiOnly;
        user.MonthlyBudget = settings.MonthlyBudget;

        await _db.SaveChangesAsync();
        return Ok(settings);
    }
}