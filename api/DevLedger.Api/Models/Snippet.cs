namespace DevLedger.Api.Models;

public class Snippet
{
    public Guid SnippetId { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public Guid? FolderId { get; set; }
    public string Title { get; set; } = string.Empty;
    public string Content { get; set; } = string.Empty;
    public string Language { get; set; } = string.Empty;
    public string? Description { get; set; }
    public string TagsCsv { get; set; } = string.Empty;
    public bool IsFavourite { get; set; }
    public bool IsArchived { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
    public DateTime UpdatedAt { get; set; } = DateTime.UtcNow;
}
