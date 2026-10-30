// Reference scope: Entity Framework Core entity and context mapping. (Microsoft Learn, n.d.c). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Models;

/// <summary>Permanent delete-wins marker: retries and stale offline edits cannot resurrect a row.</summary>
public sealed class DeletedSyncEntity
{
    public string EntityType { get; set; } = string.Empty;
    public Guid EntityId { get; set; }
    public Guid UserId { get; set; }
    public DateTime DeletedAt { get; set; } = DateTime.UtcNow;
}
