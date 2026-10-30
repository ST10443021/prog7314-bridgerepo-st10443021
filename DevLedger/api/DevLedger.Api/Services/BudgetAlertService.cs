using DevLedger.Api.Data;
using Microsoft.EntityFrameworkCore;

// Reference scope: ASP.NET Core application services. (Microsoft Learn, n.d.; Firebase, n.d.b). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Services;

/// <summary>A frozen budget period lets both write paths compare the same monthly
/// account/base-currency total before and after an atomic transaction batch.</summary>
public sealed record BudgetSnapshot(Guid UserId, DateTime MonthStart, DateTime MonthEnd,
    string Currency, decimal Budget, decimal Expenses, string Language = "en");
public sealed record BudgetAlert(Guid UserId, string Title, string Body);

/// <summary>
/// Prepare alerts inside the database transaction, then attempt delivery only after
/// commit and lock disposal. A rising 80%/100% crossing emits one alert per batch;
/// replaying an already committed sync cannot create another crossing.
/// </summary>
public class BudgetAlertService
{
    private readonly DevLedgerDbContext _db;
    private readonly NotificationService _notificationService;
    private readonly ILogger<BudgetAlertService> _logger;
    private readonly TimeProvider _clock;

    public BudgetAlertService(DevLedgerDbContext db, NotificationService notificationService,
        ILogger<BudgetAlertService> logger, TimeProvider? clock = null)
    {
        _db = db;
        _notificationService = notificationService;
        _logger = logger;
        _clock = clock ?? TimeProvider.System;
    }

    public async Task<BudgetSnapshot?> CaptureAsync(Guid userId, CancellationToken cancellationToken = default)
    {
        var user = await _db.Users.AsNoTracking().SingleOrDefaultAsync(u => u.UserId == userId, cancellationToken);
        if (user?.MonthlyBudget is null || user.MonthlyBudget <= 0) return null;
        var currency = user.BaseCurrency.Trim().ToUpperInvariant();
        if (!BudgetCurrencyConverter.IsSupported(currency)) {
            _logger.LogWarning("Budget currency {Currency} is unsupported for {UserId}.", currency, userId);
            return null;
        }
        var now = _clock.GetUtcNow().UtcDateTime;
        var start = new DateTime(now.Year, now.Month, 1, 0, 0, 0, DateTimeKind.Utc);
        var snapshot = new BudgetSnapshot(userId, start, start.AddMonths(1), currency, user.MonthlyBudget.Value, 0m, user.PreferredLanguage);
        var expenses = await ExpensesAsync(snapshot, cancellationToken);
        return expenses.HasValue ? snapshot with { Expenses = expenses.Value } : null;
    }

    public async Task<BudgetAlert?> PrepareAfterWriteAsync(BudgetSnapshot before,
        CancellationToken cancellationToken = default)
    {
        var calculated = await ExpensesAsync(before, cancellationToken);
        if (!calculated.HasValue) return null;
        var expenses = calculated.Value;
        var oldBand = Band(before.Expenses, before.Budget);
        var newBand = Band(expenses, before.Budget);
        if (newBand <= oldBand) return null;
        var percentage = Math.Round((double)expenses / (double)before.Budget * 100d, 0, MidpointRounding.AwayFromZero);
        return BudgetAlertText.Create(before.UserId, newBand, percentage,
            before.Currency, before.Budget, before.Language);
    }

    /// <summary>Push is a post-commit side effect. Failures never undo a successful
    /// sync or cause a committed direct-create request to be reported as failed.</summary>
    public async Task DeliverAsync(BudgetAlert? alert)
    {
        if (alert is null) return;
        try
        {
            var tokens = await _db.NotificationTokens.AsNoTracking().Where(n => n.UserId == alert.UserId)
                .Select(n => n.FcmToken).Distinct().ToListAsync();
            tokens = tokens.Where(t => !string.IsNullOrWhiteSpace(t)).ToList();
            if (tokens.Count == 0)
            {
                _logger.LogWarning("Budget threshold crossed for {UserId}, but no device token is registered.", alert.UserId);
                return;
            }
            await _notificationService.SendToTokensAsync(tokens, alert.Title, alert.Body, "BUDGET_ALERT");
        }
        catch (Exception error)
        {
            _logger.LogWarning(error, "Budget push failed for {UserId}; financial changes are already committed.", alert.UserId);
        }
    }

    private async Task<decimal?> ExpensesAsync(BudgetSnapshot period, CancellationToken cancellationToken)
    {
        var totals = await _db.Transactions.AsNoTracking().Where(t => t.UserId == period.UserId && t.Type == "EXPENSE" &&
            t.TransactionDate >= period.MonthStart && t.TransactionDate < period.MonthEnd)
            .GroupBy(t => t.Currency).Select(group => new { Currency = group.Key, Amount = group.Sum(t => t.Amount) })
            .ToListAsync(cancellationToken);
        var expenses = 0m;
        foreach (var total in totals)
        {
            if (!BudgetCurrencyConverter.TryConvert(total.Amount, total.Currency, period.Currency, out var converted))
            {
                // A partial or unconverted sum must not masquerade as the account budget total.
                _logger.LogWarning("Budget total cannot be calculated for {UserId}: currency {Currency} is unsupported.",
                    period.UserId, total.Currency);
                return null;
            }
            expenses += converted;
        }
        return expenses;
    }

    private static int Band(decimal expenses, decimal budget) =>
        expenses >= budget ? 100 : expenses >= budget * 0.8m ? 80 : 0;
}
