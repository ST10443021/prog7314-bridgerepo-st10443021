package com.devledger.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.devledger.app.data.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Exposes the automatic-sync switch to the Settings screen. It is the same DataStore value that the
 * Sync status screen edits, so both screens always agree; DevLedgerApplication observes it and
 * schedules or cancels the periodic WorkManager job. (Google Developers, n.d.c; Google Developers,
 * n.d.f). See REFERENCES-CODE.md for full entries.
 */
@HiltViewModel
class SyncPreferencesViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val autoSync: StateFlow<Boolean> = settingsRepository.autoSyncEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoSyncEnabled(enabled) }
    }
}
