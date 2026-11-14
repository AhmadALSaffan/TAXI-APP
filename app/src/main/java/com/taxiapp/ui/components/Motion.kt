package com.taxiapp.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.taxiapp.ui.theme.TaxiTheme
import kotlinx.coroutines.delay

/**
 * Clickable that sinks slightly while pressed and springs back on release,
 * so every tap gets a physical response.
 */
fun Modifier.pressable(
    onClickLabel: String? = null,
    role: Role? = Role.Button,
    enabled: Boolean = true,
    pressedScale: Float = 0.97f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "press",
    )
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(
            interactionSource = interaction,
            indication = LocalIndication.current,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            onClick = onClick,
        )
}

/**
 * Fades and lifts content in when it first appears. Give siblings increasing
 * [index] values so they arrive one after another.
 */
fun Modifier.enterStagger(index: Int, key: Any = Unit): Modifier = composed {
    val progress = remember(key) { Animatable(0f) }
    LaunchedEffect(key) {
        delay(55L * index)
        progress.animateTo(1f, spring(dampingRatio = 0.85f, stiffness = 260f))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 18.dp.toPx()
    }
}

/** A soft highlight sweeping across a placeholder while content loads. */
fun Modifier.shimmer(): Modifier = composed {
    val c = TaxiTheme.colors
    val t by rememberInfiniteTransition(label = "shimmer").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "shimmer-t",
    )
    // Placeholders often sit on raised surfaces, so draw them in the divider tone.
    val base = c.line
    val highlight = lerp(c.line, c.text, if (c.isDark) 0.10f else 0.06f)
    this.drawBehind {
        val w = size.width
        val x = -w + 3f * w * t
        drawRect(
            Brush.linearGradient(
                listOf(base, highlight, base),
                start = Offset(x, 0f),
                end = Offset(x + w, size.height),
            ),
        )
    }
}

/** Placeholder block shaped like the content that's on its way. */
@Composable
fun Skeleton(modifier: Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).shimmer())
}
