using DevLedger.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Data;

/// <summary>
/// EF Core database context for DevLedger.
/// PostgreSQL is used for local development and production through the
/// configured DefaultConnection connection string.
/// </summary>
public class DevLedgerDbContext : DbContext
{
    public DevLedgerDbContext(DbContextOptions<DevLedgerDbContext> options) : base(options) { }

    public DbSet<User> Users => Set<User>();
    public DbSet<Snippet> Snippets => Set<Snippet>();
    public DbSet<Transaction> Transactions => Set<Transaction>();
    public DbSet<NotificationToken> NotificationTokens => Set<NotificationToken>();

    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<User>().HasIndex(u => u.Email).IsUnique();
        modelBuilder.Entity<Snippet>().HasIndex(s => s.UserId);
        modelBuilder.Entity<Transaction>().HasIndex(t => t.UserId);
    }
}
