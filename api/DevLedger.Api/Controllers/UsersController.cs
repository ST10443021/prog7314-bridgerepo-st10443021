using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DevLedger.Api.Controllers;

/// <summary>User settings (language, currency, biometric flag, sync policy, monthly
/// hosting budget) — "/api/users/me/settings" in the Part 1 endpoint spec, satisfying
/// the core requirement "The user must be able to change their settings in the
/// app" with server-side persistence so a second device inherits the same
/// preferences.</summary>
[ApiController]
[Route("api/users/me")]
[Authorize]
public class UsersController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    public UsersController(DevLedgerDbContext db) => _db = db;

    private Guid CurrentUserId => Guid.Parse(User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpGet("settings")]
    public async Task<ActionResult<UserSettingsDto>> GetSettings()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user is null) return NotFound();
        return Ok(new UserSettingsDto(user.PreferredLanguage, user.BaseCurrency, user.BiometricEnabled, user.SyncOnWifiOnly, user.MonthlyBudget));
    }

    [HttpPut("settings")]
    public async Task<ActionResult<UserSettingsDto>> UpdateSettings([FromBody] UserSettingsDto settings)
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
