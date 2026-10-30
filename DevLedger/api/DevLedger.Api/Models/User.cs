// Reference scope: Entity Framework Core entity and context mapping. (Microsoft Learn, n.d.c). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.Models;

/// <summary>
/// Mirrors the User entity from the Part 1 Planning and Design document's Data Models
/// section (Table 3). Passwords are stored as salted hashes only; SSO-only accounts
/// leave PasswordHash null.
/// </summary>
public class User
{
    public Guid UserId { get; set; } = Guid.NewGuid();
    public string Email { get; set; } = string.Empty;
    public string FullName { get; set; } = string.Empty;
    public string? PasswordHash { get; set; }
    public string? SsoProvider { get; set; } // "GOOGLE" | "GITHUB" | null
    public string PreferredLanguage { get; set; } = "en";
    public string BaseCurrency { get; set; } = "ZAR";
    public bool BiometricEnabled { get; set; } = true;
    public string? PinHash { get; set; }
    public string? PinSalt { get; set; }
    public bool SyncOnWifiOnly { get; set; } = false;
    public decimal? MonthlyBudget { get; set; }
    public DateTime CreatedAt { get; set; } = DateTime.UtcNow;
}
