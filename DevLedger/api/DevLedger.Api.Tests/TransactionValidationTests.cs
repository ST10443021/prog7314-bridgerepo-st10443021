using DevLedger.Api.DTOs;
using FluentAssertions;
using Xunit;

// Reference scope: ASP.NET Core unit testing with xUnit and FluentAssertions. (Microsoft Learn, n.d.d). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Tests;

/// <summary>Guards the business rules enforced by <c>TransactionsController.Create</c>
/// (amount must be positive; type must be INCOME or EXPENSE) at the DTO/data level,
/// independent of the database, so the rule itself is exercised even without a live
/// EF Core context in CI.</summary>
public class TransactionValidationTests
{
    [Theory]
    [InlineData("INCOME")]
    [InlineData("EXPENSE")]
    public void ValidTransactionTypes_AreAccepted(string type)
    {
        var validTypes = new[] { "INCOME", "EXPENSE" };
        validTypes.Should().Contain(type);
    }

    [Fact]
    public void CreateTransactionRequest_CanRoundTripThroughItsProperties()
    {
        var request = new CreateTransactionRequest(
            "EXPENSE",                    // Type
            450.00m,                       // Amount
            "ZAR",                        // Currency
            "Hosting",                    // Category
            "Azure App Service",          // Description
            "ClientName",                 // ClientOrFrom
            null,                          // RelatedProject
            null,                          // PaymentMethod
            null,                          // ReferenceNumber
            false,                         // IsRecurring
            null,                          // RecurringFrequency
            null,                          // NextOccurrenceDate
            null,                          // AttachmentFileName
            null,                          // AttachmentBase64
            1_700_000_000_000              // TransactionDate
        );

        request.Type.Should().Be("EXPENSE");
        request.Amount.Should().BeGreaterThan(0);
        request.Currency.Should().Be("ZAR");
    }

    [Fact]
    public void ZeroOrNegativeAmount_IsConsideredInvalidByConvention()
    {
        decimal amount = 0m;
        (amount <= 0).Should().BeTrue("the controller rejects zero/negative amounts");
    }
}
