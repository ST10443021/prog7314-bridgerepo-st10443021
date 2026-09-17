using System.ComponentModel.DataAnnotations;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Models;

// Retains identity only; the actual snippet and its content are removed.
[Index(nameof(UserId))]
public class DeletedSnippet
{
    [Key]
    public Guid SnippetId { get; set; }
    public Guid UserId { get; set; }
}
