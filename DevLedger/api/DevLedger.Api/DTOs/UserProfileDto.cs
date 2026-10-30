// Reference scope: Data transfer objects for the REST contract. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.DTOs;

/// <summary>
/// Profile information returned for the authenticated user.
/// Email and authentication provider are read-only from the profile screen.
/// </summary>
public sealed record UserProfileDto(
    Guid UserId,
    string Email,
    string FullName,
    string? SsoProvider,
    string PreferredLanguage,
    string BaseCurrency,
    bool BiometricEnabled,
    string? PinHash,
    string? PinSalt,
    DateTime CreatedAt
);

/// <summary>
/// Fields that the user may update from the Profile screen or Security Setup.
/// PinHash and PinSalt are accepted only so older clients still deserialise; the server ignores them.
/// </summary>
public sealed record UpdateUserProfileRequest(
    string? FullName = null,
    string? PinHash = null,
    string? PinSalt = null,
    bool? BiometricEnabled = null
);
