package com.taxiapp.ui.ride

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.rememberCameraPositionState
import com.taxiapp.R
import com.taxiapp.ui.components.DefaultCamera
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.ui.components.Avatar
import com.taxiapp.ui.components.BackButton
import com.taxiapp.ui.components.CarMarker
import com.taxiapp.ui.components.PrimaryButton
import com.taxiapp.ui.components.RouteEndMarker
import com.taxiapp.ui.components.RouteLine
import com.taxiapp.ui.components.RouteStartMarker
import com.taxiapp.ui.components.SearchingRing
import com.taxiapp.ui.components.SecondaryButton
import com.taxiapp.ui.components.Skeleton
import com.taxiapp.ui.components.TaxiMap
import com.taxiapp.ui.components.enterStagger
import com.taxiapp.ui.components.firstName
import com.taxiapp.ui.components.pressable
import com.taxiapp.ui.components.rememberGlidingLatLng
import com.taxiapp.ui.home.formatPrice
import com.taxiapp.ui.theme.TaxiTheme
import com.taxiapp.util.PricingEngine
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

private val DriverStatuses = setOf(TripStatus.ACCEPTED, TripStatus.ARRIVED, TripStatus.ONGOING)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TripTrackingScreen(
    state: TripUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onArrived: () -> Unit,
    onStart: () -> Unit,
    onComplete: (cashReceived: Boolean) -> Unit,
    onNavigate: (Trip) -> Unit,
    onDownloadReceipt: (Trip) -> Unit,
    onDone: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val c = TaxiTheme.colors
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); onMessageShown() }
    }

    val trip = state.trip
    Box(Modifier.fillMaxSize().background(c.background)) {
        when {
            state.notFound -> NotFound(onBack)
            trip != null && trip.status == TripStatus.COMPLETED && !state.isDriver ->
                Receipt(trip, onDownload = { onDownloadReceipt(trip) }, onDone = onDone)
            else -> LiveTrip(state, onBack, onCancel, onArrived, onStart, onComplete, onNavigate, onDone)
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    }
}

// ─── Live trip: map + status panel ────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LiveTrip(
    state: TripUiState,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onArrived: () -> Unit,
    onStart: () -> Unit,
    onComplete: (Boolean) -> Unit,
    onNavigate: (Trip) -> Unit,
    onDone: () -> Unit,
) {
    val c = TaxiTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val trip = state.trip
    var panelHeight by remember { mutableStateOf(0.dp) }
    var topHeight by remember { mutableStateOf(0.dp) }
    var confirmCancel by remember { mutableStateOf(false) }
    var finishSheet by remember { mutableStateOf(false) }
    var slideReset by remember { mutableIntStateOf(0) }

    val pickup = trip?.let { LatLng(it.pickupLat, it.pickupLng) }
    val dropoff = trip?.let { LatLng(it.dropoffLat, it.dropoffLng) }
    val driverTarget = trip?.takeIf { it.status in DriverStatuses && it.driverLat != 0.0 }?.let { LatLng(it.driverLat, it.driverLng) }
    val driver = rememberGlidingLatLng(driverTarget)

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(pickup ?: DefaultCamera, if (pickup != null) 15f else 11f)
    }
    // Once the rider pans the map themselves, stop following until they recenter.
    var userMoved by remember { mutableStateOf(false) }
    LaunchedEffect(camera.isMoving) {
        if (camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) userMoved = true
    }

    fun framePoints(): List<LatLng> = when (trip?.status) {
        null -> emptyList()
        TripStatus.SEARCHING -> listOfNotNull(pickup)
        TripStatus.ACCEPTED, TripStatus.ARRIVED -> listOfNotNull(pickup, driverTarget)
        TripStatus.ONGOING -> listOfNotNull(driverTarget ?: pickup, dropoff)
        else -> listOfNotNull(pickup, dropoff) + state.route
    }
    suspend fun frame() {
        val points = framePoints()
        if (points.isEmpty() || panelHeight == 0.dp) return
        val update = if (points.size == 1 || points.all { it == points.first() }) {
            CameraUpdateFactory.newLatLngZoom(points.first(), 15.5f)
        } else {
            val b = LatLngBounds.builder().apply { points.forEach { include(it) } }.build()
            CameraUpdateFactory.newLatLngBounds(b, with(density) { 64.dp.roundToPx() })
        }
        runCatching { camera.animate(update, 900) }
    }
    LaunchedEffect(trip?.status, driverTarget, state.route, panelHeight, topHeight) {
        if (!userMoved) frame()
    }

    Box(Modifier.fillMaxSize()) {
        TaxiMap(camera, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topHeight, bottom = panelHeight)) {
            if (trip != null && pickup != null && dropoff != null) {
                when (trip.status) {
                    TripStatus.SEARCHING -> {
                        SearchingRing(pickup)
                        RouteStartMarker(pickup)
                    }
                    TripStatus.ACCEPTED, TripStatus.ARRIVED -> {
                        RouteStartMarker(pickup)
                    }
                    else -> {
                        RouteLine(state.route)
                        RouteStartMarker(pickup)
                        RouteEndMarker(dropoff)
                    }
                }
                driver?.let { CarMarker(it) }
            }
        }

        // Top bar
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack, stringResource(R.string.action_back), Modifier.shadow(12.dp, CircleShape))
        }

        // Recenter, shown once the map has been moved by hand.
        AnimatedVisibility(
            userMoved,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = panelHeight + 16.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.surface)
                    .pressable(onClickLabel = stringResource(R.string.home_recenter)) {
                        userMoved = false
                        scope.launch { frame() }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.home_recenter), tint = c.accentText, modifier = Modifier.size(20.dp))
            }
        }

        // Status panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeight = with(density) { it.height.toDp() } }
                .shadow(28.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(c.line))
            Spacer(Modifier.height(16.dp))
            AnimatedContent(
                targetState = trip?.status,
                transitionSpec = {
                    (fadeIn(tween(260, delayMillis = 80)) + slideInVertically(tween(320)) { it / 6 }) togetherWith
                        fadeOut(tween(140)) using SizeTransform(clip = false)
                },
                label = "trip-panel",
            ) { status ->
                if (trip == null || status == null) {
                    PanelSkeleton()
                } else if (state.isDriver) {
                    DriverPanel(
                        trip = trip.copy(status = status),
                        busy = state.busy,
                        slideReset = slideReset,
                        onArrived = onArrived,
                        onStart = onStart,
                        onSlideFinished = { finishSheet = true },
                        onNavigate = { onNavigate(trip) },
                        onCancel = { confirmCancel = true },
                        onDone = onDone,
                    )
                } else {
                    RiderPanel(
                        trip = trip.copy(status = status),
                        busy = state.busy,
                        onCancel = { confirmCancel = true },
                        onDone = onDone,
                    )
                }
            }
        }
    }

    if (confirmCancel) {
        ConfirmSheet(
            title = stringResource(R.string.trip_cancel_title),
            body = stringResource(if (state.isDriver) R.string.trip_cancel_body_driver else R.string.trip_cancel_body),
            confirm = stringResource(R.string.trip_cancel_confirm),
            dismiss = stringResource(R.string.trip_cancel_keep),
            onConfirm = { confirmCancel = false; onCancel() },
            onDismiss = { confirmCancel = false },
        )
    }
    if (finishSheet && trip != null) {
        FinishSheet(
            trip = trip,
            onFinish = { cash -> finishSheet = false; onComplete(cash) },
            onDismiss = { finishSheet = false; slideReset++ },
        )
    }
}

@Composable
private fun PanelSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Skeleton(Modifier.width(200.dp).height(24.dp))
        Skeleton(Modifier.width(140.dp).height(14.dp))
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Skeleton(Modifier.size(52.dp), CircleShape)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.width(150.dp).height(16.dp))
                Skeleton(Modifier.width(100.dp).height(12.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Skeleton(Modifier.fillMaxWidth().height(56.dp), RoundedCornerShape(16.dp))
    }
}

// ─── Rider ────────────────────────────────────────────────────────────────

@Composable
private fun RiderPanel(trip: Trip, busy: Boolean, onCancel: () -> Unit, onDone: () -> Unit) {
    val c = TaxiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (trip.status) {
            TripStatus.SEARCHING -> {
                Headline(stringResource(R.string.trip_finding_title), stringResource(R.string.trip_finding_body), Modifier.enterStagger(0))
                SearchingBar(Modifier.enterStagger(1))
                TripSummary(trip, Modifier.enterStagger(2))
                SecondaryButton(stringResource(R.string.trip_cancel), onCancel, Modifier.enterStagger(3), contentColor = c.error)
            }
            TripStatus.ACCEPTED, TripStatus.ARRIVED, TripStatus.ONGOING -> {
                val (title, body) = riderCopy(trip)
                Headline(title, body, Modifier.enterStagger(0))
                StepBar(stepOf(trip.status), Modifier.enterStagger(1))
                DriverCard(trip, Modifier.enterStagger(2))
                if (trip.status == TripStatus.ONGOING) {
                    TripSummary(trip, Modifier.enterStagger(3))
                } else {
                    Text(
                        stringResource(R.string.trip_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .enterStagger(3)
                            .clip(RoundedCornerShape(12.dp))
                            .pressable(enabled = !busy, onClick = onCancel)
                            .padding(vertical = 10.dp),
                    )
                }
            }
            else -> Cancelled(onDone, R.string.trip_back_home)
        }
    }
}

@Composable
private fun riderCopy(trip: Trip): Pair<String, String> {
    val driver = firstName(trip.driverName).ifBlank { stringResource(R.string.trip_your_driver) }
    return when (trip.status) {
        TripStatus.ACCEPTED -> {
            val minutes = driverMinutesAway(trip)
            stringResource(R.string.trip_on_the_way, driver) to
                (minutes?.let { stringResource(R.string.trip_arriving_in, it) } ?: stringResource(R.string.trip_heading_to_you))
        }
        TripStatus.ARRIVED -> stringResource(R.string.trip_driver_here) to
            stringResource(R.string.trip_look_for, listOf(trip.driverVehicle, trip.driverPlate).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { driver })
        else -> stringResource(R.string.trip_heading_to, shortAddress(trip.dropoffAddress)) to
            stringResource(R.string.trip_arrive_around, arrivalTime(trip))
    }
}

private fun driverMinutesAway(trip: Trip): Int? {
    if (trip.driverLat == 0.0) return null
    val km = PricingEngine.haversineKm(trip.driverLat, trip.driverLng, trip.pickupLat, trip.pickupLng)
    return PricingEngine.estimateDurationMinutes(km)
}

private fun arrivalTime(trip: Trip): String {
    val start = if (trip.startedAt > 0) trip.startedAt else System.currentTimeMillis()
    return DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(start + trip.durationMinutes * 60_000L))
}

private fun stepOf(status: String) = when (status) {
    TripStatus.ACCEPTED -> 0
    TripStatus.ARRIVED -> 1
    else -> 2
}

@Composable
private fun Headline(title: String, body: String, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** An amber sweep that says "we're working on it" without a spinner. */
@Composable
private fun SearchingBar(modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val t by rememberInfiniteTransition(label = "searching").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "searching-t",
    )
    BoxWithConstraints(modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(c.line)) {
        val w = maxWidth
        Box(
            Modifier
                .width(w * 0.35f)
                .height(4.dp)
                .graphicsLayer { translationX = (-0.35f + 1.35f * t) * w.toPx() }
                .clip(CircleShape)
                .background(c.accent),
        )
    }
}

/** Three segments: on the way, arrived, in the car. */
@Composable
private fun StepBar(step: Int, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { i ->
            val fill by animateFloatAsState(if (i <= step) 1f else 0f, tween(600, delayMillis = 120 * i), label = "step-$i")
            BoxWithConstraints(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(c.line)) {
                Box(Modifier.width(maxWidth * fill).height(4.dp).clip(CircleShape).background(c.accent))
            }
        }
    }
}

@Composable
private fun DriverCard(trip: Trip, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceRaised)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Avatar(trip.driverName, trip.driverPhotoUrl, 52.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    trip.driverName.ifBlank { stringResource(R.string.trip_your_driver) },
                    style = MaterialTheme.typography.titleMedium,
                    color = c.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (trip.driverRating > 0) {
                    Icon(painterResource(R.drawable.ic_star_filled), null, tint = c.accent, modifier = Modifier.size(13.dp))
                    Text(String.format(java.util.Locale.US, "%.1f", trip.driverRating), style = MaterialTheme.typography.labelMedium, color = c.text)
                }
            }
            if (trip.driverVehicle.isNotBlank()) {
                Text(trip.driverVehicle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (trip.driverPlate.isNotBlank()) {
            Text(
                trip.driverPlate.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = c.onAccent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accent)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Pickup → dropoff with the fare and how it's paid. */
@Composable
private fun TripSummary(trip: Trip, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RouteDots()
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(shortAddress(trip.pickupAddress), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(shortAddress(trip.dropoffAddress), style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatPrice(trip.price), style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(tripPaymentLabel(trip.paymentMethod), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
        }
    }
}

@Composable
private fun RouteDots() {
    val c = TaxiTheme.colors
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
        Box(Modifier.width(2.dp).height(22.dp).background(c.line))
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
    }
}

@Composable
private fun tripPaymentLabel(id: String): String = when (id.lowercase()) {
    "cash" -> stringResource(R.string.payment_cash)
    "wallet" -> stringResource(R.string.payment_wallet)
    else -> if (id.length >= 4) stringResource(R.string.payment_card_ending, id.takeLast(4)) else stringResource(R.string.payment_card)
}

@Composable
private fun Cancelled(onDone: () -> Unit, button: Int) {
    val c = TaxiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.enterStagger(0), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(c.error.copy(alpha = 0.12f)), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_close), null, tint = c.error, modifier = Modifier.size(20.dp))
            }
            Headline(stringResource(R.string.trip_cancelled_title), stringResource(R.string.trip_cancelled_body))
        }
        PrimaryButton(stringResource(button), onDone, Modifier.enterStagger(1))
    }
}

// ─── Driver ───────────────────────────────────────────────────────────────

@Composable
private fun DriverPanel(
    trip: Trip,
    busy: Boolean,
    slideReset: Int,
    onArrived: () -> Unit,
    onStart: () -> Unit,
    onSlideFinished: () -> Unit,
    onNavigate: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
) {
    val c = TaxiTheme.colors
    val passenger = firstName(trip.passengerName).ifBlank { stringResource(R.string.trip_passenger) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        when (trip.status) {
            TripStatus.ACCEPTED, TripStatus.ARRIVED, TripStatus.ONGOING -> {
                val (title, body) = when (trip.status) {
                    TripStatus.ACCEPTED -> stringResource(R.string.drive_pick_up, passenger) to trip.pickupAddress
                    TripStatus.ARRIVED -> stringResource(R.string.drive_waiting_for, passenger) to stringResource(R.string.drive_waiting_body)
                    else -> stringResource(R.string.drive_drop_off, passenger) to trip.dropoffAddress
                }
                Row(Modifier.enterStagger(0), verticalAlignment = Alignment.Top) {
                    Headline(title, body, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(formatPrice(trip.price), style = MaterialTheme.typography.titleLarge, color = c.text)
                        Text(tripPaymentLabel(trip.paymentMethod), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                    }
                }
                StepBar(stepOf(trip.status), Modifier.enterStagger(1))
                when (trip.status) {
                    TripStatus.ACCEPTED -> Row(Modifier.enterStagger(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton(stringResource(R.string.drive_navigate), onNavigate, Modifier.weight(1f), icon = R.drawable.ic_navigation)
                        PrimaryButton(stringResource(R.string.drive_arrived), onArrived, Modifier.weight(1.3f), loading = busy)
                    }
                    TripStatus.ARRIVED -> PrimaryButton(stringResource(R.string.drive_start), onStart, Modifier.enterStagger(2), loading = busy)
                    else -> {
                        SecondaryButton(stringResource(R.string.drive_navigate), onNavigate, Modifier.enterStagger(2), icon = R.drawable.ic_navigation)
                        SlideToConfirm(stringResource(R.string.drive_slide_finish), resetKey = slideReset, busy = busy, onConfirmed = onSlideFinished, modifier = Modifier.enterStagger(3))
                    }
                }
                if (trip.status != TripStatus.ONGOING) {
                    Text(
                        stringResource(R.string.trip_cancel),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .pressable(enabled = !busy, onClick = onCancel)
                            .padding(vertical = 8.dp),
                    )
                }
            }
            TripStatus.COMPLETED -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SuccessBadge(64.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(stringResource(R.string.drive_complete_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.enterStagger(1))
                    Text(
                        stringResource(R.string.drive_earned, formatPrice(trip.price)),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.accentText,
                        modifier = Modifier.enterStagger(2),
                    )
                    Text(
                        if (trip.paymentMethod.equals("cash", ignoreCase = true)) stringResource(R.string.drive_paid_cash) else stringResource(R.string.drive_paid_app),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        modifier = Modifier.enterStagger(3),
                    )
                }
                PrimaryButton(stringResource(R.string.drive_back_to_requests), onDone, Modifier.enterStagger(4))
            }
            else -> Cancelled(onDone, R.string.drive_back_to_requests)
        }
    }
}

/** Drag the amber thumb all the way across to confirm — hard to do by accident. */
@Composable
private fun SlideToConfirm(label: String, resetKey: Int, busy: Boolean, onConfirmed: () -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var maxOffset by remember { mutableStateOf(0f) }
    LaunchedEffect(resetKey) { offset.animateTo(0f, spring(dampingRatio = 0.7f, stiffness = 400f)) }
    val shimmer by rememberInfiniteTransition(label = "slide-hint").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "slide-hint-t",
    )
    val progress = if (maxOffset > 0f) offset.value / maxOffset else 0f

    Box(
        modifier
            .fillMaxWidth()
            .height(60.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(c.surfaceRaised)
            .onSizeChanged { maxOffset = it.width - with(view.resources.displayMetrics) { (60 * density) } },
        contentAlignment = Alignment.CenterStart,
    ) {
        // Fill that follows the thumb
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = ((offset.value + size.height) / size.width).coerceIn(0f, 1f)
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                }
                .background(c.accentTint),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 48.dp)
                .graphicsLayer { alpha = (1f - progress * 1.6f).coerceIn(0f, 1f) * (0.55f + 0.45f * kotlin.math.abs(kotlin.math.sin(shimmer * Math.PI)).toFloat()) },
        )
        Box(
            Modifier
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .padding(4.dp)
                .size(52.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(c.accent)
                .pointerInput(busy) {
                    if (busy) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offset.value > maxOffset * 0.85f) {
                                    offset.animateTo(maxOffset, tween(120))
                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                    onConfirmed()
                                } else {
                                    offset.animateTo(0f, spring(dampingRatio = 0.6f, stiffness = 500f))
                                }
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                    ) { change, delta ->
                        change.consume()
                        scope.launch { offset.snapTo((offset.value + delta).coerceIn(0f, maxOffset.coerceAtLeast(0f))) }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_arrow_right), null, tint = c.onAccent, modifier = Modifier.size(22.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishSheet(trip: Trip, onFinish: (cash: Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = TaxiTheme.colors
    val cash = trip.paymentMethod.equals("cash", ignoreCase = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.line)) },
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(if (cash) R.string.drive_collect_title else R.string.drive_finish_title), style = MaterialTheme.typography.titleLarge, color = c.text)
            if (cash) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.accentTint).padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.drive_collect_label), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                    Text(formatPrice(trip.price), style = MaterialTheme.typography.displayLarge, color = c.text)
                }
            }
            FareBreakdown(trip)
            PrimaryButton(stringResource(if (cash) R.string.drive_cash_received else R.string.drive_finish), { onFinish(cash) })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(title: String, body: String, confirm: String, dismiss: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val c = TaxiTheme.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.line)) },
    ) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 20.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = c.text)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
            Spacer(Modifier.height(8.dp))
            PrimaryButton(dismiss, onDismiss)
            SecondaryButton(confirm, onConfirm, contentColor = c.error)
        }
    }
}

// ─── Receipt (rider, after the trip) ─────────────────────────────────────

@Composable
private fun Receipt(trip: Trip, onDownload: () -> Unit, onDone: () -> Unit) {
    val c = TaxiTheme.colors
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(40.dp))
            SuccessBadge(84.dp)
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.receipt_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.enterStagger(1))
            Spacer(Modifier.height(4.dp))
            Text(
                if (trip.completedAt > 0) DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(trip.completedAt)) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                modifier = Modifier.enterStagger(2),
            )
            Spacer(Modifier.height(24.dp))
            Text(formatPrice(trip.price), style = MaterialTheme.typography.displayLarge, color = c.text, modifier = Modifier.enterStagger(3))
            Text(tripPaymentLabel(trip.paymentMethod), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.enterStagger(3))
            Spacer(Modifier.height(28.dp))

            Card(Modifier.enterStagger(4)) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    RouteDots()
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        StopLine(trip.pickupAddress, trip.startedAt)
                        StopLine(trip.dropoffAddress, trip.completedAt)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            if (trip.driverName.isNotBlank()) {
                DriverCard(trip, Modifier.enterStagger(5))
                Spacer(Modifier.height(12.dp))
            }
            Card(Modifier.enterStagger(6)) { FareBreakdown(trip) }
            Spacer(Modifier.height(24.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(stringResource(R.string.receipt_download), onDownload, icon = R.drawable.ic_download)
            PrimaryButton(stringResource(R.string.receipt_done), onDone)
        }
    }
}

@Composable
private fun StopLine(address: String, time: Long) {
    val c = TaxiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(shortAddress(address), style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (time > 0) Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(time)), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = TaxiTheme.colors
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .padding(18.dp),
    ) { content() }
}

@Composable
private fun FareBreakdown(trip: Trip) {
    val c = TaxiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        FareLine(stringResource(R.string.fare_base), trip.baseFare)
        FareLine(stringResource(R.string.fare_distance, com.taxiapp.util.formatKm(trip.distanceMiles)), trip.distanceFare)
        FareLine(stringResource(R.string.fare_time, trip.durationMinutes), trip.timeFare)
        if (trip.tolls > 0) FareLine(stringResource(R.string.fare_tolls), trip.tolls)
        val subtotal = trip.baseFare + trip.distanceFare + trip.timeFare + trip.tolls
        if (trip.price - subtotal > 0.009) FareLine(stringResource(R.string.fare_minimum), trip.price - subtotal)
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.fare_total), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
            Text(formatPrice(trip.price), style = MaterialTheme.typography.titleMedium, color = c.text)
        }
    }
}

@Composable
private fun FareLine(label: String, amount: Double) {
    val c = TaxiTheme.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = c.textMuted, modifier = Modifier.weight(1f))
        Text(formatPrice(amount), style = MaterialTheme.typography.bodyMedium, color = c.text)
    }
}

/** Amber disc with a check that pops in, and a ring that ripples out once. */
@Composable
private fun SuccessBadge(size: androidx.compose.ui.unit.Dp) {
    val c = TaxiTheme.colors
    val pop = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 260f)) }
        launch { ring.animateTo(1f, tween(900, delayMillis = 150)) }
    }
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(size)
                .graphicsLayer {
                    val s = 1f + 0.6f * ring.value
                    scaleX = s; scaleY = s
                    alpha = 0.5f * (1f - ring.value)
                }
                .clip(CircleShape)
                .background(c.accent),
        )
        Box(
            Modifier
                .size(size)
                .graphicsLayer { scaleX = pop.value; scaleY = pop.value }
                .clip(CircleShape)
                .background(c.accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_check),
                null,
                tint = c.onAccent,
                modifier = Modifier.size(size * 0.46f).graphicsLayer { rotationZ = -20f * (1f - pop.value) },
            )
        }
    }
}

@Composable
private fun NotFound(onBack: () -> Unit) {
    val c = TaxiTheme.colors
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_receipt), null, tint = c.textMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.trip_not_found_title), style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(stringResource(R.string.trip_not_found_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        PrimaryButton(stringResource(R.string.trip_back_home), onBack)
    }
}
