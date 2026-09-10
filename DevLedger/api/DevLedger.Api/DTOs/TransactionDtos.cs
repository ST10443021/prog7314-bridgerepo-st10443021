namespace DevLedger.Api.DTOs;

public record TransactionDto(
    string TransactionId,
    string Type,
    decimal Amount,
    string Currency,
    string Category,
    string? Description,
    long TransactionDate
);

public record CreateTransactionRequest(
    string Type,
    decimal Amount,
    string Currency,
    string Category,
    string? Description,
    long TransactionDate
);

public record TransactionSummaryDto(decimal TotalIncome, decimal TotalExpenses, decimal NetBalance, string Currency);
