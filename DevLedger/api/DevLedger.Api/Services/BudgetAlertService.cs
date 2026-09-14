using DevLedger.Api.Data;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Services;

/// <summary>Shared budget-threshold check used by both the direct transaction-create
/// endpoint and the offline-sync batch endpoint, so an over-budget alert fires
/// regardless of which path a transaction arrived through — backing the "Push
/// Notifications" core requirement (Google Developers, n.d.a).</summary>
public class BudgetAlertService
{
    private readonly DevLedgerDbContext _db;
    private readonly NotificationService _notificationService;

    public BudgetAlertService(DevLedgerDbContext db, NotificationService notificationService)
    {
        _db = db;
        _notificationService = notificationService;
    }

    public async Task CheckAndNotifyAsync(Guid userId)
    {
        var user = await _db.Users.FindAsync(userId);
        if (user?.MonthlyBudget is null || user.MonthlyBudget <= 0) return;

        var expenses = await _db.Transactions
            .Where(t => t.UserId == userId && t.Type == "EXPENSE")
            .SumAsync(t => t.Amount);

        if (expenses < user.MonthlyBudget * 0.8m) return;

        var tokens = await _db.NotificationTokens
            .Where(n => n.UserId == userId)
            .Select(n => n.FcmToken)
            .ToListAsync();
        if (tokens.Count == 0) return;

        var percentage = Math.Round((double)(expenses / user.MonthlyBudget.Value) * 100);
        await _notificationService.SendToTokensAsync(
            tokens,
            title: "Budget alert",
            body: $"You've used {percentage}% of your {user.BaseCurrency} {user.MonthlyBudget:N0} monthly budget.",
            type: "BUDGET_ALERT");
    }
}