package com.devledger.app.ui.components

import com.devledger.app.util.localization.rememberAppStrings

import com.devledger.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.ui.draw.clip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.ui.theme.DevLedgerColors

/** Filled primary action button â€” matches the `#1D6FE8` accent buttons across every
 *  design screen (Sign In, Save Transaction, Save snippet, etc.). */
@Composable
fun DevLedgerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    trailingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(trailingIcon, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** Outlined secondary button, e.g. "Continue with GitHub" / "Cancel". */
@Composable
fun DevLedgerSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DevLedgerColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = DevLedgerColors.TextPrimary),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (leadingIcon != null) {
                leadingIcon()
                Spacer(modifier = Modifier.width(12.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Dark, rounded card used for every panel â€” dashboard summary cards, snippet rows,
 *  transaction rows â€” matching `#141824` surface with a `#1E2A3E` hairline border. */
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
    isValid: Boolean = false,
    supportingText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    leadingIcon: ImageVector? = null,
    placeholder: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val strings = rememberAppStrings()
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = if (label.isNotBlank()) {
                { Text(label) }
            } else {
                null
            },
            placeholder = placeholder?.let { { Text(it, color = DevLedgerColors.TextMuted) } },
            singleLine = singleLine,
            minLines = minLines,
            isError = isError,
            keyboardOptions = keyboardOptions,
            leadingIcon = leadingIcon?.let {
                { Icon(it, contentDescription = null, tint = if (isError) DevLedgerColors.ErrorRed else DevLedgerColors.TextMuted) }
            },
            trailingIcon = {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 8.dp)) {
                    if (isPassword) {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = if (passwordVisible) strings.get(R.string.lang_hide_password) else strings.get(R.string.lang_show_password),
                                tint = DevLedgerColors.TextMuted,
                            )
                        }
                    }
                    if (isValid && !isError) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = strings.get(R.string.lang_valid),
                            tint = DevLedgerColors.SuccessGreen,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            },
            visualTransformation = if (isPassword && !passwordVisible) {
                androidx.compose.ui.text.input.PasswordVisualTransformation()
            } else {
                androidx.compose.ui.text.input.VisualTransformation.None
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DevLedgerColors.SurfaceCard,
                unfocusedContainerColor = DevLedgerColors.SurfaceCard,
                focusedTextColor = DevLedgerColors.TextPrimary,
                unfocusedTextColor = DevLedgerColors.TextPrimary,
                focusedBorderColor = if (isValid) DevLedgerColors.SuccessGreen else DevLedgerColors.AccentBlue,
                unfocusedBorderColor = if (isValid) DevLedgerColors.SuccessGreen else DevLedgerColors.Border,
                errorBorderColor = DevLedgerColors.ErrorRed,
                focusedLabelColor = if (isValid) DevLedgerColors.SuccessGreen else DevLedgerColors.AccentBlue,
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

/**
 * Color-coded password strength meter.
 * References: "Implementing visual feedback for credential strength" (Google, 2024).
 */
@Composable
fun PasswordStrengthMeter(strength: Int, modifier: Modifier = Modifier) {
    val strings = rememberAppStrings()
    val segments = 4
    val color = when (strength) {
        1 -> DevLedgerColors.ErrorRed
        2 -> DevLedgerColors.WarningAmber
        3 -> DevLedgerColors.AccentCyan
        4 -> DevLedgerColors.SuccessGreen
        else -> DevLedgerColors.Border
    }
    val label = when (strength) {
        1 -> strings.get(R.string.lang_weak)
        2 -> strings.get(R.string.lang_fair)
        3 -> strings.get(R.string.lang_good)
        4 -> strings.get(R.string.lang_strong)
        else -> ""
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(segments) { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index < strength) color else DevLedgerColors.SurfaceCardAlt),
                )
            }
        }
        if (label.isNotEmpty()) {
            Text(
                text = label,
                color = color,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
