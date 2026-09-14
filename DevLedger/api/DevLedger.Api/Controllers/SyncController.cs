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
/// Receives the batched offline-sync payload from SyncWorker on the Android client —
/// "/api/sync/batch" in the Part 1 endpoint specification. Actually persists each
/// queued operation (Section 5's "Offline Mode with Synchronisation" requirement),
/// resolving snippet conflicts on a last-write-wins basis keyed by the client's
/// updatedAt timestamp, and re-checks the budget-alert threshold so transactions
/// created offline still trigger a push once they land (Google Developers, n.d.a).
/// </summary>
[ApiController]
[Route("api/sync")]
[Authorize]
public class SyncController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    private readonly BudgetAlertService _budgetAlertService;
    private static readonly JsonSerializerOptions JsonOptions = new() { PropertyNameCaseInsensitive = true };

    public SyncController(DevLedgerDbContext db, BudgetAlertService budgetAlertService)
    {
        _db = db;
        _budgetAlertService = budgetAlertService;
    }

    private Guid CurrentUserId => Guid.Parse(User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpPost("batch")]
    public async Task<IActionResult> Batch([FromBody] SyncBatchRequest request)
    {
        var applied = new List<string>();
        var conflicts = new List<string>();
        var transactionsWereApplied = false;

        foreach (var op in request.Operations)
        {
            try
            {
                switch (op.Entity)
                {
                    case "snippet":
                        var snippetApplied = await ApplySnippetAsync(op);
                        (snippetApplied ? applied : conflicts).Add(op.Entity);
                        break;

                    case "transaction":
                        await ApplyTransactionAsync(op);
                        applied.Add(op.Entity);
                        transactionsWereApplied = true;
                        break;

                    default:
                        conflicts.Add(op.Entity);
                        break;
                }
            }
            catch (JsonException)
            {
                // A malformed payload shouldn't fail the whole batch — the rest of the
                // queue still gets applied, and this one is reported as a conflict so
                // the client knows not to mark it SYNCED.
                conflicts.Add(op.Entity);
            }
        }

        await _db.SaveChangesAsync();

        // Budget alerts fire from here too, not just the direct POST /api/transactions
        // path, so an over-budget transaction logged while offline still triggers a
        // push once it syncs — see BudgetAlertService for why this was factored out.
        if (transactionsWereApplied)
        {
            await _budgetAlertService.CheckAndNotifyAsync(CurrentUserId);
        }

        return Ok(new SyncBatchResponse(Applied: applied, Conflicts: conflicts));
    }

    private async Task<bool> ApplySnippetAsync(SyncOperation op)
    {
        var payload = JsonSerializer.Deserialize<SnippetSyncPayload>(op.Payload, JsonOptions)
            ?? throw new JsonException("Empty snippet payload.");

        var snippetId = Guid.Parse(payload.SnippetId);
        var existing = await _db.Snippets.FirstOrDefaultAsync(s => s.SnippetId == snippetId && s.UserId == CurrentUserId);
        var clientUpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.UpdatedAt).UtcDateTime;

        if (existing is null)
        {
            _db.Snippets.Add(new Snippet
            {
                SnippetId = snippetId,
                UserId = CurrentUserId, // never trust payload.UserId — always the authenticated caller
                FolderId = string.IsNullOrEmpty(payload.FolderId) ? null : Guid.Parse(payload.FolderId),
                Title = payload.Title,
                Content = payload.Content,
                Language = payload.Language,
                Description = payload.Description,
                TagsCsv = payload.TagsCsv,
                IsFavourite = payload.IsFavourite,
                IsArchived = payload.IsArchived,
                CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime,
                UpdatedAt = clientUpdatedAt,
            });
            return true;
        }

        if (clientUpdatedAt < existing.UpdatedAt)
        {
            // The server already has a newer edit (e.g. from another device) —
            // last-write-wins means this stale client copy loses the conflict.
            return false;
        }

        existing.Title = payload.Title;
        existing.Content = payload.Content;
        existing.Language = payload.Language;
        existing.Description = payload.Description;
        existing.TagsCsv = payload.TagsCsv;
        existing.IsFavourite = payload.IsFavourite;
        existing.IsArchived = payload.IsArchived;
        existing.FolderId = string.IsNullOrEmpty(payload.FolderId) ? null : Guid.Parse(payload.FolderId);
        existing.UpdatedAt = clientUpdatedAt;
        return true;
    }

    /// <summary>Transactions have no edit endpoint, so this is idempotent-insert-only,
    /// keyed by the client-generated TransactionId so a retried sync never duplicates
    /// a row.</summary>
    private async Task ApplyTransactionAsync(SyncOperation op)
    {
        var payload = JsonSerializer.Deserialize<TransactionSyncPayload>(op.Payload, JsonOptions)
            ?? throw new JsonException("Empty transaction payload.");

        var transactionId = Guid.Parse(payload.TransactionId);
        var alreadyExists = await _db.Transactions.AnyAsync(t => t.TransactionId == transactionId && t.UserId == CurrentUserId);
        if (alreadyExists) return;

        _db.Transactions.Add(new Transaction
        {
            TransactionId = transactionId,
            UserId = CurrentUserId,
            Type = payload.Type,
            Amount = payload.Amount,
            Currency = payload.Currency,
            Category = payload.Category,
            Description = payload.Description,
            TransactionDate = DateTimeOffset.FromUnixTimeMilliseconds(payload.TransactionDate).UtcDateTime,
            CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(payload.CreatedAt).UtcDateTime,
        });
    }

    // Field names are camelCase to match kotlinx.serialization's default output for the
    // Kotlin entities (JetBrains, n.d.); PropertyNameCaseInsensitive above lets these
    // PascalCase record properties bind to that JSON without custom attributes.
    private record SnippetSyncPayload(string SnippetId, string UserId, string? FolderId, string Title, string Content, string Language, string? Description, string TagsCsv, bool IsFavourite, bool IsArchived, long CreatedAt, long UpdatedAt);
    private record TransactionSyncPayload(string TransactionId, string UserId, string Type, decimal Amount, string Currency, string Category, string? Description, long TransactionDate, long CreatedAt);
}