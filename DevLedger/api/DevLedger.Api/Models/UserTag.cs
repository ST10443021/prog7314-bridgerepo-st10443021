using System.ComponentModel.DataAnnotations;

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
