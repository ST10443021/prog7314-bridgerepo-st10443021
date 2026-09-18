using FirebaseAdmin;
using FirebaseAdmin.Messaging;

namespace DevLedger.Api.Services;

/// <summary>
/// Thin wrapper around the Firebase Admin SDK for .NET (Firebase, n.d.), used to send
/// real push notifications — the server-side half of the "Implementation of
/// Real-time Notification" core requirement. Silently no-ops (with a log line) if
/// Firebase wasn't initialised, so a missing credentials file degrades push
/// notifications gracefully instead of crashing unrelated endpoints.
/// </summary>
/// <remarks>
/// Reference scope: Firebase Cloud Messaging server-side delivery.
/// (Firebase, n.d.). See REFERENCES-CODE.md for full entries.
/// </remarks>
public class NotificationService
{
    private readonly ILogger<NotificationService> _logger;

    public NotificationService(ILogger<NotificationService> logger)
    {
        _logger = logger;
    }

    public async Task SendToTokensAsync(IEnumerable<string> fcmTokens, string title, string body, string type)
    {
        if (FirebaseApp.DefaultInstance is null)
        {
            _logger.LogWarning("Firebase not initialised — skipping push notification '{Title}'.", title);
            return;
        }

        var messaging = FirebaseMessaging.DefaultInstance;
        foreach (var token in fcmTokens)
        {
            try
            {
                await messaging.SendAsync(new Message
                {
                    Token = token,
                    Notification = new Notification { Title = title, Body = body },
                    Data = new Dictionary<string, string> { ["type"] = type },
                });
            }
            catch (FirebaseMessagingException ex)
            {
                // A single stale/uninstalled-device token should never block delivery
                // to the user's other devices, so this is logged and skipped, not thrown.
                _logger.LogWarning(ex, "Failed to deliver push notification to token {Token}", token);
            }
        }
    }
}