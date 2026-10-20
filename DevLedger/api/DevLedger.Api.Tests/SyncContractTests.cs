using System.Security.Claims;
using System.Text.Json;
using DevLedger.Api.Controllers;
using DevLedger.Api.Data;
using DevLedger.Api.DTOs;
using DevLedger.Api.Models;
using Microsoft.AspNetCore.Http;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Tests;

/// <summary>
/// Calls production controllers against an isolated EF InMemory store. This verifies
/// validation/ownership/persistence decisions, not PostgreSQL locks or migration execution.
/// </summary>
public sealed class SyncContractTests
{
    private const long Stamp = 1000;
    private static readonly JsonSerializerOptions Json = new(JsonSerializerDefaults.Web);
    private readonly DbContextOptions<DevLedgerDbContext> _options =
        new DbContextOptionsBuilder<DevLedgerDbContext>().UseInMemoryDatabase(Guid.NewGuid().ToString()).Options;
    private readonly Guid _owner = Guid.NewGuid();
    private readonly Guid _other = Guid.NewGuid();

    public SyncContractTests()
    {
        using var db = Open();
        db.Users.AddRange(new User { UserId = _owner, Email = "owner@example.test" },
            new User { UserId = _other, Email = "other@example.test" });
        db.SaveChanges();
    }
    private DevLedgerDbContext Open() => new(_options);
    private ControllerContext Context(Guid user) => new() {
        HttpContext = new DefaultHttpContext { User = new ClaimsPrincipal(new ClaimsIdentity(
            new[] { new Claim("sub", user.ToString()) }, "Test")) }
    };
    private static SyncOperation Op(string entity, object payload, string action = "upsert", long timestamp = Stamp) =>
        new(entity, action, JsonSerializer.Serialize(payload, Json), timestamp);
    private object FolderPayload(Guid id, Guid? parent = null) =>
        new { folderId = id, userId = _owner, name = "Folder", parentFolderId = parent, createdAt = Stamp };
    private object TagPayload(Guid id) => new { tagId = id, userId = _owner, name = "Kotlin", colorHex = "#123456", createdAt = Stamp };
    private object TxPayload(Guid id) => new {
        transactionId = id, userId = _owner, type = "EXPENSE", amount = 50m, currency = "ZAR",
        category = "Hosting", clientOrFrom = "Test", transactionDate = Stamp, createdAt = Stamp
    };
    private async Task<ActionResult<SyncBatchResponse>> Post(params SyncOperation[] operations)
    {
        await using var db = Open();
        return await new SyncController(db) { ControllerContext = Context(_owner) }
            .Batch(new SyncBatchRequest(operations.ToList()), CancellationToken.None);
    }
    private static SyncBatchResponse Accepted(ActionResult<SyncBatchResponse> result) =>
        Assert.IsType<SyncBatchResponse>(Assert.IsType<OkObjectResult>(result.Result).Value);

    [Fact]
    public async Task MixedBatchPersistsFolderTagSnippetAndTransactionDeletion()
    {
        var folder = Guid.NewGuid(); var tag = Guid.NewGuid(); var snippet = Guid.NewGuid(); var tx = Guid.NewGuid();
        using (var db = Open()) { db.Transactions.Add(new Transaction { TransactionId = tx, UserId = _owner }); db.SaveChanges(); }
        var result = Accepted(await Post(
            Op("snippet", new { snippetId = snippet, userId = _owner, folderId = folder, title = "Code", content = "println(1)", language = "kotlin", createdAt = Stamp, updatedAt = Stamp }),
            Op("transaction", new { entityId = tx }, "delete"),
            Op("tag", TagPayload(tag)), Op("folder", FolderPayload(folder))));
        Assert.Equal(4, result.Applied.Count);
        using var check = Open();
        Assert.Equal(folder, (await check.Snippets.SingleAsync()).FolderId);
        Assert.Single(await check.UserTags.ToListAsync());
        Assert.Empty(await check.Transactions.ToListAsync());
        Assert.Single(await check.DeletedSyncEntities.ToListAsync());
    }

    [Theory]
    [InlineData("transaction")]
    [InlineData("folder")]
    [InlineData("tag")]
    public async Task DeleteRetryIsIdempotentAndOldUpsertCannotResurrect(string entity)
    {
        var id = Guid.NewGuid();
        var deletion = Op(entity, new { entityId = id, userId = _owner }, "delete");
        Accepted(await Post(deletion)); Accepted(await Post(deletion));
        object payload = entity switch { "transaction" => TxPayload(id), "folder" => FolderPayload(id), _ => TagPayload(id) };
        Assert.Contains(id.ToString(), Accepted(await Post(Op(entity, payload))).Applied);
        using var check = Open();
        Assert.Equal(1, await check.DeletedSyncEntities.CountAsync());
        Assert.Empty(await check.Transactions.ToListAsync());
        Assert.Empty(await check.Folders.ToListAsync());
        Assert.Empty(await check.UserTags.ToListAsync());
    }

    [Theory]
    [InlineData("transaction")]
    [InlineData("folder")]
    [InlineData("tag")]
    public async Task CannotDeleteAnotherUsersRecord(string entity)
    {
        var id = Guid.NewGuid();
        using (var db = Open()) {
            if (entity == "transaction") db.Transactions.Add(new Transaction { TransactionId = id, UserId = _other });
            if (entity == "folder") db.Folders.Add(new Folder { FolderId = id, UserId = _other });
            if (entity == "tag") db.UserTags.Add(new UserTag { TagId = id, UserId = _other });
            db.SaveChanges();
        }
        Assert.IsType<ForbidResult>((await Post(Op(entity, new { entityId = id }, "delete"))).Result);
        using var check = Open();
        Assert.Empty(await check.DeletedSyncEntities.ToListAsync());
    }

    [Fact]
    public async Task CannotUpsertAnotherUsersFolderId()
    {
        var id = Guid.NewGuid();
        using (var db = Open()) { db.Folders.Add(new Folder { FolderId = id, UserId = _other, Name = "Other" }); db.SaveChanges(); }
        Assert.IsType<ForbidResult>((await Post(Op("folder", FolderPayload(id)))).Result);
        using var check = Open(); Assert.Equal("Other", (await check.Folders.SingleAsync()).Name);
    }

    [Fact]
    public async Task InvalidLaterOperationDoesNotSaveEarlierUpserts()
    {
        var result = await Post(Op("folder", FolderPayload(Guid.NewGuid())), Op("tag", new { tagId = Guid.NewGuid(), userId = _owner, name = "" }));
        Assert.IsType<BadRequestObjectResult>(result.Result);
        using var check = Open(); Assert.Empty(await check.Folders.ToListAsync());
    }

    [Fact]
    public async Task FolderCycleRejectsWholeBatch()
    {
        var a = Guid.NewGuid(); var b = Guid.NewGuid();
        Assert.IsType<BadRequestObjectResult>((await Post(Op("folder", FolderPayload(a, b)), Op("folder", FolderPayload(b, a)))).Result);
        using var check = Open(); Assert.Empty(await check.Folders.ToListAsync());
    }

    [Fact]
    public async Task NestedFoldersAcceptChildBeforeParentWithinBatch()
    {
        var parent = Guid.NewGuid(); var child = Guid.NewGuid();
        Accepted(await Post(Op("folder", FolderPayload(child, parent)), Op("folder", FolderPayload(parent))));
        using var check = Open(); Assert.Equal(parent, (await check.Folders.FindAsync(child))!.ParentFolderId);
    }

    [Fact]
    public async Task DeletingFolderReassignsSnippetsAndReparentsChildren()
    {
        var old = Guid.NewGuid(); var target = Guid.NewGuid(); var child = Guid.NewGuid(); var snippet = Guid.NewGuid();
        using (var db = Open()) {
            db.Folders.AddRange(new Folder { FolderId = old, UserId = _owner }, new Folder { FolderId = child, UserId = _owner, ParentFolderId = old });
            db.Snippets.Add(new Snippet { SnippetId = snippet, UserId = _owner, FolderId = old }); db.SaveChanges();
        }
        Accepted(await Post(Op("folder", new { entityId = old, reassignToFolderId = target }, "delete"), Op("folder", FolderPayload(target))));
        using var check = Open();
        Assert.Null(await check.Folders.FindAsync(old));
        Assert.Equal(target, (await check.Snippets.FindAsync(snippet))!.FolderId);
        Assert.Null((await check.Folders.FindAsync(child))!.ParentFolderId);
    }

    [Fact]
    public async Task ReassignmentCannotPointToAnotherAccount()
    {
        var old = Guid.NewGuid(); var target = Guid.NewGuid();
        using (var db = Open()) { db.Folders.AddRange(new Folder { FolderId = old, UserId = _owner }, new Folder { FolderId = target, UserId = _other }); db.SaveChanges(); }
        Assert.IsType<ForbidResult>((await Post(Op("folder", new { entityId = old, reassignToFolderId = target }, "delete"))).Result);
        using var check = Open(); Assert.NotNull(await check.Folders.FindAsync(old));
    }

    [Fact]
    public async Task TagsEndpointOnlyReturnsCurrentAccountsCatalogue()
    {
        using (var db = Open()) { db.UserTags.AddRange(new UserTag { TagId = Guid.NewGuid(), UserId = _owner, Name = "Mine" }, new UserTag { TagId = Guid.NewGuid(), UserId = _other, Name = "Other" }); db.SaveChanges(); }
        using var check = Open();
        var result = Assert.IsType<OkObjectResult>(await new TagsController(check) { ControllerContext = Context(_owner) }.GetAll(CancellationToken.None));
        var rows = JsonSerializer.SerializeToElement(result.Value, Json);
        Assert.Equal(1, rows.GetArrayLength()); Assert.Equal("Mine", rows[0].GetProperty("name").GetString());
    }

    [Theory]
    [InlineData("transaction")]
    [InlineData("folder")]
    [InlineData("tag")]
    public async Task CannotAcknowledgeAnotherAccountsDeletionMarker(string entity)
    {
        var id = Guid.NewGuid();
        using (var db = Open())
        {
            db.DeletedSyncEntities.Add(new DeletedSyncEntity {
                EntityType = entity, EntityId = id, UserId = _other,
            });
            db.SaveChanges();
        }
        var result = await Post(Op(entity, new { entityId = id }, "delete"));
        Assert.IsType<ForbidResult>(result.Result);
        using var check = Open();
        var marker = Assert.Single(await check.DeletedSyncEntities.ToListAsync());
        Assert.Equal(_other, marker.UserId);
    }

    [Theory]
    [InlineData(DateTimeKind.Unspecified, 0L)]
    [InlineData(DateTimeKind.Utc, 0L)]
    [InlineData(DateTimeKind.Unspecified, -62135596800000L)]
    [InlineData(DateTimeKind.Utc, -62135596800000L)]
    public async Task TagCreatedAtUsesUtcTicks(DateTimeKind kind, long expectedMillis)
    {
        using (var db = Open())
        {
            db.UserTags.Add(new UserTag {
                TagId = Guid.NewGuid(), UserId = _owner, Name = "UTC",
                CreatedAt = DateTime.SpecifyKind(
                    DateTimeOffset.FromUnixTimeMilliseconds(expectedMillis).UtcDateTime, kind),
            });
            db.SaveChanges();
        }
        using var check = Open();
        var result = Assert.IsType<OkObjectResult>(await new TagsController(check) {
            ControllerContext = Context(_owner),
        }.GetAll(CancellationToken.None));
        var rows = JsonSerializer.SerializeToElement(result.Value, Json);
        Assert.Equal(expectedMillis, rows[0].GetProperty("createdAt").GetInt64());
    }

    [Fact]
    public async Task DeletionFeedIsAccountScoped()
    {
        using (var db = Open()) { db.DeletedSyncEntities.AddRange(new DeletedSyncEntity { EntityId = Guid.NewGuid(), EntityType = "tag", UserId = _owner }, new DeletedSyncEntity { EntityId = Guid.NewGuid(), EntityType = "transaction", UserId = _other }); db.SaveChanges(); }
        using var check = Open();
        var result = Assert.IsType<OkObjectResult>(await new SyncController(check) { ControllerContext = Context(_owner) }.Deletions(CancellationToken.None));
        var rows = JsonSerializer.SerializeToElement(result.Value, Json);
        Assert.Equal(1, rows.GetArrayLength()); Assert.Equal("tag", rows[0].GetProperty("entityType").GetString());
    }

    [Fact]
    public async Task DuplicateIdsRejectWithoutSaving()
    {
        var id = Guid.NewGuid();
        Assert.IsType<BadRequestObjectResult>((await Post(Op("folder", FolderPayload(id)), Op("folder", FolderPayload(id)))).Result);
        using var check = Open(); Assert.Empty(await check.Folders.ToListAsync());
    }

    [Fact]
    public async Task MoreThan100OperationsAreRejected()
    {
        var operations = Enumerable.Range(0, 101).Select(_ => Op("folder", FolderPayload(Guid.NewGuid()))).ToArray();
        Assert.IsType<BadRequestObjectResult>((await Post(operations)).Result);
    }

    [Fact]
    public async Task MissingIdentityIsRejected()
    {
        using var db = Open();
        var controller = new SyncController(db) { ControllerContext = new ControllerContext { HttpContext = new DefaultHttpContext() } };
        Assert.IsType<UnauthorizedResult>((await controller.Batch(new SyncBatchRequest(new()), CancellationToken.None)).Result);
    }
}
