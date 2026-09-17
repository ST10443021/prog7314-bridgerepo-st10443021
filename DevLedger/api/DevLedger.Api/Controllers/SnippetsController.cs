using System.Security.Claims;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Controllers;

/// <summary>Snippet CRUD — matches the "/api/snippets" rows of the Part 1 endpoint
/// specification. Every action is scoped to the authenticated caller's UserId, so a
/// user can only ever read or modify their own vault.</summary>
[ApiController]
[Route("api/snippets")]
[Authorize]
public class SnippetsController : ControllerBase
{
    private readonly DevLedgerDbContext _db;

    public SnippetsController(DevLedgerDbContext db) => _db = db;

    private Guid CurrentUserId => Guid.Parse(User.FindFirstValue(ClaimTypes.NameIdentifier)
        ?? User.FindFirst("sub")?.Value
        ?? throw new UnauthorizedAccessException());

    [HttpGet]
    public async Task<ActionResult<List<SnippetDto>>> GetAll()
    {
        var snippets = await _db.Snippets
            .Where(s => s.UserId == CurrentUserId && !s.IsArchived)
            .OrderByDescending(s => s.UpdatedAt)
            .ToListAsync();
        return Ok(snippets.Select(ToDto));
    }

    // One consistent response includes active items, archived items and deletion IDs.
    [HttpGet("snapshot")]
    public async Task<IActionResult> Snapshot(CancellationToken cancellationToken)
    {
        var userId = CurrentUserId;
        await using var scope = await SnippetMutationLock.BeginAsync(_db, userId, cancellationToken);
        var snippets = await _db.Snippets.AsNoTracking()
            .Where(item => item.UserId == userId)
            .OrderByDescending(item => item.UpdatedAt).ToListAsync(cancellationToken);
        var deletedIds = await _db.Set<DeletedSnippet>().AsNoTracking()
            .Where(item => item.UserId == userId)
            .Select(item => item.SnippetId).ToListAsync(cancellationToken);
        await scope.CommitAsync(cancellationToken);
        return Ok(new {
            snippets = snippets.Select(ToDto).ToList(),
            deletedIds = deletedIds.Select(id => id.ToString()).ToList(),
        });
    }

    [HttpGet("{id}")]
    public async Task<ActionResult<SnippetDto>> GetById(Guid id)
    {
        var snippet = await _db.Snippets.FirstOrDefaultAsync(s => s.SnippetId == id && s.UserId == CurrentUserId);
        return snippet is null ? NotFound() : Ok(ToDto(snippet));
    }

    [HttpPost]
    public async Task<ActionResult<SnippetDto>> Create([FromBody] CreateSnippetRequest request)
    {
        if (string.IsNullOrWhiteSpace(request.Title) || string.IsNullOrWhiteSpace(request.Content))
            return BadRequest("Title and content are required.");

        await using var scope = await SnippetMutationLock.BeginAsync(_db, CurrentUserId);
        var snippet = new Snippet
        {
            UserId = CurrentUserId,
            Title = request.Title,
            Content = request.Content,
            Language = request.Language,
            Description = request.Description,
            TagsCsv = string.Join(",", request.Tags ?? new List<string>()),
            FolderId = string.IsNullOrEmpty(request.FolderId) ? null : Guid.Parse(request.FolderId),
        };
        _db.Snippets.Add(snippet);
        await _db.SaveChangesAsync();
        await scope.CommitAsync();
        return CreatedAtAction(nameof(GetById), new { id = snippet.SnippetId }, ToDto(snippet));
    }

    [HttpPut("{id}")]
    public async Task<ActionResult<SnippetDto>> Update(Guid id, [FromBody] CreateSnippetRequest request)
    {
        await using var scope = await SnippetMutationLock.BeginAsync(_db, CurrentUserId);
        var snippet = await _db.Snippets.FirstOrDefaultAsync(s => s.SnippetId == id && s.UserId == CurrentUserId);
        if (snippet is null) return NotFound();

        snippet.Title = request.Title;
        snippet.Content = request.Content;
        snippet.Language = request.Language;
        snippet.Description = request.Description;
        snippet.TagsCsv = string.Join(",", request.Tags ?? new List<string>());
        snippet.UpdatedAt = DateTime.UtcNow;

        await _db.SaveChangesAsync();
        await scope.CommitAsync();
        return Ok(ToDto(snippet));
    }

    [HttpDelete("{id}")]
    public async Task<IActionResult> Delete(Guid id, CancellationToken cancellationToken)
    {
        var userId = CurrentUserId;
        await using var scope = await SnippetMutationLock.BeginAsync(_db, userId, cancellationToken);
        var deleted = await _db.Set<DeletedSnippet>().SingleOrDefaultAsync(
            item => item.SnippetId == id, cancellationToken);
        if (deleted is not null)
            return deleted.UserId == userId ? NoContent() : NotFound();
        var snippet = await _db.Snippets.SingleOrDefaultAsync(
            item => item.SnippetId == id && item.UserId == userId, cancellationToken);
        if (snippet is null) return NotFound();
        _db.Snippets.Remove(snippet);
        _db.Set<DeletedSnippet>().Add(new DeletedSnippet { SnippetId = id, UserId = userId });
        await _db.SaveChangesAsync(cancellationToken);
        await scope.CommitAsync(cancellationToken);
        return NoContent();
    }

    private static SnippetDto ToDto(Snippet s) => new(
        SnippetId: s.SnippetId.ToString(),
        FolderId: s.FolderId?.ToString(),
        Title: s.Title,
        Content: s.Content,
        Language: s.Language,
        Description: s.Description,
        Tags: s.TagsCsv.Split(',', StringSplitOptions.RemoveEmptyEntries).ToList(),
        IsFavourite: s.IsFavourite,
        IsArchived: s.IsArchived,
        CreatedAt: new DateTimeOffset(s.CreatedAt).ToUnixTimeMilliseconds(),
        UpdatedAt: new DateTimeOffset(s.UpdatedAt).ToUnixTimeMilliseconds()
    );
}
