package com.taxiapp.ui.search

import androidx.compose.animation.AnimatedContent
import com.taxiapp.ui.components.UserLocationMarker
import com.taxiapp.ui.components.TaxiMap
import com.taxiapp.ui.components.PrimaryButton
import com.taxiapp.ui.components.PickupPin
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.CameraPosition
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.activity.compose.BackHandler
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.taxiapp.R
import com.taxiapp.ui.components.DefaultCamera
import com.taxiapp.data.model.PlaceResult
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.ui.components.BackButton
import com.taxiapp.ui.components.Skeleton
import com.taxiapp.ui.components.enterStagger
import com.taxiapp.ui.components.pressable
import com.taxiapp.ui.theme.TaxiTheme

@Composable
fun DestinationSearchScreen(
    state: SearchUiState,
    pickupAddress: String?,
    onQueryChange: (String) -> Unit,
    onPick: (PlaceResult) -> Unit,
    onPickRecent: (RecentDestination) -> Unit,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
    onCloseMap: () -> Unit,
    onPinDragStarted: () -> Unit,
    onPinMoved: (LatLng) -> Unit,
    onConfirmPin: (LatLng) -> Unit,
) {
    AnimatedContent(
        targetState = state.mapPicking,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "search-or-map",
    ) { picking ->
        if (picking) {
            BackHandler(onBack = onCloseMap)
            MapPicker(state, onCloseMap, onPinDragStarted, onPinMoved, onConfirmPin)
        } else {
            SearchList(state, pickupAddress, onQueryChange, onPick, onPickRecent, onBack, onOpenMap)
        }
    }
}

@Composable
private fun SearchList(
    state: SearchUiState,
    pickupAddress: String?,
    onQueryChange: (String) -> Unit,
    onPick: (PlaceResult) -> Unit,
    onPickRecent: (RecentDestination) -> Unit,
    onBack: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val c = TaxiTheme.colors
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    LaunchedEffect(Unit) { focus.requestFocus() }

    Column(
        Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        // Header: back + the two ends of the trip.
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BackButton(onBack, stringResource(R.string.action_back), Modifier.padding(top = 6.dp))
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(c.surface),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(c.accent))
                    Text(
                        pickupAddress ?: stringResource(R.string.home_current_location),
                        style = MaterialTheme.typography.bodyMedium,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(Modifier.padding(start = 38.dp).fillMaxWidth().height(1.dp).background(c.line))
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp).height(52.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.query.isEmpty()) {
                            Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                        }
                        BasicTextField(
                            value = state.query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.titleMedium.copy(color = c.text),
                            cursorBrush = SolidColor(c.accent),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                focusManager.clearFocus()
                                state.results.firstOrNull()?.let(onPick)
                            }),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    }
                    AnimatedVisibility(state.query.isNotEmpty(), enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut()) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(c.surfaceRaised)
                                .pressable(onClickLabel = stringResource(R.string.search_clear)) { onQueryChange("") },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.search_clear), tint = c.textMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        ResultRow(
            icon = R.drawable.ic_location_pin,
            title = stringResource(R.string.search_on_map),
            subtitle = stringResource(R.string.search_on_map_body),
            loading = false,
            onClick = { focusManager.clearFocus(); onOpenMap() },
            accent = true,
        )
        Box(Modifier.padding(horizontal = 20.dp).fillMaxWidth().height(1.dp).background(c.line))
        Spacer(Modifier.height(4.dp))

        val mode = when {
            state.query.trim().length < 2 -> 0
            state.searching && state.results.isEmpty() -> 1
            state.results.isEmpty() -> 2
            else -> 3
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
            label = "search-body",
            modifier = Modifier.weight(1f),
        ) { m ->
            when (m) {
                0 -> RecentList(state, onPickRecent)
                1 -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                    repeat(5) { i -> ResultSkeleton(Modifier.enterStagger(i)) }
                }
                2 -> Message(
                    if (state.failed) R.string.search_failed_title else R.string.search_empty_title,
                    if (state.failed) R.string.search_failed_body else R.string.search_empty_body,
                )
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    itemsIndexed(state.results, key = { _, p -> p.placeId }) { i, place ->
                        ResultRow(
                            icon = R.drawable.ic_location_pin,
                            title = place.primaryText,
                            subtitle = place.secondaryText,
                            loading = state.resolvingId == place.placeId,
                            onClick = { focusManager.clearFocus(); onPick(place) },
                            modifier = Modifier.animateItem().enterStagger(i.coerceAtMost(6), key = place.placeId),
                        )
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun RecentList(state: SearchUiState, onPickRecent: (RecentDestination) -> Unit) {
    val c = TaxiTheme.colors
    Column(Modifier.fillMaxSize()) {
        when {
            state.recentsLoading -> Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
                Skeleton(Modifier.width(70.dp).height(12.dp))
                repeat(3) { i -> ResultSkeleton(Modifier.enterStagger(i)) }
            }
            state.recents.isEmpty() -> Message(R.string.search_start_title, R.string.search_start_body)
            else -> {
                Text(
                    stringResource(R.string.search_recent),
                    style = MaterialTheme.typography.labelMedium,
                    color = c.textMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).enterStagger(0),
                )
                state.recents.forEachIndexed { i, place ->
                    val parts = place.address.split(",", limit = 2)
                    ResultRow(
                        icon = R.drawable.ic_history,
                        title = parts.first().trim(),
                        subtitle = parts.getOrNull(1)?.trim().orEmpty(),
                        loading = false,
                        onClick = { onPickRecent(place) },
                        modifier = Modifier.enterStagger(1 + i),
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(icon: Int, title: String, subtitle: String, loading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, accent: Boolean = false) {
    val c = TaxiTheme.colors
    Row(
        modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (accent) c.accentTint else c.surface), contentAlignment = Alignment.Center) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), color = c.accent, strokeWidth = 2.dp)
            } else {
                Icon(painterResource(icon), null, tint = if (accent) c.accentText else c.textMuted, modifier = Modifier.size(18.dp))
            }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ResultSkeleton(modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Skeleton(Modifier.size(40.dp), CircleShape)
        Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Skeleton(Modifier.width(170.dp).height(14.dp))
            Skeleton(Modifier.width(110.dp).height(11.dp))
        }
    }
}

@Composable
private fun Message(title: Int, body: Int) {
    val c = TaxiTheme.colors
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp).enterStagger(0),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(60.dp).clip(CircleShape).background(c.surface), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_search), null, tint = c.textMuted, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium, color = c.text)
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = c.textMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** Drag the map under a fixed pin to choose exactly where to go. */
@Composable
private fun MapPicker(
    state: SearchUiState,
    onClose: () -> Unit,
    onDragStarted: () -> Unit,
    onPinMoved: (LatLng) -> Unit,
    onConfirm: (LatLng) -> Unit,
) {
    val c = TaxiTheme.colors
    val density = LocalDensity.current
    val view = LocalView.current
    var panelHeight by remember { mutableStateOf(0.dp) }
    var topHeight by remember { mutableStateOf(0.dp) }
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(state.pickup ?: DefaultCamera, if (state.pickup != null) 16f else 12f)
    }
    LaunchedEffect(Unit) { onPinMoved(camera.position.target) }

    val dragging = camera.isMoving && camera.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE
    var wasDragging by remember { mutableStateOf(false) }
    LaunchedEffect(dragging) {
        if (dragging) {
            wasDragging = true
            onDragStarted()
        }
    }
    LaunchedEffect(camera.isMoving) {
        if (!camera.isMoving && wasDragging) {
            wasDragging = false
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onPinMoved(camera.position.target)
        }
    }

    Box(Modifier.fillMaxSize().background(c.background)) {
        TaxiMap(camera, Modifier.fillMaxSize(), contentPadding = PaddingValues(top = topHeight, bottom = panelHeight)) {
            state.pickup?.let { UserLocationMarker(it) }
        }
        Box(Modifier.fillMaxSize().padding(top = topHeight, bottom = panelHeight), contentAlignment = Alignment.Center) {
            PickupPin(lifted = dragging, resolving = state.pinResolving, destination = true, modifier = Modifier.offset(y = (-26).dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { topHeight = with(density) { it.height.toDp() } }
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BackButton(onClose, stringResource(R.string.action_back), Modifier.shadow(12.dp, CircleShape))
        }
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
                .padding(top = 18.dp, bottom = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.search_on_map_title), style = MaterialTheme.typography.titleLarge, color = c.text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(c.text))
                AnimatedContent(
                    targetState = if (state.pinResolving) null else state.pinAddress.orEmpty(),
                    transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                    label = "pin-address",
                ) { address ->
                    when {
                        address == null -> Skeleton(Modifier.width(220.dp).height(16.dp))
                        address.isEmpty() -> Text(stringResource(R.string.search_on_map_unnamed), style = MaterialTheme.typography.titleMedium, color = c.textMuted)
                        else -> Text(address, style = MaterialTheme.typography.titleMedium, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            PrimaryButton(
                stringResource(R.string.search_on_map_confirm),
                onClick = { onConfirm(camera.position.target) },
                enabled = !state.pinResolving && !dragging,
            )
        }
    }
}
