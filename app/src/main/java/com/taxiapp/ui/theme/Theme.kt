package com.taxiapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun TaxiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val c = if (darkTheme) SignalAmberDark else SignalAmberLight
    val base = if (darkTheme) darkColorScheme() else lightColorScheme()
    val scheme = base.copy(
        primary = c.accent,
        onPrimary = c.onAccent,
        primaryContainer = c.accentTint,
        onPrimaryContainer = c.accentText,
        background = c.background,
        onBackground = c.text,
        surface = c.surface,
        onSurface = c.text,
        surfaceVariant = c.surfaceRaised,
        onSurfaceVariant = c.textMuted,
        surfaceContainer = c.surface,
        surfaceContainerHigh = c.surfaceRaised,
        inverseSurface = c.text,
        inverseOnSurface = c.background,
        outline = c.line,
        outlineVariant = c.line,
        error = c.error,
    )

    CompositionLocalProvider(LocalTaxiColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = TaxiTypography, content = content)
    }
}

/** App colours beyond Material's scheme: `TaxiTheme.colors.mapRoad`, `TaxiTheme.colors.accentText`, … */
object TaxiTheme {
    val colors: TaxiColors
        @Composable @ReadOnlyComposable
        get() = LocalTaxiColors.current
}
