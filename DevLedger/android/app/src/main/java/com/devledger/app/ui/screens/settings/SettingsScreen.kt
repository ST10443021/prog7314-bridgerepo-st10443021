package com.devledger.app.ui.screens.settings

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.sync.SyncScheduler
import com.devledger.app.ui.components.DevLedgerPrimaryButton
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import com.devledger.app.util.CurrencyConverter

@Composable
fun SettingsScreen(
    onSignedOut: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(state.language, state.isLoaded) {
        if (state.isLoaded) {
            applyAppLocale(context, state.language)
        }
    }

    LaunchedEffect(state.appliedWifiOnlySync) {
        state.appliedWifiOnlySync?.let {
            SyncScheduler.schedulePeriodicSync(context, it)
        }
    }

    SettingsScreenContent(
        state = state,
        onBack = onBack,
        onLanguageChange = viewModel::setLanguage,
        onBiometricEnabledChange = viewModel::setBiometricEnabled,
        onWifiOnlySyncChange = viewModel::setWifiOnlySync,
        onSyncNow = {
            SyncScheduler.syncNow(
                context = context,
                wifiOnly = state.wifiOnlySync,
            )
        },
        onBaseCurrencyChange = viewModel::setBaseCurrency,
        onSaveChanges = viewModel::saveSettings,
        onSignOut = {
            viewModel.signOut()
            onSignedOut()
        },
        onRetryLoading = viewModel::loadSettings,
    )
}

@Composable
fun SettingsScreenContent(
    state: SettingsUiState,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onBiometricEnabledChange: (Boolean) -> Unit,
    onWifiOnlySyncChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onBaseCurrencyChange: (String) -> Unit,
    onSaveChanges: () -> Unit,
    onSignOut: () -> Unit,
    onRetryLoading: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 10.dp,
            end = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SettingsHeader(onBack = onBack)
        }

        if (state.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    color = DevLedgerColors.AccentBlue,
                    trackColor = DevLedgerColors.SurfaceCardAlt,
                )
            }
        }

        state.error?.let { error ->
            item {
                StatusMessage(
                    message = error,
                    color = DevLedgerColors.ErrorRed,
                    actionText = if (
                        !state.isLoaded && !state.isLoading
                    ) {
                        "Retry"
                    } else {
                        null
                    },
                    onAction = onRetryLoading,
                )
            }
        }

        state.message?.let { message ->
            item {
                StatusMessage(
                    message = message,
                    color = DevLedgerColors.SuccessGreen,
                )
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Filled.Language,
                title = stringResource(R.string.settings_language),
                subtitle = "Choose your preferred application language.",
                accentColor = DevLedgerColors.AccentBlue,
            ) {
                LanguageOption(
                    tag = "en",
                    label = stringResource(R.string.language_english),
                    currentTag = state.language,
                    onSelect = onLanguageChange,
                )

                HorizontalDivider(color = DevLedgerColors.Border)

                LanguageOption(
                    tag = "af",
                    label = stringResource(R.string.language_afrikaans),
                    currentTag = state.language,
                    onSelect = onLanguageChange,
                )

                HorizontalDivider(color = DevLedgerColors.Border)

                LanguageOption(
                    tag = "xh",
                    label = stringResource(R.string.language_xhosa),
                    currentTag = state.language,
                    onSelect = onLanguageChange,
                )
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Filled.Lock,
                title = "Security",
                subtitle = "Manage how access to your account is protected.",
                accentColor = DevLedgerColors.AccentBlue,
            ) {
                ToggleSettingRow(
                    title = "Biometric Login",
                    description = "Use fingerprint or face recognition to log in.",
                    checked = state.biometricEnabled,
                    onCheckedChange = onBiometricEnabledChange,
                )
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Filled.Sync,
                title = stringResource(R.string.settings_sync),
                subtitle = "Manage data synchronisation and offline updates.",
                accentColor = DevLedgerColors.AccentBlue,
            ) {
                ToggleSettingRow(
                    title = stringResource(R.string.settings_wifi_only),
                    description = "Only synchronise while connected to Wi-Fi.",
                    checked = state.wifiOnlySync,
                    onCheckedChange = onWifiOnlySyncChange,
                )

                HorizontalDivider(color = DevLedgerColors.Border)

                SettingActionRow(
                    title = stringResource(R.string.settings_sync_now),
                    description = "Upload pending changes and download updates.",
                    actionText = "Sync",
                    onClick = onSyncNow,
                )
            }
        }

        item {
            SettingsSectionCard(
                icon = Icons.Filled.AttachMoney,
                title = stringResource(R.string.settings_currency),
                subtitle = "Choose the currency used for financial totals.",
                accentColor = DevLedgerColors.AccentPurple,
            ) {
                CurrencyOptions(
                    selectedCurrency = state.baseCurrency,
                    onCurrencySelected = onBaseCurrencyChange,
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(2.dp))

            DevLedgerPrimaryButton(
                text = "Save changes",
                onClick = onSaveChanges,
                enabled = !state.isSaving && state.hasChanges,
                loading = state.isSaving,
            )
        }

        item {
            OutlinedButton(
                onClick = onSignOut,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = DevLedgerColors.ErrorRed.copy(alpha = 0.7f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = DevLedgerColors.ErrorRed,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Logout,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(modifier = Modifier.size(8.dp))

                Text(
                    text = stringResource(R.string.settings_logout),
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SettingsHeader(
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = DevLedgerColors.SurfaceCard,
                    shape = CircleShape,
                ),
        ) {
            Icon(
                imageVector = Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = DevLedgerColors.TextSecondary,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Column {
            Text(
                text = stringResource(R.string.settings_title),
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = stringResource(R.string.settings_subtitle),
                color = DevLedgerColors.TextMuted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun SettingsSectionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 14.dp,
                    top = 14.dp,
                    end = 14.dp,
                    bottom = 10.dp,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = accentColor.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(21.dp),
                    )
                }

                Spacer(modifier = Modifier.size(11.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = title,
                        color = DevLedgerColors.TextPrimary,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                    )

                    Text(
                        text = subtitle,
                        color = DevLedgerColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            HorizontalDivider(color = DevLedgerColors.Border)

            Column {
                content()
            }
        }
    }
}

@Composable
private fun LanguageOption(
    tag: String,
    label: String,
    currentTag: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelect(tag) }
            .padding(
                horizontal = 14.dp,
                vertical = 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        RadioButton(
            selected = currentTag == tag,
            onClick = { onSelect(tag) },
            colors = RadioButtonDefaults.colors(
                selectedColor = DevLedgerColors.AccentBlue,
                unselectedColor = DevLedgerColors.TextMuted,
            ),
        )
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.padding(
            horizontal = 14.dp,
            vertical = 12.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = description,
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(modifier = Modifier.size(12.dp))

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = DevLedgerColors.AccentBlue,
                uncheckedThumbColor = DevLedgerColors.TextMuted,
                uncheckedTrackColor = DevLedgerColors.SurfaceCardAlt,
                uncheckedBorderColor = DevLedgerColors.Border,
            ),
        )
    }
}

@Composable
private fun SettingActionRow(
    title: String,
    description: String,
    actionText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(
            horizontal = 14.dp,
            vertical = 10.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Text(
                text = title,
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )

            Text(
                text = description,
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextButton(onClick = onClick) {
            Text(
                text = actionText,
                color = DevLedgerColors.AccentBlue,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CurrencyOptions(
    selectedCurrency: String,
    onCurrencySelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CurrencyConverter.supportedCurrencies().forEach { code ->
            FilterChip(
                selected = selectedCurrency == code,
                onClick = { onCurrencySelected(code) },
                label = {
                    Text(
                        text = code,
                        fontWeight = if (selectedCurrency == code) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor =
                        DevLedgerColors.AccentBlueDark,
                    selectedLabelColor =
                        DevLedgerColors.TextPrimary,
                    containerColor =
                        DevLedgerColors.SurfaceCardAlt,
                    labelColor =
                        DevLedgerColors.TextSecondary,
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = selectedCurrency == code,
                    borderColor = DevLedgerColors.Border,
                    selectedBorderColor = DevLedgerColors.AccentBlue,
                ),
            )
        }
    }
}

@Composable
private fun StatusMessage(
    message: String,
    color: Color,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            width = 1.dp,
            color = color.copy(alpha = 0.5f),
        ),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = 14.dp,
                vertical = 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                color = color,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )

            if (actionText != null) {
                TextButton(onClick = onAction) {
                    Text(
                        text = actionText,
                        color = color,
                    )
                }
            }
        }
    }
}

private fun applyAppLocale(
    context: Context,
    languageTag: String,
) {
    val localeList = LocaleListCompat.forLanguageTags(languageTag)

    androidx.appcompat.app.AppCompatDelegate
        .setApplicationLocales(localeList)
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun SettingsScreenPreview() {
    DevLedgerTheme {
        SettingsScreenContent(
            state = SettingsUiState(
                language = "en",
                biometricEnabled = true,
                wifiOnlySync = false,
                baseCurrency = "ZAR",
                isLoading = false,
                isSaving = false,
                isLoaded = true,
                hasChanges = true,
            ),
            onBack = {},
            onLanguageChange = {},
            onBiometricEnabledChange = {},
            onWifiOnlySyncChange = {},
            onSyncNow = {},
            onBaseCurrencyChange = {},
            onSaveChanges = {},
            onSignOut = {},
            onRetryLoading = {},
        )
    }
}