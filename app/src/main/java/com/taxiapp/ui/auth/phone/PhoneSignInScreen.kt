package com.taxiapp.ui.auth.phone

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.taxiapp.R
import com.taxiapp.ui.auth.Country
import com.taxiapp.ui.components.BackButton
import com.taxiapp.ui.components.PrimaryButton
import com.taxiapp.ui.theme.Manrope
import com.taxiapp.ui.theme.TaxiTheme

private const val CODE_LENGTH = 6

@Composable
fun PhoneSignInScreen(
    state: PhoneSignInState,
    countries: List<Country>,
    onBack: () -> Unit,
    onDigitsChange: (String) -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSendCode: () -> Unit,
    onCodeChange: (String) -> Unit,
    onResend: () -> Unit,
    onEditNumber: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val c = TaxiTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        AnimatedContent(
            targetState = state.step,
            transitionSpec = {
                val forward = targetState == PhoneStep.Code
                (slideInHorizontally(tween(320)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(220)))
                    .togetherWith(slideOutHorizontally(tween(320)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(160)))
            },
            label = "phone-step",
        ) { step ->
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .imePadding()
                    .padding(horizontal = 24.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                when (step) {
                    PhoneStep.Number -> NumberStep(state, countries, onBack, onDigitsChange, onCountrySelected, onSendCode)
                    PhoneStep.Code -> CodeStep(state, onEditNumber, onCodeChange, onResend)
                }
            }
        }

        SnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .systemBarsPadding()
                .imePadding()
                .padding(16.dp),
        )
    }
}

// ─── Step 1: phone number ─────────────────────────────────────────────────

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.NumberStep(
    state: PhoneSignInState,
    countries: List<Country>,
    onBack: () -> Unit,
    onDigitsChange: (String) -> Unit,
    onCountrySelected: (Country) -> Unit,
    onSendCode: () -> Unit,
) {
    val c = TaxiTheme.colors
    var pickingCountry by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    BackButton(onBack, stringResource(R.string.action_back))
    Spacer(Modifier.height(28.dp))
    Text(stringResource(R.string.phone_title), style = MaterialTheme.typography.headlineMedium, color = c.text)
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.phone_subtitle), style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
    Spacer(Modifier.height(28.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Country picker
        Row(
            Modifier
                .height(60.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(c.surfaceRaised)
                .clickable(onClickLabel = stringResource(R.string.phone_choose_country)) { pickingCountry = true }
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(state.country.flag, fontSize = 22.sp)
            Text(state.country.dialCode, style = MaterialTheme.typography.titleMedium, color = c.text)
            Icon(painterResource(R.drawable.ic_keyboard_arrow_down), null, tint = c.textMuted, modifier = Modifier.size(18.dp))
        }

        // Number field
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val borderColor = when {
            state.numberError != null -> c.error
            focused -> c.accent
            else -> c.surfaceRaised
        }
        BasicTextField(
            value = state.digits,
            onValueChange = onDigitsChange,
            singleLine = true,
            interactionSource = interaction,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onSendCode() }),
            textStyle = TextStyle(fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = c.text, letterSpacing = 0.5.sp),
            cursorBrush = SolidColor(c.accent),
            modifier = Modifier
                .weight(1f)
                .height(60.dp)
                .focusRequester(focus)
                .semantics { contentType = ContentType.PhoneNumberNational },
            decorationBox = { field ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(c.surfaceRaised)
                        .border(BorderStroke(1.5.dp, borderColor), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (state.digits.isEmpty()) {
                        Text(stringResource(R.string.phone_hint), style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp), color = c.textMuted)
                    }
                    field()
                }
            },
        )
    }

    Text(
        state.numberError ?: "",
        style = MaterialTheme.typography.bodySmall,
        color = c.error,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp).heightIn(min = 17.dp),
    )

    Spacer(Modifier.weight(1f))
    Text(
        stringResource(R.string.phone_rates),
        style = MaterialTheme.typography.bodySmall,
        color = c.textMuted,
        modifier = Modifier.padding(bottom = 12.dp),
    )
    PrimaryButton(
        text = stringResource(R.string.phone_send_code),
        onClick = onSendCode,
        enabled = state.canContinue,
        loading = state.sending,
        modifier = Modifier.padding(bottom = 16.dp),
    )

    if (pickingCountry) {
        CountrySheet(
            countries = countries,
            selected = state.country,
            onSelect = {
                onCountrySelected(it)
                pickingCountry = false
            },
            onDismiss = { pickingCountry = false },
        )
    }
}

// ─── Step 2: SMS code ─────────────────────────────────────────────────────

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.CodeStep(
    state: PhoneSignInState,
    onEditNumber: () -> Unit,
    onCodeChange: (String) -> Unit,
    onResend: () -> Unit,
) {
    val c = TaxiTheme.colors
    val view = LocalView.current

    // Shake and buzz on a wrong code.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.codeError) {
        if (state.codeError != null) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
            for (x in listOf(14f, -12f, 9f, -6f, 3f, 0f)) shake.animateTo(x, tween(45))
        }
    }
    LaunchedEffect(state.signedInRole) {
        if (state.signedInRole != null) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    }

    BackButton(onEditNumber, stringResource(R.string.phone_edit_number))
    Spacer(Modifier.height(28.dp))
    Text(stringResource(R.string.code_title), style = MaterialTheme.typography.headlineMedium, color = c.text)
    Spacer(Modifier.height(8.dp))
    Text(
        buildAnnotatedString {
            append(stringResource(R.string.code_subtitle_prefix))
            append(" ")
            withStyle(SpanStyle(color = c.text, fontWeight = FontWeight.Bold)) { append(state.country.dialCode + " " + state.digits) }
        },
        style = MaterialTheme.typography.bodyLarge,
        color = c.textMuted,
    )
    TextButton(onClick = onEditNumber, modifier = Modifier.offset(x = (-12).dp)) {
        Text(stringResource(R.string.phone_edit_number), style = MaterialTheme.typography.labelMedium, color = c.accentText)
    }
    Spacer(Modifier.height(16.dp))

    OtpField(
        code = state.code,
        hasError = state.codeError != null,
        enabled = !state.verifying,
        onCodeChange = onCodeChange,
        modifier = Modifier.offset { IntOffset(shake.value.dp.roundToPx(), 0) },
    )

    Spacer(Modifier.height(16.dp))
    Box(Modifier.heightIn(min = 24.dp)) {
        when {
            state.verifying || state.signedInRole != null -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(Modifier.size(16.dp), color = c.accent, strokeWidth = 2.dp)
                Text(stringResource(R.string.code_verifying), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            }
            state.codeError != null -> Text(state.codeError, style = MaterialTheme.typography.bodyMedium, color = c.error)
        }
    }

    Spacer(Modifier.weight(1f))

    // Resend
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (state.resendSeconds > 0) {
            Text(
                stringResource(R.string.code_resend_in, state.resendSeconds / 60, state.resendSeconds % 60),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                modifier = Modifier.padding(vertical = 14.dp),
            )
        } else {
            Text(stringResource(R.string.code_not_received), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            TextButton(onClick = onResend, enabled = !state.sending) {
                Text(stringResource(R.string.code_resend), style = MaterialTheme.typography.labelMedium, color = c.accentText)
            }
        }
    }
}

/**
 * Six boxes over one hidden text field, so paste, SMS autofill and backspace
 * work like a normal input.
 */
@Composable
private fun OtpField(
    code: String,
    hasError: Boolean,
    enabled: Boolean,
    onCodeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = TaxiTheme.colors
    val focus = remember { FocusRequester() }
    LaunchedEffect(enabled) { if (enabled) focus.requestFocus() }

    val caret = rememberInfiniteTransition(label = "caret")
    val caretAlpha by caret.animateFloat(1f, 0f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "caret-alpha")

    val label = stringResource(R.string.code_field_label)
    BasicTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = enabled,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
        cursorBrush = SolidColor(c.accent.copy(alpha = 0f)),
        textStyle = TextStyle(color = c.background.copy(alpha = 0f)),
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focus)
            .semantics {
                contentType = ContentType.SmsOtpCode
                contentDescription = label
            },
        decorationBox = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(CODE_LENGTH) { i ->
                    val char = code.getOrNull(i)?.toString() ?: ""
                    val isCurrent = i == code.length && enabled
                    val borderColor = when {
                        hasError -> c.error
                        isCurrent -> c.accent
                        char.isNotEmpty() -> c.line
                        else -> c.surfaceRaised
                    }
                    // Filled boxes pop in slightly.
                    val pop = remember { Animatable(1f) }
                    LaunchedEffect(char) {
                        if (char.isNotEmpty()) {
                            pop.snapTo(0.86f)
                            pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 700f))
                        }
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (char.isNotEmpty()) c.surface else c.surfaceRaised)
                            .border(BorderStroke(if (isCurrent || hasError) 2.dp else 1.dp, borderColor), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (char.isNotEmpty()) {
                            Text(
                                char,
                                style = MaterialTheme.typography.headlineMedium,
                                color = c.text,
                                modifier = Modifier.graphicsLayer(scaleX = pop.value, scaleY = pop.value),
                            )
                        } else if (isCurrent) {
                            Box(
                                Modifier
                                    .width(2.dp)
                                    .height(26.dp)
                                    .alpha(caretAlpha)
                                    .background(c.accent),
                            )
                        }
                    }
                }
            }
        },
    )
}

// ─── Country picker ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CountrySheet(
    countries: List<Country>,
    selected: Country,
    onSelect: (Country) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = TaxiTheme.colors
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(query, countries) {
        val q = query.trim()
        if (q.isEmpty()) countries
        else countries.filter { it.name.contains(q, ignoreCase = true) || it.dialCode.contains(q.removePrefix("+")) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 38.dp, height = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.line),
            )
        },
    ) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(stringResource(R.string.phone_choose_country), style = MaterialTheme.typography.titleLarge, color = c.text)
            Spacer(Modifier.height(14.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.text),
                cursorBrush = SolidColor(c.accent),
                modifier = Modifier.fillMaxWidth(),
                decorationBox = { field ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(c.surfaceRaised)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(painterResource(R.drawable.ic_search), null, tint = c.textMuted, modifier = Modifier.size(18.dp))
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) Text(stringResource(R.string.phone_search_country), style = MaterialTheme.typography.bodyLarge, color = c.textMuted)
                            field()
                        }
                    }
                },
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(filtered, key = { it.name + it.dialCode }) { country ->
                    val isSelected = country == selected
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) c.accentTint else c.surface)
                            .clickable { onSelect(country) }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(country.flag, fontSize = 22.sp)
                        Text(country.name, style = MaterialTheme.typography.bodyLarge, color = c.text, modifier = Modifier.weight(1f))
                        Text(country.dialCode, style = MaterialTheme.typography.titleMedium, color = if (isSelected) c.accentText else c.textMuted)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────

private val PreviewCountry = Country("🇺🇸", "United States", "+1")

@Preview(widthDp = 400, heightDp = 860)
@Composable
private fun NumberPreview() = TaxiTheme(darkTheme = true) {
    PhoneSignInScreen(PhoneSignInState(country = PreviewCountry, digits = "4155550132"), listOf(PreviewCountry), {}, {}, {}, {}, {}, {}, {}, {})
}

@Preview(widthDp = 400, heightDp = 860)
@Composable
private fun CodePreview() = TaxiTheme(darkTheme = false) {
    PhoneSignInScreen(
        PhoneSignInState(step = PhoneStep.Code, country = PreviewCountry, digits = "4155550132", code = "4719", resendSeconds = 42),
        listOf(PreviewCountry), {}, {}, {}, {}, {}, {}, {}, {},
    )
}
