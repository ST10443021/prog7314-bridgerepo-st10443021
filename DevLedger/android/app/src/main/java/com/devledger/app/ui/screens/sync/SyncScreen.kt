package com.devledger.app.ui.screens.sync

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import java.text.DateFormat
import java.util.Date

@Composable
fun SyncScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    SyncScreenContent(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onSyncNow = viewModel::syncNow,
        onRetry = viewModel::retrySync,
    )
}

@Composable
fun SyncScreenContent(
    state: SyncUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onSyncNow: () -> Unit,
    onRetry: () -> Unit,
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SyncHeader(onBack = onBack)
        }

        item {
            OverallSyncCard(
                state = state,
                onSyncNow = onSyncNow,
                onRetry = onRetry,
            )
        }

        item {
            SyncStatistics(
                state = state,
            )
        }

        item {
            SectionLabel("Sync Queue")

            Spacer(modifier = Modifier.height(8.dp))

            SyncQueueCard(
                state = state,
                onRetry = onRetry,
            )
        }

        item {
            DataSafetyCard()
        }

        item {
            SectionLabel("Sync Settings")

            Spacer(modifier = Modifier.height(8.dp))

            SyncSettingsCard(
                wifiOnly = state.wifiOnlySync,
                onOpenSettings = onOpenSettings,
            )
        }
    }
}

@Composable
private fun SyncHeader(
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

        Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = "Sync Status",
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Monitor and manage your data synchronisation.",
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun OverallSyncCard(
    state: SyncUiState,
    onSyncNow: () -> Unit,
    onRetry: () -> Unit,
) {
    val visual = syncVisual(state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = 1.dp,
            color = DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            color = visual.color.copy(alpha = 0.16f),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (state.isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = visual.color,
                            strokeWidth = 3.dp,
                        )
                    }

                    Icon(
                        imageVector = visual.icon,
                        contentDescription = null,
                        tint = visual.color,
                        modifier = Modifier.size(30.dp),
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = visual.title,
                        color = DevLedgerColors.TextPrimary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )

                    Text(
                        text = visual.description,
                        color = DevLedgerColors.TextMuted,
                        style = MaterialTheme.typography.bodySmall,
                    )

                    state.lastCompletedAt?.let {
                        Spacer(modifier = Modifier.height(5.dp))

                        Text(
                            text = "Last synced: ${formatDateTime(it)}",
                            color = DevLedgerColors.SuccessGreen,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (state.isSyncing) {
                Spacer(modifier = Modifier.height(14.dp))

                LinearProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = DevLedgerColors.AccentBlue,
                    trackColor = DevLedgerColors.SurfaceCardAlt,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = progressDescription(state),
                    color = DevLedgerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.error?.let { error ->
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = error,
                    color = DevLedgerColors.ErrorRed,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = if (
                    state.runState == SyncRunState.FAILED
                ) {
                    onRetry
                } else {
                    onSyncNow
                },
                enabled = !state.isSyncing,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DevLedgerColors.AccentBlue,
                ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Sync,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = when {
                        state.isSyncing -> "Synchronising"
                        state.runState == SyncRunState.FAILED ->
                            "Retry Sync"
                        else -> "Sync Now"
                    },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SyncStatistics(
    state: SyncUiState,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        SyncStatisticCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Code,
            value = state.totalSnippets.toString(),
            label = "Snippets",
            pending = state.pendingSnippets,
            color = DevLedgerColors.AccentBlue,
        )

        SyncStatisticCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.Folder,
            value = state.totalFolders.toString(),
            label = "Folders",
            pending = 0,
            color = DevLedgerColors.AccentPurple,
        )

        SyncStatisticCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Outlined.Code,
            value = state.totalTags.toString(),
            label = "Tags",
            pending = 0,
            color = DevLedgerColors.SuccessGreen,
        )

        SyncStatisticCard(
            modifier = Modifier.weight(1f),
            icon = Icons.Filled.AttachMoney,
            value = state.totalTransactions.toString(),
            label = "Transactions",
            pending = state.pendingTransactions,
            color = Color(0xFFFFA726),
        )
    }
}

@Composable
private fun SyncStatisticCard(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    pending: Int,
    color: Color,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            width = 1.dp,
            color = DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 5.dp,
                    vertical = 12.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(21.dp),
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = value,
                color = DevLedgerColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = label,
                color = DevLedgerColors.TextMuted,
                fontSize = 9.sp,
                maxLines = 1,
            )

            Spacer(modifier = Modifier.height(5.dp))

            Text(
                text = if (pending > 0) {
                    "$pending pending"
                } else {
                    "Synced"
                },
                color = if (pending > 0) {
                    Color(0xFFFFA726)
                } else {
                    DevLedgerColors.SuccessGreen
                },
                fontSize = 9.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun SyncQueueCard(
    state: SyncUiState,
    onRetry: () -> Unit,
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
            if (state.totalPending == 0) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = DevLedgerColors.SuccessGreen,
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Queue is empty",
                            color = DevLedgerColors.TextPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )

                        Text(
                            text = "There are no local changes waiting to upload.",
                            color = DevLedgerColors.TextMuted,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            } else {
                SyncQueueRow(
                    icon = Icons.Outlined.Code,
                    title = "Snippet changes",
                    count = state.pendingSnippets,
                    color = DevLedgerColors.AccentBlue,
                )

                HorizontalDivider(color = DevLedgerColors.Border)

                SyncQueueRow(
                    icon = Icons.Filled.AttachMoney,
                    title = "Financial transactions",
                    count = state.pendingTransactions,
                    color = DevLedgerColors.SuccessGreen,
                )
            }

            if (
                state.runState == SyncRunState.FAILED &&
                state.totalPending > 0
            ) {
                HorizontalDivider(color = DevLedgerColors.Border)

                TextButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "Retry failed items",
                        color = DevLedgerColors.ErrorRed,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncQueueRow(
    icon: ImageVector,
    title: String,
    count: Int,
    color: Color,
) {
    Row(
        modifier = Modifier.padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    color = color.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

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
                text = if (count == 1) {
                    "1 item waiting to synchronise"
                } else {
                    "$count items waiting to synchronise"
                },
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            text = count.toString(),
            color = if (count > 0) {
                Color(0xFFFFA726)
            } else {
                DevLedgerColors.SuccessGreen
            },
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DataSafetyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = DevLedgerColors.AccentBlue.copy(alpha = 0.45f),
        ),
        colors = CardDefaults.cardColors(
            containerColor =
                DevLedgerColors.AccentBlue.copy(alpha = 0.08f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = DevLedgerColors.AccentBlue,
                modifier = Modifier.size(28.dp),
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Your local data is safe",
                    color = DevLedgerColors.AccentBlue,
                    fontWeight = FontWeight.SemiBold,
                )

                Text(
                    text = "Unsynchronised records remain available in Room until the API confirms them.",
                    color = DevLedgerColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SyncSettingsCard(
    wifiOnly: Boolean,
    onOpenSettings: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenSettings),
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
            SettingsRow(
                icon = Icons.Filled.Sync,
                title = "Automatic Sync",
                value = "Every 15 minutes",
                color = DevLedgerColors.AccentBlue,
            )

            HorizontalDivider(color = DevLedgerColors.Border)

            SettingsRow(
                icon = Icons.Filled.Wifi,
                title = "Sync on Wi-Fi only",
                value = if (wifiOnly) "Enabled" else "Disabled",
                color = if (wifiOnly) {
                    DevLedgerColors.SuccessGreen
                } else {
                    DevLedgerColors.TextMuted
                },
            )

            HorizontalDivider(color = DevLedgerColors.Border)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = null,
                    tint = DevLedgerColors.AccentPurple,
                )

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Manage Sync Settings",
                    color = DevLedgerColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    fontWeight = FontWeight.Medium,
                )

                Text(
                    text = "Open",
                    color = DevLedgerColors.AccentBlue,
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String,
    color: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(22.dp),
        )

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = title,
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )

        Text(
            text = value,
            color = color,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionLabel(
    text: String,
) {
    Text(
        text = text,
        color = DevLedgerColors.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

private data class SyncVisual(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val color: Color,
)

private fun syncVisual(
    state: SyncUiState,
): SyncVisual {
    return when (state.runState) {
        SyncRunState.QUEUED -> SyncVisual(
            title = "Sync queued",
            description = "Waiting for the required network connection.",
            icon = Icons.Filled.Sync,
            color = Color(0xFFFFA726),
        )

        SyncRunState.RUNNING -> SyncVisual(
            title = "Synchronising",
            description = "Uploading pending local changes securely.",
            icon = Icons.Filled.Sync,
            color = DevLedgerColors.AccentBlue,
        )

        SyncRunState.FAILED -> SyncVisual(
            title = "Sync requires attention",
            description = "Some local changes could not be uploaded.",
            icon = Icons.Filled.Warning,
            color = DevLedgerColors.ErrorRed,
        )

        SyncRunState.CANCELLED -> SyncVisual(
            title = "Sync cancelled",
            description = "Your local data remains available and unchanged.",
            icon = Icons.Filled.Warning,
            color = Color(0xFFFFA726),
        )

        SyncRunState.SUCCEEDED -> SyncVisual(
            title = if (state.totalPending == 0) {
                "All up to date"
            } else {
                "Sync completed"
            },
            description = if (state.totalPending == 0) {
                "Your local changes have been synchronised."
            } else {
                "New changes are waiting for the next sync."
            },
            icon = Icons.Filled.CheckCircle,
            color = DevLedgerColors.SuccessGreen,
        )

        SyncRunState.IDLE -> SyncVisual(
            title = if (state.totalPending == 0) {
                "All up to date"
            } else {
                "${state.totalPending} changes pending"
            },
            description = if (state.totalPending == 0) {
                "There are no local changes waiting to upload."
            } else {
                "Synchronise when you are ready."
            },
            icon = if (state.totalPending == 0) {
                Icons.Filled.CheckCircle
            } else {
                Icons.Filled.Sync
            },
            color = if (state.totalPending == 0) {
                DevLedgerColors.SuccessGreen
            } else {
                Color(0xFFFFA726)
            },
        )
    }
}

private fun progressDescription(
    state: SyncUiState,
): String {
    val entity = state.currentEntity ?: "Data"

    return if (state.totalItems > 0) {
        "$entity: ${state.processedItems} of ${state.totalItems} items"
    } else {
        "$entity synchronisation is starting"
    }
}

private fun formatDateTime(
    timestamp: Long,
): String {
    return DateFormat.getDateTimeInstance(
        DateFormat.MEDIUM,
        DateFormat.SHORT,
    ).format(Date(timestamp))
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun SyncScreenPreview() {
    DevLedgerTheme {
        SyncScreenContent(
            state = SyncUiState(
                totalSnippets = 128,
                totalFolders = 24,
                totalTags = 18,
                totalTransactions = 248,
                pendingSnippets = 2,
                pendingTransactions = 1,
                wifiOnlySync = true,
                runState = SyncRunState.RUNNING,
                progress = 0.66f,
                processedItems = 2,
                totalItems = 3,
                currentEntity = "Transactions",
            ),
            onBack = {},
            onOpenSettings = {},
            onSyncNow = {},
            onRetry = {},
        )
    }
}