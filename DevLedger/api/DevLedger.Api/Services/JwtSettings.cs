namespace DevLedger.Api.Services;

/// <summary>
/// Reads the JWT signing configuration and refuses to start without a real secret.
/// The previous code fell back to a hard-coded key that was visible in source control, which would
/// have let anyone mint valid tokens against any deployment that forgot to set the real one
/// (OWASP Foundation, n.d.a; Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.
/// </summary>
public static class JwtSettings
{
    /// <summary>HMAC-SHA256 needs at least 256 bits of key material.</summary>
    public const int MinimumKeyLength = 32;

    public static string RequireSigningKey(IConfiguration configuration)
    {
        var key = configuration["Jwt:SigningKey"];
        if (string.IsNullOrWhiteSpace(key) || key.Length < MinimumKeyLength)
        {
            throw new InvalidOperationException(
                "Jwt:SigningKey is missing or shorter than 32 characters. " +
                "On Render set the environment variable Jwt__SigningKey to a long random value; " +
                "locally run: dotnet user-secrets set \"Jwt:SigningKey\" \"<random 48+ character value>\".");
        }
        return key;
    }

    public static string Issuer(IConfiguration configuration) => configuration["Jwt:Issuer"] ?? "DevLedger.Api";
}
