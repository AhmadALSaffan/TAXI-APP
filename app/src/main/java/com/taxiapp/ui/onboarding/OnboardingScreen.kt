package com.taxiapp.ui.onboarding

import android.view.HapticFeedbackConstants
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.taxiapp.R
import com.taxiapp.ui.theme.TaxiTheme
import kotlin.math.abs

private data class OnboardingPage(
    @StringRes val title: Int,
    @StringRes val accent: Int,
    @StringRes val body: Int,
)

private val Pages = listOf(
    OnboardingPage(R.string.onboarding_1_title, R.string.onboarding_1_accent, R.string.onboarding_1_body),
    OnboardingPage(R.string.onboarding_2_title, R.string.onboarding_2_accent, R.string.onboarding_2_body),
    OnboardingPage(R.string.onboarding_3_title, R.string.onboarding_3_accent, R.string.onboarding_3_body),
)

private const val AUTO_ADVANCE_MS = 5200

/**
 * Onboarding + sign-in. A full-screen motion background with three short pages
 * that advance on their own until the user swipes, and Google as the main sign-in.
 *
 * @param backgroundVideo optional res/raw video; when null the drawn map motion plays.
 */
@Composable
fun OnboardingScreen(
    state: SignInState,
    onGoogleClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onErrorShown: () -> Unit,
    @RawRes backgroundVideo: Int? = null,
) {
    val c = TaxiTheme.colors
    val pagerState = rememberPagerState { Pages.size }
    val pagerPosition by remember {
        derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction }
    }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state) {
        if (state is SignInState.Error) {
            snackbar.showSnackbar(state.message)
            onErrorShown()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        if (backgroundVideo != null) {
            VideoBackground(backgroundVideo, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(c.background.copy(alpha = 0.35f)))
        } else {
            MotionBackground(c, pagerPosition, Modifier.fillMaxSize())
        }

        // Scrims keep the status bar and the text readable over the motion.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to c.background.copy(alpha = 0.55f),
                        0.12f to Color.Transparent,
                        0.42f to Color.Transparent,
                        0.62f to c.background.copy(alpha = 0.92f),
                        1f to c.background,
                    ),
                ),
        )

        Column(
            Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 24.dp),
        ) {
            Wordmark(Modifier.padding(top = 12.dp))
            Spacer(Modifier.weight(1f))

            PageText(pagerState)
            Spacer(Modifier.height(24.dp))
            PageProgress(pagerState)
            Spacer(Modifier.height(32.dp))

            GoogleButton(loading = state is SignInState.Loading, onClick = onGoogleClick)
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onPhoneClick,
                enabled = state !is SignInState.Loading,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(
                    stringResource(R.string.onboarding_phone),
                    style = MaterialTheme.typography.labelLarge,
                    color = c.text,
                )
            }
            Text(
                stringResource(R.string.onboarding_legal),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 12.dp),
            )
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .padding(16.dp),
        )
    }
}

@Composable
private fun Wordmark(modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Same mark and amber tile as the launcher icon.
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_launcher_mark), contentDescription = null, tint = c.onAccent, modifier = Modifier.size(52.dp))
        }
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun PageText(pagerState: PagerState) {
    val c = TaxiTheme.colors
    HorizontalPager(pagerState, Modifier.fillMaxWidth()) { index ->
        val page = Pages[index]
        val title = stringResource(page.title)
        val accent = stringResource(page.accent)
        val offset = (pagerState.currentPage - index) + pagerState.currentPageOffsetFraction
        Column(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = 1f - abs(offset).coerceIn(0f, 1f) * 0.9f
                    translationX = offset * size.width * 0.25f // text trails the swipe a little
                },
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                buildAnnotatedString {
                    val start = title.indexOf(accent)
                    if (start < 0) {
                        append(title)
                    } else {
                        append(title.substring(0, start))
                        withStyle(SpanStyle(color = c.accentText)) { append(accent) }
                        append(title.substring(start + accent.length))
                    }
                },
                style = MaterialTheme.typography.displayLarge,
                color = c.text,
            )
            Text(stringResource(page.body), style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
        }
    }
}

/**
 * One segment per page. The current one fills while the page waits, then the
 * pager advances; once the user swipes, auto-advance stops.
 */
@Composable
private fun PageProgress(pagerState: PagerState) {
    val c = TaxiTheme.colors
    val dragged by pagerState.interactionSource.collectIsDraggedAsState()
    var userTookOver by rememberSaveable { mutableStateOf(false) }
    if (dragged) userTookOver = true

    val fill = remember { Animatable(0f) }
    LaunchedEffect(pagerState.settledPage, userTookOver) {
        if (userTookOver) {
            fill.snapTo(1f)
            return@LaunchedEffect
        }
        fill.snapTo(0f)
        fill.animateTo(1f, tween(AUTO_ADVANCE_MS, easing = LinearEasing))
        pagerState.animateScrollToPage((pagerState.settledPage + 1) % Pages.size)
    }

    val label = stringResource(R.string.onboarding_page_of, pagerState.currentPage + 1, Pages.size)
    Row(
        Modifier.semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Pages.indices.forEach { i ->
            val active = i == pagerState.currentPage
            val width by animateDpAsState(if (active) 36.dp else 14.dp, label = "segment")
            val progress = when {
                active -> fill.value
                i < pagerState.currentPage -> 1f
                else -> 0f
            }
            Box(
                Modifier
                    .width(width)
                    .height(4.dp)
                    .drawBehind {
                        val r = CornerRadius(size.height / 2)
                        drawRoundRect(c.line, cornerRadius = r)
                        drawRoundRect(
                            c.accent,
                            size = size.copy(width = size.width * progress),
                            cornerRadius = r,
                        )
                    },
            )
        }
    }
}

/** Google's own button style: white with the full-colour G, per their branding rules. */
@Composable
private fun GoogleButton(loading: Boolean, onClick: () -> Unit) {
    val c = TaxiTheme.colors
    val view = LocalView.current
    Button(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            onClick()
        },
        enabled = !loading,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color(0xFF1F1F1F),
            disabledContainerColor = Color.White,
            disabledContentColor = Color(0xFF1F1F1F),
        ),
        border = if (c.isDark) null else BorderStroke(1.dp, Color(0xFF747775)),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF1F1F1F), strokeWidth = 2.5.dp)
        } else {
            Image(painterResource(R.drawable.ic_google), contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.onboarding_google), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun OnboardingDarkPreview() {
    TaxiTheme(darkTheme = true) { OnboardingScreen(SignInState.Idle, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 400, heightDp = 860)
@Composable
private fun OnboardingLightPreview() {
    TaxiTheme(darkTheme = false) { OnboardingScreen(SignInState.Idle, {}, {}, {}) }
}
