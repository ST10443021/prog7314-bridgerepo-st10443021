using DevLedger.Api.Models;
using Microsoft.EntityFrameworkCore;

namespace DevLedger.Api.Data;

/// <summary>
/// EF Core database context. Configured for SQLite in Development (a single
/// devledger.db file — zero external setup for markers) and can be pointed at
/// Azure Database for PostgreSQL / Azure SQL in Production via the connection
/// string in appsettings.Production.json, matching the "Development Stack and
/// Hosting" section of the Part 1 Planning and Design document.
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
