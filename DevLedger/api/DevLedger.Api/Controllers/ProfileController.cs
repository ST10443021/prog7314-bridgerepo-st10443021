using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DevLedger.Api.Controllers;

[ApiController]
[Route("api/users/me")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class ProfileController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    public ProfileController(DevLedgerDbContext db)
    {
        _db = db;
    }

    private Guid CurrentUserId => Guid.Parse(
        User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException()
    );

    [HttpGet]
    public async Task<ActionResult<UserProfileDto>> GetProfile()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);

        if (user is null)
        {
            return NotFound();
        }

        return Ok(ToDto(user));
    }

    [HttpPut]
    public async Task<ActionResult<UserProfileDto>> UpdateProfile(
        [FromBody] UpdateUserProfileRequest request)
    {
        var user = await _db.Users.FindAsync(CurrentUserId);

        if (user is null)
        {
            return NotFound();
        }

        if (request.FullName != null)
        {
            var fullName = request.FullName.Trim();
            if (string.IsNullOrWhiteSpace(fullName))
            {
                ModelState.AddModelError(nameof(request.FullName), "Full name is required.");
                return ValidationProblem(ModelState);
            }
            user.FullName = fullName;
        }

        // PinHash / PinSalt in the request are deliberately ignored: the PIN stays on the device.
        if (request.BiometricEnabled.HasValue) user.BiometricEnabled = request.BiometricEnabled.Value;

        await _db.SaveChangesAsync();

        return Ok(ToDto(user));
    }

    private static UserProfileDto ToDto(
        Models.User user)
    {
        return new UserProfileDto(
            user.UserId,
            user.Email,
            user.FullName,
            user.SsoProvider,
            user.PreferredLanguage,
            user.BaseCurrency,
            user.BiometricEnabled,
            PinHash: null,
            PinSalt: null,
            user.CreatedAt
        );
    }
}
