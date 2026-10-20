namespace DevLedger.Api.Models;

/// <summary>Permanent delete-wins marker: retries and stale offline edits cannot resurrect a row.</summary>
public sealed class DeletedSyncEntity
{
    public string EntityType { get; set; } = string.Empty;
    public Guid EntityId { get; set; }
    public Guid UserId { get; set; }
    public DateTime DeletedAt { get; set; } = DateTime.UtcNow;
}
