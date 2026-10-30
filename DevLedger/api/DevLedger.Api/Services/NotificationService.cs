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

    /// <summary>True when the Firebase Admin SDK was initialised with valid credentials.</summary>
    public virtual bool IsConfigured => FirebaseApp.DefaultInstance is not null;

    /// <summary>
    /// Sends a DATA-ONLY high-priority message. A message that carries a "notification" payload is
    /// shown by Android itself while the app is in the background, and the app's
    /// onMessageReceived never runs, so the alert would never reach the in-app Notifications list
    /// (Firebase, n.d.a). With data only, DevLedgerMessagingService always runs, shows the system
    /// notification itself and records it in Room.
    /// </summary>
    public virtual async Task SendToTokensAsync(IEnumerable<string> fcmTokens, string title, string body, string type)
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
                    Data = new Dictionary<string, string>
                    {
                        ["title"] = title,
                        ["body"] = body,
                        ["type"] = type,
                    },
                    Android = new AndroidConfig { Priority = Priority.High },
                });
            }
            catch (FirebaseMessagingException ex)
            {
                // A single stale/uninstalled-device token should never block delivery
                // to the user's other devices, so this is logged and skipped, not thrown.
                _logger.LogWarning(ex, "Failed to deliver push notification to a registered device.");
            }
        }
    }
}