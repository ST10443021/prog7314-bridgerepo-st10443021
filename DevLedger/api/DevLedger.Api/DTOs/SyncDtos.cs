// Reference scope: Data transfer objects for the REST contract. (Microsoft Learn, n.d.). See REFERENCES-CODE.md for full entries.

namespace DevLedger.Api.DTOs;

public record SyncOperation(string Entity, string Action, string Payload, long ClientTimestamp);
public record SyncBatchRequest(List<SyncOperation> Operations);
public record SyncBatchResponse(List<string> Applied, List<string> Conflicts);
public record RegisterDeviceRequest(string FcmToken, string DeviceId);
public record UserSettingsDto(string Language, string BaseCurrency, bool BiometricEnabled, bool SyncOnWifiOnly, decimal? MonthlyBudget);
