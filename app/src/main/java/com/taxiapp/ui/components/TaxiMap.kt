package com.taxiapp.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.GoogleMapOptions
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.GoogleMapComposable
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.Polyline
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.RoundCap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.graphics.Color
import com.taxiapp.R
import kotlinx.coroutines.launch
import com.taxiapp.ui.theme.TaxiTheme

/** Where the map starts before the first location fix: central Gomel. */
val DefaultCamera = LatLng(52.4345, 30.9754)

/** Google Map in the Signal Amber style, with Google's own buttons hidden. */
@Composable
fun TaxiMap(
    cameraPositionState: CameraPositionState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable @GoogleMapComposable () -> Unit = {},
) {
    val context = LocalContext.current
    val dark = TaxiTheme.colors.isDark
    val style = remember(dark) {
        val res = if (dark) R.raw.map_style_amber_dark else R.raw.map_style_amber_light
        MapStyleOptions(context.resources.openRawResource(res).bufferedReader().use { it.readText() })
    }
    // Cover the map in its own base colour until tiles are drawn, then fade it away,
    // so there's never a grey flash.
    val mapBaseArgb = TaxiTheme.colors.mapBase.toArgb()
    var loaded by remember { mutableStateOf(false) }
    // Never keep the map hidden for long, even if the loaded callback is slow or never comes.
    LaunchedEffect(Unit) { kotlinx.coroutines.delay(1500); loaded = true }
    val cover by animateFloatAsState(if (loaded) 0f else 1f, tween(450), label = "map-cover")
    Box(modifier) {
    GoogleMap(
        modifier = Modifier.matchParentSize(),
        onMapLoaded = { loaded = true },
        // Unloaded tiles show the theme's map colour, not Google's default cream.
        googleMapOptionsFactory = { GoogleMapOptions().backgroundColor(mapBaseArgb) },
        cameraPositionState = cameraPositionState,
        contentPadding = contentPadding,
        properties = remember(style) { MapProperties(mapStyleOptions = style, isMyLocationEnabled = false) },
        uiSettings = remember {
            MapUiSettings(
                zoomControlsEnabled = false,
                myLocationButtonEnabled = false,
                compassEnabled = false,
                mapToolbarEnabled = false,
                indoorLevelPickerEnabled = false,
            )
        },
        content = content,
    )
    if (cover > 0f) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = cover }
                .background(TaxiTheme.colors.mapBase),
        )
    }
    }
}

/**
 * The pickup pin that sits over the map centre. It lifts while the map is
 * dragged and settles back when the rider lets go; the head pulses while the
 * address for the new spot is being looked up.
 */
@Composable
fun PickupPin(lifted: Boolean, resolving: Boolean, modifier: Modifier = Modifier, destination: Boolean = false) {
    val c = TaxiTheme.colors
    val lift by animateFloatAsState(
        if (lifted) 1f else 0f,
        spring(dampingRatio = if (lifted) 0.8f else 0.42f, stiffness = 420f),
        label = "pin-lift",
    )
    val pulse by rememberInfiniteTransition(label = "pin-pulse").animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(520), RepeatMode.Reverse), label = "pin-pulse-v",
    )
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Column(
            Modifier.graphicsLayer { translationY = -16.dp.toPx() * lift },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .shadow(10.dp, if (destination) RoundedCornerShape(12.dp) else CircleShape)
                    .clip(if (destination) RoundedCornerShape(12.dp) else CircleShape)
                    .background(if (destination) c.text else c.accent),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .graphicsLayer {
                            val s = if (resolving) pulse else 1f
                            scaleX = s; scaleY = s
                        }
                        .clip(if (destination) RoundedCornerShape(3.dp) else CircleShape)
                        .background(if (destination) c.surface else c.onAccent),
                )
            }
            Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
        }
        // Ground shadow: shrinks and fades as the pin rises.
        Box(
            Modifier
                .graphicsLayer {
                    scaleX = 1f - 0.45f * lift
                    scaleY = 1f - 0.45f * lift
                    alpha = 0.45f - 0.25f * lift
                }
                .size(width = 14.dp, height = 5.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Color.Black),
        )
    }
}

/** The rider's position: an amber dot with a soft, breathing halo. */
@Composable
@GoogleMapComposable
fun UserLocationMarker(position: LatLng) {
    val c = TaxiTheme.colors
    val pulse = rememberInfiniteTransition(label = "me").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "me-pulse",
    )
    val t by pulse
    Circle(
        center = position,
        radius = 40.0 + 80.0 * t,
        fillColor = c.accent.copy(alpha = 0.22f * (1f - t)),
        strokeWidth = 0f,
    )
    val state = remember { MarkerState(position) }
    state.position = position
    MarkerComposable(c.isDark, state = state, anchor = Offset(0.5f, 0.5f), zIndex = 2f) {
        Box(
            Modifier
                .size(22.dp)
                .shadow(6.dp, CircleShape)
                .clip(CircleShape)
                .background(c.surface)
                .border(5.dp, c.accent, CircleShape),
        )
    }
}

/** A car marker for a driver's own position. */
@Composable
@GoogleMapComposable
fun CarMarker(position: LatLng) {
    val c = TaxiTheme.colors
    val state = remember { MarkerState(position) }
    state.position = position
    MarkerComposable(c.isDark, state = state, anchor = Offset(0.5f, 0.5f), zIndex = 3f) {
        Box(
            Modifier
                .size(40.dp)
                .shadow(8.dp, CircleShape)
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_car_filled), null, tint = c.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}

/** A pickup pin for a trip request. */
@Composable
@GoogleMapComposable
fun PickupMarker(position: LatLng, label: String, onClick: () -> Unit = {}) {
    val c = TaxiTheme.colors
    val state = remember(position) { MarkerState(position) }
    MarkerComposable(label, c.isDark, state = state, anchor = Offset(0.5f, 1f), onClick = { onClick(); true }) {
        Box(
            Modifier
                .shadow(6.dp, RoundedCornerShape(12.dp))
                .clip(RoundedCornerShape(12.dp))
                .background(c.surface)
                .border(2.dp, c.accent, RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = c.text,
            )
        }
    }
}

/** Ring that grows from the driver while they're online and waiting for requests. */
@Composable
@GoogleMapComposable
fun SearchingRing(center: LatLng) {
    val c = TaxiTheme.colors
    val t by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "radar-t",
    )
    Circle(
        center = center,
        radius = 120.0 + 900.0 * t,
        fillColor = c.accent.copy(alpha = 0.12f * (1f - t)),
        strokeColor = c.accent.copy(alpha = 0.5f * (1f - t)),
        strokeWidth = 3f,
    )
}

/**
 * The trip route in amber over a darker casing. It draws itself from the start
 * when [points] first arrive. Draws nothing until there is a real road route:
 * a straight line between the ends would cut through buildings.
 */
@Composable
@GoogleMapComposable
fun RouteLine(points: List<LatLng>) {
    val c = TaxiTheme.colors
    if (points.size < 2) return
    val reveal = remember(points) { Animatable(0f) }
    LaunchedEffect(points) { reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val shown = remember(points, reveal.value) {
        val n = (points.size * reveal.value).toInt().coerceIn(2, points.size)
        points.subList(0, n)
    }
    Polyline(
        points = shown,
        color = if (c.isDark) Color.Black.copy(alpha = 0.55f) else c.accentText.copy(alpha = 0.35f),
        width = 22f,
        jointType = JointType.ROUND,
        startCap = RoundCap(),
        endCap = RoundCap(),
        zIndex = 0f,
    )
    Polyline(
        points = shown,
        color = c.accent,
        width = 12f,
        jointType = JointType.ROUND,
        startCap = RoundCap(),
        endCap = RoundCap(),
        zIndex = 1f,
    )
}

/** Start of the route: an amber ring. */
@Composable
@GoogleMapComposable
fun RouteStartMarker(position: LatLng) {
    val c = TaxiTheme.colors
    val state = remember(position) { MarkerState(position) }
    MarkerComposable(c.isDark, state = state, anchor = Offset(0.5f, 0.5f), zIndex = 2f) {
        Box(
            Modifier
                .size(20.dp)
                .shadow(5.dp, CircleShape)
                .clip(CircleShape)
                .background(c.surface)
                .border(5.dp, c.accent, CircleShape),
        )
    }
}

/** End of the route: a solid square, so start and end read differently at a glance. */
@Composable
@GoogleMapComposable
fun RouteEndMarker(position: LatLng) {
    val c = TaxiTheme.colors
    val state = remember(position) { MarkerState(position) }
    MarkerComposable(c.isDark, state = state, anchor = Offset(0.5f, 0.5f), zIndex = 2f) {
        Box(
            Modifier
                .size(20.dp)
                .shadow(5.dp, RoundedCornerShape(5.dp))
                .clip(RoundedCornerShape(5.dp))
                .background(c.text),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(c.surface))
        }
    }
}

/** Glides between position updates instead of jumping, e.g. for a moving driver. */
@Composable
fun rememberGlidingLatLng(target: LatLng?): LatLng? {
    if (target == null) return null
    val lat = remember { Animatable(target.latitude.toFloat()) }
    val lng = remember { Animatable(target.longitude.toFloat()) }
    LaunchedEffect(target) {
        kotlinx.coroutines.coroutineScope {
            launch { lat.animateTo(target.latitude.toFloat(), tween(1200, easing = LinearEasing)) }
            launch { lng.animateTo(target.longitude.toFloat(), tween(1200, easing = LinearEasing)) }
        }
    }
    // Floats lose precision at map scale only beyond ~1 m, which is fine for a moving car.
    return LatLng(lat.value.toDouble(), lng.value.toDouble())
}
