using System.Net.Http.Headers;
using System.Text.Json;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Google.Apis.Auth;
using Microsoft.AspNetCore.Mvc;
using Microsoft.AspNetCore.RateLimiting;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Handles SSO, email registration/login and token refresh — matching the
/// "/api/auth/*" rows of the Part 1 endpoint specification table.
/// </summary>
[ApiController]
[Route("api/auth")]
[EnableRateLimiting("auth")]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing, rate limiting and HTTP response patterns.
/// (Microsoft Learn, n.d.; Microsoft Learn, n.d.a; OWASP Foundation, n.d.a). See REFERENCES-CODE.md.
/// Every action is rate limited per client IP (see the "auth" policy in Program.cs) to slow
/// password guessing and token-endpoint abuse.
/// </remarks>
public class AuthController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly TokenService _tokenService;
    private readonly IConfiguration _configuration;
    private readonly IHttpClientFactory _httpClientFactory;
    private readonly ILogger<AuthController> _logger;

    public AuthController(
        DevLedgerDbContext db,
        TokenService tokenService,
        IConfiguration configuration,
        IHttpClientFactory httpClientFactory,
        ILogger<AuthController> logger)
    {
        _db = db;
        _tokenService = tokenService;
        _configuration = configuration;
        _httpClientFactory = httpClientFactory;
        _logger = logger;
    }

    /// <summary>Emails are matched case-insensitively and stored in lower case.</summary>
    private static string NormalizeEmail(string? email) => (email ?? string.Empty).Trim().ToLowerInvariant();

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

                if (string.IsNullOrEmpty(request.IdToken))
                    return BadRequest("Missing idToken for Google sign-in.");

                GoogleJsonWebSignature.Payload payload;
                try
                {
                    payload = await GoogleJsonWebSignature.ValidateAsync(request.IdToken, new GoogleJsonWebSignature.ValidationSettings
                    {
                        Audience = new[] { googleWebClientId },
                    });
                }
                catch (InvalidJwtException ex)
                {
                    // Details go to the server log only; the client gets a generic message.
                    _logger.LogWarning(ex, "Google SSO token validation failed.");
                    return Unauthorized("Google sign-in could not be verified.");
                }

                verifiedEmail = NormalizeEmail(payload.Email);
                verifiedName = payload.Name ?? payload.Email;
                break;

            case "GITHUB":
                if (string.IsNullOrEmpty(request.Code))
                    return BadRequest("Missing authorization code for GitHub sign-in.");

                var githubClientId = _configuration["GitHub:ClientId"]
                    ?? throw new InvalidOperationException("GitHub:ClientId is not configured — set it via user-secrets.");
                var githubClientSecret = _configuration["GitHub:ClientSecret"]
                    ?? throw new InvalidOperationException("GitHub:ClientSecret is not configured — set it via user-secrets.");

                GitHubUserInfo githubUser;
                try
                {
                    githubUser = await ExchangeGitHubCodeForUserAsync(request.Code, githubClientId, githubClientSecret);
                }
                catch (InvalidOperationException ex)
                {
                    _logger.LogWarning(ex, "GitHub SSO validation failed.");
                    return Unauthorized("GitHub sign-in could not be verified.");
                }

                verifiedEmail = NormalizeEmail(githubUser.Email);
                verifiedName = githubUser.Name;
                break;

            default:
                return BadRequest("Unsupported SSO provider.");
        }

        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email.ToLower() == verifiedEmail);
        if (user is null)
        {
            user = new User { Email = verifiedEmail, FullName = verifiedName, SsoProvider = request.Provider };
            _db.Users.Add(user);
            await _db.SaveChangesAsync();
        }

        return Ok(BuildAuthResponse(user));
    }

    /// <summary>
    /// Exchanges a GitHub authorization code for an access token, then fetches the
    /// user's profile and a verified email address. Only the API — never the
    /// Android app — ever sees the client secret, matching GitHub's confidential-client
    /// requirement for the OAuth Authorization Code flow.
    /// </summary>
    private async Task<GitHubUserInfo> ExchangeGitHubCodeForUserAsync(string code, string clientId, string clientSecret)
    {
        var client = _httpClientFactory.CreateClient();
        client.DefaultRequestHeaders.UserAgent.ParseAdd("DevLedger-Api"); // GitHub rejects requests with no User-Agent

        // Step 1: exchange the code for an access token.
        var tokenRequest = new HttpRequestMessage(HttpMethod.Post, "https://github.com/login/oauth/access_token")
        {
            Content = new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["client_id"] = clientId,
                ["client_secret"] = clientSecret,
                ["code"] = code,
            }),
        };
        tokenRequest.Headers.Accept.Add(new MediaTypeWithQualityHeaderValue("application/json"));

        var tokenResponse = await client.SendAsync(tokenRequest);
        var tokenJson = await tokenResponse.Content.ReadAsStringAsync();
        using var tokenDoc = JsonDocument.Parse(tokenJson);

        if (!tokenDoc.RootElement.TryGetProperty("access_token", out var accessTokenProp))
        {
            var error = tokenDoc.RootElement.TryGetProperty("error_description", out var errProp)
                ? errProp.GetString()
                : "no access_token in response";
            throw new InvalidOperationException($"Token exchange failed: {error}");
        }
        var githubAccessToken = accessTokenProp.GetString();

        // Step 2: fetch the profile.
        var userRequest = new HttpRequestMessage(HttpMethod.Get, "https://api.github.com/user");
        userRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubAccessToken);
        userRequest.Headers.UserAgent.ParseAdd("DevLedger-Api");
        var userResponse = await client.SendAsync(userRequest);
        using var userDoc = JsonDocument.Parse(await userResponse.Content.ReadAsStringAsync());
        var login = userDoc.RootElement.GetProperty("login").GetString() ?? "github-user";
        var name = userDoc.RootElement.TryGetProperty("name", out var nameProp) && nameProp.ValueKind == JsonValueKind.String
            ? nameProp.GetString() ?? login
            : login;
        var publicEmail = userDoc.RootElement.TryGetProperty("email", out var emailProp) && emailProp.ValueKind == JsonValueKind.String
            ? emailProp.GetString()
            : null;

        // Step 3: GitHub only returns a public email if the user opted in. If not,
        // fetch the verified primary address from the emails endpoint (requires the
        // "user:email" scope, which the Android app must request — see Phase 5 notes).
        var email = publicEmail;
        if (string.IsNullOrEmpty(email))
        {
            var emailsRequest = new HttpRequestMessage(HttpMethod.Get, "https://api.github.com/user/emails");
            emailsRequest.Headers.Authorization = new AuthenticationHeaderValue("Bearer", githubAccessToken);
            emailsRequest.Headers.UserAgent.ParseAdd("DevLedger-Api");
            var emailsResponse = await client.SendAsync(emailsRequest);
            using var emailsDoc = JsonDocument.Parse(await emailsResponse.Content.ReadAsStringAsync());
            foreach (var entry in emailsDoc.RootElement.EnumerateArray())
            {
                if (entry.GetProperty("primary").GetBoolean() && entry.GetProperty("verified").GetBoolean())
                {
                    email = entry.GetProperty("email").GetString();
                    break;
                }
            }
        }

        if (string.IsNullOrEmpty(email))
            throw new InvalidOperationException("GitHub account has no verified email address.");

        return new GitHubUserInfo(email, name);
    }

    private record GitHubUserInfo(string Email, string Name);

    [HttpPost("register")]
    public async Task<ActionResult<AuthResponse>> Register([FromBody] RegisterRequest request)
    {
        var email = NormalizeEmail(request.Email);
        if (email.Length == 0 || email.Length > 254 || !email.Contains('@') || email.StartsWith('@') || email.EndsWith('@'))
            return BadRequest("Enter a valid email address.");
        if (string.IsNullOrWhiteSpace(request.FullName))
            return BadRequest("Full name is required.");
        if (string.IsNullOrEmpty(request.Password) || request.Password.Length < 8)
            return BadRequest("Password must be at least 8 characters.");

        if (await _db.Users.AnyAsync(u => u.Email.ToLower() == email))
            return Conflict("An account with this email already exists.");

        var user = new User
        {
            Email = email,
            FullName = request.FullName.Trim(),
            PasswordHash = PasswordHasher.Hash(request.Password),
        };
        _db.Users.Add(user);
        await _db.SaveChangesAsync();

        return Ok(BuildAuthResponse(user));
    }

    [HttpPost("login")]
    public async Task<ActionResult<AuthResponse>> Login([FromBody] EmailLoginRequest request)
    {
        var email = NormalizeEmail(request.Email);
        var user = await _db.Users.FirstOrDefaultAsync(u => u.Email.ToLower() == email);
        if (user is null || user.PasswordHash is null || !PasswordHasher.Verify(request.Password, user.PasswordHash))
            return Unauthorized("Invalid email or password.");

        return Ok(BuildAuthResponse(user));
    }

    /// <summary>
    /// Exchanges a valid refresh token for a new access/refresh pair (rotation), so the app can
    /// renew a session silently instead of forcing a new sign-in every hour.
    /// </summary>
    [HttpPost("refresh")]
    public async Task<ActionResult<AuthResponse>> Refresh([FromBody] RefreshRequest request)
    {
        var userId = _tokenService.ValidateRefreshToken(request.RefreshToken);
        if (userId is null)
            return Unauthorized("Your session has expired. Please sign in again.");

        var user = await _db.Users.FindAsync(userId.Value);
        if (user is null)
            return Unauthorized("Your session has expired. Please sign in again.");

        return Ok(BuildAuthResponse(user));
    }

    /// <summary>
    /// The PIN hash and salt are intentionally NOT returned. The PIN is device-local and is never
    /// stored server-side, so a database leak cannot be used to brute-force 4-digit PINs offline
    /// (OWASP Foundation, n.d.a). The fields remain in the DTO (always null) so older clients still parse it.
    /// </summary>
    private AuthResponse BuildAuthResponse(User user) => new(
        AccessToken: _tokenService.GenerateAccessToken(user),
        RefreshToken: _tokenService.GenerateRefreshToken(user),
        UserId: user.UserId.ToString(),
        FullName: user.FullName,
        Email: user.Email,
        PinHash: null,
        PinSalt: null,
        BiometricEnabled: user.BiometricEnabled
    );
}
