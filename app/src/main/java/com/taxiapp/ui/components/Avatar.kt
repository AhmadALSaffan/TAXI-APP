package com.taxiapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.taxiapp.ui.theme.Manrope
import com.taxiapp.ui.theme.TaxiTheme

/** Profile photo, or the person's initials on an amber tint when there's no photo. */
@Composable
fun Avatar(name: String, photoUrl: String, size: Dp, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(c.accentTint),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(name),
            color = c.accentText,
            fontFamily = Manrope,
            fontWeight = FontWeight.ExtraBold,
            fontSize = (size.value * 0.36f).sp,
        )
        if (photoUrl.isNotBlank()) {
            AsyncImage(
                model = photoUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "•" }

fun firstName(name: String): String = name.trim().substringBefore(" ")
