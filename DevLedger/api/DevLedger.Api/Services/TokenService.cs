using System.IdentityModel.Tokens.Jwt;
using System.Security.Claims;
using System.Security.Cryptography;
using System.Text;
using DevLedger.Api.Models;
using Microsoft.IdentityModel.Tokens;

namespace DevLedger.Api.Services;

/// <summary>
/// Issues and validates the JWT access/refresh token pair described in Section 5.1 of
/// the Part 1 Planning and Design document ("Authentication uses JWT bearer tokens
/// validated on every request except the SSO callback and refresh-token endpoints").
/// </summary>
public class TokenService
{
    private readonly string _signingKey;
    private readonly string _issuer;

    public TokenService(IConfiguration configuration)
    {
        _signingKey = configuration["Jwt:SigningKey"] ?? "dev-only-signing-key-change-in-production-32chars!!";
        _issuer = configuration["Jwt:Issuer"] ?? "DevLedger.Api";
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

    public string GenerateRefreshToken()
    {
        var bytes = RandomNumberGenerator.GetBytes(64);
        return Convert.ToBase64String(bytes);
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
