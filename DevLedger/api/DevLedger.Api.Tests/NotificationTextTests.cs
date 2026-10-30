using DevLedger.Api.Controllers;
using DevLedger.Api.Services;
using FluentAssertions;
using Microsoft.Extensions.Logging.Abstractions;
using Xunit;

namespace DevLedger.Api.Tests;

/// <summary>
/// Tests for the localised test-push text and for the push service reporting that it is not
/// configured when Firebase credentials are absent (as in CI). Reference scope: FCM server-side
/// delivery. (Firebase, n.d.a). See REFERENCES-CODE.md for full entries.
/// </summary>
public class NotificationTextTests
{
    [Theory]
    [InlineData("en", "DevLedger test")]
    [InlineData("af", "DevLedger-toets")]
    [InlineData("xh", "Uvavanyo lwe-DevLedger")]
    [InlineData("XH", "Uvavanyo lwe-DevLedger")]
    [InlineData(null, "DevLedger test")]
    [InlineData("zz", "DevLedger test")]
    public void TestPushText_UsesTheUsersLanguageAndFallsBackToEnglish(string? language, string expectedTitle)
    {
        var (title, body) = TestPushText.For(language);

        title.Should().Be(expectedTitle);
        body.Should().NotBeNullOrWhiteSpace();
    }

    [Fact]
    public void NotificationService_ReportsNotConfiguredWithoutFirebaseCredentials()
    {
        var service = new NotificationService(NullLogger<NotificationService>.Instance);

        service.IsConfigured.Should().BeFalse();
    }
}
