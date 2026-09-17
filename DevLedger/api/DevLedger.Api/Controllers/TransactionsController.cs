using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>Income/expense CRUD plus summary and chart endpoints — backs the Financial
/// Tracker screen. Every action is scoped to the authenticated caller's UserId (JWT
/// "sub" claim, never a client-supplied value — Microsoft Learn, n.d.).</summary>
[ApiController]
[Route("api/transactions")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class TransactionsController : ControllerBase
{
    private const long MaxAttachmentBytes = 2 * 1024 * 1024; // 2MB — see class note on storage.
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
    public async Task<ActionResult<List<TransactionDto>>> GetAll([FromQuery] string? type, [FromQuery] string? search)
    {
        var query = _db.Transactions.Where(t => t.UserId == CurrentUserId);
        if (type == "INCOME" || type == "EXPENSE") query = query.Where(t => t.Type == type);
        if (!string.IsNullOrWhiteSpace(search))
        {
            var term = search.Trim();
            query = query.Where(t =>
                t.Category.Contains(term) || t.ClientOrFrom.Contains(term) ||
                (t.Description != null && t.Description.Contains(term)) ||
                (t.RelatedProject != null && t.RelatedProject.Contains(term)));
        }
        var transactions = await query.OrderByDescending(t => t.TransactionDate).ToListAsync();
        return Ok(transactions.Select(ToDto));
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<TransactionDto>> GetById(Guid id)
    {
        var tx = await _db.Transactions.FirstOrDefaultAsync(t => t.TransactionId == id && t.UserId == CurrentUserId);
        return tx is null ? NotFound() : Ok(ToDto(tx));
    }

    [HttpGet("distinct-projects")]
    public async Task<ActionResult<List<string>>> GetDistinctProjects()
    {
        var projects = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId && t.RelatedProject != null && t.RelatedProject != "")
            .Select(t => t.RelatedProject!)
            .Distinct()
            .OrderBy(p => p)
            .ToListAsync();
        return Ok(projects);
    }

    [HttpPost]
    public async Task<ActionResult<TransactionDto>> Create([FromBody] CreateTransactionRequest request)
    {
        var validationError = Validate(request);
        if (validationError is not null) return BadRequest(validationError);

        var tx = new Transaction
        {
            UserId = CurrentUserId,
            Type = request.Type,
            Amount = request.Amount,
            Currency = request.Currency,
            Category = request.Category,
            Description = request.Description,
            ClientOrFrom = request.ClientOrFrom,
            RelatedProject = request.RelatedProject,
            PaymentMethod = request.PaymentMethod,
            ReferenceNumber = request.ReferenceNumber,
            IsRecurring = request.IsRecurring,
            RecurringFrequency = request.IsRecurring ? request.RecurringFrequency : null,
            NextOccurrenceDate = request.IsRecurring && request.NextOccurrenceDate is not null
                ? DateTimeOffset.FromUnixTimeMilliseconds(request.NextOccurrenceDate.Value).UtcDateTime
                : null,
            AttachmentFileName = request.AttachmentFileName,
            AttachmentBase64 = request.AttachmentBase64,
            TransactionDate = DateTimeOffset.FromUnixTimeMilliseconds(request.TransactionDate).UtcDateTime,
        };
        _db.Transactions.Add(tx);
        await _db.SaveChangesAsync();

        await _budgetAlertService.CheckAndNotifyAsync(CurrentUserId);

        return CreatedAtAction(nameof(GetById), new { id = tx.TransactionId }, ToDto(tx));
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

    /// <summary>Per-day income/expense totals for a given month, feeding the Monthly
    /// Summary chart. Rolled up server-side rather than shipping every raw row to the
    /// client and aggregating in Kotlin.</summary>
    [HttpGet("daily-series")]
    public async Task<ActionResult<List<DailySeriesPointDto>>> DailySeries([FromQuery] int year, [FromQuery] int month)
    {
        if (month < 1 || month > 12) return BadRequest("Month must be between 1 and 12.");
        var monthStart = new DateTime(year, month, 1, 0, 0, 0, DateTimeKind.Utc);
        var monthEnd = monthStart.AddMonths(1);

        var rows = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId && t.TransactionDate >= monthStart && t.TransactionDate < monthEnd)
            .ToListAsync();

        var points = rows
            .GroupBy(t => t.TransactionDate.Date)
            .Select(g => new DailySeriesPointDto(
                new DateTimeOffset(g.Key).ToUnixTimeMilliseconds(),
                g.Where(t => t.Type == "INCOME").Sum(t => t.Amount),
                g.Where(t => t.Type == "EXPENSE").Sum(t => t.Amount)))
            .OrderBy(p => p.Date)
            .ToList();

        return Ok(points);
    }

    [HttpGet("category-breakdown")]
    public async Task<ActionResult<List<CategoryBreakdownDto>>> CategoryBreakdown([FromQuery] string type)
    {
        if (type != "INCOME" && type != "EXPENSE") return BadRequest("type must be INCOME or EXPENSE.");
        var rows = await _db.Transactions
            .Where(t => t.UserId == CurrentUserId && t.Type == type)
            .GroupBy(t => t.Category)
            .Select(g => new CategoryBreakdownDto(g.Key, g.Sum(t => t.Amount)))
            .OrderByDescending(c => c.Total)
            .ToListAsync();
        return Ok(rows);
    }

    [HttpGet("{id}/attachment")]
    public async Task<IActionResult> GetAttachment(Guid id)
    {
        var tx = await _db.Transactions.FirstOrDefaultAsync(t => t.TransactionId == id && t.UserId == CurrentUserId);
        if (tx?.AttachmentBase64 is null) return NotFound();
        return Ok(new { fileName = tx.AttachmentFileName, base64 = tx.AttachmentBase64 });
    }

    private static string? Validate(CreateTransactionRequest r)
    {
        if (r.Type != "INCOME" && r.Type != "EXPENSE") return "Type must be INCOME or EXPENSE.";
        if (r.Amount <= 0) return "Amount must be greater than zero.";
        if (string.IsNullOrWhiteSpace(r.Category)) return "Category is required.";
        if (string.IsNullOrWhiteSpace(r.ClientOrFrom)) return "Client/From is required.";
        if (r.IsRecurring && string.IsNullOrWhiteSpace(r.RecurringFrequency)) return "A frequency is required for recurring transactions.";
        if (r.AttachmentBase64 is not null && r.AttachmentBase64.Length > MaxAttachmentBytes * 4 / 3) // base64 overhead
            return "Attachment must be 2MB or smaller.";
        return null;
    }

    private static TransactionDto ToDto(Transaction t) => new(
        TransactionId: t.TransactionId.ToString(), Type: t.Type, Amount: t.Amount, Currency: t.Currency,
        Category: t.Category, Description: t.Description, ClientOrFrom: t.ClientOrFrom, RelatedProject: t.RelatedProject,
        PaymentMethod: t.PaymentMethod, ReferenceNumber: t.ReferenceNumber, IsRecurring: t.IsRecurring,
        RecurringFrequency: t.RecurringFrequency,
        NextOccurrenceDate: t.NextOccurrenceDate is null ? null : new DateTimeOffset(t.NextOccurrenceDate.Value).ToUnixTimeMilliseconds(),
        AttachmentFileName: t.AttachmentFileName, HasAttachment: t.AttachmentBase64 is not null,
        TransactionDate: new DateTimeOffset(t.TransactionDate).ToUnixTimeMilliseconds()
    );
}