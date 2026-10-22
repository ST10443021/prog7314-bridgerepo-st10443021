package com.devledger.app.ui.components

import com.devledger.app.util.localization.rememberAppStrings

import com.devledger.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.ui.theme.DevLedgerColors
import androidx.compose.material3.MaterialTheme

/** Simple top bar with an optional back button — used across the secondary screens
 *  (Snippet Editor, Add Transaction, etc.) matching the design's `TopBar.tsx`. */
@Composable
fun DevLedgerTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val strings = rememberAppStrings()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DevLedgerColors.SurfaceNav)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.get(R.string.lang_back), tint = DevLedgerColors.TextPrimary)
            }
        } else {
            Spacer(modifier = Modifier.width(12.dp))
        }
        Text(
            text = title,
            color = DevLedgerColors.TextPrimary,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Row(content = actions)
        Spacer(modifier = Modifier.width(4.dp))
    }
}
