package com.devledger.app.ui.screens.notifications

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.NotificationEntity
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

private enum class NotificationFilter {
    ALL,
    UNREAD,
}

@Composable
fun NotificationsScreen(
    viewModel: NotificationsViewModel = hiltViewModel(),
) {
    val notifications by viewModel.notifications.collectAsState()

    NotificationsContent(
        notifications = notifications,
        onMarkRead = viewModel::markRead,
        onMarkAllRead = viewModel::markAllRead,
    )
}

@Composable
private fun NotificationsContent(
    notifications: List<NotificationEntity>,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
) {
    var selectedFilter by rememberSaveable {
        androidx.compose.runtime.mutableStateOf(
            NotificationFilter.ALL
        )
    }

    val unreadCount = remember(notifications) {
        notifications.count { !it.isRead }
    }

    val filteredNotifications = remember(
        notifications,
        selectedFilter,
    ) {
        when (selectedFilter) {
            NotificationFilter.ALL -> notifications

            NotificationFilter.UNREAD ->
                notifications.filter { !it.isRead }
        }
    }

    val groupedNotifications = remember(filteredNotifications) {
        filteredNotifications
            .sortedByDescending { it.createdAt }
            .groupBy { dateGroup(it.createdAt) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
            .statusBarsPadding(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 12.dp,
            end = 16.dp,
            bottom = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            NotificationsHeader(
                unreadCount = unreadCount,
                onMarkAllRead = onMarkAllRead,
            )
        }

        item {
            NotificationFilters(
                selectedFilter = selectedFilter,
                totalCount = notifications.size,
                unreadCount = unreadCount,
                onFilterSelected = {
                    selectedFilter = it
                },
            )
        }

        if (filteredNotifications.isEmpty()) {
            item {
                EmptyNotificationsCard(
                    showingUnreadOnly =
                        selectedFilter == NotificationFilter.UNREAD,
                )
            }
        } else {
            listOf("Today", "Yesterday", "Earlier")
                .forEach { groupName ->
                    val groupedItems =
                        groupedNotifications[groupName].orEmpty()

                    if (groupedItems.isNotEmpty()) {
                        item(
                            key = "heading_$groupName"
                        ) {
                            NotificationGroupHeading(groupName)
                        }

                        items(
                            items = groupedItems,
                            key = { it.notificationId },
                        ) { notification ->
                            NotificationCard(
                                notification = notification,
                                onClick = {
                                    if (!notification.isRead) {
                                        onMarkRead(
                                            notification.notificationId
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun NotificationsHeader(
    unreadCount: Int,
    onMarkAllRead: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.notifications_title
                    ),
                    color = DevLedgerColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )

                if (unreadCount > 0) {
                    Spacer(modifier = Modifier.width(8.dp))

                    Box(
                        modifier = Modifier
                            .background(
                                color = DevLedgerColors.AccentBlue,
                                shape = CircleShape,
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 2.dp,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                text = stringResource(
                    R.string.notifications_subtitle
                ),
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }

        TextButton(
            onClick = onMarkAllRead,
            enabled = unreadCount > 0,
        ) {
            Text(
                text = stringResource(
                    R.string.notifications_mark_all_read
                ),
                color = if (unreadCount > 0) {
                    DevLedgerColors.AccentBlue
                } else {
                    DevLedgerColors.TextMuted
                },
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun NotificationFilters(
    selectedFilter: NotificationFilter,
    totalCount: Int,
    unreadCount: Int,
    onFilterSelected: (NotificationFilter) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        NotificationFilterChip(
            modifier = Modifier.weight(1f),
            selected = selectedFilter == NotificationFilter.ALL,
            label = "All",
            count = totalCount,
            onClick = {
                onFilterSelected(NotificationFilter.ALL)
            },
        )

        NotificationFilterChip(
            modifier = Modifier.weight(1f),
            selected =
                selectedFilter == NotificationFilter.UNREAD,
            label = "Unread",
            count = unreadCount,
            onClick = {
                onFilterSelected(NotificationFilter.UNREAD)
            },
        )
    }
}

@Composable
private fun NotificationFilterChip(
    modifier: Modifier,
    selected: Boolean,
    label: String,
    count: Int,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = label,
                    fontWeight = if (selected) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                )

                Spacer(modifier = Modifier.width(6.dp))

                Box(
                    modifier = Modifier
                        .background(
                            color = if (selected) {
                                DevLedgerColors.AccentBlue
                            } else {
                                DevLedgerColors.SurfaceCardAlt
                            },
                            shape = CircleShape,
                        )
                        .padding(
                            horizontal = 6.dp,
                            vertical = 1.dp,
                        ),
                ) {
                    Text(
                        text = count.toString(),
                        color = if (selected) {
                            Color.White
                        } else {
                            DevLedgerColors.TextMuted
                        },
                        fontSize = 10.sp,
                    )
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = DevLedgerColors.SurfaceCard,
            labelColor = DevLedgerColors.TextSecondary,
            selectedContainerColor =
                DevLedgerColors.AccentBlueDark,
            selectedLabelColor =
                DevLedgerColors.TextPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            borderColor = DevLedgerColors.Border,
            selectedBorderColor =
                DevLedgerColors.AccentBlue,
        ),
    )
}

@Composable
private fun NotificationGroupHeading(
    title: String,
) {
    Text(
        text = title,
        color = DevLedgerColors.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(
            top = 5.dp,
            bottom = 1.dp,
        ),
    )
}

@Composable
private fun NotificationCard(
    notification: NotificationEntity,
    onClick: () -> Unit,
) {
    val visual = notificationVisual(notification.type)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            width = 1.dp,
            color = if (notification.isRead) {
                DevLedgerColors.Border
            } else {
                DevLedgerColors.AccentBlue.copy(alpha = 0.55f)
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = if (notification.isRead) {
                DevLedgerColors.SurfaceCard
            } else {
                DevLedgerColors.AccentBlue.copy(alpha = 0.08f)
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(
                        color = visual.color.copy(alpha = 0.17f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = visual.icon,
                    contentDescription = null,
                    tint = visual.color,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = notification.title,
                        color = DevLedgerColors.TextPrimary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (notification.isRead) {
                            FontWeight.Medium
                        } else {
                            FontWeight.Bold
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = relativeTime(
                            notification.createdAt
                        ),
                        color = DevLedgerColors.TextMuted,
                        fontSize = 10.sp,
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = notification.message,
                    color = DevLedgerColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = visual.color.copy(
                                    alpha = 0.13f
                                ),
                                shape = RoundedCornerShape(5.dp),
                            )
                            .padding(
                                horizontal = 7.dp,
                                vertical = 3.dp,
                            ),
                    ) {
                        Text(
                            text = visual.label,
                            color = visual.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    if (!notification.isRead) {
                        Spacer(modifier = Modifier.weight(1f))

                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    color =
                                        DevLedgerColors.AccentBlue,
                                    shape = CircleShape,
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyNotificationsCard(
    showingUnreadOnly: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 28.dp),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = 24.dp,
                    vertical = 36.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .background(
                        color =
                            DevLedgerColors.AccentBlue.copy(
                                alpha = 0.14f
                            ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (showingUnreadOnly) {
                        Icons.Filled.CheckCircle
                    } else {
                        Icons.Filled.Notifications
                    },
                    contentDescription = null,
                    tint = DevLedgerColors.AccentBlue,
                    modifier = Modifier.size(28.dp),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (showingUnreadOnly) {
                    "You're all caught up"
                } else {
                    stringResource(R.string.notifications_empty)
                },
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (showingUnreadOnly) {
                    "There are no unread notifications."
                } else {
                    "New alerts will appear here when they arrive."
                },
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private data class NotificationVisual(
    val icon: ImageVector,
    val color: Color,
    val label: String,
)

private fun notificationVisual(
    type: String,
): NotificationVisual {
    return when (type.uppercase()) {
        "BUDGET",
        "EXPENSE",
        "FINANCE",
        "INCOME",
        "TRANSACTION",
            -> NotificationVisual(
            icon = Icons.Filled.AttachMoney,
            color = Color(0xFFFF5252),
            label = "Finance",
        )

        "SYNC" -> NotificationVisual(
            icon = Icons.Filled.Sync,
            color = DevLedgerColors.SuccessGreen,
            label = "Sync",
        )

        "SECURITY" -> NotificationVisual(
            icon = Icons.Filled.Lock,
            color = Color(0xFFFFA726),
            label = "Security",
        )

        "SNIPPET" -> NotificationVisual(
            icon = Icons.Outlined.Code,
            color = DevLedgerColors.AccentBlue,
            label = "Snippet",
        )

        else -> NotificationVisual(
            icon = Icons.Filled.Notifications,
            color = DevLedgerColors.AccentPurple,
            label = "System",
        )
    }
}

private fun dateGroup(
    timestamp: Long,
): String {
    val notificationDate = Calendar.getInstance().apply {
        timeInMillis = timestamp
    }

    val today = Calendar.getInstance()

    if (
        notificationDate.get(Calendar.YEAR) ==
        today.get(Calendar.YEAR) &&
        notificationDate.get(Calendar.DAY_OF_YEAR) ==
        today.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Today"
    }

    today.add(Calendar.DAY_OF_YEAR, -1)

    if (
        notificationDate.get(Calendar.YEAR) ==
        today.get(Calendar.YEAR) &&
        notificationDate.get(Calendar.DAY_OF_YEAR) ==
        today.get(Calendar.DAY_OF_YEAR)
    ) {
        return "Yesterday"
    }

    return "Earlier"
}

private fun relativeTime(
    timestamp: Long,
): String {
    val elapsed = (
            System.currentTimeMillis() - timestamp
            ).coerceAtLeast(0L)

    val minutes = TimeUnit.MILLISECONDS
        .toMinutes(elapsed)

    val hours = TimeUnit.MILLISECONDS
        .toHours(elapsed)

    val days = TimeUnit.MILLISECONDS
        .toDays(elapsed)

    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        hours < 24 -> "${hours}h ago"
        days < 7 -> "${days}d ago"
        else -> SimpleDateFormat(
            "d MMM",
            Locale.getDefault(),
        ).format(Date(timestamp))
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun NotificationsPreview() {
    val now = System.currentTimeMillis()

    DevLedgerTheme {
        NotificationsContent(
            notifications = listOf(
                NotificationEntity(
                    notificationId = "1",
                    title = "Budget Alert",
                    message = "You have exceeded your food budget for this month.",
                    type = "BUDGET",
                    isRead = false,
                    createdAt = now - 3_600_000,
                ),
                NotificationEntity(
                    notificationId = "2",
                    title = "Sync Successful",
                    message = "Your data has been synchronised with the cloud.",
                    type = "SYNC",
                    isRead = true,
                    createdAt = now - 86_400_000,
                ),
                NotificationEntity(
                    notificationId = "3",
                    title = "Security Warning",
                    message = "A new login was detected from another device.",
                    type = "SECURITY",
                    isRead = false,
                    createdAt = now - 172_800_000,
                ),
            ),
            onMarkRead = {},
            onMarkAllRead = {},
        )
    }
}