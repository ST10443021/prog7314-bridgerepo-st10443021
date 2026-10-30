package com.devledger.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Remembers that the user tapped a DevLedger push notification so the app can open the in-app
 * Notifications list. The request is only honoured once the user is past the PIN/biometric gate
 * (see DevLedgerNavHost), so tapping a notification can never bypass authentication.
 * Reference scope: notification tap handling. (Firebase, n.d.; Google Developers, n.d.n).
 * See REFERENCES-CODE.md for full entries.
 */
object NotificationDeepLink {
    /** Intent extra added to the PendingIntent of every DevLedger notification. */
    const val EXTRA_OPEN_NOTIFICATIONS = "com.devledger.app.OPEN_NOTIFICATIONS"

    private val _pending = MutableStateFlow(false)
    val pending: StateFlow<Boolean> = _pending

    fun request() { _pending.value = true }
    fun consume() { _pending.value = false }
}
