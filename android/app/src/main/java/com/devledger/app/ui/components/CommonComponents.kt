package com.devledger.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.ui.theme.DevLedgerColors

/** Filled primary action button — matches the `#1D6FE8` accent buttons across every
 *  design screen (Sign In, Save Transaction, Save snippet, etc.). */
@Composable
fun DevLedgerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = DevLedgerColors.AccentBlue,
            contentColor = Color.White,
            disabledContainerColor = DevLedgerColors.AccentBlue.copy(alpha = 0.4f),
        ),
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Outlined secondary button, e.g. "Continue with GitHub" / "Cancel". */
@Composable
fun DevLedgerSecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevLedgerColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.TextPrimary),
    ) {
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

/** Dark, rounded card used for every panel — dashboard summary cards, snippet rows,
 *  transaction rows — matching `#141824` surface with a `#1E2A3E` hairline border. */
@Composable
fun DevLedgerCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DevLedgerColors.SurfaceCard)
            .border(1.dp, DevLedgerColors.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
        content = content,
    )
}

@Composable
fun DevLedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            visualTransformation = if (isPassword) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DevLedgerColors.SurfaceCard,
                unfocusedContainerColor = DevLedgerColors.SurfaceCard,
                focusedTextColor = DevLedgerColors.TextPrimary,
                unfocusedTextColor = DevLedgerColors.TextPrimary,
                focusedBorderColor = DevLedgerColors.AccentBlue,
                unfocusedBorderColor = DevLedgerColors.Border,
                focusedLabelColor = DevLedgerColors.AccentBlue,
                unfocusedLabelColor = DevLedgerColors.TextMuted,
                cursorColor = DevLedgerColors.AccentBlue,
            ),
        )
        if (supportingText != null) {
            Text(
                text = supportingText,
                color = if (isError) DevLedgerColors.ErrorRed else DevLedgerColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp),
            )
        }
    }
}
