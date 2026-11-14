package com.taxiapp.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import android.util.Log
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.data.location.StreetAddressLookup
import com.taxiapp.data.model.PlaceResult
import com.taxiapp.data.model.TripStatus
import com.taxiapp.data.repository.HomeRepository
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val results: List<PlaceResult> = emptyList(),
    val searching: Boolean = false,
    val failed: Boolean = false,
    val recents: List<RecentDestination> = emptyList(),
    val recentsLoading: Boolean = true,
    /** The place being looked up after a tap; its row shows a spinner. */
    val resolvingId: String? = null,
    /** Where the rider is being picked up; centres the map picker. */
    val pickup: LatLng? = null,
    /** "Choose on map" is open. */
    val mapPicking: Boolean = false,
    val pinAddress: String? = null,
    val pinResolving: Boolean = false,
)

/** A place the rider picked, ready to hand back to the home screen. */
data class ChosenPlace(val address: String, val lat: Double, val lng: Double)

@HiltViewModel
class DestinationSearchViewModel @Inject constructor(
    private val placesClient: PlacesClient,
    private val homeRepository: HomeRepository,
    private val geocoder: StreetAddressLookup,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _chosen = MutableStateFlow<ChosenPlace?>(null)
    val chosen: StateFlow<ChosenPlace?> = _chosen.asStateFlow()

    private var sessionToken = AutocompleteSessionToken.newInstance()
    private var searchJob: Job? = null
    private var pinJob: Job? = null
    /** Google Places failed (no billing, key restricted, offline…): use the device geocoder. */
    private var placesDown = false

    init {
        viewModelScope.launch {
            val rides = (homeRepository.getRideHistory() as? Resource.Success)?.data.orEmpty()
            val recents = rides.asSequence()
                .filter { it.status == TripStatus.COMPLETED && it.dropoffAddress.isNotBlank() }
                .distinctBy { it.dropoffAddress }
                .take(5)
                .map { RecentDestination(it.dropoffAddress, it.dropoffLat, it.dropoffLng, it.createdAt) }
                .toList()
            _state.update { it.copy(recents = recents, recentsLoading = false) }
        }
        val lat = savedState.get<Double>(DestinationSearchActivity.EXTRA_PICKUP_LAT)
        val lng = savedState.get<Double>(DestinationSearchActivity.EXTRA_PICKUP_LNG)
        if (lat != null && lng != null && (lat != 0.0 || lng != 0.0)) _state.update { it.copy(pickup = LatLng(lat, lng)) }
        savedState.get<String>(DestinationSearchActivity.EXTRA_PREFILL)?.takeIf { it.isNotBlank() }?.let(::onQueryChange)
    }

    fun onQueryChange(query: String) {
        _state.update { it.copy(query = query, failed = false) }
        searchJob?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.update { it.copy(results = emptyList(), searching = false) }
            return
        }
        _state.update { it.copy(searching = true) }
        searchJob = viewModelScope.launch {
            delay(280) // wait for a pause in typing
            val results = searchPlaces(q) ?: geocoder.search(q, _state.value.pickup?.let { it.latitude to it.longitude })
            _state.update { it.copy(results = results, searching = false, failed = false) }
        }
    }

    /** Google Places autocomplete, or null when it isn't available. */
    private suspend fun searchPlaces(q: String): List<PlaceResult>? {
        if (placesDown) return null
        return try {
            val request = FindAutocompletePredictionsRequest.builder()
                .setSessionToken(sessionToken)
                .setQuery(q)
                .apply {
                    _state.value.pickup?.let { p ->
                        setOrigin(p)
                        // This API only takes a rectangle; ~30 km around the pickup.
                        setLocationBias(
                            com.google.android.libraries.places.api.model.RectangularBounds.newInstance(
                                LatLng(p.latitude - 0.27, p.longitude - 0.3),
                                LatLng(p.latitude + 0.27, p.longitude + 0.3),
                            ),
                        )
                    }
                }
                .build()
            placesClient.findAutocompletePredictions(request).await().autocompletePredictions.map {
                PlaceResult(it.placeId, it.getPrimaryText(null).toString(), it.getSecondaryText(null).toString())
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable, not Exception: the Places SDK reports some problems as Errors.
            Log.w(TAG, "Places search failed; using the device geocoder instead", e)
            placesDown = true
            null
        }
    }

    fun pick(place: PlaceResult) {
        if (_state.value.resolvingId != null) return
        // Geocoder results already carry coordinates.
        if (place.lat != 0.0 || place.lng != 0.0) {
            _chosen.value = ChosenPlace(listOf(place.primaryText, place.secondaryText).filter { it.isNotBlank() }.joinToString(", "), place.lat, place.lng)
            return
        }
        _state.update { it.copy(resolvingId = place.placeId) }
        viewModelScope.launch {
            val fields = listOf(Place.Field.ID, Place.Field.DISPLAY_NAME, Place.Field.LOCATION, Place.Field.FORMATTED_ADDRESS)
            val chosen = try {
                val fetched = placesClient.fetchPlace(FetchPlaceRequest.builder(place.placeId, fields).setSessionToken(sessionToken).build()).await().place
                val loc = fetched.location
                ChosenPlace(fetched.formattedAddress ?: fetched.displayName ?: place.primaryText, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Throwable) {
                // Coordinates of 0,0 make the home screen ask for the place again.
                ChosenPlace(place.primaryText, 0.0, 0.0)
            }
            sessionToken = AutocompleteSessionToken.newInstance()
            _state.update { it.copy(resolvingId = null) }
            _chosen.value = chosen
        }
    }

    fun pickRecent(place: RecentDestination) {
        if (place.lat == 0.0 && place.lng == 0.0) onQueryChange(place.address)
        else _chosen.value = ChosenPlace(place.address, place.lat, place.lng)
    }

    fun openMap() = _state.update { it.copy(mapPicking = true) }

    fun closeMap() = _state.update { it.copy(mapPicking = false) }

    fun onPinDragStarted() = _state.update { it.copy(pinResolving = true) }

    /** The map stopped with the destination pin over this spot. */
    fun onPinMoved(target: LatLng) {
        _state.update { it.copy(pinResolving = true) }
        pinJob?.cancel()
        pinJob = viewModelScope.launch {
            val address = geocoder.describe(target.latitude, target.longitude)
            _state.update { it.copy(pinAddress = address, pinResolving = false) }
        }
    }

    fun confirmPin(target: LatLng) {
        val s = _state.value
        if (s.pinResolving) return
        val address = s.pinAddress ?: String.format(java.util.Locale.US, "%.5f, %.5f", target.latitude, target.longitude)
        _chosen.value = ChosenPlace(address, target.latitude, target.longitude)
    }

    private companion object {
        const val TAG = "DestinationSearch"
    }
}
