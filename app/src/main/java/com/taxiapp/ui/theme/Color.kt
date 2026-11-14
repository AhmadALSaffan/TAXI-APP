package com.taxiapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Signal Amber: graphite surfaces with a refined taxi amber.
 *
 * [accent] is for fills (buttons, routes, markers). Use [accentText] when amber
 * is the text or icon colour itself — on light surfaces plain amber fails contrast.
 */
@Immutable
data class TaxiColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val text: Color,
    val textMuted: Color,
    val line: Color,
    val accent: Color,
    val accentText: Color,
    val onAccent: Color,
    val accentTint: Color,
    val error: Color,
    val mapBase: Color,
    val mapRoad: Color,
    val mapRoadMinor: Color,
    val mapWater: Color,
    val mapPark: Color,
)

val SignalAmberDark = TaxiColors(
    isDark = true,
    background = Color(0xFF0E0F11),
    surface = Color(0xFF17191C),
    surfaceRaised = Color(0xFF212428),
    text = Color(0xFFF4F4F1),
    textMuted = Color(0xFF8F9297),
    line = Color(0xFF2B2E33),
    accent = Color(0xFFFFB224),
    accentText = Color(0xFFFFB224),
    onAccent = Color(0xFF1A1300),
    accentTint = Color(0x1CFFB224),
    error = Color(0xFFFF6B5E),
    mapBase = Color(0xFF131518),
    mapRoad = Color(0xFF25282D),
    mapRoadMinor = Color(0xFF1C1F23),
    mapWater = Color(0xFF0E1820),
    mapPark = Color(0xFF141A15),
)

val SignalAmberLight = TaxiColors(
    isDark = false,
    background = Color(0xFFF4F4F2),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFF2F2EF),
    text = Color(0xFF111214),
    textMuted = Color(0xFF64676C),
    line = Color(0xFFE3E3DF),
    accent = Color(0xFFFFAA0F),
    accentText = Color(0xFF9A6400),
    onAccent = Color(0xFF1A1300),
    accentTint = Color(0x21FFAA0F),
    error = Color(0xFFC62828),
    mapBase = Color(0xFFE9E9E6),
    mapRoad = Color(0xFFFFFFFF),
    mapRoadMinor = Color(0xFFF6F6F4),
    mapWater = Color(0xFFCBD8E2),
    mapPark = Color(0xFFD8E3D4),
)

val LocalTaxiColors = staticCompositionLocalOf { SignalAmberDark }
