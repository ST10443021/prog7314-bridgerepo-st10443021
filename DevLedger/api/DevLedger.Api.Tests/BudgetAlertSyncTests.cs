using System.Security.Claims;
using System.Text.Json;
using DevLedger.Api.Controllers;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;
using Microsoft.Extensions.DependencyInjection;
using Microsoft.Extensions.Logging.Abstractions;

namespace DevLedger.Api.Tests;

/// <summary>Production controllers and budget service, isolated EF InMemory store,
/// fake push sender. No credentials or live Firebase calls are needed. PostgreSQL
/// commit/lock behaviour and actual phone delivery remain integration checks.</summary>
public sealed class BudgetAlertSyncTests
{
    private static readonly DateTimeOffset Now = new(2026, 10, 21, 12, 0, 0, TimeSpan.Zero);
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    private const long Stamp = 1000;
    private readonly Guid _owner = Guid.NewGuid();
    private readonly Guid _other = Guid.NewGuid();
    private readonly DbContextOptions<DevLedgerDbContext> _options = new DbContextOptionsBuilder<DevLedgerDbContext>()
        .UseInMemoryDatabase(Guid.NewGuid().ToString()).Options;
    private readonly FakePush _push = new();

    public BudgetAlertSyncTests()
    {
        using var db = Open();
        db.Users.AddRange(new User { UserId = _owner, Email = "owner@example.test", MonthlyBudget = 1000m, BaseCurrency = "ZAR" },
            new User { UserId = _other, Email = "other@example.test", MonthlyBudget = 1000m });
        db.NotificationTokens.AddRange(new NotificationToken { UserId = _owner, FcmToken = "owner-device" },
            new NotificationToken { UserId = _other, FcmToken = "other-device" });
        db.SaveChanges();
    }
    private DevLedgerDbContext Open() => new(_options);
    private BudgetAlertService Alerts(DevLedgerDbContext db) => new(db, _push, NullLogger<BudgetAlertService>.Instance, new Clock());
    private ControllerContext Context() => new() { HttpContext = new DefaultHttpContext {
        User = new ClaimsPrincipal(new ClaimsIdentity(new[] { new Claim("sub", _owner.ToString()) }, "Test")) } };
    private async Task<ActionResult<SyncBatchResponse>> Post(params SyncOperation[] operations)
    {
        await using var db = Open();
        return await new SyncController(db, Alerts(db)) { ControllerContext = Context() }
            .Batch(new SyncBatchRequest(operations.ToList()), CancellationToken.None);
    }
    private static SyncBatchResponse Accepted(ActionResult<SyncBatchResponse> response) =>
        Assert.IsType<SyncBatchResponse>(Assert.IsType<OkObjectResult>(response.Result).Value);
    private static SyncOperation Op(string entity, object payload, string action = "upsert") =>
        new(entity, action, JsonSerializer.Serialize(payload, Json), Stamp);
    private SyncOperation Tx(Guid id, decimal amount, string type = "EXPENSE", string currency = "ZAR",
        DateTime? date = null, Guid? owner = null) => Op("transaction", new {
        transactionId = id, userId = owner ?? _owner, type, amount, currency, category = "Hosting",
        clientOrFrom = "Test", transactionDate = new DateTimeOffset(date ?? Now.UtcDateTime).ToUnixTimeMilliseconds(), createdAt = Stamp,
    });
    private Guid Seed(decimal amount, string type = "EXPENSE", string currency = "ZAR", DateTime? date = null, Guid? owner = null)
    {
        using var db = Open();
        var row = new Transaction { UserId = owner ?? _owner, Type = type, Currency = currency, Amount = amount,
            TransactionDate = date ?? Now.UtcDateTime, ClientOrFrom = "Test", Category = "Hosting" };
        db.Transactions.Add(row); db.SaveChanges(); return row.TransactionId;
    }

    [Fact]
    public async Task SyncedExpenseCrossesEightyPercentAndPushSeesPersistedData()
    {
        Seed(795m); var id = Guid.NewGuid();
        _push.OnSend = async () => { await using var db = Open(); Assert.NotNull(await db.Transactions.FindAsync(id)); };
        Accepted(await Post(Tx(id, 5m)));
        var sent = Assert.Single(_push.Sent);
        Assert.Equal(new[] { "owner-device" }, sent.Tokens);
        Assert.Equal("BUDGET_ALERT", sent.Type);
        Assert.Contains("80%", sent.Body);
        Assert.Contains("ZAR 1,000.00", sent.Body);
    }

    [Fact]
    public async Task BatchWithSeveralExpensesProducesOneAlertFromItsFinalTotal()
    {
        Accepted(await Post(Tx(Guid.NewGuid(), 500m), Tx(Guid.NewGuid(), 350m)));
        Assert.Contains("85%", Assert.Single(_push.Sent).Body);
    }

    [Fact]
    public async Task IdenticalSyncRetryCannotRepeatAnAlertOrCreateAnotherTransaction()
    {
        var operation = Tx(Guid.NewGuid(), 850m);
        Accepted(await Post(operation)); Accepted(await Post(operation));
        Assert.Single(_push.Sent);
        using var db = Open(); Assert.Single(await db.Transactions.ToListAsync());
    }

    [Fact]
    public async Task FurtherSpendingAboveEightyDoesNotRepeatItButReachingTheLimitDoes()
    {
        Accepted(await Post(Tx(Guid.NewGuid(), 800m)));
        Accepted(await Post(Tx(Guid.NewGuid(), 50m)));
        Assert.Single(_push.Sent);
        Accepted(await Post(Tx(Guid.NewGuid(), 150m)));
        Assert.Equal(2, _push.Sent.Count);
        Assert.Equal("Budget limit reached", _push.Sent[1].Title);
        Assert.Contains("100%", _push.Sent[1].Body);
    }

    [Fact]
    public async Task JumpAcrossBothThresholdsSendsOnlyTheHighestWarning()
    {
        Accepted(await Post(Tx(Guid.NewGuid(), 1200m)));
        var sent = Assert.Single(_push.Sent);
        Assert.Equal("Budget limit reached", sent.Title);
        Assert.Contains("120%", sent.Body);
    }

    [Fact]
    public async Task FailedLaterOperationDoesNotPersistOrSendForAnEarlierExpense()
    {
        Assert.IsType<BadRequestObjectResult>((await Post(Tx(Guid.NewGuid(), 850m), Tx(Guid.NewGuid(), 0m))).Result);
        Assert.Empty(_push.Sent);
        using var db = Open(); Assert.Empty(await db.Transactions.ToListAsync());
    }

    [Fact]
    public async Task RejectedSnippetConflictCannotSendForTheRejectedMixedBatch()
    {
        var id = Guid.NewGuid();
        using (var db = Open()) {
            db.Snippets.Add(new Snippet { SnippetId = id, UserId = _owner, Title = "Code", Content = "server",
                Language = "kotlin", UpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(Stamp + 1).UtcDateTime });
            db.SaveChanges();
        }
        var result = await Post(Tx(Guid.NewGuid(), 850m), Op("snippet", new {
            snippetId = id, userId = _owner, title = "Code", content = "local", language = "kotlin", createdAt = Stamp, updatedAt = Stamp,
        }));
        Assert.IsType<ConflictObjectResult>(result.Result);
        Assert.Empty(_push.Sent);
        using var check = Open(); Assert.Empty(await check.Transactions.ToListAsync());
    }

    [Fact]
    public async Task AnotherUsersPayloadIsRejectedWithoutAPush()
    {
        Assert.IsType<ForbidResult>((await Post(Tx(Guid.NewGuid(), 850m, owner: _other))).Result);
        Assert.Empty(_push.Sent);
    }

    [Fact]
    public async Task OnlyCurrentUtcMonthExpensesForThisAccountCount()
    {
        Seed(750m);
        Seed(9000m, date: Now.UtcDateTime.AddMonths(-1));
        Seed(9000m, date: new DateTime(2026, 11, 1, 0, 0, 0, DateTimeKind.Utc));
        Seed(9000m, type: "INCOME");
        Seed(9000m, owner: _other);
        Accepted(await Post(Tx(Guid.NewGuid(), 50m)));
        Assert.Contains("80%", Assert.Single(_push.Sent).Body);
    }

    [Fact]
    public async Task AccountBaseCurrencyConvertsOtherSupportedCurrencies()
    {
        using (var db = Open()) { (await db.Users.FindAsync(_owner))!.BaseCurrency = "usd"; await db.SaveChangesAsync(); }
        Seed(650m, currency: "usd"); Seed(1810m, currency: "ZAR");
        Accepted(await Post(Tx(Guid.NewGuid(), 50m, currency: "USD")));
        var sent = Assert.Single(_push.Sent);
        Assert.Contains("80%", sent.Body); Assert.Contains("USD 1,000.00", sent.Body);
    }

    [Fact]
    public async Task ForeignCurrencyExpenseCanCrossTheBaseCurrencyBudget()
    {
        Accepted(await Post(Tx(Guid.NewGuid(), 100m, currency: "USD")));
        var sent = Assert.Single(_push.Sent);
        Assert.Contains("181%", sent.Body);
        Assert.Contains("ZAR 1,000.00", sent.Body);
    }

    [Fact]
    public async Task UnsupportedCurrencyDoesNotProduceAMisleadingPartialBudgetAlert()
    {
        Seed(750m); Seed(100m, currency: "JPY");
        Accepted(await Post(Tx(Guid.NewGuid(), 50m)));
        Assert.Empty(_push.Sent);
        using var check = Open(); Assert.Equal(3, await check.Transactions.CountAsync());
    }

    [Fact]
    public async Task IncomeBelowThresholdDoesNotSendAnExpenseWarning()
    {
        Seed(750m);
        Accepted(await Post(Tx(Guid.NewGuid(), 5000m, type: "INCOME")));
        Assert.Empty(_push.Sent);
    }

    [Fact]
    public async Task UpdatingAnExpenseCanCrossTheThresholdButReplayingThatUpdateCannot()
    {
        var id = Seed(700m);
        var operation = Tx(id, 850m);
        Accepted(await Post(operation)); Accepted(await Post(operation));
        Assert.Contains("85%", Assert.Single(_push.Sent).Body);
    }

    [Fact]
    public async Task DeletionAndReplacementUseTheFinalBatchTotal()
    {
        var old = Seed(700m);
        Accepted(await Post(Tx(Guid.NewGuid(), 200m), Op("transaction", new { entityId = old, userId = _owner }, "delete")));
        Assert.Empty(_push.Sent);
    }

    [Fact]
    public async Task FallingBelowAndLaterRecrossingAThresholdCanSendANewWarning()
    {
        var old = Seed(700m); var extra = Guid.NewGuid();
        Accepted(await Post(Tx(extra, 150m)));
        Accepted(await Post(Op("transaction", new { entityId = extra, userId = _owner }, "delete")));
        Assert.Single(_push.Sent);
        Accepted(await Post(Tx(Guid.NewGuid(), 150m)));
        Assert.Equal(2, _push.Sent.Count);
        using var db = Open(); Assert.NotNull(await db.Transactions.FindAsync(old));
    }

    [Theory]
    [InlineData(null)]
    [InlineData(0)]
    [InlineData(-1)]
    public async Task MissingOrNonpositiveBudgetDoesNotNotify(int? budget)
    {
        using (var db = Open()) {
            (await db.Users.FindAsync(_owner))!.MonthlyBudget = budget.HasValue ? (decimal)budget.Value : null;
            await db.SaveChangesAsync();
        }
        Accepted(await Post(Tx(Guid.NewGuid(), 850m)));
        Assert.Empty(_push.Sent);
    }

    [Fact]
    public async Task MissingDeviceTokenDoesNotBreakSuccessfulSync()
    {
        using (var db = Open()) { db.NotificationTokens.RemoveRange(db.NotificationTokens.Where(t => t.UserId == _owner)); db.SaveChanges(); }
        Accepted(await Post(Tx(Guid.NewGuid(), 850m)));
        Assert.Equal(0, _push.Attempts);
        using var check = Open(); Assert.Single(await check.Transactions.ToListAsync());
    }

    [Fact]
    public async Task PushFailureCannotTurnACommittedUploadIntoAFailureOrAnotherAlertOnReplay()
    {
        _push.Throw = true; var operation = Tx(Guid.NewGuid(), 850m);
        Accepted(await Post(operation)); Accepted(await Post(operation));
        Assert.Equal(1, _push.Attempts);
        using var db = Open(); Assert.Single(await db.Transactions.ToListAsync());
    }

    [Fact]
    public async Task DuplicateStoredDeviceTokensAreSentOnlyOnce()
    {
        using (var db = Open()) { db.NotificationTokens.Add(new NotificationToken { UserId = _owner, FcmToken = "owner-device" }); db.SaveChanges(); }
        Accepted(await Post(Tx(Guid.NewGuid(), 850m)));
        Assert.Equal(new[] { "owner-device" }, Assert.Single(_push.Sent).Tokens);
    }

    [Fact]
    public async Task DirectCreationSharesTheRuleAndSyncingThatSameRowDoesNotRepeatTheAlert()
    {
        using var db = Open();
        var controller = new TransactionsController(db, Alerts(db)) { ControllerContext = Context() };
        var request = new CreateTransactionRequest("EXPENSE", 850m, "ZAR", "Hosting", null, "Test", null, null, null,
            false, null, null, null, null, Now.ToUnixTimeMilliseconds());
        var result = await controller.Create(request);
        var dto = Assert.IsType<TransactionDto>(Assert.IsType<CreatedAtActionResult>(result.Result).Value);
        Assert.Single(_push.Sent);
        Accepted(await Post(Tx(Guid.Parse(dto.TransactionId), 850m)));
        Assert.Single(_push.Sent);
    }

    [Fact]
    public void ExistingServiceRegistrationsCanResolveAllAffectedControllers()
    {
        using var db = Open();
        var services = new ServiceCollection();
        services.AddLogging(); services.AddSingleton(db);
        services.AddSingleton<NotificationService>(); services.AddScoped<BudgetAlertService>();
        services.AddTransient<SyncController>(); services.AddTransient<FoldersController>(); services.AddTransient<TransactionsController>();
        using var provider = services.BuildServiceProvider(); using var scope = provider.CreateScope();
        Assert.NotNull(scope.ServiceProvider.GetRequiredService<SyncController>());
        Assert.NotNull(scope.ServiceProvider.GetRequiredService<FoldersController>());
        Assert.NotNull(scope.ServiceProvider.GetRequiredService<TransactionsController>());
    }

    private sealed class Clock : TimeProvider { public override DateTimeOffset GetUtcNow() => Now; }
    private sealed record SentPush(string[] Tokens, string Title, string Body, string Type);
    private sealed class FakePush : NotificationService
    {
        public FakePush() : base(NullLogger<NotificationService>.Instance) { }
        public List<SentPush> Sent { get; } = new();
        public int Attempts { get; private set; }
        public bool Throw { get; set; }
        public Func<Task>? OnSend { get; set; }
        public override async Task SendToTokensAsync(IEnumerable<string> tokens, string title, string body, string type)
        {
            Attempts++;
            if (OnSend is not null) await OnSend();
            if (Throw) throw new InvalidOperationException("Simulated push transport failure");
            Sent.Add(new SentPush(tokens.ToArray(), title, body, type));
        }
    }
}
