using System.Text;
using System.Threading.RateLimiting;
using DevLedger.Api.Data;
using DevLedger.Api.Services;
using FirebaseAdmin;
using Google.Apis.Auth.OAuth2;
using Microsoft.AspNetCore.Authentication.JwtBearer;
using Microsoft.AspNetCore.HttpOverrides;
using Microsoft.EntityFrameworkCore;
using Microsoft.IdentityModel.Tokens;

// Reference scope: ASP.NET Core application setup. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

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
builder.Services.AddHttpClient();

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

// The signing key must come from configuration (Render env var Jwt__SigningKey, or user-secrets
// locally). There is deliberately no hard-coded fallback; the app refuses to start without one.
var jwtSigningKey = JwtSettings.RequireSigningKey(builder.Configuration);
var jwtIssuer = JwtSettings.Issuer(builder.Configuration);

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

// CORS is intentionally not enabled: the only client is the native Android app, and browsers never
// call this API cross-origin, so an "allow any origin" policy would only widen the attack surface.

// Render terminates TLS in a reverse proxy; honour X-Forwarded-* so rate limiting sees the real client IP.
builder.Services.Configure<ForwardedHeadersOptions>(options =>
{
    options.ForwardedHeaders = ForwardedHeaders.XForwardedFor | ForwardedHeaders.XForwardedProto;
    options.KnownProxies.Clear();
});

// Brute-force protection for sign-in, registration, SSO and refresh: 10 requests per minute per IP
// (OWASP Foundation, n.d.a; Microsoft Learn, n.d.a). Applied with [EnableRateLimiting("auth")].
builder.Services.AddRateLimiter(options =>
{
    options.RejectionStatusCode = StatusCodes.Status429TooManyRequests;
    options.AddPolicy("auth", httpContext => RateLimitPartition.GetFixedWindowLimiter(
        httpContext.Connection.RemoteIpAddress?.ToString() ?? "unknown",
        _ => new FixedWindowRateLimiterOptions
        {
            PermitLimit = 10,
            Window = TimeSpan.FromMinutes(1),
            QueueLimit = 0,
        }));
});

// Firebase Admin SDK — enables server-initiated push notifications (Firebase, n.d.b),
// the server-side half of the "Implementation of Real-time Notification" core
// requirement. Reads a service-account credentials file whose path comes from
// user-secrets locally or a Render Secret File in production; never committed.
var firebaseCredentialsPath = builder.Configuration["Firebase:CredentialsPath"];
if (!string.IsNullOrEmpty(firebaseCredentialsPath) && File.Exists(firebaseCredentialsPath) && FirebaseApp.DefaultInstance is null)
{
    FirebaseApp.Create(new AppOptions
    {
        Credential = GoogleCredential.FromFile(firebaseCredentialsPath),
    });
}
builder.Services.AddSingleton<NotificationService>();
builder.Services.AddScoped<BudgetAlertService>();

var app = builder.Build();

if (FirebaseApp.DefaultInstance is null)
{
    app.Logger.LogWarning("Firebase Admin SDK was not initialised — push notifications are disabled until Firebase:CredentialsPath points at a valid service-account file.");
}

app.UseForwardedHeaders();

// Swagger exposes every endpoint, so it is limited to Development. For a recorded demonstration of
// the hosted API, set the environment variable Swagger__Enabled=true on the host and remove it afterwards.
if (app.Environment.IsDevelopment() || app.Configuration.GetValue<bool>("Swagger:Enabled"))
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.UseRateLimiter();
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

app.MapGet("/", () => Results.Ok(new { status = "DevLedger API is running", version = "1.0.0" }));

app.Run();

// Exposed for WebApplicationFactory<Program> in the test project.
public partial class Program { }