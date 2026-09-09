package com.devledger.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.util.CurrencyConverter

/**
 * Settings screen. Covers every user-facing preference required by the Part 1 brief:
 * language (multi-language, satisfying "at least two South African Languages" once
 * Afrikaans/isiXhosa are selected), biometric lock toggle, Wi-Fi-only sync toggle
 * (offline mode policy), base currency, and a manual "Sync Now" action.
 */
@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(stringResource(R.string.settings_title), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(stringResource(R.string.settings_subtitle), color = DevLedgerColors.TextMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 20.dp))

        SectionLabel(stringResource(R.string.settings_language))
        DevLedgerCard(modifier = Modifier.padding(bottom = 16.dp)) {
            LanguageOption("en", stringResource(R.string.language_english), state.language) {
                viewModel.setLanguage(it); applyAppLocale(context, it)
            }
            LanguageOption("af", stringResource(R.string.language_afrikaans), state.language) {
                viewModel.setLanguage(it); applyAppLocale(context, it)
            }
            LanguageOption("xh", stringResource(R.string.language_xhosa), state.language) {
                viewModel.setLanguage(it); applyAppLocale(context, it)
            }
        }

        SectionLabel(stringResource(R.string.settings_security))
        DevLedgerCard(modifier = Modifier.padding(bottom = 16.dp)) {
            ToggleRow(stringResource(R.string.settings_biometric_login), state.biometricEnabled, viewModel::setBiometricEnabled)
        }

        SectionLabel(stringResource(R.string.settings_sync))
        DevLedgerCard(modifier = Modifier.padding(bottom = 16.dp)) {
            ToggleRow(stringResource(R.string.settings_wifi_only), state.wifiOnlySync) { enabled ->
                viewModel.setWifiOnlySync(enabled)
                SyncScheduler.schedulePeriodicSync(context, enabled)
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { SyncScheduler.syncNow(context) }) {
                Text(stringResource(R.string.settings_sync_now), color = DevLedgerColors.AccentBlue)
            }
        }

        SectionLabel(stringResource(R.string.settings_currency))
        DevLedgerCard(modifier = Modifier.padding(bottom = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyConverter.supportedCurrencies().forEach { code ->
                    FilterChip(
                        selected = state.baseCurrency == code,
                        onClick = { viewModel.setBaseCurrency(code) },
                        label = { Text(code) },
                    )
                }
            }
        }

        OutlinedButton(
            onClick = { viewModel.signOut(); onSignedOut() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.ErrorRed),
        ) {
            Text(stringResource(R.string.settings_logout))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = DevLedgerColors.TextMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun LanguageOption(tag: String, label: String, currentTag: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DevLedgerColors.TextPrimary, fontSize = 14.sp)
        RadioButton(
            selected = currentTag == tag,
            onClick = { onSelect(tag) },
            colors = RadioButtonDefaults.colors(selectedColor = DevLedgerColors.AccentBlue),
        )
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, color = DevLedgerColors.TextPrimary, fontSize = 14.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = DevLedgerColors.AccentBlue),
        )
    }
}

/** Applies the selected language app-wide using the AndroidX per-app language API
 *  (Google Developers, n.d.g), which works back to API 26 via the compat shim. */
private fun applyAppLocale(context: Context, languageTag: String) {
    val localeList = LocaleListCompat.forLanguageTags(languageTag)
    androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(localeList)
}
