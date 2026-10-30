// Reference scope: Data transfer objects for the REST contract. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.DTOs;

public record FolderDto(string FolderId, string Name, string? ParentFolderId, int SnippetCount, long CreatedAt);
public record CreateFolderRequest(string Name, string? ParentFolderId);

/// <summary>ReassignToFolderId is required so deleting a folder never orphans or
/// silently deletes the snippets inside it — null means "move to All Snippets" (no folder).</summary>
public record DeleteFolderRequest(string? ReassignToFolderId);