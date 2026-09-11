using System.Text;
using DevLedger.Api.Data;
using DevLedger.Api.Services;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

var builder = WebApplication.CreateBuilder(args);

// ---------------------------------------------------------------------------
// Services — architecture matches Section 5 ("REST API and Back-End
// Architecture") of the Part 1 Planning and Design document: a stateless
// ASP.NET Core Web API, Entity Framework Core as the ORM, JWT bearer auth.
// ---------------------------------------------------------------------------
builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();
builder.Services.AddSingleton<TokenService>();

// PostgreSQL connection.
// Local development uses appsettings.Development.json.
// Production will use the Render environment variable:
// ConnectionStrings__DefaultConnection.
var connectionString = builder.Configuration.GetConnectionString("DefaultConnection")
    ?? throw new InvalidOperationException(
        "Connection string 'DefaultConnection' was not found.");
if (builder.Environment.IsEnvironment("Testing"))
{
    builder.Services.AddDbContext<DevLedgerDbContext>(options => options.UseInMemoryDatabase("DevLedgerTestDb"));
}
else
{
    builder.Services.AddDbContext<DevLedgerDbContext>(options => options.UseNpgsql(connectionString));
}

var jwtSigningKey = builder.Configuration["Jwt:SigningKey"] ?? "dev-only-signing-key-change-in-production-32chars!!";
var jwtIssuer = builder.Configuration["Jwt:Issuer"] ?? "DevLedger.Api";

builder.Services.AddAuthentication(JwtBearerDefaults.AuthenticationScheme)
    .AddJwtBearer(options =>
    {
        options.MapInboundClaims = false; // keep the raw "sub" claim name instead of ClaimTypes.NameIdentifier remapping
        options.TokenValidationParameters = new TokenValidationParameters
        {
            ValidateIssuer = true,
            ValidateAudience = true,
            ValidateLifetime = true,
            ValidateIssuerSigningKey = true,
            ValidIssuer = jwtIssuer,
            ValidAudience = jwtIssuer,
            IssuerSigningKey = new SymmetricSecurityKey(Encoding.UTF8.GetBytes(jwtSigningKey)),
        };
    });
builder.Services.AddAuthorization();

builder.Services.AddCors(options =>
{
    options.AddDefaultPolicy(policy => policy.AllowAnyOrigin().AllowAnyMethod().AllowAnyHeader());
});

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseCors();
app.UseAuthentication();
app.UseAuthorization();
app.MapControllers();

// Apply pending EF Core migrations on startup.
// This keeps the PostgreSQL schema in sync with the application's migrations.
if (!app.Environment.IsEnvironment("Testing"))
{
    using var scope = app.Services.CreateScope();
    var db = scope.ServiceProvider.GetRequiredService<DevLedgerDbContext>();
    db.Database.Migrate();
}

app.MapGet("/", () => Results.Ok(new { status = "DevLedger API is running", version = "1.0.0-prototype" }));

app.Run();

// Exposed for WebApplicationFactory<Program> in the test project.
public partial class Program { }
