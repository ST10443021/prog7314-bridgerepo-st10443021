package com.devledger.app.ui.screens.legal

import com.devledger.app.util.localization.rememberAppStrings

import com.devledger.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.ui.theme.DevLedgerColors

/**
 * Displays the DevLedger Privacy Policy.
 *
 * This screen contains static privacy information and therefore does not
 * require a ViewModel.
 */
@Composable
fun PrivacyPolicyScreen(
    onBack: () -> Unit,
) {
    val strings = rememberAppStrings()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onBack)
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = strings.get(R.string.lang_back),
                tint = DevLedgerColors.TextPrimary
            )

            Text(
                text = strings.get(R.string.lang_privacy_policy),
                color = DevLedgerColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp)
            )
        }

        HorizontalDivider(
            color = DevLedgerColors.SurfaceCardAlt
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {

            Text(
                text = strings.get(R.string.lang_devledger_privacy_policy),
                color = DevLedgerColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = strings.get(R.string.lang_last_updated_11_september_2026),
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            Surface(
                color = DevLedgerColors.SurfaceCardAlt,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = strings.get(R.string.lang_this_privacy_policy_explains_how_devledger_collects_uses_sto),
                    color = DevLedgerColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            PrivacySection(
                number = "1",
                title = strings.get(R.string.lang_information_we_collect),
                body = strings.get(R.string.lang_privacy_1_body)
            )

            PrivacySection(
                number = "2",
                title = strings.get(R.string.lang_authentication_information),
                body = strings.get(R.string.lang_privacy_2_body)
            )

            PrivacySection(
                number = "3",
                title = strings.get(R.string.lang_biometric_and_pin_security),
                body = strings.get(R.string.lang_privacy_3_body)
            )

            PrivacySection(
                number = "4",
                title = strings.get(R.string.lang_how_we_use_your_information),
                body = strings.get(R.string.lang_privacy_4_body)
            )

            PrivacySection(
                number = "5",
                title = strings.get(R.string.lang_local_storage),
                body = strings.get(R.string.lang_privacy_5_body)
            )

            PrivacySection(
                number = "6",
                title = strings.get(R.string.lang_cloud_storage_and_synchronisation),
                body = strings.get(R.string.lang_privacy_6_body)
            )

            PrivacySection(
                number = "7",
                title = strings.get(R.string.lang_third_party_services),
                body = strings.get(R.string.lang_privacy_7_body)
            )

            PrivacySection(
                number = "8",
                title = strings.get(R.string.lang_notifications),
                body = strings.get(R.string.lang_privacy_8_body)
            )

            PrivacySection(
                number = "9",
                title = strings.get(R.string.lang_financial_data),
                body = strings.get(R.string.lang_privacy_9_body)
            )

            PrivacySection(
                number = "10",
                title = strings.get(R.string.lang_data_security),
                body = strings.get(R.string.lang_privacy_10_body)
            )

            PrivacySection(
                number = "11",
                title = strings.get(R.string.lang_your_choices),
                body = strings.get(R.string.lang_privacy_11_body)
            )

            PrivacySection(
                number = "12",
                title = strings.get(R.string.lang_changes_to_this_policy),
                body = strings.get(R.string.lang_privacy_12_body)
            )

            PrivacySection(
                number = "13",
                title = strings.get(R.string.lang_contact),
                body = strings.get(R.string.lang_privacy_13_body)
            )

            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "© 2026 DevLedger",
                color = DevLedgerColors.TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PrivacySection(
    number: String,
    title: String,
    body: String,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "$number. $title",
            color = DevLedgerColors.AccentBlue,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = body,
            color = DevLedgerColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 22.sp
        )
    }
}
