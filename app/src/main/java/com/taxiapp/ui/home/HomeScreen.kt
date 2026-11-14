package com.taxiapp.ui.home

import android.view.HapticFeedbackConstants
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.rememberCameraPositionState
import com.taxiapp.R
import com.taxiapp.ui.components.DefaultCamera
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.ui.components.Avatar
import com.taxiapp.ui.components.PickupPin
import com.taxiapp.ui.components.Skeleton
import com.taxiapp.ui.components.TaxiMap
import com.taxiapp.ui.components.UserLocationMarker
import com.taxiapp.ui.components.enterStagger
import com.taxiapp.ui.components.firstName
import com.taxiapp.ui.components.pressable
import com.taxiapp.ui.theme.TaxiTheme
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Shown until the first location fix arrives.

@Composable
fun HomeScreen(
    state: HomeUiState,
    onTab: (HomeTab) -> Unit,
    onSearch: (prefill: String?) -> Unit,
    onRecentPlace: (RecentDestination) -> Unit,
    onPickupMoved: (LatLng) -> Unit,
    onPickupDragStarted: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenTrip: (Trip) -> Unit,
    onSignOut: () -> Unit,
) {
    val c = TaxiTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(c.background),
    ) {
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = state.tab,
                transitionSpec = {
                    (fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.985f))
                        .togetherWith(fadeOut(tween(140)))
                },
                label = "home-tab",
            ) { tab ->
                when (tab) {
                    HomeTab.Home -> MapHome(
                        state, onTab, onSearch, onRecentPlace, onPickupMoved, onPickupDragStarted,
                        onRefreshLocation, onRequestLocation, onOpenTrip,
                    )
                    HomeTab.Rides -> RidesTab(state, onOpenTrip)
                    HomeTab.Account -> AccountTab(state, onSignOut)
                }
            }
        }
        BottomNav(state.tab, onTab)
    }
}

// ─── Home: map with a movable pickup pin + route card ─────────────────────

@Composable
private fun MapHome(
    state: HomeUiState,
    onTab: (HomeTab) -> Unit,
    onSearch: (String?) -> Unit,
    onRecentPlace: (RecentDestination) -> Unit,
    onPickupMoved: (LatLng) -> Unit,
    onPickupDragStarted: () -> Unit,
    onRefreshLocation: () -> Unit,
    onRequestLocation: () -> Unit,
    onOpenTrip: (Trip) -> Unit,
) {
    val c = TaxiTheme.colors
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    var panelHeight by remember { mutableStateOf(0.dp) }
    var topBarHeight by remember { mutableStateOf(0.dp) }

    val camera = rememberCameraPositionState {
        val start = state.pickup ?: state.myLocation
        position = CameraPosition.fromLatLngZoom(start ?: DefaultCamera, if (start != null) 16.5f else 11f)
    }

    // Fly to the rider the first time we learn where they are.
    var centeredOnce by remember { mutableStateOf(state.myLocation != null) }
    LaunchedEffect(state.myLocation) {
        val me = state.myLocation ?: return@LaunchedEffect
        if (!centeredOnce) {
            centeredOnce = true
            camera.animate(CameraUpdateFactory.newLatLngZoom(me, 16.5f), 1000)
        }
    }

    // The rider is dragging the map: lift the pin and slide the panel out of the way.
    val dragging = camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging) {
            wasDragging = true
            onPickupDragStarted()
        }
    }
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && wasDragging) {
            wasDragging = false
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onPickupMoved(camera.position.target)
        }
    }
    val panelSlide by animateFloatAsState(
        if (dragging) 1f else 0f,
        spring(dampingRatio = 0.9f, stiffness = if (dragging) 500f else 320f),
        label = "panel-slide",
    )

    Box(Modifier.fillMaxSize()) {
        TaxiMap(
            cameraPositionState = camera,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = topBarHeight, bottom = panelHeight),
        ) {
            state.myLocation?.let { UserLocationMarker(it) }
        }

        // Pickup pin, centred in the visible part of the map.
        Box(
            Modifier
                .fillMaxSize()
                .padding(top = topBarHeight, bottom = panelHeight),
            contentAlignment = Alignment.Center,
        ) {
            if (state.myLocation != null || state.pickup != null) {
                PickupPin(lifted = dragging, resolving = state.pickupResolving, modifier = Modifier.offset(y = (-26).dp))
            }
        }

        // Top bar: account only — the map stays clear.
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topBarHeight = with(density) { it.height.toDp() } }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(c.surface)
                    .pressable(onClickLabel = stringResource(R.string.tab_account)) { onTab(HomeTab.Account) },
                contentAlignment = Alignment.Center,
            ) {
                Avatar(state.user?.displayName.orEmpty(), state.user?.photoUrl.orEmpty(), 40.dp)
            }
        }

        // Recenter
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = panelHeight + 16.dp)
                .graphicsLayer { alpha = 1f - panelSlide }
                .size(48.dp)
                .shadow(12.dp, CircleShape)
                .clip(CircleShape)
                .background(c.surface)
                .pressable(onClickLabel = stringResource(R.string.home_recenter)) {
                    onRefreshLocation()
                    state.myLocation?.let { me ->
                        scope.launch {
                            camera.animate(CameraUpdateFactory.newLatLngZoom(me, 16.5f), 700)
                            onPickupMoved(me)
                        }
                    }
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
                .graphicsLayer {
                    // Slide most of the panel away while dragging; keep the route card's top in view.
                    translationY = panelSlide * (size.height - 150.dp.toPx()).coerceAtLeast(0f)
                }
                .shadow(28.dp, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
                .background(c.surface)
                .padding(horizontal = 20.dp)
                .padding(top = 10.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(Modifier.align(Alignment.CenterHorizontally).size(36.dp, 4.dp).clip(CircleShape).background(c.line))

            AnimatedVisibility(state.activeTrip != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                state.activeTrip?.let { ActiveTripCard(it, onOpenTrip) }
            }
            if (state.locationDenied) LocationOffRow(onRequestLocation)

            Text(
                greeting(state.user?.displayName.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = c.textMuted,
                modifier = Modifier.enterStagger(0).padding(horizontal = 4.dp),
            )

            RouteCard(state, onSearch, Modifier.enterStagger(1))

            PlacesList(state, onSearch, onRecentPlace)
        }
    }
}

/** Pickup (from the pin) above the destination search — the one thing this screen is for. */
@Composable
private fun RouteCard(state: HomeUiState, onSearch: (String?) -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(c.surfaceRaised),
    ) {
        // Pickup
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(c.accent))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_pickup), style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                AnimatedContent(
                    targetState = if (state.pickupResolving || state.pickupAddress == null) null else state.pickupAddress,
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "pickup-address",
                ) { address ->
                    if (address == null) {
                        Skeleton(Modifier.padding(top = 4.dp).width(170.dp).height(16.dp))
                    } else {
                        Text(address, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        Box(Modifier.padding(start = 44.dp, end = 18.dp).fillMaxWidth().height(1.dp).background(c.line))
        // Destination
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(pressedScale = 0.985f) { onSearch(null) }
                .padding(start = 18.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(c.text))
            Text(stringResource(R.string.home_where_to_short), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.weight(1f))
            Box(Modifier.size(44.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_arrow_right), stringResource(R.string.home_search), tint = c.onAccent, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun PlacesList(state: HomeUiState, onSearch: (String?) -> Unit, onRecentPlace: (RecentDestination) -> Unit) {
    val c = TaxiTheme.colors
    val user = state.user
    val places = buildList {
        if (!user?.homeAddress.isNullOrBlank()) add(PlaceRow(R.drawable.ic_home, R.string.home_place_home, user!!.homeAddress) { onSearch(user.homeAddress) })
        if (!user?.workAddress.isNullOrBlank()) add(PlaceRow(R.drawable.ic_briefcase, R.string.home_place_work, user!!.workAddress) { onSearch(user.workAddress) })
        state.recentPlaces.forEach { place -> add(PlaceRow(R.drawable.ic_history, null, place.address) { onRecentPlace(place) }) }
    }.take(3)

    when {
        state.ridesLoading && places.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(vertical = 6.dp)) {
            repeat(2) { i ->
                Row(Modifier.enterStagger(2 + i), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Skeleton(Modifier.size(36.dp), CircleShape)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Skeleton(Modifier.width(150.dp).height(14.dp))
                        Skeleton(Modifier.width(90.dp).height(11.dp))
                    }
                }
            }
        }
        places.isEmpty() -> Text(
            stringResource(R.string.home_places_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
            modifier = Modifier.enterStagger(2).padding(horizontal = 4.dp),
        )
        else -> Column {
            places.forEachIndexed { i, place -> PlaceItem(place, Modifier.enterStagger(2 + i, key = place.address)) }
        }
    }
}

private data class PlaceRow(@DrawableRes val icon: Int, val title: Int?, val address: String, val onClick: () -> Unit)

@Composable
private fun PlaceItem(place: PlaceRow, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClick = place.onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(painterResource(place.icon), null, tint = c.textMuted, modifier = Modifier.size(17.dp))
        }
        Column(Modifier.weight(1f)) {
            val title = place.title?.let { stringResource(it) } ?: place.address
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (place.title != null) place.address else stringResource(R.string.home_place_recent),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActiveTripCard(trip: Trip, onOpenTrip: (Trip) -> Unit) {
    val c = TaxiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(c.accentTint)
            .pressable { onOpenTrip(trip) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(c.accent), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_car_filled), null, tint = c.onAccent, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(stringResource(statusLabel(trip.status)), style = MaterialTheme.typography.titleMedium, color = c.text)
            Text(
                stringResource(R.string.home_active_trip_to, trip.dropoffAddress),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(stringResource(R.string.action_open), style = MaterialTheme.typography.labelMedium, color = c.accentText)
    }
}

@Composable
private fun LocationOffRow(onRequestLocation: () -> Unit) {
    val c = TaxiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(c.surfaceRaised)
            .padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.home_location_off), style = MaterialTheme.typography.bodyMedium, color = c.text, modifier = Modifier.weight(1f))
        TextButton(onClick = onRequestLocation) {
            Text(stringResource(R.string.action_allow), style = MaterialTheme.typography.labelMedium, color = c.accentText)
        }
    }
}

@Composable
private fun greeting(name: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val part = stringResource(
        when {
            hour < 12 -> R.string.greeting_morning
            hour < 18 -> R.string.greeting_afternoon
            else -> R.string.greeting_evening
        },
    )
    val first = firstName(name)
    return if (first.isBlank()) part else "$part, $first"
}

// ─── Rides ────────────────────────────────────────────────────────────────

@Composable
private fun RidesTab(state: HomeUiState, onOpenTrip: (Trip) -> Unit) {
    val c = TaxiTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.rides_title), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.enterStagger(0))
        Spacer(Modifier.height(18.dp))
        when {
            state.ridesLoading && state.rides.isEmpty() -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { i -> RideSkeleton(Modifier.enterStagger(1 + i)) }
            }
            state.rides.isEmpty() -> EmptyState(R.drawable.ic_history, R.string.rides_empty_title, R.string.rides_empty_body)
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(state.rides, key = { _, trip -> trip.tripId }) { i, trip ->
                    RideItem(trip, onOpenTrip, Modifier.animateItem().enterStagger(1 + i.coerceAtMost(6)))
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun RideSkeleton(modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Skeleton(Modifier.width(110.dp).height(12.dp))
            Spacer(Modifier.weight(1f))
            Skeleton(Modifier.width(80.dp).height(22.dp), CircleShape)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Skeleton(Modifier.width(190.dp).height(16.dp))
                Skeleton(Modifier.width(120.dp).height(12.dp))
            }
            Skeleton(Modifier.width(64.dp).height(22.dp))
        }
    }
}

@Composable
private fun RideItem(trip: Trip, onOpenTrip: (Trip) -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    // Live trips reopen tracking; finished ones open their receipt.
    val opens = trip.status != TripStatus.CANCELLED
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(c.surface)
            .then(if (opens) Modifier.pressable { onOpenTrip(trip) } else Modifier)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatDate(trip.createdAt), style = MaterialTheme.typography.bodySmall, color = c.textMuted, modifier = Modifier.weight(1f))
            StatusChip(trip.status)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(trip.dropoffAddress, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.rides_from, trip.pickupAddress),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(formatPrice(trip.price), style = MaterialTheme.typography.titleLarge, color = c.text)
        }
    }
}

@Composable
private fun StatusChip(status: String) {
    val c = TaxiTheme.colors
    val (bg, fg) = when (status) {
        TripStatus.COMPLETED -> c.surfaceRaised to c.textMuted
        TripStatus.CANCELLED -> c.error.copy(alpha = 0.12f) to c.error
        else -> c.accentTint to c.accentText
    }
    Text(
        stringResource(statusLabel(status)),
        style = MaterialTheme.typography.bodySmall,
        color = fg,
        modifier = Modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

private fun statusLabel(status: String) = when (status) {
    TripStatus.SEARCHING -> R.string.status_searching
    TripStatus.ACCEPTED -> R.string.status_accepted
    TripStatus.ARRIVED -> R.string.status_arrived
    TripStatus.ONGOING -> R.string.status_ongoing
    TripStatus.CANCELLED -> R.string.status_cancelled
    else -> R.string.status_completed
}

// ─── Account ──────────────────────────────────────────────────────────────

@Composable
private fun AccountTab(state: HomeUiState, onSignOut: () -> Unit) {
    val c = TaxiTheme.colors
    val user = state.user
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.tab_account), style = MaterialTheme.typography.headlineMedium, color = c.text, modifier = Modifier.enterStagger(0))
        Spacer(Modifier.height(20.dp))

        Row(
            Modifier
                .enterStagger(1)
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(c.surface)
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (user == null) {
                Skeleton(Modifier.size(64.dp), CircleShape)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Skeleton(Modifier.width(150.dp).height(18.dp))
                    Skeleton(Modifier.width(110.dp).height(13.dp))
                }
            } else {
                Avatar(user.displayName, user.photoUrl, 64.dp)
                Column(Modifier.weight(1f)) {
                    Text(user.displayName.takeIf { it.isNotBlank() } ?: stringResource(R.string.account_no_name), style = MaterialTheme.typography.titleLarge, color = c.text)
                    val contact = listOf(user.phone, user.email).firstOrNull { it.isNotBlank() }
                    if (contact != null) Text(contact, style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(Modifier.enterStagger(2), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile(user?.let { String.format(Locale.getDefault(), "%.2f", it.rating) }, stringResource(R.string.account_rating), Modifier.weight(1f))
            StatTile(if (state.ridesLoading) null else state.rides.count { it.status == TripStatus.COMPLETED }.toString(), stringResource(R.string.account_trips), Modifier.weight(1f))
            StatTile(
                user?.let { u -> u.memberSince.takeIf { it > 0 }?.let { SimpleDateFormat("MMM yyyy", Locale.getDefault()).format(Date(it)) } ?: "—" },
                stringResource(R.string.account_member_since),
                Modifier.weight(1f),
            )
        }

        Spacer(Modifier.weight(1f))
        OutlinedButton(
            onClick = onSignOut,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, c.line),
            modifier = Modifier.enterStagger(3).fillMaxWidth().height(56.dp),
        ) {
            Text(stringResource(R.string.account_sign_out), style = MaterialTheme.typography.labelLarge, color = c.error)
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun StatTile(value: String?, label: String, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    Column(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(c.surface)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (value == null) Skeleton(Modifier.width(52.dp).height(20.dp))
        else Text(value, style = MaterialTheme.typography.titleLarge, color = c.text, maxLines = 1)
        Text(label, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1)
    }
}

// ─── Shared bits ──────────────────────────────────────────────────────────

@Composable
fun EmptyState(@DrawableRes icon: Int, title: Int, body: Int) {
    val c = TaxiTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(top = 56.dp).enterStagger(1),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, tint = c.textMuted, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted)
    }
}

@Composable
private fun BottomNav(selected: HomeTab, onTab: (HomeTab) -> Unit) {
    val c = TaxiTheme.colors
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .navigationBarsPadding()
            .height(64.dp)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavItem(HomeTab.Home, R.drawable.ic_nav_home, R.string.tab_home, selected, onTab)
        NavItem(HomeTab.Rides, R.drawable.ic_nav_history, R.string.tab_rides, selected, onTab)
        NavItem(HomeTab.Account, R.drawable.ic_nav_profile, R.string.tab_account, selected, onTab)
    }
}

@Composable
private fun NavItem(tab: HomeTab, @DrawableRes icon: Int, label: Int, selected: HomeTab, onTab: (HomeTab) -> Unit) {
    val c = TaxiTheme.colors
    val isSelected = tab == selected
    val active by animateFloatAsState(if (isSelected) 1f else 0f, spring(dampingRatio = 0.7f, stiffness = 500f), label = "nav")
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .pressable(role = Role.Tab, pressedScale = 0.92f) { onTab(tab) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painterResource(icon),
            contentDescription = null,
            tint = if (isSelected) c.accentText else c.textMuted,
            modifier = Modifier
                .size(23.dp)
                .graphicsLayer { scaleX = 1f + 0.08f * active; scaleY = 1f + 0.08f * active },
        )
        Text(stringResource(label), style = MaterialTheme.typography.bodySmall, color = if (isSelected) c.text else c.textMuted)
        // Small amber bar under the active tab.
        Box(
            Modifier
                .graphicsLayer { scaleX = active; alpha = active }
                .size(width = 16.dp, height = 3.dp)
                .clip(CircleShape)
                .background(c.accent),
        )
    }
}

private fun formatDate(millis: Long): String =
    if (millis <= 0) "" else SimpleDateFormat("MMM d · h:mm a", Locale.getDefault()).format(Date(millis))

fun formatPrice(value: Double): String = com.taxiapp.util.formatMoney(value)
