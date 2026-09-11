using DevLedger.Api.Data;
using Microsoft.EntityFrameworkCore;
using Microsoft.EntityFrameworkCore.Design;

namespace DevLedger.Api;

public class DevLedgerDbContextFactory : IDesignTimeDbContextFactory<DevLedgerDbContext>
{
    public DevLedgerDbContext CreateDbContext(string[] args)
    {
        var optionsBuilder = new DbContextOptionsBuilder<DevLedgerDbContext>();

        var connectionString = "Host=dpg-dahsq4nqj5pc73adios0-a.oregon-postgres.render.com;Database=devledger;Username=devledger_user;Password=ovS0VXkx9lUxBMh0dlGCbPJSUD8XJm0G;SSL Mode=Require;Trust Server Certificate=true";

        optionsBuilder.UseNpgsql(connectionString);

        return new DevLedgerDbContext(optionsBuilder.Options);
    }
}