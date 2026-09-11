using DevLedger.Api.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;
using Microsoft.Extensions.Configuration;

namespace DevLedger.Api;

public class DevLedgerDbContextFactory : IDesignTimeDbContextFactory<DevLedgerDbContext>
{
    public DevLedgerDbContext CreateDbContext(string[] args)
    {
        // Design-time factory used by `dotnet ef` commands, which run outside the
        // normal web host — so it builds its own configuration here rather than
        // hardcoding a connection string, pulling from User Secrets (local dev)
        // or environment variables (CI/production) instead.
        var configuration = new ConfigurationBuilder()
            .SetBasePath(Directory.GetCurrentDirectory())
            .AddJsonFile("appsettings.json", optional: true)
            .AddUserSecrets<DevLedgerDbContextFactory>(optional: true)
            .AddEnvironmentVariables()
            .Build();

        var connectionString = configuration.GetConnectionString("DefaultConnection")
            ?? throw new InvalidOperationException(
                "Connection string 'DefaultConnection' was not found. Set it via User Secrets or an environment variable.");

        var optionsBuilder = new DbContextOptionsBuilder<DevLedgerDbContext>();
        optionsBuilder.UseNpgsql(connectionString);

        return new DevLedgerDbContext(optionsBuilder.Options);
    }
}