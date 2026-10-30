package com.devledger.app.ui.screens.notifications

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.R
import com.devledger.app.data.local.entity.NotificationEntity
import com.devledger.app.data.remote.DevLedgerApiService
import com.devledger.app.data.repository.NotificationRepository
import com.devledger.app.util.localization.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Backs the Notifications screen — real-time alerts received via Firebase Cloud
 *  Messaging and mirrored to Room by [com.devledger.app.notifications.DevLedgerMessagingService]. */
@HiltViewModel
/**
 * Reference scope: UI and state-holder separation.
 * (Google Developers, n.d.a; Google Developers, n.d.f). See REFERENCES-CODE.md for full entries.
 */
class NotificationsViewModel @Inject constructor(
    private val repository: NotificationRepository,
    private val api: DevLedgerApiService,
) : ViewModel() {

    private val _isSendingTest = MutableStateFlow(false)
    val isSendingTest: StateFlow<Boolean> = _isSendingTest

    private val _testMessage = MutableStateFlow<UiText?>(null)
    val testMessage: StateFlow<UiText?> = _testMessage

    /**
     * Requests a real push from the server so the full FCM chain (API, Firebase, device,
     * in-app list) can be shown on demand, for example in the demonstration video.
     */
    fun sendTestPush() {
        if (_isSendingTest.value) return
        _isSendingTest.value = true
        viewModelScope.launch {
            val messageRes = try {
                val response = api.sendTestNotification()
                when {
                    response.isSuccessful -> R.string.notif_test_sent
                    response.code() == 404 -> R.string.notif_test_no_device
                    else -> R.string.notif_test_failed
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w("NotificationsVM", "Test push request failed", error)
                R.string.notif_test_failed
            }
            _testMessage.value = UiText.Resource(messageRes)
            _isSendingTest.value = false
        }
    }

    val notifications: StateFlow<List<NotificationEntity>> =
        repository.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun markRead(id: String) = viewModelScope.launch { repository.markRead(id) }
    fun markAllRead() = viewModelScope.launch { repository.markAllRead() }
}
