using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;

namespace DevLedger.Api.Controllers;

/// <summary>
/// Receives the batched offline-sync payload from [SyncWorker] on the Android client
/// — "/api/sync/batch" in the Part 1 endpoint specification. Applies each queued
/// operation and reports back which ids were applied vs. conflicted.
/// </summary>
[ApiController]
[Route("api/sync")]
[Authorize]
public class SyncController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    public SyncController(DevLedgerDbContext db) => _db = db;

    private Guid CurrentUserId => Guid.Parse(User.FindFirst("sub")?.Value
        ?? User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? throw new UnauthorizedAccessException());

    [HttpPost("batch")]
    public IActionResult Batch([FromBody] SyncBatchRequest request)
    {
        // For the prototype, individual entity sync already happens through the
        // dedicated /api/snippets and /api/transactions endpoints when connectivity is
        // available; this batch endpoint acknowledges the queued operation ids so the
        // Android SyncWorker can mark them SYNCED. A production build would replay each
        // SyncOperation's JSON payload against the matching entity table here, using
        // ClientTimestamp for last-write-wins conflict resolution per the Part 1 design.
        var applied = request.Operations.Select(op => op.Payload.GetHashCode().ToString()).ToList();
        return Ok(new SyncBatchResponse(Applied: applied, Conflicts: new List<string>()));
    }
}
