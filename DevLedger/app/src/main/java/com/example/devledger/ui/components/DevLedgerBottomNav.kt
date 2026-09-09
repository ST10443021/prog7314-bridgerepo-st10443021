package com.devledger.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.R
import com.devledger.app.ui.theme.DevLedgerColors

enum class BottomNavTab { DASHBOARD, SNIPPETS, FINANCE, NOTIFICATIONS }

/** Bottom navigation bar with a centre FAB — a 1:1 port of BottomNav.tsx from the
 *  Part 1 pixel-accurate Figma export (same colours, spacing and centre-FAB layout). */
@Composable
fun DevLedgerBottomNav(
    active: BottomNavTab,
    onNavigate: (BottomNavTab) -> Unit,
    onFabClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(DevLedgerColors.SurfaceNav)
            .border(BorderStroke(1.dp, DevLedgerColors.Border)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(Icons.Filled.Home, stringRes(R.string.nav_dashboard), active == BottomNavTab.DASHBOARD) {
                onNavigate(BottomNavTab.DASHBOARD)
            }
            NavItem(Icons.Outlined.Code, stringRes(R.string.nav_snippets), active == BottomNavTab.SNIPPETS) {
                onNavigate(BottomNavTab.SNIPPETS)
            }
            IconButton(
                onClick = onFabClick,
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(DevLedgerColors.AccentBlue)
                    .border(3.dp, DevLedgerColors.SurfaceNav, CircleShape),
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add", tint = Color.White)
            }
            NavItem(Icons.Filled.AttachMoney, stringRes(R.string.nav_finance), active == BottomNavTab.FINANCE) {
                onNavigate(BottomNavTab.FINANCE)
            }
            NavItem(Icons.Filled.Notifications, stringRes(R.string.nav_notifications), active == BottomNavTab.NOTIFICATIONS) {
                onNavigate(BottomNavTab.NOTIFICATIONS)
            }
        }
    }
}

@Composable
private fun NavItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    val color = if (active) DevLedgerColors.AccentBlue else DevLedgerColors.TextMuted
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = label, tint = color)
        }
        Text(text = label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Medium)
    }
}

// Small helper so this file can be previewed without a Context; real screens use
// stringResource(R.string...) directly. Kept local to avoid an extra Context param
// on every NavItem call above.
@Composable
private fun stringRes(id: Int): String = androidx.compose.ui.res.stringResource(id)
