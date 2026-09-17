using System.Security.Claims;
using System.Text.Json;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Persists queued offline changes for the authenticated user.
/// Supports snippet upserts/deletes and financial-transaction upserts.
/// Unsupported operations are rejected rather than falsely acknowledged.
/// </summary>
[ApiController]
[Route("api/sync")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class SyncController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    public SyncController(DevLedgerDbContext db)
    {
        _db = db;
    }

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
                user => user.UserId == userId,
                cancellationToken))
        {
            return Unauthorized();
        }

        if (request.Operations is null)
            return BadRequest("An operations list is required.");

        if (request.Operations.Count > 100)
            return BadRequest("Send at most 100 operations per batch.");

        await using var transactionScope = await SnippetMutationLock.BeginAsync(_db, userId, cancellationToken);

        var applied = new List<string>();

        var seenOperations = new HashSet<string>(
            StringComparer.OrdinalIgnoreCase);

        foreach (var operation in request.Operations)
        {
            if (operation is null)
                return BadRequest("A sync operation cannot be null.");

            if (operation.Action != "upsert" && !(operation.Entity == "snippet" && operation.Action == "delete"))
            {
                return BadRequest(
                    "Only upsert and snippet delete operations are supported.");
            }

            if (string.IsNullOrWhiteSpace(operation.Payload))
                return BadRequest("A sync payload is required.");

            switch (operation.Entity)
            {
                case "snippet":
                {
                    SnippetSyncPayload? payload;

                    try
                    {
                        payload = JsonSerializer.Deserialize<SnippetSyncPayload>(
                            operation.Payload,
                            JsonOptions);
                    }
                    catch (JsonException)
                    {
                        return BadRequest(
                            "A snippet payload contains invalid JSON or values.");
                    }

                    if (payload is null || payload.SnippetId == Guid.Empty)
                        return BadRequest("A valid snippet ID is required.");

                    if (payload.UserId != userId)
                        return Forbid();

                    var operationKey = $"snippet:{payload.SnippetId}";

                    if (!seenOperations.Add(operationKey))
                    {
                        return BadRequest(
                            "A batch cannot contain duplicate snippet IDs.");
                    }

                    var deleted = await _db.Set<DeletedSnippet>().SingleOrDefaultAsync(
                        item => item.SnippetId == payload.SnippetId, cancellationToken);
                    if (deleted is not null && deleted.UserId != userId) return Forbid();

                    // A permanent deletion wins over an old offline edit or restore.
                    // Acknowledge retries; the client's snapshot pull removes stale copies.
                    if (deleted is not null)
                    {
                        applied.Add(payload.SnippetId.ToString());
                        break;
                    }

                    if (operation.Action == "delete")
                    {
                        var toDelete = await _db.Snippets.SingleOrDefaultAsync(
                            item => item.SnippetId == payload.SnippetId, cancellationToken);
                        if (toDelete is not null && toDelete.UserId != userId) return Forbid();
                        if (toDelete is not null) _db.Snippets.Remove(toDelete);
                        // Also records deletes of snippets created and deleted offline.
                        _db.Set<DeletedSnippet>().Add(new DeletedSnippet {
                            SnippetId = payload.SnippetId, UserId = userId,
                        });
                        applied.Add(payload.SnippetId.ToString());
                        break;
                    }

                    if (string.IsNullOrWhiteSpace(payload.Title) ||
                        string.IsNullOrWhiteSpace(payload.Content) ||
                        string.IsNullOrWhiteSpace(payload.Language))
                    {
                        return BadRequest(
                            "Each snippet requires a title, content and language.");
                    }

                    if (payload.UpdatedAt != operation.ClientTimestamp ||
                        payload.CreatedAt < 0 ||
                        payload.UpdatedAt < payload.CreatedAt)
                    {
                        return BadRequest(
                            "The snippet timestamps are invalid.");
                    }

                    try
                    {
                        _ = DateTimeOffset.FromUnixTimeMilliseconds(
                            payload.CreatedAt);

                        _ = DateTimeOffset.FromUnixTimeMilliseconds(
                            payload.UpdatedAt);
                    }
                    catch (ArgumentOutOfRangeException)
                    {
                        return BadRequest(
                            "The snippet timestamps are outside the supported range.");
                    }

                    var snippet = await _db.Snippets.SingleOrDefaultAsync(
                        item => item.SnippetId == payload.SnippetId,
                        cancellationToken);

                    if (snippet is not null &&
                        snippet.UserId != userId)
                    {
                        return Forbid();
                    }

                    if (snippet is not null)
                    {
                        var serverTimestamp =
                            new DateTimeOffset(snippet.UpdatedAt)
                                .ToUnixTimeMilliseconds();

                        if (serverTimestamp > payload.UpdatedAt ||
                            (serverTimestamp == payload.UpdatedAt &&
                             !HasSameSnippetContent(snippet, payload)))
                        {
                            return Conflict(
                                new SyncBatchResponse(
                                    Applied: new List<string>(),
                                    Conflicts: new List<string>
                                    {
                                        payload.SnippetId.ToString()
                                    }));
                        }
                    }
                    else
                    {
                        snippet = new Snippet
                        {
                            SnippetId = payload.SnippetId,
                            UserId = userId,
                            CreatedAt = DateTimeOffset
                                .FromUnixTimeMilliseconds(payload.CreatedAt)
                                .UtcDateTime,
                        };

                        _db.Snippets.Add(snippet);
                    }

                    snippet.FolderId = payload.FolderId;
                    snippet.Title = payload.Title!;
                    snippet.Content = payload.Content!;
                    snippet.Language = payload.Language!;
                    snippet.Description = payload.Description;
                    snippet.TagsCsv = PayloadTags(payload);
                    snippet.IsFavourite = payload.IsFavourite;
                    snippet.IsArchived = payload.IsArchived;
                    snippet.UpdatedAt = DateTimeOffset
                        .FromUnixTimeMilliseconds(payload.UpdatedAt)
                        .UtcDateTime;

                    applied.Add(payload.SnippetId.ToString());

                    break;
                }

                case "transaction":
                {
                    TransactionSyncPayload? payload;

                    try
                    {
                        payload = JsonSerializer.Deserialize<TransactionSyncPayload>(
                            operation.Payload,
                            JsonOptions);
                    }
                    catch (JsonException)
                    {
                        return BadRequest("A transaction payload contains invalid JSON or values.");
                    }

                    if (payload is null || payload.TransactionId == Guid.Empty)
                    {
                        return BadRequest("A valid transaction ID is required.");
                    }

                    if (payload.UserId != userId)
                        return Forbid();

                    var operationKey = $"transaction:{payload.TransactionId}";

                    if (!seenOperations.Add(operationKey))
                    {
                        return BadRequest("A batch cannot contain duplicate transaction IDs.");
                    }

                    if (payload.Type is not ("INCOME" or "EXPENSE"))
                    {
                        return BadRequest("Transaction type must be INCOME or EXPENSE.");
                    }

                    if (payload.Amount <= 0)
                    {
                        return BadRequest("Transaction amount must be greater than zero.");
                    }

                    if (string.IsNullOrWhiteSpace(payload.Currency) || payload.Currency.Trim().Length != 3)
                    {
                        return BadRequest("A valid three-letter currency code is required.");
                    }

                    if (string.IsNullOrWhiteSpace(payload.Category))
                    {
                        return BadRequest("Transaction category is required.");
                    }

                    if (payload.CreatedAt != operation.ClientTimestamp)
                    {
                        return BadRequest("The transaction client timestamp is invalid.");
                    }

                    DateTime createdAt;
                    DateTime transactionDate;

                    try
                    {
                        createdAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime;
                        transactionDate = DateTimeOffset.FromUnixTimeMilliseconds(payload.TransactionDate).UtcDateTime;
                    }
                    catch (ArgumentOutOfRangeException)
                    {
                        return BadRequest("The transaction timestamps are outside the supported range.");
                    }

                    var transaction = await _db.Transactions.SingleOrDefaultAsync(
                        item => item.TransactionId == payload.TransactionId,
                        cancellationToken);

                    if (transaction is not null && transaction.UserId != userId)
                    {
                        return Forbid();
                    }

                    if (transaction is null)
                    {
                        transaction = new Transaction
                        {
                            TransactionId = payload.TransactionId,
                            UserId = userId,
                            Type = payload.Type,
                            Amount = payload.Amount,
                            Currency = payload.Currency.Trim().ToUpperInvariant(),
                            Category = payload.Category.Trim(),
                            Description = string.IsNullOrWhiteSpace(payload.Description) ? null : payload.Description.Trim(),
                            ClientOrFrom = payload.ClientOrFrom,
                            RelatedProject = payload.RelatedProject,
                            PaymentMethod = payload.PaymentMethod,
                            ReferenceNumber = payload.ReferenceNumber,
                            IsRecurring = payload.IsRecurring,
                            RecurringFrequency = payload.RecurringFrequency,
                            NextOccurrenceDate = payload.NextOccurrenceDate is null
                                ? null
                                : DateTimeOffset.FromUnixTimeMilliseconds(payload.NextOccurrenceDate.Value).UtcDateTime,
                            AttachmentFileName = payload.AttachmentFileName,
                            AttachmentBase64 = payload.AttachmentBase64,
                            TransactionDate = transactionDate,
                            CreatedAt = createdAt,
                        };

                        _db.Transactions.Add(transaction);
                    }
                    else
                    {
                        transaction.Type = payload.Type;
                        transaction.Amount = payload.Amount;
                        transaction.Currency = payload.Currency.Trim().ToUpperInvariant();
                        transaction.Category = payload.Category.Trim();
                        transaction.Description = string.IsNullOrWhiteSpace(payload.Description) ? null : payload.Description.Trim();
                        transaction.ClientOrFrom = payload.ClientOrFrom;
                        transaction.RelatedProject = payload.RelatedProject;
                        transaction.PaymentMethod = payload.PaymentMethod;
                        transaction.ReferenceNumber = payload.ReferenceNumber;
                        transaction.IsRecurring = payload.IsRecurring;
                        transaction.RecurringFrequency = payload.RecurringFrequency;
                        transaction.NextOccurrenceDate = payload.NextOccurrenceDate is null
                            ? null
                            : DateTimeOffset.FromUnixTimeMilliseconds(payload.NextOccurrenceDate.Value).UtcDateTime;
                        transaction.AttachmentFileName = payload.AttachmentFileName;
                        transaction.AttachmentBase64 = payload.AttachmentBase64;
                        transaction.TransactionDate = transactionDate;
                    }

                    applied.Add(payload.TransactionId.ToString());

                    break;
                }

                default:
                    return BadRequest($"Unsupported sync entity '{operation.Entity}'.");
            }
        }

        // EF Core commits the complete batch atomically.
        await _db.SaveChangesAsync(cancellationToken);
        await transactionScope.CommitAsync(cancellationToken);

        return Ok(
            new SyncBatchResponse(
                Applied: applied,
                Conflicts: new List<string>()));
    }

    private static string PayloadTags(SnippetSyncPayload payload) =>
        payload.TagsCsv ?? string.Join(",", payload.Tags ?? new List<string>());

    private static bool HasSameSnippetContent(
        Snippet snippet,
        SnippetSyncPayload payload) =>
        snippet.FolderId == payload.FolderId &&
        snippet.Title == payload.Title &&
        snippet.Content == payload.Content &&
        snippet.Language == payload.Language &&
        snippet.Description == payload.Description &&
        snippet.TagsCsv == (PayloadTags(payload)) &&
        snippet.IsFavourite == payload.IsFavourite &&
        snippet.IsArchived == payload.IsArchived;

    public sealed record SnippetSyncPayload(
        Guid SnippetId,
        Guid UserId,
        Guid? FolderId,
        string? Title,
        string? Content,
        string? Language,
        string? Description,
        string? TagsCsv,
        bool IsFavourite,
        bool IsArchived,
        long CreatedAt,
        long UpdatedAt,
        List<string>? Tags = null);

    public sealed record TransactionSyncPayload(
        Guid TransactionId,
        Guid UserId,
        string Type,
        decimal Amount,
        string Currency,
        string Category,
        string? Description,
        string ClientOrFrom,
        string? RelatedProject,
        string? PaymentMethod,
        string? ReferenceNumber,
        bool IsRecurring,
        string? RecurringFrequency,
        long? NextOccurrenceDate,
        string? AttachmentFileName,
        string? AttachmentBase64,
        long TransactionDate,
        long CreatedAt);
}
