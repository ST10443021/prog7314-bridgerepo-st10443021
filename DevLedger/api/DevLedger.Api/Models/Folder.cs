// Reference scope: Entity Framework Core entity and context mapping. (Microsoft Learn, n.d.c). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Models;

/// <summary>Organisational folder for the Snippet Vault — supports the nested-folder
/// pattern from the Part 1 Research Report's competitive analysis of SnippetsLab.</summary>
public class Folder
{
    public Guid FolderId { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public string Name { get; set; } = string.Empty;
    public Guid? ParentFolderId { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}