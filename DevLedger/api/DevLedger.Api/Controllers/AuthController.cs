using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Google.Apis.Auth;
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
    private readonly IConfiguration _configuration;

    public AuthController(DevLedgerDbContext db, TokenService tokenService, IConfiguration configuration)
    {
        _db = db;
        _tokenService = tokenService;
        _configuration = configuration;
    }

    [HttpPost("sso")]
    public async Task<ActionResult<AuthResponse>> Sso([FromBody] SsoLoginRequest request)
    {
        string verifiedEmail;
        string verifiedName;

        switch (request.Provider)
        {
            case "GOOGLE":
                var googleWebClientId = _configuration["Google:WebClientId"]
                    ?? throw new InvalidOperationException("Google:WebClientId is not configured — set it via user-secrets.");

                GoogleJsonWebSignature.Payload payload;
                try
                {
                    // This is the real verification: it checks the token's signature against
                    // Google's public keys, confirms it hasn't expired, and confirms it was
                    // issued for OUR client ID (Audience) — not just any Google app's token.
                    payload = await GoogleJsonWebSignature.ValidateAsync(request.IdToken, new GoogleJsonWebSignature.ValidationSettings
                    {
                        Audience = new[] { googleWebClientId },
                    });
                }
                catch (InvalidJwtException ex)
                {
                    // Log the error so it shows up in Render/Console logs
                    Console.WriteLine($"Google SSO Validation failed: {ex.Message}");
                    return Unauthorized($"Invalid Google ID token: {ex.Message}");
                }

                // Trust ONLY what Google's verified payload says — never the client-supplied
                // fields — this is what closes the "random entry" hole.
                verifiedEmail = payload.Email;
                verifiedName = payload.Name ?? payload.Email;
                break;

            case "GITHUB":
                // Phase 5 — not implemented yet. Returning 400 instead of silently accepting
                // it, so it fails loudly rather than pretending to work.
                return BadRequest("GitHub SSO is not implemented yet — complete Phase 5 first.");

            default:
                return BadRequest("Unsupported SSO provider.");
        }

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email == verifiedEmail);
        if (user is null)
        {
            user = new User { Email = verifiedEmail, FullName = verifiedName, SsoProvider = request.Provider };
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