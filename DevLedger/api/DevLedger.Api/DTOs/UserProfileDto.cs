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
    DateTime CreatedAt
);

/// <summary>
/// Fields that the user may update from the Profile screen.
/// </summary>
public sealed record UpdateUserProfileRequest(
    string FullName
);