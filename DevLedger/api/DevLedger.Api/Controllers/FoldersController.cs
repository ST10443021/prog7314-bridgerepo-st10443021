using System.Security.Claims;
using System.Text.Json;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>Folder CRUD for the Snippet Vault's organisational structure. Deleting a
/// folder always requires an explicit reassignment target for its snippets — never a
/// silent delete-with-contents — per the Folder and Tag Management Screen's specified
/// behaviour in the Part 1 design document.</summary>
[ApiController]
[Route("api/folders")]
[Authorize]
/// <remarks>
/// Reference scope: ASP.NET Core controller, routing and HTTP response patterns.
/// (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class FoldersController : ControllerBase
{
    private readonly DevLedgerDbContext _db;
    public FoldersController(DevLedgerDbContext db) => _db = db;

    private Guid CurrentUserId => Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? User.FindFirst("sub")?.Value
        ?? throw new UnauthorizedAccessException());

    [HttpGet]
    public async Task<ActionResult<List<FolderDto>>> GetAll()
    {
        var folders = await _db.Folders.Where(f => f.UserId == CurrentUserId).OrderBy(f => f.Name).ToListAsync();
        var counts = await _db.Snippets
            .Where(s => s.UserId == CurrentUserId && !s.IsArchived && s.FolderId != null)
            .GroupBy(s => s.FolderId)
            .Select(g => new { FolderId = g.Key, Count = g.Count() })
            .ToDictionaryAsync(g => g.FolderId, g => g.Count);

        return Ok(folders.Select(f => ToDto(f, counts.GetValueOrDefault(f.FolderId, 0))));
    }

    [HttpPost]
    public async Task<ActionResult<FolderDto>> Create([FromBody] CreateFolderRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Name))
            return BadRequest("Folder name is required.");

        var folder = new Folder
        {
            UserId = CurrentUserId,
            Name = request.Name.Trim(),
            ParentFolderId = string.IsNullOrEmpty(request.ParentFolderId) ? null : Guid.Parse(request.ParentFolderId),
        };
        _db.Folders.Add(folder);
        await _db.SaveChangesAsync();
        return Ok(ToDto(folder, 0));
    }

    [HttpPut("{id}")]
    public async Task<ActionResult<FolderDto>> Rename(Guid id, [FromBody] CreateFolderRequest request)
    {
        var folder = await _db.Folders.FirstOrDefaultAsync(f => f.FolderId == id && f.UserId == CurrentUserId);
        if (folder is null) return NotFound();
        if (string.IsNullOrWhiteSpace(request.Name)) return BadRequest("Folder name is required.");

        folder.Name = request.Name.Trim();
        folder.ParentFolderId = string.IsNullOrEmpty(request.ParentFolderId) ? null : Guid.Parse(request.ParentFolderId);
        await _db.SaveChangesAsync();

        var count = await _db.Snippets.CountAsync(s => s.UserId == CurrentUserId && s.FolderId == id && !s.IsArchived);
        return Ok(ToDto(folder, count));
    }

    [HttpDelete("{id}")]
    public async Task<IActionResult> Delete(Guid id, [FromBody] DeleteFolderRequest request)
    {
        var folder = await _db.Folders.FirstOrDefaultAsync(f => f.FolderId == id && f.UserId == CurrentUserId);
        if (folder is null) return NotFound();

        Guid? target = null;
        if (!string.IsNullOrWhiteSpace(request.ReassignToFolderId))
        {
            if (!Guid.TryParse(request.ReassignToFolderId, out var parsed)) return BadRequest("Invalid reassignment folder ID.");
            target = parsed;
        }
        var sync = new SyncController(_db) { ControllerContext = ControllerContext };
        var result = await sync.Batch(new SyncBatchRequest(new List<SyncOperation> {
            new("folder", "delete", JsonSerializer.Serialize(new {
                entityId = id, userId = CurrentUserId, reassignToFolderId = target
            }), DateTimeOffset.UtcNow.ToUnixTimeMilliseconds())
        }), HttpContext.RequestAborted);
        return result.Result is OkObjectResult ? NoContent() : result.Result ?? StatusCode(500);
    }

    private static FolderDto ToDto(Folder f, int count) => new(
        f.FolderId.ToString(), f.Name, f.ParentFolderId?.ToString(), count,
        new DateTimeOffset(f.CreatedAt).ToUnixTimeMilliseconds());
}
