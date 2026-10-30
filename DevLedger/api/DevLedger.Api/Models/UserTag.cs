using System.ComponentModel.DataAnnotations;

// Reference scope: Entity Framework Core entity and context mapping. (Microsoft Learn, n.d.c). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Models;

/// <summary>Account-owned tag catalogue, independent of tags embedded in snippet CSV.</summary>
public sealed class UserTag
{
    [Key] public Guid TagId { get; set; }
    public Guid UserId { get; set; }
    public string Name { get; set; } = string.Empty;
    public string? ColorHex { get; set; }
    public DateTime CreatedAt { get; set; }
}
