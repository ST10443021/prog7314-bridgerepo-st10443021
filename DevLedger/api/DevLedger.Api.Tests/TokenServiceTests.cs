using DevLedger.Api.Models;
using DevLedger.Api.Services;
using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace DevLedger.Api.Tests;

/// <summary>
/// Unit tests for <see cref="TokenService"/> and <see cref="JwtSettings"/>: access tokens, signed
/// stateless refresh tokens, and the rule that the API never starts with a missing or weak signing
/// key. Reference scope: JWT validation. (Microsoft Learn, n.d.; OWASP Foundation, n.d.a).
/// See REFERENCES-CODE.md for full entries.
/// </summary>
public class TokenServiceTests
{
    private const string Key = "test-signing-key-at-least-32-characters-long";

    private static IConfiguration Config(string? key, string issuer = "DevLedger.Api.Tests")
    {
        var values = new Dictionary<string, string?> { ["Jwt:Issuer"] = issuer };
        if (key is not null) values["Jwt:SigningKey"] = key;
        return new ConfigurationBuilder().AddInMemoryCollection(values).Build();
    }

    private static TokenService BuildService(string key = Key, string issuer = "DevLedger.Api.Tests") =>
        new(Config(key, issuer));

    private static User NewUser() => new() { Email = "dev@devledger.app", FullName = "Test Dev" };

    [Fact]
    public void GenerateAccessToken_ProducesANonEmptyJwtString()
    {
        var token = BuildService().GenerateAccessToken(NewUser());

        token.Should().NotBeNullOrEmpty();
        token.Split('.').Should().HaveCount(3); // header.payload.signature
    }

    [Fact]
    public void GenerateRefreshToken_ProducesADifferentValueEachTime()
    {
        var service = BuildService();
        var user = NewUser();

        service.GenerateRefreshToken(user).Should().NotBe(service.GenerateRefreshToken(user));
    }

    [Fact]
    public void ValidateRefreshToken_ReturnsTheUserIdForAFreshToken()
    {
        var service = BuildService();
        var user = NewUser();

        service.ValidateRefreshToken(service.GenerateRefreshToken(user)).Should().Be(user.UserId);
    }

    [Fact]
    public void ValidateRefreshToken_RejectsAnAccessToken()
    {
        var service = BuildService();

        // Access tokens have a different audience and no token_use claim.
        service.ValidateRefreshToken(service.GenerateAccessToken(NewUser())).Should().BeNull();
    }

    [Fact]
    public void ValidateRefreshToken_RejectsATokenSignedWithAnotherKey()
    {
        var token = BuildService("another-signing-key-also-32-characters-long!").GenerateRefreshToken(NewUser());

        BuildService().ValidateRefreshToken(token).Should().BeNull();
    }

    [Theory]
    [InlineData(null)]
    [InlineData("")]
    [InlineData("not-a-jwt")]
    public void ValidateRefreshToken_RejectsMissingOrMalformedInput(string? token)
    {
        BuildService().ValidateRefreshToken(token).Should().BeNull();
    }

    [Fact]
    public void Constructor_RefusesToStartWithoutASigningKey()
    {
        var act = () => new TokenService(Config(null));

        act.Should().Throw<InvalidOperationException>().WithMessage("*Jwt:SigningKey*");
    }

    [Fact]
    public void Constructor_RefusesAShortSigningKey()
    {
        var act = () => new TokenService(Config("too-short"));

        act.Should().Throw<InvalidOperationException>();
    }
}
