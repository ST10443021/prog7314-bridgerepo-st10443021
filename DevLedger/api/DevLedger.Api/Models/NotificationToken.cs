namespace DevLedger.Api.Models;

/// <summary>Registered Firebase Cloud Messaging device token, one row per device per
/// user, per the "NotificationToken" entity in the Part 1 data model.</summary>
public class NotificationToken
{
    public Guid Id { get; set; } = Guid.NewGuid();
    public Guid UserId { get; set; }
    public string FcmToken { get; set; } = string.Empty;
    public string DeviceId { get; set; } = string.Empty;
    public DateTime RegisteredAt { get; set; } = DateTime.UtcNow;
}
