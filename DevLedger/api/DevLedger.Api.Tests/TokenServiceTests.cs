using DevLedger.Api.Models;
using DevLedger.Api.Services;
using FluentAssertions;
using Microsoft.Extensions.Configuration;
using Xunit;

namespace DevLedger.Api.Tests;

public class TokenServiceTests
{
    private static TokenService BuildService()
    {
        var config = new ConfigurationBuilder()
            .AddInMemoryCollection(new Dictionary<string, string?>
            {
                ["Jwt:SigningKey"] = "test-signing-key-at-least-32-characters-long",
                ["Jwt:Issuer"] = "DevLedger.Api.Tests",
            })
            .Build();
        return new TokenService(config);
    }

    [Fact]
    public void GenerateAccessToken_ProducesANonEmptyJwtString()
    {
        var service = BuildService();
        var user = new User { Email = "dev@devledger.app", FullName = "Test Dev" };

        var token = service.GenerateAccessToken(user);

        token.Should().NotBeNullOrEmpty();
        token.Split('.').Should().HaveCount(3); // header.payload.signature
    }

    [Fact]
    public void GenerateRefreshToken_ProducesADifferentValueEachTime()
    {
        var service = BuildService();
        var first = service.GenerateRefreshToken();
        var second = service.GenerateRefreshToken();
        first.Should().NotBe(second);
    }
}
