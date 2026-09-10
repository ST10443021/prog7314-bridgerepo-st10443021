using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Handles SSO, email registration/login and token refresh — matching the
/// "/api/auth/*" rows of the Part 1 endpoint specification table.
/// </summary>
[ApiController]
[Route("api/auth")]
public class AuthController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly TokenService _tokenService;

    public AuthController(DevLedgerDbContext db, TokenService tokenService)
    {
        _db = db;
        _tokenService = tokenService;
    }

    [HttpPost("sso")]
    public async Task<ActionResult<AuthResponse>> Sso([FromBody] SsoLoginRequest request)
    {
        // NOTE: for the prototype, the SSO id token is trusted as-is once the
        // provider is recognised. In production this calls Google/GitHub's token
        // verification endpoint before trusting the claims inside idToken.
        if (request.Provider != "GOOGLE" && request.Provider != "GITHUB")
            return BadRequest("Unsupported SSO provider.");

        var email = $"{request.Provider.ToLower()}-{request.IdToken.GetHashCode()}@devledger.local";
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == email);
        if (user is null)
        {
            user = new User { Email = email, FullName = $"{request.Provider} User", SsoProvider = request.Provider };
            _db.Users.Add(user);
            await _db.SaveChangesAsync();
        }

        return Ok(BuildAuthResponse(user));
    }

    [HttpPost("register")]
    public async Task<ActionResult<AuthResponse>> Register([FromBody] RegisterRequest request)
    {
        if (await _db.Users.AnyAsync(u => u.Email == request.Email))
            return Conflict("An account with this email already exists.");

        var user = new User
        {
            Email = request.Email,
            FullName = request.FullName,
            PasswordHash = PasswordHasher.Hash(request.Password),
        };
        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        return Ok(BuildAuthResponse(user));
    }

    [HttpPost("login")]
    public async Task<ActionResult<AuthResponse>> Login([FromBody] EmailLoginRequest request)
    {
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == request.Email);
        if (user is null || user.PasswordHash is null || !PasswordHasher.Verify(request.Password, user.PasswordHash))
            return Unauthorized("Invalid email or password.");

        return Ok(BuildAuthResponse(user));
    }

    [HttpPost("refresh")]
    public ActionResult<AuthResponse> Refresh([FromBody] RefreshRequest request)
    {
        // Simplified for the prototype: a real implementation persists refresh tokens
        // per-device and rotates them on use. Rejecting here forces a fresh sign-in,
        // which is safe behaviour even if under-featured for production.
        return Unauthorized("Refresh token flow not implemented in the prototype — please sign in again.");
    }

    private AuthResponse BuildAuthResponse(User user) => new(
        AccessToken: _tokenService.GenerateAccessToken(user),
        RefreshToken: _tokenService.GenerateRefreshToken(),
        UserId: user.UserId.ToString(),
        FullName: user.FullName,
        Email: user.Email
    );
}
