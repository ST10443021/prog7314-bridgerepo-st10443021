using System.Globalization;
using System.Resources;

// Reference scope: ASP.NET Core application services. (Microsoft Learn, n.d.; Firebase, n.d.b). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Services;

/// <summary>Localize server-originated alerts from the account's saved language so
/// FCM's background notification and the Android foreground inbox agree.</summary>
/// <remarks>https://learn.microsoft.com/dotnet/core/extensions/resources</remarks>
public static class BudgetAlertText
{
    private static readonly ResourceManager Resources = new(
        "DevLedger.Api.Resources.BudgetAlerts", typeof(BudgetAlertText).Assembly);

    public static BudgetAlert Create(Guid userId, int band, double percentage,
        string currency, decimal budget, string? language)
    {
        var tag = language?.Split('-')[0].ToLowerInvariant();
        // Unknown account preferences fall back deliberately to the default resources.
        var culture = tag is "af" or "xh" ? CultureInfo.GetCultureInfo(tag) : CultureInfo.InvariantCulture;
        var title = Resources.GetString(band >= 100 ? "LimitReached" : "Alert", culture)!;
        var body = string.Format(culture, Resources.GetString("Body", culture)!, percentage, currency, budget);
        return new BudgetAlert(userId, title, body);
    }
}
