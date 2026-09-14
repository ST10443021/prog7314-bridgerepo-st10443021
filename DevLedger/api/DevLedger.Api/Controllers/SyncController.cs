using System.Security.Claims;
using System.Text.Json;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Persists queued snippet and transaction upserts for the authenticated user —
/// "/api/sync/batch" in the Part 1 endpoint specification.
/// </summary>
[ApiController]
[Route("api/sync")]
[Authorize]
public class SyncController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly BudgetAlertService _budgetAlertService;
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public SyncController(DevLedgerDbContext db, BudgetAlertService budgetAlertService) =>
        (_db, _budgetAlertService) = (db, budgetAlertService);

    [HttpPost("batch")]
    public async Task<ActionResult<SyncBatchResponse>> Batch(
        [FromBody] SyncBatchRequest request,
        CancellationToken cancellationToken)
    {
        var subject = User.FindFirst("sub")?.Value
            ?? User.FindFirstValue(ClaimTypes.NameIdentifier);

        if (!Guid.TryParse(subject, out var userId))
            return Unauthorized();

        if (!await _db.Users.AnyAsync(
                user => user.UserId == userId, cancellationToken))
            return Unauthorized();

        if (request.Operations is null)
            return BadRequest("An operations list is required.");

        if (request.Operations.Count > 100)
            return BadRequest("Send at most 100 operations per batch.");

        if (request.Operations.Any(operation =>
                operation is null ||
                operation.Action != "upsert" ||
                (operation.Entity != "snippet" && operation.Entity != "transaction")))
        {
            return BadRequest("This endpoint supports snippet and transaction upserts only.");
        }

        var snippetPayloads = new List<SnippetSyncPayload>();
        var transactionPayloads = new List<TransactionSyncPayload>();
        var seenSnippetIds = new HashSet<Guid>();
        var seenTransactionIds = new HashSet<Guid>();

        // Pass 1: validate every payload before touching the database
        foreach (var operation in request.Operations)
        {
            if (string.IsNullOrWhiteSpace(operation.Payload))
                return BadRequest("A payload is required for every operation.");

            if (operation.Entity == "snippet")
            {
                SnippetSyncPayload? payload;
                try
                {
                    payload = JsonSerializer.Deserialize<SnippetSyncPayload>(operation.Payload, JsonOptions);
                }
                catch (JsonException)
                {
                    return BadRequest("A snippet payload contains invalid JSON or values.");
                }

                if (payload is null || payload.SnippetId == Guid.Empty)
                    return BadRequest("A valid snippet ID is required.");
                if (payload.UserId != userId)
                    return Forbid();
                if (!seenSnippetIds.Add(payload.SnippetId))
                    return BadRequest("A batch cannot contain duplicate snippet IDs.");
                if (string.IsNullOrWhiteSpace(payload.Title) || string.IsNullOrWhiteSpace(payload.Content) || string.IsNullOrWhiteSpace(payload.Language))
                    return BadRequest("Each snippet requires a title, content and language.");
                if (payload.UpdatedAt != operation.ClientTimestamp || payload.CreatedAt < 0 || payload.UpdatedAt < payload.CreatedAt)
                    return BadRequest("The snippet timestamps are invalid.");

                try
                {
                    _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt);
                    _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.UpdatedAt);
                }
                catch (ArgumentOutOfRangeException)
                {
                    return BadRequest("The snippet timestamps are outside the supported range.");
                }

                snippetPayloads.Add(payload);
            }
            else // "transaction"
            {
                TransactionSyncPayload? payload;
                try
                {
                    payload = JsonSerializer.Deserialize<TransactionSyncPayload>(operation.Payload, JsonOptions);
                }
                catch (JsonException)
                {
                    return BadRequest("A transaction payload contains invalid JSON or values.");
                }

                if (payload is null || payload.TransactionId == Guid.Empty)
                    return BadRequest("A valid transaction ID is required.");
                if (payload.UserId != userId)
                    return Forbid();
                if (!seenTransactionIds.Add(payload.TransactionId))
                    return BadRequest("A batch cannot contain duplicate transaction IDs.");
                if (payload.Type != "INCOME" && payload.Type != "EXPENSE")
                    return BadRequest("Each transaction requires type INCOME or EXPENSE.");
                if (payload.Amount <= 0 || string.IsNullOrWhiteSpace(payload.Category) || string.IsNullOrWhiteSpace(payload.Currency))
                    return BadRequest("Each transaction requires a positive amount, category and currency.");
                if (payload.CreatedAt != operation.ClientTimestamp || payload.CreatedAt < 0 || payload.TransactionDate < 0)
                    return BadRequest("The transaction timestamps are invalid.");

                try
                {
                    _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt);
                    _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.TransactionDate);
                }
                catch (ArgumentOutOfRangeException)
                {
                    return BadRequest("The transaction timestamps are outside the supported range.");
                }

                transactionPayloads.Add(payload);
            }
        }

        var applied = new List<string>();
        var conflicts = new List<string>();

        // Pass 2: apply snippets (Last-Write-Wins based on client timestamp)
        foreach (var payload in snippetPayloads)
        {
            var snippet = await _db.Snippets.FirstOrDefaultAsync(s => s.SnippetId == payload.SnippetId, cancellationToken);
            if (snippet != null)
            {
                var payloadUpdatedAtUtc = DateTimeOffset.FromUnixTimeMilliseconds(payload.UpdatedAt).UtcDateTime;
                if (snippet.UpdatedAt > payloadUpdatedAtUtc && !HasSameContent(snippet, payload))
                {
                    conflicts.Add(payload.SnippetId.ToString());
                    continue;
                }
            }
            else
            {
                snippet = new Snippet
                {
                    SnippetId = payload.SnippetId,
                    UserId = userId,
                    CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime,
                };
                _db.Snippets.Add(snippet);
            }

            snippet.FolderId = payload.FolderId;
            snippet.Title = payload.Title!;
            snippet.Content = payload.Content!;
            snippet.Language = payload.Language!;
            snippet.Description = payload.Description;
            snippet.TagsCsv = payload.TagsCsv ?? string.Empty;
            snippet.IsFavourite = payload.IsFavourite;
            snippet.IsArchived = payload.IsArchived;
            snippet.UpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.UpdatedAt).UtcDateTime;

            applied.Add(payload.SnippetId.ToString());
        }

        if (conflicts.Count > 0)
        {
            return Ok(new SyncBatchResponse(Applied: new List<string>(), Conflicts: conflicts));
        }

        // Pass 3: apply transactions
        foreach (var payload in transactionPayloads)
        {
            var alreadyExists = await _db.Transactions.AnyAsync(t => t.TransactionId == payload.TransactionId, cancellationToken);
            if (alreadyExists)
            {
                applied.Add(payload.TransactionId.ToString());
                continue;
            }

            _db.Transactions.Add(new Transaction
            {
                TransactionId = payload.TransactionId,
                UserId = userId,
                Type = payload.Type,
                Amount = payload.Amount,
                Currency = payload.Currency,
                Category = payload.Category,
                Description = payload.Description,
                TransactionDate = DateTimeOffset.FromUnixTimeMilliseconds(payload.TransactionDate).UtcDateTime,
                CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime,
            });
            applied.Add(payload.TransactionId.ToString());
        }

        await _db.SaveChangesAsync(cancellationToken);

        if (transactionPayloads.Count > 0)
            await _budgetAlertService.CheckAndNotifyAsync(userId);

        return Ok(new SyncBatchResponse(Applied: applied, Conflicts: new List<string>()));
    }

    private static bool HasSameContent(Snippet snippet, SnippetSyncPayload payload) =>
        snippet.FolderId == payload.FolderId &&
        snippet.Title == payload.Title &&
        snippet.Content == payload.Content &&
        snippet.Language == payload.Language &&
        snippet.Description == payload.Description &&
        snippet.TagsCsv == (payload.TagsCsv ?? string.Empty) &&
        snippet.IsFavourite == payload.IsFavourite &&
        snippet.IsArchived == payload.IsArchived;

    public sealed record SnippetSyncPayload(
        Guid SnippetId, Guid UserId, Guid? FolderId, string? Title, string? Content, string? Language,
        string? Description, string? TagsCsv, bool IsFavourite, bool IsArchived, long CreatedAt, long UpdatedAt);

    public sealed record TransactionSyncPayload(
        Guid TransactionId, Guid UserId, string Type, decimal Amount, string Currency, string Category,
        string? Description, long TransactionDate, long CreatedAt);
}