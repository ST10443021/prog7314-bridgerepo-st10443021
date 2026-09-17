using System.Buffers.Binary;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Storage;

namespace DevLedger.Api.Data;

public sealed class SnippetMutationLock : IAsyncDisposable
{
    private static readonly SemaphoreSlim TestGate = new(1, 1);
    private readonly IDbContextTransaction? _transaction;
    private readonly bool _testLock;

    private SnippetMutationLock(IDbContextTransaction? transaction, bool testLock = false)
    {
        _transaction = transaction;
        _testLock = testLock;
    }

    // PostgreSQL transaction lock serialises deletion, upload and snapshot reads
    // for one account, including requests served by different API instances.
    public static async Task<SnippetMutationLock> BeginAsync(
        DevLedgerDbContext db, Guid userId, CancellationToken cancellationToken = default)
    {
        // Preserve the project's existing EF InMemory integration-test setup.
        if (db.Database.IsInMemory())
        {
            await TestGate.WaitAsync(cancellationToken);
            return new SnippetMutationLock(null, testLock: true);
        }
        var transaction = await db.Database.BeginTransactionAsync(cancellationToken);
        try
        {
            var key = BinaryPrimitives.ReadInt64LittleEndian(userId.ToByteArray());
            await db.Database.ExecuteSqlInterpolatedAsync(
                $"SELECT pg_advisory_xact_lock({key})", cancellationToken);
            return new SnippetMutationLock(transaction);
        }
        catch
        {
            await transaction.DisposeAsync();
            throw;
        }
    }

    public Task CommitAsync(CancellationToken cancellationToken = default) =>
        _transaction?.CommitAsync(cancellationToken) ?? Task.CompletedTask;

    public async ValueTask DisposeAsync()
    {
        if (_transaction is not null) await _transaction.DisposeAsync();
        if (_testLock) TestGate.Release();
    }
}
