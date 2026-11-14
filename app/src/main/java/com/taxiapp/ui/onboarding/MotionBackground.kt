package com.taxiapp.ui.onboarding

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.taxiapp.ui.theme.Manrope
import com.taxiapp.ui.theme.TaxiColors
import kotlin.math.abs

/**
 * A slowly drifting night map that plays like a background video, drawn in the
 * app's own map colours. Each onboarding page brings forward its own scene:
 *   0 – rides drawing across the city
 *   1 – a driver heading to a pulsing pickup point
 *   2 – a route with its fare pinned at the destination
 *
 * [pagerPosition] is the pager's fractional position; it pans the map (parallax)
 * and cross-fades the scenes while the user swipes.
 *
 * World units are "design dp" for a 400 dp-wide screen, scaled to the real width.
 */
@Composable
fun MotionBackground(
    colors: TaxiColors,
    pagerPosition: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    val transition = rememberInfiniteTransition(label = "onboarding-motion")
    val routeClock by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(4800, easing = LinearEasing)), label = "routes",
    )
    val trafficClock by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(18000, easing = LinearEasing)), label = "traffic",
    )
    val drift by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(26000), RepeatMode.Reverse), label = "drift",
    )
    val pulse by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200, easing = LinearEasing)), label = "pulse",
    )

    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier) {
        val routeT = if (reduceMotion) 0.75f else routeClock
        val trafficT = if (reduceMotion) 0.3f else trafficClock
        val driftT = if (reduceMotion) 0.5f else drift
        val pulseT = if (reduceMotion) 0.4f else pulse

        drawRect(colors.mapBase)

        val s = size.width / 400f // design dp → px
        val camX = -(pagerPosition * 110f + driftT * 36f)
        // Start the city lower so routes play below the status bar and wordmark.
        val camY = 80f - driftT * 18f

        scale(s, s, pivot = Offset.Zero) {
            translate(camX, camY) {
                drawCity(colors, size.height / s)
                drawTraffic(colors, trafficT)

                val w0 = sceneWeight(pagerPosition, 0)
                val w1 = sceneWeight(pagerPosition, 1)
                val w2 = sceneWeight(pagerPosition, 2)

                if (w0 > 0f) {
                    sceneCache.ridesScene.forEachIndexed { i, route ->
                        drawRoute(route, colors, phase(routeT, i * 0.33f), w0)
                    }
                }
                if (w1 > 0f) drawPickupScene(sceneCache, colors, routeT, pulseT, w1)
                if (w2 > 0f) drawFareScene(sceneCache, colors, routeT, w2, textMeasurer, s)
            }
        }
    }
}

// ─── Scene geometry (design dp) ────────────────────────────────────────────

private val MAJOR_X = listOf(40f, 190f, 340f, 490f, 640f)
private val MINOR_X = listOf(115f, 265f, 415f, 565f, 715f)
private val MAJOR_Y = listOf(120f, 300f, 480f, 660f, 840f, 1020f)
private val MINOR_Y = listOf(210f, 390f, 570f, 750f, 930f)

private class MotionScene {
    val ridesScene: List<RoutePath> = listOf(
        route(40f to 570f, 265f to 570f, 265f to 300f, 415f to 300f, 415f to 120f),
        route(640f to 660f, 640f to 390f, 490f to 390f, 490f to 210f),
        route(115f to 120f, 115f to 210f, 340f to 210f, 340f to 40f),
    )
    val driverPath: RoutePath = route(565f to 660f, 565f to 390f, 340f to 390f)
    val pickup = Offset(340f, 390f)
    val fareRoute: RoutePath = route(190f to 750f, 190f to 480f, 415f to 480f, 415f to 210f)

    val water = Path().apply {
        moveTo(-60f, -20f); lineTo(330f, -20f)
        cubicTo(290f, 40f, 210f, 80f, 120f, 70f)
        cubicTo(40f, 62f, -10f, 100f, -60f, 120f); close()
    }
    val parks = listOf(
        Offset(198f, 308f) to Size(59f, 74f),
        Offset(498f, 128f) to Size(59f, 74f),
        Offset(48f, 488f) to Size(59f, 74f),
        Offset(573f, 488f) to Size(59f, 74f),
    )
    val traffic = listOf( // a road and the direction cars travel along it
        Car(horizontal = true, at = 300f, from = -40f, to = 760f),
        Car(horizontal = true, at = 660f, from = 760f, to = -40f),
        Car(horizontal = false, at = 190f, from = -40f, to = 1100f),
        Car(horizontal = false, at = 490f, from = 1100f, to = -40f),
        Car(horizontal = true, at = 480f, from = -40f, to = 760f, phase = 0.5f),
        Car(horizontal = false, at = 640f, from = -40f, to = 1100f, phase = 0.35f),
    )
}

private data class Car(
    val horizontal: Boolean, val at: Float, val from: Float, val to: Float, val phase: Float = 0f,
)

private class RoutePath(val path: Path) {
    private val measure = PathMeasure().apply { setPath(path, false) }
    val length: Float = measure.length

    fun segment(fraction: Float): Path = Path().also {
        measure.getSegment(0f, length * fraction.coerceIn(0f, 1f), it, true)
    }

    fun pointAt(fraction: Float): Offset = measure.getPosition(length * fraction.coerceIn(0f, 1f))
}

private fun route(vararg points: Pair<Float, Float>) = RoutePath(
    Path().apply {
        moveTo(points[0].first, points[0].second)
        points.drop(1).forEach { (x, y) -> lineTo(x, y) }
    },
)

// 1 at the page, fading to 0 one page away.
private fun sceneWeight(position: Float, page: Int) = (1f - abs(position - page)).coerceIn(0f, 1f)

private fun phase(t: Float, offset: Float) = (t + offset) % 1f

// ─── Drawing ───────────────────────────────────────────────────────────────

private fun DrawScope.drawCity(c: TaxiColors, visibleHeight: Float) {
    drawPath(sceneCache.water, c.mapWater)
    sceneCache.parks.forEach { (topLeft, size) ->
        drawRoundRect(c.mapPark, topLeft, size, CornerRadius(10f))
    }
    val bottom = visibleHeight + 120f
    MINOR_X.forEach { x -> drawLine(c.mapRoadMinor, Offset(x, -40f), Offset(x, bottom), 4f, StrokeCap.Round) }
    MINOR_Y.forEach { y -> drawLine(c.mapRoadMinor, Offset(-40f, y), Offset(800f, y), 4f, StrokeCap.Round) }
    MAJOR_X.forEach { x -> drawLine(c.mapRoad, Offset(x, -40f), Offset(x, bottom), 8f, StrokeCap.Round) }
    MAJOR_Y.forEach { y -> drawLine(c.mapRoad, Offset(-40f, y), Offset(800f, y), 8f, StrokeCap.Round) }
    drawLine(c.mapRoad, Offset(-60f, 780f), Offset(800f, 40f), 12f, StrokeCap.Round)
}

private val sceneCache = MotionScene()

private fun DrawScope.drawTraffic(c: TaxiColors, t: Float) {
    sceneCache.traffic.forEach { car ->
        val u = (t + car.phase) % 1f
        val pos = car.from + (car.to - car.from) * u
        val center = if (car.horizontal) Offset(pos, car.at) else Offset(car.at, pos)
        val heading = when {
            car.horizontal && car.to > car.from -> 90f
            car.horizontal -> 270f
            car.to > car.from -> 180f
            else -> 0f
        }
        drawCar(center, heading, c.text.copy(alpha = 0.55f), c.mapBase)
    }
}

private fun DrawScope.drawCar(center: Offset, headingDegrees: Float, body: Color, glass: Color) {
    rotate(headingDegrees, pivot = center) {
        drawRoundRect(body, Offset(center.x - 6f, center.y - 10f), Size(12f, 20f), CornerRadius(4f))
        drawRoundRect(glass.copy(alpha = 0.6f), Offset(center.x - 4f, center.y - 6f), Size(8f, 4f), CornerRadius(1.5f))
    }
}

/** A route that draws itself, holds, then fades before starting again. */
private fun DrawScope.drawRoute(route: RoutePath, c: TaxiColors, t: Float, weight: Float) {
    val drawProgress = (t / 0.62f).coerceAtMost(1f)
    val fade = if (t > 0.86f) 1f - (t - 0.86f) / 0.14f else 1f
    val alpha = weight * fade
    if (alpha <= 0f) return

    val drawn = route.segment(easeOut(drawProgress))
    drawPath(drawn, c.accent.copy(alpha = 0.18f * alpha), style = Stroke(14f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawPath(drawn, c.accent.copy(alpha = alpha), style = Stroke(4.5f, cap = StrokeCap.Round, join = StrokeJoin.Round))

    val start = route.pointAt(0f)
    drawCircle(c.mapBase.copy(alpha = alpha), 7f, start)
    drawCircle(c.accent.copy(alpha = alpha), 7f, start, style = Stroke(3.5f))
    if (drawProgress < 1f) {
        drawCircle(c.accent.copy(alpha = alpha), 5.5f, route.pointAt(easeOut(drawProgress)))
    } else {
        drawPin(route.pointAt(1f), c, alpha)
    }
}

private fun DrawScope.drawPickupScene(scene: MotionScene, c: TaxiColors, t: Float, pulseT: Float, weight: Float) {
    // Pulsing pickup point.
    drawCircle(c.accent.copy(alpha = (1f - pulseT) * 0.35f * weight), 10f + 34f * pulseT, scene.pickup)
    drawCircle(c.accent.copy(alpha = 0.18f * weight), 22f, scene.pickup)
    drawCircle(c.accent.copy(alpha = weight), 8f, scene.pickup)
    drawCircle(c.mapBase.copy(alpha = weight), 8f, scene.pickup, style = Stroke(3f))

    // Driver approaching along a dotted path, then waiting at the pickup.
    val travel = easeInOut((t / 0.8f).coerceAtMost(1f))
    drawPath(
        scene.driverPath.path,
        c.accent.copy(alpha = 0.7f * weight),
        style = Stroke(4f, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(1f, 10f))),
    )
    val pos = scene.driverPath.pointAt(travel)
    val ahead = scene.driverPath.pointAt((travel + 0.01f).coerceAtMost(1f))
    val heading = if (abs(ahead.x - pos.x) > abs(ahead.y - pos.y)) {
        if (ahead.x > pos.x) 90f else 270f
    } else {
        if (ahead.y > pos.y) 180f else 0f
    }
    drawCircle(c.surface.copy(alpha = weight), 18f, pos)
    drawCar(pos, heading, c.text.copy(alpha = weight), c.surface)
}

private fun DrawScope.drawFareScene(
    scene: MotionScene,
    c: TaxiColors,
    t: Float,
    weight: Float,
    textMeasurer: TextMeasurer,
    pxPerUnit: Float,
) {
    drawRoute(scene.fareRoute, c, t.coerceAtMost(0.8f), weight)

    // Fare chip above the destination; the price is what PricingEngine charges for this length of trip.
    val dest = scene.fareRoute.pointAt(1f)
    val layout = textMeasurer.measure(
        "12.60 BYN · 9 min",
        TextStyle(fontFamily = Manrope, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = c.onAccent),
    )
    val padH = 12f
    val padV = 7f
    val chipW = layout.size.width / pxPerUnit + padH * 2
    val chipH = layout.size.height / pxPerUnit + padV * 2
    val topLeft = Offset(dest.x - chipW / 2f, dest.y - 34f - chipH)
    val appear = ((t - 0.5f) / 0.15f).coerceIn(0f, 1f) * weight
    if (appear <= 0f) return
    drawRoundRect(c.accent.copy(alpha = appear), topLeft, Size(chipW, chipH), CornerRadius(chipH / 2f))
    scale(1f / pxPerUnit, 1f / pxPerUnit, pivot = Offset(topLeft.x + padH, topLeft.y + padV)) {
        drawText(layout, topLeft = Offset(topLeft.x + padH, topLeft.y + padV), alpha = appear)
    }
}

private fun DrawScope.drawPin(at: Offset, c: TaxiColors, alpha: Float) {
    val pin = Path().apply {
        moveTo(at.x, at.y)
        cubicTo(at.x - 4f, at.y - 8f, at.x - 14f, at.y - 14f, at.x - 14f, at.y - 24f)
        cubicTo(at.x - 14f, at.y - 32f, at.x - 8f, at.y - 38f, at.x, at.y - 38f)
        cubicTo(at.x + 8f, at.y - 38f, at.x + 14f, at.y - 32f, at.x + 14f, at.y - 24f)
        cubicTo(at.x + 14f, at.y - 14f, at.x + 4f, at.y - 8f, at.x, at.y)
        close()
    }
    drawPath(pin, c.accent.copy(alpha = alpha))
    drawCircle(c.onAccent.copy(alpha = alpha), 5f, Offset(at.x, at.y - 24f))
}

private fun easeOut(x: Float) = 1f - (1f - x) * (1f - x) * (1f - x)
private fun easeInOut(x: Float) = if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).let { it * it * it } / 2f
