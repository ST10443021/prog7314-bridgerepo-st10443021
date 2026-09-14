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
/// Persists queued snippet upserts for the authenticated user.
/// Unsupported operations are rejected rather than falsely acknowledged.
/// </summary>
[ApiController]
[Route("api/sync")]
[Authorize]
public class SyncController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly BudgetAlertService _budgetAlertService;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    private static readonly JsonSerializerOptions JsonOptions =
        new(JsonSerializerDefaults.Web);

    public SyncController(DevLedgerDbContext db) => _db = db;

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

        // Validate the supported operation types before processing any records.
        if (request.Operations.Any(operation =>
                operation is null ||
                operation.Entity != "snippet" ||
                operation.Action != "upsert"))
        {
            return BadRequest(
                "This endpoint currently supports snippet upserts only.");
        }

        var payloads = new List<SnippetSyncPayload>();
        var seenIds = new HashSet<Guid>();

        foreach (var operation in request.Operations)
        {
            if (string.IsNullOrWhiteSpace(operation.Payload))
                return BadRequest("A snippet payload is required.");

            SnippetSyncPayload? payload;

            try
            {
                payload = JsonSerializer.Deserialize<SnippetSyncPayload>(
                    operation.Payload, JsonOptions);
            }
            catch (JsonException)
            {
                return BadRequest("A snippet payload contains invalid JSON or values.");
            }

            if (payload is null || payload.SnippetId == Guid.Empty)
                return BadRequest("A valid snippet ID is required.");

            // Never trust a payload to select the account that owns the record.
            if (payload.UserId != userId)
                return Forbid();

            if (!seenIds.Add(payload.SnippetId))
                return BadRequest("A batch cannot contain duplicate snippet IDs.");

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
                return BadRequest("The snippet timestamps are invalid.");
            }

            try
            {
                _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt);
                _ = DateTimeOffset.FromUnixTimeMilliseconds(payload.UpdatedAt);
            }
            catch (ArgumentOutOfRangeException)
            {
                return BadRequest("The snippet timestamps are outside the supported range.");
            }

            payloads.Add(payload);
        }

        var applied = new List<string>();

        foreach (var payload in payloads)
        {
            // Search by ID first so an ID belonging to another account cannot
            // be treated as a new record or overwritten.
            var snippet = await _db.Snippets.SingleOrDefaultAsync(
                item => item.SnippetId == payload.SnippetId,
                cancellationToken);

            if (snippet is not null && snippet.UserId != userId)
                return Forbid();

            if (snippet is not null)
            {
                var serverTimestamp = new DateTimeOffset(snippet.UpdatedAt)
                    .ToUnixTimeMilliseconds();

                if (serverTimestamp > payload.UpdatedAt ||
                    (serverTimestamp == payload.UpdatedAt &&
                     !HasSameContent(snippet, payload)))
                {
                    // No SaveChanges has occurred, so the entire batch remains
                    // unapplied when a conflict is found.
                    return Conflict(new SyncBatchResponse(
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
            snippet.TagsCsv = payload.TagsCsv ?? string.Empty;
            snippet.IsFavourite = payload.IsFavourite;
            snippet.IsArchived = payload.IsArchived;
            snippet.UpdatedAt = DateTimeOffset
                .FromUnixTimeMilliseconds(payload.UpdatedAt)
                .UtcDateTime;

            applied.Add(payload.SnippetId.ToString());
        }

        // EF Core commits this SaveChanges operation atomically.
        // Acknowledgements are returned only after persistence succeeds.
        await _db.SaveChangesAsync(cancellationToken);

        return Ok(new SyncBatchResponse(
            Applied: applied,
            Conflicts: new List<string>()));
    }

    private static bool HasSameContent(
        Snippet snippet,
        SnippetSyncPayload payload) =>
        snippet.FolderId == payload.FolderId &&
        snippet.Title == payload.Title &&
        snippet.Content == payload.Content &&
        snippet.Language == payload.Language &&
        snippet.Description == payload.Description &&
        snippet.TagsCsv == (payload.TagsCsv ?? string.Empty) &&
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
        long UpdatedAt);
}
