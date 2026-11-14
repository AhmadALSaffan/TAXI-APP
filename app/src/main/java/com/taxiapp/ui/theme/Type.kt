package com.taxiapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.taxiapp.R

// Manrope variable font (res/font/manrope.ttf, SIL OFL — see licenses/).
@OptIn(ExperimentalTextApi::class)
private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

val TaxiTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.03).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.02).em,
    ),
    titleLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.01).em,
    ),
    titleMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 14.sp, lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 17.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.ExtraBold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.01.em,
    ),
    labelMedium = TextStyle(
        fontFamily = Manrope, fontWeight = FontWeight.Bold,
        fontSize = 13.sp, lineHeight = 16.sp,
    ),
)
