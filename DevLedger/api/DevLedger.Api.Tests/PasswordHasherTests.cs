using DevLedger.Api.Services;
using FluentAssertions;
using Xunit;

// Reference scope: ASP.NET Core unit testing with xUnit and FluentAssertions. (Microsoft Learn, n.d.d). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Tests;

/// <summary>Unit tests for <see cref="PasswordHasher"/> — verifies password hashing is
/// salted (two hashes of the same password differ) and that verification round-trips
/// correctly, satisfying Part 2's "Design and execute detailed software unit
/// testing" outcome for the API layer.</summary>
public class PasswordHasherTests
{
    [Fact]
    public void Hash_ProducesADifferentStringEachTimeForTheSamePassword()
    {
        var hash1 = PasswordHasher.Hash("Sup3rSecret!");
        var hash2 = PasswordHasher.Hash("Sup3rSecret!");

        hash1.Should().NotBe(hash2); // different random salts
    }

    [Fact]
    public void Verify_ReturnsTrueForTheCorrectPassword()
    {
        var hash = PasswordHasher.Hash("Sup3rSecret!");
        PasswordHasher.Verify("Sup3rSecret!", hash).Should().BeTrue();
    }

    [Fact]
    public void Verify_ReturnsFalseForAnIncorrectPassword()
    {
        var hash = PasswordHasher.Hash("Sup3rSecret!");
        PasswordHasher.Verify("WrongPassword", hash).Should().BeFalse();
    }

    [Fact]
    public void Verify_ReturnsFalseForAMalformedStoredHash()
    {
        PasswordHasher.Verify("anything", "not-a-valid-hash-format").Should().BeFalse();
    }
}
