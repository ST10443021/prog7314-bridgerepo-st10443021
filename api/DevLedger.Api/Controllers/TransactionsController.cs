using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
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

    public TransactionsController(DevLedgerDbContext db) => _db = db;

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

    /// <summary>Server-side budget check backing the "Push Notifications: FCM alerts
    /// when cloud hosting budgets reach threshold limits" feature from the Part 1 app
    /// concept. A real deployment sends this via Firebase Admin SDK; here it is a
    /// documented extension point (see README "Push notification wiring").</summary>
    private async Task CheckBudgetThreshold()
    {
        var user = await _db.Users.FindAsync(CurrentUserId);
        if (user?.MonthlyBudget is null || user.MonthlyBudget <= 0) return;

        var expenses = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId && t.Type == "EXPENSE")
            .SumAsync(t => t.Amount);

        if (expenses >= user.MonthlyBudget * 0.8m)
        {
            // TODO (production): call the Firebase Admin SDK here with the user's
            // registered NotificationToken rows to push a real budget-threshold alert.
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
