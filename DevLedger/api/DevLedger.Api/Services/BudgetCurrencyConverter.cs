namespace DevLedger.Api.Services;

/// <summary>Matches Android CurrencyConverter's existing offline prototype rates.
/// These are manually maintained app constants, not live FX quotations.</summary>
public static class BudgetCurrencyConverter
{
    private static readonly IReadOnlyDictionary<string, decimal> RatesToZar = new Dictionary<string, decimal> {
        ["ZAR"] = 1m, ["USD"] = 18.10m, ["EUR"] = 19.60m, ["GBP"] = 22.90m,
    };
    public static bool IsSupported(string currency) => RatesToZar.ContainsKey(currency.Trim().ToUpperInvariant());
    public static bool TryConvert(decimal amount, string from, string to, out decimal converted)
    {
        converted = 0m;
        if (!RatesToZar.TryGetValue(from.Trim().ToUpperInvariant(), out var source) ||
            !RatesToZar.TryGetValue(to.Trim().ToUpperInvariant(), out var target)) return false;
        converted = amount * source / target;
        return true;
    }
}
