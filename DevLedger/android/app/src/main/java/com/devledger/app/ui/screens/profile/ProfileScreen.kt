package com.devledger.app.ui.screens.profile

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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.devledger.app.data.local.entity.UserEntity
import com.devledger.app.ui.theme.DevLedgerColors
import com.devledger.app.ui.theme.DevLedgerTheme
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import androidx.compose.foundation.border

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    ProfileScreenContent(
        state = state,
        onBack = onBack,
        onOpenSettings = onOpenSettings,
        onRefresh = viewModel::refreshProfile,
        onStartEditing = viewModel::startEditing,
        onNameChange = viewModel::updateFullName,
        onCancelEditing = viewModel::cancelEditing,
        onSaveProfile = viewModel::saveProfile,
    )
}

@Composable
fun ProfileScreenContent(
    state: ProfileUiState,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onStartEditing: () -> Unit,
    onNameChange: (String) -> Unit,
    onCancelEditing: () -> Unit,
    onSaveProfile: () -> Unit,
) {
    val user = state.user

    val currencyFormat = remember(user?.baseCurrency) {
        NumberFormat.getCurrencyInstance(
            Locale.forLanguageTag("en-ZA")
        ).apply {
            runCatching {
                currency = Currency.getInstance(
                    user?.baseCurrency ?: "ZAR"
                )
            }
        }
    }

    if (state.isEditing) {
        EditProfileDialog(
            fullName = state.editableFullName,
            isSaving = state.isSaving,
            error = state.error,
            onNameChange = onNameChange,
            onDismiss = onCancelEditing,
            onSave = onSaveProfile,
        )
    }

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
            ProfileHeader(onBack = onBack)
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

        if (user != null) {
            item {
                ProfileHeroCard(
                    user = user,
                    onEdit = onStartEditing,
                )
            }

            item {
                ProfileSectionLabel("Account Information")

                Spacer(modifier = Modifier.height(8.dp))

                ProfileSectionCard {
                    ProfileInformationRow(
                        icon = Icons.Filled.AccountCircle,
                        title = "Full Name",
                        value = user.fullName,
                        iconColor = DevLedgerColors.AccentBlue,
                        onClick = onStartEditing,
                    )

                    HorizontalDivider(color = DevLedgerColors.Border)

                    ProfileInformationRow(
                        icon = Icons.Filled.Email,
                        title = "Email Address",
                        value = user.email,
                        iconColor = DevLedgerColors.AccentBlue,
                    )

                    HorizontalDivider(color = DevLedgerColors.Border)

                    ProfileInformationRow(
                        icon = Icons.Filled.Lock,
                        title = "Sign-in Method",
                        value = providerLabel(user.ssoProvider),
                        iconColor = DevLedgerColors.SuccessGreen,
                    )
                }
            }

            item {
                ProfileSectionLabel("Preferences")

                Spacer(modifier = Modifier.height(8.dp))

                ProfileSectionCard {
                    ProfileInformationRow(
                        icon = Icons.Filled.Language,
                        title = "Language",
                        value = languageLabel(
                            user.preferredLanguage
                        ),
                        iconColor = DevLedgerColors.AccentPurple,
                        actionText = "Change",
                        onClick = onOpenSettings,
                    )

                    HorizontalDivider(color = DevLedgerColors.Border)

                    ProfileInformationRow(
                        icon = Icons.Filled.AttachMoney,
                        title = "Base Currency",
                        value = user.baseCurrency,
                        iconColor = DevLedgerColors.SuccessGreen,
                        actionText = "Change",
                        onClick = onOpenSettings,
                    )

                    HorizontalDivider(color = DevLedgerColors.Border)

                    ProfileInformationRow(
                        icon = Icons.Filled.Settings,
                        title = "Application Settings",
                        value = "Sync, security and preferences",
                        iconColor = DevLedgerColors.AccentBlue,
                        actionText = "Open",
                        onClick = onOpenSettings,
                    )
                }
            }

            item {
                ProfileSectionLabel("Security")

                Spacer(modifier = Modifier.height(8.dp))

                ProfileSectionCard {
                    ProfileInformationRow(
                        icon = Icons.Filled.Lock,
                        title = "Biometric Login",
                        value = if (user.biometricEnabled) {
                            "Enabled"
                        } else {
                            "Disabled"
                        },
                        valueColor = if (user.biometricEnabled) {
                            DevLedgerColors.SuccessGreen
                        } else {
                            DevLedgerColors.TextMuted
                        },
                        iconColor = DevLedgerColors.AccentPurple,
                        actionText = "Manage",
                        onClick = onOpenSettings,
                    )
                }
            }

            item {
                ProfileSectionLabel("Stats Overview")

                Spacer(modifier = Modifier.height(8.dp))

                StatisticsCard(
                    totalSnippets = state.totalSnippets,
                    totalFolders = state.totalFolders,
                    totalTags = state.totalTags,
                    totalIncome = currencyFormat.format(
                        state.totalIncome
                    ),
                )
            }
        } else if (!state.isLoading) {
            item {
                EmptyProfileCard(onRetry = onRefresh)
            }
        }

        if (!state.isEditing) {
            state.error?.let { error ->
                item {
                    ProfileStatusCard(
                        message = error,
                        color = DevLedgerColors.ErrorRed,
                        actionText = "Retry",
                        onAction = onRefresh,
                    )
                }
            }
        }

        state.message?.let { message ->
            item {
                ProfileStatusCard(
                    message = message,
                    color = DevLedgerColors.SuccessGreen,
                )
            }
        }
    }
}

@Composable
private fun ProfileHeader(
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
                text = "User Profile",
                color = DevLedgerColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = "Manage your account information and preferences.",
                color = DevLedgerColors.TextMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ProfileHeroCard(
    user: UserEntity,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(82.dp)
                    .background(
                        color = DevLedgerColors.AccentBlueDark,
                        shape = CircleShape,
                    )
                    .then(
                        Modifier.border(
                            width = 2.dp,
                            color = DevLedgerColors.AccentBlue,
                            shape = CircleShape,
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials(user.fullName),
                    color = Color.White,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = user.fullName,
                    color = DevLedgerColors.TextPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "DevLedger Developer",
                    color = DevLedgerColors.AccentBlue,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = user.email,
                    color = DevLedgerColors.TextMuted,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier
                    .size(38.dp)
                    .background(
                        color = DevLedgerColors.AccentBlueDark,
                        shape = CircleShape,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Filled.Edit,
                    contentDescription = "Edit profile",
                    tint = DevLedgerColors.AccentBlue,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
private fun ProfileSectionLabel(
    text: String,
) {
    Text(
        text = text,
        color = DevLedgerColors.TextSecondary,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ProfileSectionCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column {
            content()
        }
    }
}

@Composable
private fun ProfileInformationRow(
    icon: ImageVector,
    title: String,
    value: String,
    iconColor: Color,
    valueColor: Color = DevLedgerColors.TextMuted,
    actionText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickModifier)
            .padding(
                horizontal = 14.dp,
                vertical = 12.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    color = iconColor.copy(alpha = 0.16f),
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
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
                text = value,
                color = valueColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (actionText != null) {
            Text(
                text = actionText,
                color = DevLedgerColors.AccentBlue,
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun StatisticsCard(
    totalSnippets: Int,
    totalFolders: Int,
    totalTags: Int,
    totalIncome: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            DevLedgerColors.Border,
        ),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        ) {
            StatisticItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Code,
                value = totalSnippets.toString(),
                label = "Snippets",
                color = DevLedgerColors.AccentBlue,
            )

            StatisticItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.Folder,
                value = totalFolders.toString(),
                label = "Folders",
                color = DevLedgerColors.AccentPurple,
            )

            StatisticItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Outlined.Code,
                value = totalTags.toString(),
                label = "Tags",
                color = DevLedgerColors.SuccessGreen,
            )

            StatisticItem(
                modifier = Modifier.weight(1f),
                icon = Icons.Filled.AttachMoney,
                value = totalIncome,
                label = "Income",
                color = DevLedgerColors.ErrorRed,
            )
        }
    }
}

@Composable
private fun StatisticItem(
    modifier: Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
) {
    Column(
        modifier = modifier.padding(horizontal = 3.dp),
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
            fontSize = 13.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = label,
            color = DevLedgerColors.TextMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun EditProfileDialog(
    fullName: String,
    isSaving: Boolean,
    error: String?,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {
            if (!isSaving) {
                onDismiss()
            }
        },
        containerColor = DevLedgerColors.SurfaceCard,
        title = {
            Text(
                text = "Edit Profile",
                color = DevLedgerColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = fullName,
                    onValueChange = onNameChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = {
                        Text("Full Name")
                    },
                    singleLine = true,
                    enabled = !isSaving,
                    isError = error != null,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor =
                            DevLedgerColors.TextPrimary,
                        unfocusedTextColor =
                            DevLedgerColors.TextPrimary,
                        focusedBorderColor =
                            DevLedgerColors.AccentBlue,
                        unfocusedBorderColor =
                            DevLedgerColors.Border,
                        focusedLabelColor =
                            DevLedgerColors.AccentBlue,
                        unfocusedLabelColor =
                            DevLedgerColors.TextMuted,
                        cursorColor =
                            DevLedgerColors.AccentBlue,
                    ),
                )

                if (error != null) {
                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = error,
                        color = DevLedgerColors.ErrorRed,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving && fullName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = DevLedgerColors.AccentBlue,
                ),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
            ) {
                Text(
                    text = "Cancel",
                    color = DevLedgerColors.TextSecondary,
                )
            }
        },
    )
}

@Composable
private fun EmptyProfileCard(
    onRetry: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = DevLedgerColors.SurfaceCard,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Profile information is unavailable.",
                color = DevLedgerColors.TextMuted,
            )

            TextButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    color = DevLedgerColors.AccentBlue,
                )
            }
        }
    }
}

@Composable
private fun ProfileStatusCard(
    message: String,
    color: Color,
    actionText: String? = null,
    onAction: () -> Unit = {},
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            color.copy(alpha = 0.5f),
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

private fun initials(
    fullName: String,
): String {
    return fullName
        .trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .joinToString("")
        .ifBlank { "DL" }
}

private fun providerLabel(
    provider: String?,
): String {
    return when (provider?.uppercase()) {
        "GOOGLE" -> "Google"
        "GITHUB" -> "GitHub"
        else -> "Email and password"
    }
}

private fun languageLabel(
    tag: String,
): String {
    return when (tag.lowercase()) {
        "af" -> "Afrikaans"
        "xh" -> "isiXhosa"
        else -> "English"
    }
}

@Preview(
    showBackground = true,
    widthDp = 390,
    heightDp = 844,
)
@Composable
private fun ProfileScreenPreview() {
    DevLedgerTheme {
        ProfileScreenContent(
            state = ProfileUiState(
                user = UserEntity(
                    userId = "user123",
                    email = "alex.morgan@example.com",
                    fullName = "Alex Morgan",
                    ssoProvider = "GOOGLE",
                    preferredLanguage = "en",
                    biometricEnabled = true,
                    baseCurrency = "ZAR",
                ),
                editableFullName = "Alex Morgan",
                totalSnippets = 128,
                totalFolders = 24,
                totalTags = 18,
                totalIncome = 98_540.0,
                isLoading = false,
            ),
            onBack = {},
            onOpenSettings = {},
            onRefresh = {},
            onStartEditing = {},
            onNameChange = {},
            onCancelEditing = {},
            onSaveProfile = {},
        )
    }
}