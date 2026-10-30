// Reference scope: Data transfer objects for the REST contract. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.DTOs;

public record SnippetDto(
    string SnippetId,
    string? FolderId,
    string Title,
    string Content,
    string Language,
    string? Description,
    List<string> Tags,
    bool IsFavourite,
    bool IsArchived,
    long CreatedAt,
    long UpdatedAt
);

public record CreateSnippetRequest(
    string Title,
    string Content,
    string Language,
    string? FolderId,
    string? Description,
    List<string>? Tags
);
