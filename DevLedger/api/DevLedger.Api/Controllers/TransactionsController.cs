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
    private readonly BudgetAlertService _budgetAlertService;

    public TransactionsController(DevLedgerDbContext db, BudgetAlertService budgetAlertService)
    {
        _db = db;
        _budgetAlertService = budgetAlertService;
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

        await _budgetAlertService.CheckAndNotifyAsync(CurrentUserId);

        return CreatedAtAction(nameof(GetAll), ToDto(tx));
    }

    [HttpPut("{id:guid}")]
    public async Task<ActionResult<TransactionDto>> Update(
        Guid id,
        [FromBody] CreateTransactionRequest request)
    {
        if (request.Amount <= 0)
            return BadRequest("Amount must be greater than zero.");

        if (request.Type != "INCOME" &&
            request.Type != "EXPENSE")
        {
            return BadRequest(
                "Type must be INCOME or EXPENSE.");
        }

        if (string.IsNullOrWhiteSpace(request.Currency) ||
            request.Currency.Trim().Length != 3)
        {
            return BadRequest(
                "A valid three-letter currency code is required.");
        }

        if (string.IsNullOrWhiteSpace(request.Category))
            return BadRequest("Category is required.");

        DateTime transactionDate;

        try
        {
            transactionDate = DateTimeOffset
                .FromUnixTimeMilliseconds(request.TransactionDate)
                .UtcDateTime;
        }
        catch (ArgumentOutOfRangeException)
        {
            return BadRequest(
                "Transaction date is outside the supported range.");
        }

        var transaction = await _db.Transactions
            .SingleOrDefaultAsync(
                tx =>
                    tx.TransactionId == id &&
                    tx.UserId == CurrentUserId);

        if (transaction is null)
            return NotFound();

        transaction.Type = request.Type;
        transaction.Amount = request.Amount;
        transaction.Currency =
            request.Currency.Trim().ToUpperInvariant();
        transaction.Category = request.Category.Trim();
        transaction.Description =
            string.IsNullOrWhiteSpace(request.Description)
                ? null
                : request.Description.Trim();
        transaction.TransactionDate = transactionDate;

        await _db.SaveChangesAsync();

        await CheckBudgetThreshold();

        return Ok(ToDto(transaction));
    }

    [HttpDelete("{id:guid}")]
    public async Task<IActionResult> Delete(Guid id)
    {
        var transaction = await _db.Transactions
            .SingleOrDefaultAsync(
                tx =>
                    tx.TransactionId == id &&
                    tx.UserId == CurrentUserId);

        if (transaction is null)
            return NotFound();

        _db.Transactions.Remove(transaction);
        await _db.SaveChangesAsync();

        return NoContent();
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