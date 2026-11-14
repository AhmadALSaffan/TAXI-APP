package com.taxiapp.ui.ride

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.rememberCameraPositionState
import com.taxiapp.R
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.data.model.RideOption
import com.taxiapp.ui.components.BackButton
import com.taxiapp.ui.components.PrimaryButton
import com.taxiapp.ui.components.RouteEndMarker
import com.taxiapp.ui.components.RouteLine
import com.taxiapp.ui.components.RouteStartMarker
import com.taxiapp.ui.components.Skeleton
import com.taxiapp.ui.components.TaxiMap
import com.taxiapp.ui.components.enterStagger
import com.taxiapp.ui.components.pressable
import com.taxiapp.ui.home.formatPrice
import com.taxiapp.ui.theme.TaxiTheme
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RideSelectionScreen(
    state: RideSelectionUiState,
    onBack: () -> Unit,
    onSelect: (String) -> Unit,
    onSelectPayment: (PaymentMethod) -> Unit,
    onConfirm: () -> Unit,
    onErrorShown: () -> Unit,
) {
    val c = TaxiTheme.colors
    val density = LocalDensity.current
    var panelHeight by remember { mutableStateOf(0.dp) }
    var topHeight by remember { mutableStateOf(0.dp) }
    var showPayments by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbar.showSnackbar(it); onErrorShown() }
    }

    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.pickup, 13f)
    }
    // Frame the whole trip in the space between the header and the panel.
    LaunchedEffect(state.route, panelHeight, topHeight) {
        if (panelHeight == 0.dp) return@LaunchedEffect
        val bounds = LatLngBounds.builder().include(state.pickup).include(state.dropoff)
            .apply { state.route.forEach { include(it) } }.build()
        val pad = with(density) { 56.dp.roundToPx() }
        runCatching { camera.animate(CameraUpdateFactory.newLatLngBounds(bounds, pad), 900) }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        TaxiMap(camera, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topHeight, bottom = panelHeight)) {
            RouteLine(state.route)
            RouteStartMarker(state.pickup)
            RouteEndMarker(state.dropoff)
        }

        // Header: back + where this trip goes.
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackButton(onBack, stringResource(R.string.action_back), Modifier.shadow(12.dp, CircleShape))
            Row(
                Modifier
                    .weight(1f)
                    .shadow(12.dp, RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.surface)
                    .padding(horizontal = 14.dp, vertical = 11.dp)
                    .enterStagger(0),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(c.accent))
                Text(shortAddress(state.pickupAddress), style = MaterialTheme.typography.labelMedium, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Icon(painterResource(R.drawable.ic_arrow_right), null, tint = c.textMuted, modifier = Modifier.size(12.dp))
                Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
                Text(shortAddress(state.dropoffAddress), style = MaterialTheme.typography.labelMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
        }

        // Panel
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
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.ride_choose), style = MaterialTheme.typography.titleLarge, color = c.text, modifier = Modifier.enterStagger(0))
            Spacer(Modifier.height(2.dp))
            TripFacts(state, Modifier.enterStagger(1))
            Spacer(Modifier.height(14.dp))

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.options.forEachIndexed { i, option ->
                    RideOptionRow(
                        option = option,
                        price = state.fares[option.id]?.total,
                        selected = option.id == state.selectedId,
                        onClick = { onSelect(option.id) },
                        modifier = Modifier.enterStagger(2 + i),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            PaymentRow(state.payment, Modifier.enterStagger(5)) { showPayments = true }
            Spacer(Modifier.height(12.dp))

            PrimaryButton(
                text = stringResource(R.string.ride_book, stringResource(rideName(state.selectedId)), state.selectedFare?.let { formatPrice(it.total) } ?: ""),
                onClick = onConfirm,
                loading = state.booking,
                enabled = state.selectedFare != null && state.payment != null,
                modifier = Modifier.enterStagger(6),
            )
        }

        SnackbarHost(snackbar, Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp))
    }

    if (showPayments) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPayments = false },
            sheetState = sheet,
            containerColor = c.surface,
            dragHandle = { Box(Modifier.padding(top = 10.dp).size(36.dp, 4.dp).clip(CircleShape).background(c.line)) },
        ) {
            Column(Modifier.padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.payment_title), style = MaterialTheme.typography.titleLarge, color = c.text)
                Spacer(Modifier.height(12.dp))
                state.paymentMethods.forEachIndexed { i, method ->
                    val selected = method.id == state.payment?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .enterStagger(i)
                            .clip(RoundedCornerShape(16.dp))
                            .pressable {
                                onSelectPayment(method)
                                showPayments = false
                            }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        PaymentIcon(method)
                        Text(paymentLabel(method), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
                        if (selected) Icon(painterResource(R.drawable.ic_check_circle), null, tint = c.accentText, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun TripFacts(state: RideSelectionUiState, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val fare = state.selectedFare
    val arrive = remember(fare?.durationMinutes, state.selected.etaMinutes) {
        fare?.let {
            DateFormat.getTimeInstance(DateFormat.SHORT)
                .format(Date(System.currentTimeMillis() + (it.durationMinutes + state.selected.etaMinutes) * 60_000L))
        }
    }
    if (fare == null) {
        Skeleton(modifier.padding(top = 4.dp).width(200.dp).height(14.dp))
    } else {
        Text(
            stringResource(R.string.ride_facts, com.taxiapp.util.formatKm(fare.distanceMiles), fare.durationMinutes, arrive.orEmpty()),
            style = MaterialTheme.typography.bodyMedium,
            color = c.textMuted,
            modifier = modifier,
        )
    }
}

@Composable
private fun RideOptionRow(option: RideOption, price: Double?, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = TaxiTheme.colors
    val border by animateColorAsState(if (selected) c.accent else c.line, label = "ride-border")
    val bg by animateColorAsState(if (selected) c.accentTint else c.surface, label = "ride-bg")
    val carShift by animateFloatAsState(if (selected) 1f else 0f, spring(dampingRatio = 0.5f, stiffness = 300f), label = "car-shift")
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bg)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(18.dp))
            .pressable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(width = 52.dp, height = 40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) c.accent else c.surfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(option.iconRes),
                null,
                tint = if (selected) c.onAccent else c.textMuted,
                modifier = Modifier
                    .size(if (option.id == "xl") 26.dp else 22.dp)
                    .graphicsLayer { translationX = carShift * 3.dp.toPx() },
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(rideName(option.id)), style = MaterialTheme.typography.titleMedium, color = c.text)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Icon(painterResource(R.drawable.ic_nav_profile), null, tint = c.textMuted, modifier = Modifier.size(12.dp))
                    Text(option.subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted)
                }
            }
            Text(
                stringResource(R.string.ride_eta_desc, option.etaMinutes, stringResource(rideDescription(option.id))),
                style = MaterialTheme.typography.bodySmall,
                color = c.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (price == null) Skeleton(Modifier.width(56.dp).height(18.dp))
        else Text(formatPrice(price), style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun PaymentRow(method: PaymentMethod?, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = TaxiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .pressable(onClickLabel = stringResource(R.string.payment_title), onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (method == null) {
            Skeleton(Modifier.size(36.dp), CircleShape)
            Skeleton(Modifier.width(90.dp).height(14.dp))
            Spacer(Modifier.weight(1f))
        } else {
            PaymentIcon(method)
            Text(paymentLabel(method), style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        }
        Text(stringResource(R.string.payment_change), style = MaterialTheme.typography.labelMedium, color = c.accentText)
    }
}

@Composable
fun PaymentIcon(method: PaymentMethod) {
    val c = TaxiTheme.colors
    Box(Modifier.size(36.dp).clip(CircleShape).background(c.surfaceRaised), contentAlignment = Alignment.Center) {
        Icon(painterResource(paymentIcon(method.type)), null, tint = c.text, modifier = Modifier.size(18.dp))
    }
}

@DrawableRes
fun paymentIcon(type: String): Int = when (type) {
    "cash" -> R.drawable.ic_cash
    "wallet" -> R.drawable.ic_nav_wallet
    else -> R.drawable.ic_credit_card
}

@Composable
fun paymentLabel(method: PaymentMethod): String = when (method.type) {
    "cash" -> stringResource(R.string.payment_cash)
    "wallet" -> stringResource(R.string.payment_wallet)
    else -> method.label
}

@StringRes
fun rideName(id: String): Int = when (id) {
    "premium" -> R.string.ride_premium
    "xl" -> R.string.ride_xl
    else -> R.string.ride_economy
}

@StringRes
private fun rideDescription(id: String): Int = when (id) {
    "premium" -> R.string.ride_premium_desc
    "xl" -> R.string.ride_xl_desc
    else -> R.string.ride_economy_desc
}

/** First part of an address ("King Fahd Rd, Riyadh…" → "King Fahd Rd"). */
fun shortAddress(address: String): String = address.substringBefore(",").trim().ifEmpty { address }
