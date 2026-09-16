namespace DevLedger.Api.DTOs;

public record SsoLoginRequest(string Provider, string? IdToken = null, string? Code = null);
public record EmailLoginRequest(string Email, string Password);
public record RegisterRequest(string Email, string Password, string FullName);
public record RefreshRequest(string RefreshToken);
public record AuthResponse(
    string AccessToken,
    string RefreshToken,
    string UserId,
    string FullName,
    string Email,
    string? PinHash = null,
    string? PinSalt = null,
    bool? BiometricEnabled = null
);
