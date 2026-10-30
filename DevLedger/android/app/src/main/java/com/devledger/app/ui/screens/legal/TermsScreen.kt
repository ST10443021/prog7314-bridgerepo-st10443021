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

// Reference scope: Jetpack Compose screen. (Google Developers, n.d.p). See REFERENCES-CODE.md for full entries.

/**
 * Displays the DevLedger Terms and Conditions.
 *
 * This screen contains static legal information and therefore does not
 * require a ViewModel.
 */
@Composable
fun TermsScreen(
    onBack: () -> Unit,
) {
    val strings = rememberAppStrings()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
    ) {

        // Top bar
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
                text = strings.get(R.string.lang_terms_conditions),
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
                text = strings.get(R.string.lang_devledger_terms_conditions),
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
                    text = strings.get(R.string.lang_by_creating_an_account_or_using_devledger_you_agree_to_these),
                    color = DevLedgerColors.TextSecondary,
                    fontSize = 14.sp,
                    lineHeight = 21.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            LegalSection(
                number = "1",
                title = strings.get(R.string.lang_about_devledger),
                body = strings.get(R.string.lang_terms_1_body)
            )

            LegalSection(
                number = "2",
                title = strings.get(R.string.lang_your_account),
                body = strings.get(R.string.lang_terms_2_body)
            )

            LegalSection(
                number = "3",
                title = strings.get(R.string.lang_security_and_authentication),
                body = strings.get(R.string.lang_terms_3_body)
            )

            LegalSection(
                number = "4",
                title = strings.get(R.string.lang_your_content),
                body = strings.get(R.string.lang_terms_4_body)
            )

            LegalSection(
                number = "5",
                title = strings.get(R.string.lang_acceptable_use),
                body = strings.get(R.string.lang_terms_5_body)
            )

            LegalSection(
                number = "6",
                title = strings.get(R.string.lang_cloud_synchronisation),
                body = strings.get(R.string.lang_terms_6_body)
            )

            LegalSection(
                number = "7",
                title = strings.get(R.string.lang_third_party_services),
                body = strings.get(R.string.lang_terms_7_body)
            )

            LegalSection(
                number = "8",
                title = strings.get(R.string.lang_financial_information),
                body = strings.get(R.string.lang_terms_8_body)
            )

            LegalSection(
                number = "9",
                title = strings.get(R.string.lang_availability),
                body = strings.get(R.string.lang_terms_9_body)
            )

            LegalSection(
                number = "10",
                title = strings.get(R.string.lang_privacy),
                body = strings.get(R.string.lang_terms_10_body)
            )

            LegalSection(
                number = "11",
                title = strings.get(R.string.lang_changes_to_these_terms),
                body = strings.get(R.string.lang_terms_11_body)
            )

            LegalSection(
                number = "12",
                title = strings.get(R.string.lang_contact),
                body = strings.get(R.string.lang_terms_12_body)
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
private fun LegalSection(
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
