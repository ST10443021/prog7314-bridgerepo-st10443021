using System.Security.Claims;
using DevLedger.Api.Data;
using Microsoft.AspNetCore.Authorization;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

// Reference scope: ASP.NET Core controller, routing and HTTP responses. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Controllers;

[ApiController]
[Authorize]
[Route("api/tags")]
public sealed class TagsController(DevLedgerDbContext db) : ControllerBase
{
    /// <summary>Full account-scoped catalogue. Snippet CSV assignments are separate.</summary>
    [HttpGet]
    public async Task<IActionResult> GetAll(CancellationToken cancellationToken)
    {
        if (!Guid.TryParse(User.FindFirst("sub")?.Value ??
                User.FindFirstValue(ClaimTypes.NameIdentifier), out var userId)) return Unauthorized();
        var tags = await db.UserTags.AsNoTracking().Where(t => t.UserId == userId)
            .OrderBy(t => t.Name).ToListAsync(cancellationToken);
        return Ok(tags.Select(t => new {
            tagId = t.TagId.ToString(), t.Name, t.ColorHex,
            // Sync stores UTC. Unspecified DateTime values must not inherit
            // the server's local timezone (including at DateTime.MinValue).
            createdAt = new DateTimeOffset(DateTime.SpecifyKind(t.CreatedAt, DateTimeKind.Utc))
                .ToUnixTimeMilliseconds()
        }));
    }
}
