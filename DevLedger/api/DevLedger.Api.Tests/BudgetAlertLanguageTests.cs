using DevLedger.Api.Services;
using Xunit;

namespace DevLedger.Api.Tests;

public class BudgetAlertLanguageTests
{
    [Theory]
    [InlineData("en", "Budget limit reached")]
    [InlineData("af", "Begrotingsperk bereik")]
    [InlineData("xh", "Umda wohlahlo lwabiwo-mali ufikelelwe")]
    [InlineData("af-ZA", "Begrotingsperk bereik")]
    [InlineData("unknown", "Budget limit reached")]
    public void AlertUsesSavedLanguageAndPreservesAmounts(string language, string expectedTitle)
    {
        var alert = BudgetAlertText.Create(Guid.NewGuid(), 100, 105, "ZAR", 1000m, language);
        Assert.Equal(expectedTitle, alert.Title);
        Assert.Contains("105%", alert.Body);
        Assert.Contains("ZAR", alert.Body);
        Assert.DoesNotContain("{0", alert.Body);
    }

    [Fact]
    public void DefaultResourcesPreserveExistingEnglishMessage()
    {
        var alert = BudgetAlertText.Create(Guid.NewGuid(), 80, 80, "ZAR", 1000m, null);
        Assert.Equal("Budget alert", alert.Title);
        Assert.Equal("You've used 80% of your ZAR 1,000.00 monthly budget.", alert.Body);
    }
}
