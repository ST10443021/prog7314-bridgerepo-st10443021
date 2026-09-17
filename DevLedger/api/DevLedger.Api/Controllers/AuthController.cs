using System.Net.Http.Headers;
using System.Text.Json;
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
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class AuthController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly TokenService _tokenService;
    private readonly IConfiguration _configuration;
    private readonly IHttpClientFactory _httpClientFactory;

    public AuthController(
        DevLedgerDbContext db,
        TokenService tokenService,
        IConfiguration configuration,
        IHttpClientFactory httpClientFactory)
    {
        _db = db;
        _tokenService = tokenService;
        _configuration = configuration;
        _httpClientFactory = httpClientFactory;
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
                    Console.WriteLine($"Google SSO validation failed: {ex.Message}");
                    return Unauthorized($"Invalid Google ID token: {ex.Message}");
                }

                verifiedEmail = payload.Email;
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
                    Console.WriteLine($"GitHub SSO validation failed: {ex.Message}");
                    return Unauthorized($"GitHub sign-in failed: {ex.Message}");
                }

                verifiedEmail = githubUser.Email;
                verifiedName = githubUser.Name;
                break;

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
        return Unauthorized("Refresh token flow not implemented in the prototype — please sign in again.");
    }

    private AuthResponse BuildAuthResponse(User user) => new(
        AccessToken: _tokenService.GenerateAccessToken(user),
        RefreshToken: _tokenService.GenerateRefreshToken(),
        UserId: user.UserId.ToString(),
        FullName: user.FullName,
        Email: user.Email,
        PinHash: user.PinHash,
        PinSalt: user.PinSalt,
        BiometricEnabled: user.BiometricEnabled
    );
}
