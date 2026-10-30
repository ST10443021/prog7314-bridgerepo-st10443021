// Reference scope: Data transfer objects for the REST contract. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.DTOs;

public record TransactionDto(
    string TransactionId, string Type, decimal Amount, string Currency, string Category,
    string? Description, string ClientOrFrom, string? RelatedProject, string? PaymentMethod,
    string? ReferenceNumber, bool IsRecurring, string? RecurringFrequency, long? NextOccurrenceDate,
    string? AttachmentFileName, bool HasAttachment, long TransactionDate
);

public record CreateTransactionRequest(
    string Type, decimal Amount, string Currency, string Category, string? Description,
    string ClientOrFrom, string? RelatedProject, string? PaymentMethod, string? ReferenceNumber,
    bool IsRecurring, string? RecurringFrequency, long? NextOccurrenceDate,
    string? AttachmentFileName, string? AttachmentBase64, long TransactionDate
);

public record TransactionSummaryDto(decimal TotalIncome, decimal TotalExpenses, decimal NetBalance, string Currency);

/// <summary>One data point in the Monthly Summary chart — a day's totals, matching the
/// x-axis granularity in the Financial Tracker mockup.</summary>
public record DailySeriesPointDto(long Date, decimal Income, decimal Expenses);

public record CategoryBreakdownDto(string Category, decimal Total);