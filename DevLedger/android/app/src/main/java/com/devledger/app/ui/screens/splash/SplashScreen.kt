package com.devledger.app.ui.screens.splash

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devledger.app.R
import com.devledger.app.ui.theme.DevLedgerColors
import kotlinx.coroutines.delay

// Reference scope: Jetpack Compose screen. (Google Developers, n.d.p). See REFERENCES-CODE.md for full entries.

private const val SPLASH_DURATION = 1500L

/**
 * Splash screen: initialises local data/session and routes on to Authentication or,
 * for an already-signed-in user, straight to the Dashboard — matching the "Splash
 * Screen" specification in the Part 1 Planning and Design document.
 */
@Composable
fun SplashScreen(
    isLoggedIn: Boolean,
    onNavigateNext: (loggedIn: Boolean) -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = SPLASH_DURATION.toInt(), easing = LinearEasing)
        )
        onNavigateNext(isLoggedIn)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DevLedgerColors.BgDeep)
    ) {
        // 1. Background Sparkles & Tech Decorations
        BackgroundSparkles()

        // 2. Large Faint Shield Watermark behind the logo (clipped cleanly)
        Image(
            painter = painterResource(id = R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier
                .align(Alignment.Center)
                .size(420.dp)
                .offset(y = (-60).dp)
                .clip(CircleShape),
            alpha = 0.04f
        )

        // 3. Main Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1.2f))

            // Main Shield Logo with circular/rounded clip to cleanly remove any square border edge artifact
            Image(
                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                contentDescription = null,
                modifier = Modifier
                    .size(220.dp)
                    .aspectRatio(1f)
                    .clip(CircleShape)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Annotated text for "DevLedger" (Dev in white, Ledger in blue)
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color.White)) {
                        append("Dev")
                    }
                    withStyle(style = SpanStyle(color = DevLedgerColors.AccentBlue)) {
                        append("Ledger")
                    }
                },
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Muted tagline text
            Text(
                text = stringResource(R.string.app_tagline),
                color = DevLedgerColors.TextSecondary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
            )

            Spacer(modifier = Modifier.weight(1.0f))

            // 4. Custom Segmented/Dashed Progress Indicator that fills up
            SegmentedProgressIndicator(
                progress = progress.value,
                modifier = Modifier.size(54.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Initialization status text
            Text(
                text = stringResource(R.string.splash_initializing),
                color = DevLedgerColors.TextMuted,
                fontSize = 13.sp,
            )

            Spacer(modifier = Modifier.height(64.dp))
        }
    }
}

/**
 * Draws the high-tech background decoration grid and code sparkles seen on the design.
 */
@Composable
fun BackgroundSparkles() {
    Box(modifier = Modifier.fillMaxSize()) {
        // Left Side Dot Grid Matrix with highlighted blue dots
        Canvas(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp)
                .size(60.dp, 140.dp)
        ) {
            val rows = 8
            val cols = 4
            val spacingX = 12.dp.toPx()
            val spacingY = 14.dp.toPx()
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val isHighlighted = (r == 2 && c == 2) || (r == 2 && c == 3) || (r == 5 && c == 2) || (r == 5 && c == 3)
                    val color = if (isHighlighted) {
                        Color(0xFF1D6FE8).copy(alpha = 0.4f)
                    } else {
                        Color(0xFF1E2A3E).copy(alpha = 0.12f)
                    }
                    drawCircle(
                        color = color,
                        radius = 2.dp.toPx(),
                        center = Offset(c * spacingX, r * spacingY)
                    )
                }
            }
        }

        // Right Side Decorative Code Sparkles Code Lines and Elements
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp, top = 40.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Upper right spark
            Column(horizontalAlignment = Alignment.End) {
                Text("</>", color = Color(0xFF1D6FE8).copy(alpha = 0.08f), fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(24.dp, 2.dp).background(Color(0xFF1E2A3E).copy(alpha = 0.15f)))
                    Box(modifier = Modifier.size(12.dp, 2.dp).background(Color(0xFF1E2A3E).copy(alpha = 0.15f)))
                }
            }

            // Middle right spark
            Column(horizontalAlignment = Alignment.End) {
                Text("</>", color = Color(0xFF1D6FE8).copy(alpha = 0.08f), fontFamily = FontFamily.Monospace, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(36.dp, 2.dp).background(Color(0xFF1E2A3E).copy(alpha = 0.15f)))
                    Box(modifier = Modifier.size(8.dp, 2.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.2f)))
                }
            }

            // Lower right spark dots
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.size(3.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.3f)))
                Box(modifier = Modifier.size(3.dp).background(Color(0xFF1D6FE8).copy(alpha = 0.3f)))
                Box(modifier = Modifier.size(3.dp).background(Color(0xFF1E2A3E).copy(alpha = 0.15f)))
            }
        }
    }
}

/**
 * Custom segmented circular loading indicator that fills up as the splash progresses.
 */
@Composable
fun SegmentedProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.rotate(rotation)) {
        val strokeWidth = 3.5.dp.toPx()
        val numSegments = 8
        val sweepAngle = 33f
        val gapAngle = 12f
        
        for (i in 0 until numSegments) {
            val startAngle = i * (sweepAngle + gapAngle) - 90f
            
            // Progress logic: segments turn bright blue as progress increases
            val segmentThreshold = (i.toFloat() / numSegments)
            val color = if (progress > segmentThreshold) {
                Color(0xFF1D6FE8)
            } else {
                Color(0xFF141C2C)
            }
            
            drawArc(
                color = color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF060810)
@Composable
fun SplashScreenPreview() {
    SplashScreen(isLoggedIn = false, onNavigateNext = {})
}