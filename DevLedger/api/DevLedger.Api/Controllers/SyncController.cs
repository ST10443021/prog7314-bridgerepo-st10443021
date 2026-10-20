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
/// Supports snippet, financial transaction, folder and tag upserts/deletes.
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

        if (request.Operations.Any(op => op is null)) return BadRequest("A sync operation cannot be null.");
        // Existing Android versions sent snippets before folders. Stage parents/catalogue
        // before dependent records; defer folder parent validation until all upserts exist.
        var changedFolders = new List<Folder>();
        foreach (var operation in request.Operations.OrderBy(OperationPriority))
        {
            if (operation is null)
                return BadRequest("A sync operation cannot be null.");

            if (operation.Action is not ("upsert" or "delete"))
            {
                return BadRequest(
                    "Only upsert and delete operations are supported.");
            }

            if (string.IsNullOrWhiteSpace(operation.Payload))
                return BadRequest("A sync payload is required.");

            if (operation.Action == "delete" && operation.Entity != "snippet")
            {
                var deletionError = await DeleteEntity(operation, userId, cancellationToken);
                if (deletionError is not null) return deletionError;
                var deletePayload = JsonSerializer.Deserialize<DeletePayload>(operation.Payload, JsonOptions)!;
                if (!seenOperations.Add($"{operation.Entity}:{deletePayload.EntityId}"))
                    return BadRequest("A batch cannot contain duplicate entity IDs.");
                applied.Add(deletePayload.EntityId.ToString());
                continue;
            }

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

                    if (payload.FolderId is not null)
                    {
                        var parent = await _db.Folders.FindAsync(new object[] { payload.FolderId.Value }, cancellationToken);
                        if (parent is not null && parent.UserId != userId) return Forbid();
                        if (parent is null)
                        {
                            var removed = await _db.DeletedSyncEntities.FindAsync(
                                new object[] { "folder", payload.FolderId.Value }, cancellationToken);
                            if (removed is null) return BadRequest("The snippet folder does not exist. Upload folders first.");
                            if (removed.UserId != userId) return Forbid();
                        }
                        snippet.FolderId = parent?.FolderId;
                    }
                    else snippet.FolderId = null;
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

                    var deletedTransaction = await _db.DeletedSyncEntities.FindAsync(
                        new object[] { "transaction", payload.TransactionId }, cancellationToken);
                    if (deletedTransaction is not null)
                    {
                        if (deletedTransaction.UserId != userId) return Forbid();
                        applied.Add(payload.TransactionId.ToString());
                        break;
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

                    if (string.IsNullOrWhiteSpace(payload.ClientOrFrom) ||
                        (payload.IsRecurring && string.IsNullOrWhiteSpace(payload.RecurringFrequency)))
                        return BadRequest("Client/From and recurring frequency (when recurring) are required.");

                    if (payload.CreatedAt != operation.ClientTimestamp)
                    {
                        return BadRequest("The transaction client timestamp is invalid.");
                    }

                    DateTime createdAt;
                    DateTime transactionDate;
                    DateTime? nextOccurrenceDate;

                    try
                    {
                        createdAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime;
                        transactionDate = DateTimeOffset.FromUnixTimeMilliseconds(payload.TransactionDate).UtcDateTime;
                        nextOccurrenceDate = payload.NextOccurrenceDate is null ? null :
                            DateTimeOffset.FromUnixTimeMilliseconds(payload.NextOccurrenceDate.Value).UtcDateTime;
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
                            NextOccurrenceDate = nextOccurrenceDate,
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
                        transaction.NextOccurrenceDate = nextOccurrenceDate;
                        transaction.AttachmentFileName = payload.AttachmentFileName;
                        transaction.AttachmentBase64 = payload.AttachmentBase64;
                        transaction.TransactionDate = transactionDate;
                    }

                    applied.Add(payload.TransactionId.ToString());

                    break;
                }

                case "folder":
                {
                    FolderPayload? payload;
                    try { payload = JsonSerializer.Deserialize<FolderPayload>(operation.Payload, JsonOptions); }
                    catch (JsonException) { return BadRequest("Invalid folder payload."); }
                    if (payload is null || payload.FolderId == Guid.Empty || string.IsNullOrWhiteSpace(payload.Name))
                        return BadRequest("A folder ID and name are required.");
                    if (payload.UserId != userId) return Forbid();
                    if (!seenOperations.Add($"folder:{payload.FolderId}")) return BadRequest("Duplicate folder ID.");
                    var removed = await _db.DeletedSyncEntities.FindAsync(new object[] { "folder", payload.FolderId }, cancellationToken);
                    if (removed is not null)
                    {
                        if (removed.UserId != userId) return Forbid();
                        applied.Add(payload.FolderId.ToString()); break;
                    }
                    if (!ValidTimestamp(payload.CreatedAt) || payload.CreatedAt != operation.ClientTimestamp)
                        return BadRequest("Invalid folder timestamp.");
                    var folder = await _db.Folders.FindAsync(new object[] { payload.FolderId }, cancellationToken);
                    if (folder is not null && folder.UserId != userId) return Forbid();
                    if (folder is null)
                    {
                        folder = new Folder { FolderId = payload.FolderId, UserId = userId,
                            CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime };
                        _db.Folders.Add(folder);
                    }
                    folder.Name = payload.Name.Trim();
                    folder.ParentFolderId = payload.ParentFolderId;
                    changedFolders.Add(folder);
                    applied.Add(payload.FolderId.ToString());
                    break;
                }
                case "tag":
                {
                    TagPayload? payload;
                    try { payload = JsonSerializer.Deserialize<TagPayload>(operation.Payload, JsonOptions); }
                    catch (JsonException) { return BadRequest("Invalid tag payload."); }
                    if (payload is null || payload.TagId == Guid.Empty || string.IsNullOrWhiteSpace(payload.Name))
                        return BadRequest("A tag ID and name are required.");
                    if (payload.UserId != userId) return Forbid();
                    if (!seenOperations.Add($"tag:{payload.TagId}")) return BadRequest("Duplicate tag ID.");
                    var removed = await _db.DeletedSyncEntities.FindAsync(new object[] { "tag", payload.TagId }, cancellationToken);
                    if (removed is not null)
                    {
                        if (removed.UserId != userId) return Forbid();
                        applied.Add(payload.TagId.ToString()); break;
                    }
                    if (!ValidTimestamp(payload.CreatedAt) || payload.CreatedAt != operation.ClientTimestamp)
                        return BadRequest("Invalid tag timestamp.");
                    if (payload.ColorHex is not null && !System.Text.RegularExpressions.Regex.IsMatch(payload.ColorHex, "^#[0-9a-fA-F]{6}$"))
                        return BadRequest("Tag colour must be #RRGGBB.");
                    var tag = await _db.UserTags.FindAsync(new object[] { payload.TagId }, cancellationToken);
                    if (tag is not null && tag.UserId != userId) return Forbid();
                    if (tag is null)
                    {
                        tag = new UserTag { TagId = payload.TagId, UserId = userId,
                            CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime };
                        _db.UserTags.Add(tag);
                    }
                    tag.Name = payload.Name.Trim(); tag.ColorHex = payload.ColorHex;
                    applied.Add(payload.TagId.ToString());
                    break;
                }
                default:
                    return BadRequest($"Unsupported sync entity '{operation.Entity}'.");
            }
        }

        foreach (var folder in changedFolders)
        {
            if (_db.Entry(folder).State == EntityState.Deleted) continue;
            var visited = new HashSet<Guid> { folder.FolderId };
            var parentId = folder.ParentFolderId;
            while (parentId is not null)
            {
                if (!visited.Add(parentId.Value)) return BadRequest("Folder parents cannot form a cycle.");
                var parent = await _db.Folders.FindAsync(new object[] { parentId.Value }, cancellationToken);
                if (parent is null || _db.Entry(parent).State == EntityState.Deleted)
                {
                    var removed = await _db.DeletedSyncEntities.FindAsync(new object[] { "folder", parentId.Value }, cancellationToken);
                    if (removed is null) return BadRequest("A parent folder does not exist.");
                    if (removed.UserId != userId) return Forbid();
                    folder.ParentFolderId = null;
                    break;
                }
                if (parent.UserId != userId) return Forbid();
                parentId = parent.ParentFolderId;
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

    [HttpGet("deletions")]
    public async Task<IActionResult> Deletions(CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(User.FindFirst("sub")?.Value ?? User.FindFirstValue(ClaimTypes.NameIdentifier), out var userId))
            return Unauthorized();
        var markers = await _db.DeletedSyncEntities.AsNoTracking().Where(d => d.UserId == userId)
            .ToListAsync(cancellationToken);
        return Ok(markers.Select(d => new { d.EntityType, entityId = d.EntityId.ToString() }));
    }

    private static int OperationPriority(SyncOperation operation) => operation.Action == "delete" ? 4 :
        operation.Entity switch { "folder" => 0, "tag" => 1, "snippet" => 2, _ => 3 };

    private static bool ValidTimestamp(long value)
    {
        if (value < 0) return false;
        try { _ = DateTimeOffset.FromUnixTimeMilliseconds(value); return true; }
        catch (ArgumentOutOfRangeException) { return false; }
    }

    private async Task<ActionResult<SyncBatchResponse>?> DeleteEntity(
        SyncOperation operation, Guid userId, CancellationToken cancellationToken)
    {
        if (operation.Entity is not ("folder" or "tag" or "transaction")) return BadRequest("Unsupported deletion entity.");
        DeletePayload? payload;
        try { payload = JsonSerializer.Deserialize<DeletePayload>(operation.Payload, JsonOptions); }
        catch (JsonException) { return BadRequest("Invalid deletion payload."); }
        if (payload is null || payload.EntityId == Guid.Empty || !ValidTimestamp(operation.ClientTimestamp))
            return BadRequest("A valid entity ID and deletion timestamp are required.");
        if (payload.UserId.HasValue && payload.UserId != userId) return Forbid();
        var marker = await _db.DeletedSyncEntities.FindAsync(new object[] { operation.Entity, payload.EntityId }, cancellationToken);
        if (marker is not null)
        {
            if (marker.UserId != userId) return Forbid();
            // Return the nullable wrapper itself; a ternary with Forbid() can
            // invoke MVC's implicit ActionResult conversion on a null value.
            return null;
        }
        switch (operation.Entity)
        {
            case "transaction":
                var tx = await _db.Transactions.FindAsync(new object[] { payload.EntityId }, cancellationToken);
                if (tx is not null && tx.UserId != userId) return Forbid();
                if (tx is not null) _db.Transactions.Remove(tx);
                break;
            case "tag":
                var tag = await _db.UserTags.FindAsync(new object[] { payload.EntityId }, cancellationToken);
                if (tag is not null && tag.UserId != userId) return Forbid();
                if (tag is not null) _db.UserTags.Remove(tag);
                // Catalogue deletion does not remove free-text tag assignments from snippets.
                break;
            case "folder":
                var folder = await _db.Folders.FindAsync(new object[] { payload.EntityId }, cancellationToken);
                if (folder is not null && folder.UserId != userId) return Forbid();
                Guid? targetId = payload.ReassignToFolderId;
                if (targetId == payload.EntityId) return BadRequest("A folder cannot be reassigned to itself.");
                if (targetId.HasValue)
                {
                    var target = await _db.Folders.FindAsync(new object[] { targetId.Value }, cancellationToken);
                    if (target is not null && target.UserId != userId) return Forbid();
                    if (target is null || _db.Entry(target).State == EntityState.Deleted)
                    {
                        var removedTarget = await _db.DeletedSyncEntities.FindAsync(new object[] { "folder", targetId.Value }, cancellationToken);
                        if (removedTarget is null) return BadRequest("The reassignment folder does not exist.");
                        if (removedTarget.UserId != userId) return Forbid();
                        targetId = null;
                    }
                }
                var snippets = await _db.Snippets.Where(s => s.UserId == userId && s.FolderId == payload.EntityId).ToListAsync(cancellationToken);
                foreach (var snippet in snippets.Concat(_db.Snippets.Local.Where(s => s.UserId == userId && s.FolderId == payload.EntityId)).Distinct())
                {
                    snippet.FolderId = targetId;
                    snippet.UpdatedAt = DateTime.UtcNow;
                }
                var children = await _db.Folders.Where(f => f.UserId == userId && f.ParentFolderId == payload.EntityId).ToListAsync(cancellationToken);
                foreach (var child in children.Concat(_db.Folders.Local.Where(f => f.UserId == userId && f.ParentFolderId == payload.EntityId)).Distinct())
                    child.ParentFolderId = folder?.ParentFolderId;
                if (folder is not null) _db.Folders.Remove(folder);
                break;
        }
        _db.DeletedSyncEntities.Add(new DeletedSyncEntity {
            EntityType = operation.Entity, EntityId = payload.EntityId, UserId = userId,
        });
        return null;
    }

    private sealed record DeletePayload(Guid EntityId, Guid? UserId = null, Guid? ReassignToFolderId = null);
    private sealed record FolderPayload(Guid FolderId, Guid UserId, string? Name, Guid? ParentFolderId, long CreatedAt);
    private sealed record TagPayload(Guid TagId, Guid UserId, string? Name, string? ColorHex, long CreatedAt);

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
