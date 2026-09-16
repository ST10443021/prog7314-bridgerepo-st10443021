namespace DevLedger.Api.Models;

public class Transaction
{
    public Guid TransactionId { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public string Type { get; set; } = "EXPENSE"; // "INCOME" | "EXPENSE"
    public decimal Amount { get; set; }
    public string Currency { get; set; } = "ZAR";
    public string Category { get; set; } = string.Empty;
    public string? Description { get; set; }
    public string ClientOrFrom { get; set; } = string.Empty;
    public string? RelatedProject { get; set; }
    public string? PaymentMethod { get; set; }
    public string? ReferenceNumber { get; set; }
    public bool IsRecurring { get; set; }
    public string? RecurringFrequency { get; set; } // "WEEKLY" | "MONTHLY" | "YEARLY"
    public DateTime? NextOccurrenceDate { get; set; }
    public string? AttachmentFileName { get; set; }
    public string? AttachmentBase64 { get; set; }
    public DateTime TransactionDate { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}