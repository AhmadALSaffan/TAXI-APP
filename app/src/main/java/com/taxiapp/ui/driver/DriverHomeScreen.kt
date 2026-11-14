package com.taxiapp.ui.driver

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.graphics.drawscope.Stroke
import com.taxiapp.ui.components.Skeleton
import com.taxiapp.ui.components.enterStagger
import com.taxiapp.ui.components.pressable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.rememberCameraPositionState
import com.taxiapp.R
import com.taxiapp.ui.components.DefaultCamera
import com.taxiapp.data.model.Trip
import com.taxiapp.ui.components.Avatar
import com.taxiapp.ui.components.CarMarker
import com.taxiapp.ui.components.PickupMarker
import com.taxiapp.ui.components.PrimaryButton
import com.taxiapp.ui.components.SearchingRing
import com.taxiapp.ui.components.TaxiMap
import com.taxiapp.ui.home.formatPrice
import com.taxiapp.ui.theme.TaxiTheme
import kotlinx.coroutines.launch
import java.util.Locale


@Composable
fun DriverHomeScreen(
    state: DriverUiState,
    onSetOnline: (Boolean) -> Unit,
    onAccept: (Trip) -> Unit,
    onResumeTrip: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRequestLocation: () -> Unit,
    onSignOut: () -> Unit,
    onMessageShown: () -> Unit,
) {
    val c = TaxiTheme.colors
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var panelHeight by remember { mutableStateOf(0.dp) }
    var topBarHeight by remember { mutableStateOf(0.dp) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.location ?: DefaultCamera, if (state.location != null) 14.5f else 11f)
    }
    var centeredOnce by remember { mutableStateOf(state.location != null) }
    LaunchedEffect(state.location) {
        val me = state.location ?: return@LaunchedEffect
        if (!centeredOnce) {
            centeredOnce = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(me, 14.5f), 900)
        }
    }

    // When requests come in, frame the driver and every pickup so none hide under the panels.
    val requestIds = state.requests.map { it.trip.tripId }
    LaunchedEffect(requestIds, panelHeight, topBarHeight) {
        val me = state.location ?: return@LaunchedEffect
        if (state.requests.isEmpty()) return@LaunchedEffect
        val bounds = LatLngBounds.builder().include(me).apply {
            state.requests.forEach { include(LatLng(it.trip.pickupLat, it.trip.pickupLng)) }
        }.build()
        val padding = with(density) { 96.dp.roundToPx() }
        runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, padding), 800) }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        TaxiMap(camera, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topBarHeight, bottom = panelHeight)) {
            state.location?.let { me ->
                if (state.isOnline && state.requests.isEmpty()) SearchingRing(me)
                CarMarker(me)
            }
            state.requests.forEach { request ->
                PickupMarker(LatLng(request.trip.pickupLat, request.trip.pickupLng), formatPrice(request.trip.price))
            }
        }

        TopBar(state, onSignOut, Modifier.onSizeChanged { topBarHeight = with(density) { it.height.toDp() } })

        // Recenter
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = panelHeight + 16.dp)
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .background(c.surface)
                .clickable(role = Role.Button, onClickLabel = stringResource(R.string.home_recenter)) {
                    onRefreshLocation()
                    state.location?.let { me -> scope.launch { camera.animate(CameraUpdateFactory.newLatLngZoom(me, 15f), 700) } }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_my_location), stringResource(R.string.home_recenter), tint = c.accentText, modifier = Modifier.size(20.dp))
        }

        // Bottom panel
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { panelHeight = with(density) { it.height.toDp() } }
                .shadow(24.dp, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .background(c.surface)
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 10.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(38.dp, 4.dp).clip(CircleShape).background(c.line))

            state.summary.activeTrip?.let { ActiveTripBanner(it, onResumeTrip) }

            if (state.locationDenied) {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surfaceRaised).padding(start = 14.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.driver_location_needed), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
                    TextButton(onClick = onRequestLocation) {
                        Text(stringResource(R.string.action_allow), style = MaterialTheme.typography.labelMedium, color = c.accentText)
                    }
                }
            }

            AnimatedContent(state.isOnline, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "online") { online ->
                if (online) OnlinePanel(state, onSetOnline, onAccept) else OfflinePanel(state.location != null, onSetOnline)
            }
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 72.dp, start = 16.dp, end = 16.dp))
    }
}

@Composable
private fun TopBar(state: DriverUiState, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Driver identity (tap for menu)
        Box(Modifier.weight(1f)) {
            Row(
                Modifier
                    .shadow(10.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .background(c.surface)
                    .clickable(role = Role.Button) { menuOpen = true }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(state.user?.displayName.orEmpty(), state.user?.photoUrl.orEmpty(), 40.dp)
                Column {
                    Text(
                        state.user?.displayName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.driver_you),
                        style = MaterialTheme.typography.labelMedium,
                        color = c.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(painterResource(R.drawable.ic_star_filled), null, tint = c.accent, modifier = Modifier.size(12.dp))
                        Text(
                            String.format(Locale.getDefault(), "%.2f", state.user?.rating ?: 5.0),
                            style = MaterialTheme.typography.bodySmall,
                            color = c.textMuted,
                        )
                    }
                }
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }, containerColor = c.surface) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.account_sign_out), color = c.error, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        menuOpen = false
                        onSignOut()
                    },
                )
            }
        }

        // Today's earnings
        Column(
            Modifier
                .shadow(10.dp, RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(c.surface)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(stringResource(R.string.driver_today), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
            if (!state.summaryLoaded) {
                Skeleton(Modifier.padding(vertical = 4.dp).width(78.dp).height(20.dp))
                Skeleton(Modifier.width(44.dp).height(11.dp))
            } else {
                Text(formatPrice(state.summary.todayEarnings), style = MaterialTheme.typography.titleLarge, color = c.text)
                Text(
                    pluralStringResource(R.plurals.driver_trips_count, state.summary.todayTrips, state.summary.todayTrips),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                )
            }
        }
    }
}

@Composable
private fun OfflinePanel(hasLocation: Boolean, onSetOnline: (Boolean) -> Unit) {
    val c = TaxiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        StatusRow(online = false, onToggle = null)
        Column(Modifier.padding(horizontal = 2.dp)) {
            Text(stringResource(R.string.driver_offline_title), style = MaterialTheme.typography.headlineMedium, color = c.text)
            Text(stringResource(R.string.driver_offline_body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
        }
        PrimaryButton(
            text = stringResource(R.string.driver_go_online),
            onClick = { onSetOnline(true) },
            enabled = hasLocation,
        )
    }
}

@Composable
private fun OnlinePanel(state: DriverUiState, onSetOnline: (Boolean) -> Unit, onAccept: (Trip) -> Unit) {
    val c = TaxiTheme.colors
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusRow(online = true, onToggle = { onSetOnline(false) })
        if (state.requests.isEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceRaised)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Radar(Modifier.size(40.dp))
                Column {
                    Text(stringResource(R.string.driver_waiting_title), style = MaterialTheme.typography.titleMedium, color = c.text)
                    Text(stringResource(R.string.driver_waiting_body), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
        } else {
            Text(
                pluralStringResource(R.plurals.driver_requests_nearby, state.requests.size, state.requests.size),
                style = MaterialTheme.typography.titleMedium,
                color = c.text,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            LazyColumn(Modifier.heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(state.requests, key = { _, r -> r.trip.tripId }) { i, request ->
                    RequestCard(
                        request,
                        accepting = state.acceptingTripId == request.trip.tripId,
                        enabled = state.acceptingTripId == null,
                        onAccept = onAccept,
                        modifier = Modifier.animateItem().enterStagger(i.coerceAtMost(4), key = request.trip.tripId),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusRow(online: Boolean, onToggle: (() -> Unit)?) {
    val c = TaxiTheme.colors
    val pulse by rememberInfiniteTransition(label = "status").animateFloat(
        1f, 0.35f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "status-dot",
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(10.dp)
                .alpha(if (online) pulse else 1f)
                .clip(CircleShape)
                .background(if (online) c.accent else c.textMuted),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            stringResource(if (online) R.string.driver_status_online else R.string.driver_status_offline),
            style = MaterialTheme.typography.labelMedium,
            color = if (online) c.text else c.textMuted,
            modifier = Modifier.weight(1f),
        )
        if (onToggle != null) {
            TextButton(onClick = onToggle) {
                Text(stringResource(R.string.driver_go_offline), style = MaterialTheme.typography.labelMedium, color = c.textMuted)
            }
        }
    }
}

@Composable
private fun RequestCard(request: TripRequest, accepting: Boolean, enabled: Boolean, onAccept: (Trip) -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val trip = request.trip
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surfaceRaised)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                trip.rideType.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
                color = c.accentText,
                modifier = Modifier.clip(CircleShape).background(c.accentTint).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            Text(
                stringResource(R.string.driver_pickup_away, String.format(Locale.getDefault(), "%.1f", request.pickupKm)),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            Text(formatPrice(trip.price), style = MaterialTheme.typography.headlineMedium, color = c.text)
        }

        // Pickup → drop-off
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.padding(top = 5.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                Box(Modifier.width(2.dp).height(22.dp).background(c.line))
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(trip.pickupAddress, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trip.dropoffAddress, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(
                    R.string.driver_trip_length,
                    com.taxiapp.util.formatKm(trip.distanceMiles),
                    trip.durationMinutes,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                modifier = Modifier.weight(1f),
            )
            PrimaryButton(
                text = stringResource(R.string.driver_accept),
                onClick = { onAccept(trip) },
                enabled = enabled,
                loading = accepting,
                modifier = Modifier.width(140.dp),
            )
        }
    }
}

@Composable
private fun ActiveTripBanner(trip: Trip, onResumeTrip: () -> Unit) {
    val c = TaxiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.accentTint)
            .pressable(onClick = onResumeTrip)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_navigation), null, tint = c.onAccent, modifier = Modifier.size(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.driver_trip_in_progress), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(
                stringResource(R.string.driver_pickup_at, trip.pickupAddress),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(stringResource(R.string.action_continue), style = MaterialTheme.typography.labelMedium, color = c.accentText)
    }
}

/** Expanding amber rings — the driver is visibly "listening" for requests. */
@Composable
private fun Radar(modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val t by rememberInfiniteTransition(label = "radar").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "radar-t",
    )
    Canvas(modifier) {
        val maxR = size.minDimension / 2f
        for (i in 0 until 3) {
            val p = (t + i / 3f) % 1f
            drawCircle(c.accent.copy(alpha = 0.5f * (1f - p)), radius = maxR * p, style = Stroke(width = 2.dp.toPx()))
        }
        drawCircle(c.accent, radius = 5.dp.toPx())
    }
}
