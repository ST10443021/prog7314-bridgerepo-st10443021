using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Text;
using DevLedger.Api.Models;
using Microsoft.IdentityModel.Tokens;

namespace DevLedger.Api.Services;

/// <summary>
/// Issues and validates the JWT access/refresh token pair described in Section 5.1 of
/// the Part 1 Planning and Design document ("Authentication uses JWT bearer tokens
/// validated on every request except the SSO callback and refresh-token endpoints").
/// </summary>
/// <remarks>
/// Reference scope: token-based authentication integration; token policy is application-specific.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class TokenService
{
    private const string RefreshTokenUse = "refresh";
    private static readonly TimeSpan RefreshLifetime = TimeSpan.FromDays(30);

    private readonly string _signingKey;
    private readonly string _issuer;

    /// <summary>Refresh tokens use their own audience so they can never be accepted as access tokens.</summary>
    private string RefreshAudience => _issuer + ":refresh";

    public TokenService(IConfiguration configuration)
    {
        // No fallback: the API refuses to start without a real secret (see JwtSettings).
        _signingKey = JwtSettings.RequireSigningKey(configuration);
        _issuer = JwtSettings.Issuer(configuration);
    }

    public string GenerateAccessToken(User user)
    {
        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, user.UserId.ToString()),
            new Claim(JwtRegisteredClaimNames.Email, user.Email),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString()),
        };

        var key = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_signingKey));
        var credentials = new SigningCredentials(key, SecurityAlgorithms.HmacSha256);

        var token = new JwtSecurityToken(
            issuer: _issuer,
            audience: _issuer,
            claims: claims,
            expires: DateTime.UtcNow.AddHours(1),
            signingCredentials: credentials
        );

        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    /// <summary>
    /// Signed, stateless 30-day refresh token. It is bound to one user, carries token_use=refresh
    /// and a different audience from access tokens, and is rotated on every refresh, so the client's
    /// automatic renewal works without a refresh-token table.
    /// </summary>
    public string GenerateRefreshToken(User user)
    {
        var claims = new[]
        {
            new Claim(JwtRegisteredClaimNames.Sub, user.UserId.ToString()),
            new Claim(JwtRegisteredClaimNames.Jti, Guid.NewGuid().ToString()),
            new Claim("token_use", RefreshTokenUse),
        };
        var credentials = new SigningCredentials(
            new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_signingKey)), SecurityAlgorithms.HmacSha256);
        var token = new JwtSecurityToken(
            issuer: _issuer,
            audience: RefreshAudience,
            claims: claims,
            expires: DateTime.UtcNow.Add(RefreshLifetime),
            signingCredentials: credentials);
        return new JwtSecurityTokenHandler().WriteToken(token);
    }

    /// <summary>Returns the user id inside a valid, unexpired refresh token, otherwise null.</summary>
    public Guid? ValidateRefreshToken(string? refreshToken)
    {
        if (string.IsNullOrWhiteSpace(refreshToken)) return null;
        try
        {
            new JwtSecurityTokenHandler().ValidateToken(refreshToken, new TokenValidationParameters
            {
                ValidateIssuer = true,
                ValidIssuer = _issuer,
                ValidateAudience = true,
                ValidAudience = RefreshAudience,
                ValidateLifetime = true,
                ValidateIssuerSigningKey = true,
                IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_signingKey)),
                ClockSkew = TimeSpan.FromMinutes(1),
            }, out var validated);

            // Read the raw claims so the result does not depend on inbound claim-type mapping.
            var jwt = (JwtSecurityToken)validated;
            if (jwt.Claims.FirstOrDefault(c => c.Type == "token_use")?.Value != RefreshTokenUse) return null;
            var subject = jwt.Claims.FirstOrDefault(c => c.Type == JwtRegisteredClaimNames.Sub)?.Value;
            return Guid.TryParse(subject, out var userId) ? userId : null;
        }
        catch (Exception error) when (error is SecurityTokenException or ArgumentException)
        {
            return null;
        }
    }

    public TokenValidationParameters GetValidationParameters() => new()
    {
        ValidateIssuer = true,
        ValidateAudience = true,
        ValidateLifetime = true,
        ValidateIssuerSigningKey = true,
        ValidIssuer = _issuer,
        ValidAudience = _issuer,
        IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(_signingKey)),
    };
}
