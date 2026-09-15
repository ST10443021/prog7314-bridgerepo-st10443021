using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DevLedger.Api.Controllers;

[ApiController]
[Route("api/users/me")]
[Authorize]
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
        var fullName = request.FullName?.Trim();

        if (string.IsNullOrWhiteSpace(fullName))
        {
            ModelState.AddModelError(
                nameof(request.FullName),
                "Full name is required."
            );

            return ValidationProblem(ModelState);
        }

        if (fullName.Length > 100)
        {
            ModelState.AddModelError(
                nameof(request.FullName),
                "Full name cannot exceed 100 characters."
            );

            return ValidationProblem(ModelState);
        }

        var user = await _db.Users.FindAsync(CurrentUserId);

        if (user is null)
        {
            return NotFound();
        }

        user.FullName = fullName;

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
            user.CreatedAt
        );
    }
}