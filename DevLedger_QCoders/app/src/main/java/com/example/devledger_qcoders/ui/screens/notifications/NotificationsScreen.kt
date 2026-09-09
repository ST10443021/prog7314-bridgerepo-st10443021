package com.devledger.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.R
import com.devledger.app.data.local.entity.NotificationEntity
import com.devledger.app.ui.components.DevLedgerCard
import com.devledger.app.ui.theme.DevLedgerColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Real-time notifications screen — satisfies the Part 1 core requirement
 *  "Implementation of Real-time Notification". Backed by Room so it renders the same
 *  list whether the alert arrived while the app was foregrounded or offline. */
@Composable
fun NotificationsScreen(viewModel: NotificationsViewModel = hiltViewModel()) {
    val notifications by viewModel.notifications.collectAsState()
    val dateFormat = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Column(modifier = Modifier.fillMaxSize().background(DevLedgerColors.BgDeep).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(stringResource(R.string.notifications_title), color = DevLedgerColors.TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.notifications_subtitle), color = DevLedgerColors.TextMuted, fontSize = 12.sp)
            }
            TextButton(onClick = viewModel::markAllRead) {
                Text(stringResource(R.string.notifications_mark_all_read), color = DevLedgerColors.AccentBlue, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (notifications.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.notifications_empty), color = DevLedgerColors.TextMuted, fontSize = 13.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(notifications, key = { it.notificationId }) { notification ->
                    NotificationRow(notification, dateFormat) { viewModel.markRead(notification.notificationId) }
                }
            }
        }
    }
}

@Composable
private fun NotificationRow(notification: NotificationEntity, dateFormat: SimpleDateFormat, onClick: () -> Unit) {
    DevLedgerCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.Top) {
            val dotColor = if (notification.isRead) DevLedgerColors.TextMuted else DevLedgerColors.AccentBlue
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, end = 10.dp)
                    .size(8.dp)
                    .background(dotColor, shape = androidx.compose.foundation.shape.CircleShape),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title, color = DevLedgerColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text(notification.message, color = DevLedgerColors.TextMuted, fontSize = 12.sp)
                Text(dateFormat.format(Date(notification.createdAt)), color = DevLedgerColors.TextMuted, fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
