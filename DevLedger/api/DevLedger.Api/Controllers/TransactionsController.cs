using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>Income/expense CRUD plus a summary endpoint — backs the "Dev Expense &
/// Income Tracker" custom feature and matches "/api/transactions*" in the Part 1
/// endpoint specification.</summary>
[ApiController]
[Route("api/transactions")]
[Authorize]
public class TransactionsController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly NotificationService _notificationService;

    public TransactionsController(DevLedgerDbContext db, NotificationService notificationService)
    {
        _db = db;
        _notificationService = notificationService;
    }

    private Guid CurrentUserId => Guid.Parse(User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpGet]
    public async Task<ActionResult<List<TransactionDto>>> GetAll()
    {
        var transactions = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId)
            .OrderByDescending(t => t.TransactionDate)
            .ToListAsync();
        return Ok(transactions.Select(ToDto));
    }

    [HttpPost]
    public async Task<ActionResult<TransactionDto>> Create([FromBody] CreateTransactionRequest request)
    {
        if (request.Amount <= 0) return BadRequest("Amount must be greater than zero.");
        if (request.Type != "INCOME" && request.Type != "EXPENSE") return BadRequest("Type must be INCOME or EXPENSE.");

        var tx = new Transaction
        {
            UserId = CurrentUserId,
            Type = request.Type,
            Amount = request.Amount,
            Currency = request.Currency,
            Category = request.Category,
            Description = request.Description,
            TransactionDate = DateTimeOffset.FromUnixTimeMilliseconds(request.TransactionDate).UtcDateTime,
        };
        _db.Transactions.Add(tx);
        await _db.SaveChangesAsync();

        await CheckBudgetThreshold();

        return CreatedAtAction(nameof(GetAll), ToDto(tx));
    }

    [HttpGet("summary")]
    public async Task<ActionResult<TransactionSummaryDto>> Summary()
    {
        var transactions = await _db.Transactions.Where(t => t.UserId == CurrentUserId).ToListAsync();
        var income = transactions.Where(t => t.Type == "INCOME").Sum(t => t.Amount);
        var expenses = transactions.Where(t => t.Type == "EXPENSE").Sum(t => t.Amount);
        var user = await _db.Users.FindAsync(CurrentUserId);
        return Ok(new TransactionSummaryDto(income, expenses, income - expenses, user?.BaseCurrency ?? "ZAR"));
    }

    /// <summary>Server-side budget check backing the "Push Notifications" core
    /// requirement, sent via the Firebase Admin SDK (Google, n.d.a) to every device
    /// the user has registered through /api/notifications/register.</summary>
    private async Task CheckBudgetThreshold()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user?.MonthlyBudget is null || user.MonthlyBudget <= 0) return;

        var expenses = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId && t.Type == "EXPENSE")
            .SumAsync(t => t.Amount);

        if (expenses >= user.MonthlyBudget * 0.8m)
        {
            var tokens = await _db.NotificationTokens
                .Where(n => n.UserId == CurrentUserId)
                .Select(n => n.FcmToken)
                .ToListAsync();

            if (tokens.Count > 0)
            {
                var percentage = Math.Round((double)(expenses / user.MonthlyBudget.Value) * 100);
                await _notificationService.SendToTokensAsync(
                    tokens,
                    title: "Budget alert",
                    body: $"You've used {percentage}% of your {user.BaseCurrency} {user.MonthlyBudget:N0} monthly budget.",
                    type: "BUDGET_ALERT");
            }
        }
    }

    private static TransactionDto ToDto(Transaction t) => new(
        TransactionId: t.TransactionId.ToString(),
        Type: t.Type,
        Amount: t.Amount,
        Currency: t.Currency,
        Category: t.Category,
        Description: t.Description,
        TransactionDate: new DateTimeOffset(t.TransactionDate).ToUnixTimeMilliseconds()
    );
}