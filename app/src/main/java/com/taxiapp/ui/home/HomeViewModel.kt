package com.taxiapp.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.data.location.StreetAddressLookup
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.data.model.User
import com.taxiapp.data.repository.AuthRepository
import com.taxiapp.data.repository.FcmTokenSync
import com.taxiapp.data.repository.HomeRepository
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { Home, Rides, Account }

data class HomeUiState(
    val tab: HomeTab = HomeTab.Home,
    val user: User? = null,
    val signedOut: Boolean = false,
    val myLocation: LatLng? = null,
    /** Where the rider will be picked up: their location, or wherever they move the pin. */
    val pickup: LatLng? = null,
    val pickupAddress: String? = null,
    /** True while the address for [pickup] is being looked up. */
    val pickupResolving: Boolean = false,
    val locationDenied: Boolean = false,
    val rides: List<Trip> = emptyList(),
    val ridesLoading: Boolean = true,
) {
    /** A trip that hasn't finished yet, so the rider can get back to it. */
    val activeTrip: Trip? get() = rides.firstOrNull { it.status in ACTIVE_STATUSES }

    /** Up to three distinct places from finished trips. */
    val recentPlaces: List<RecentDestination>
        get() = rides.asSequence()
            .filter { it.status == TripStatus.COMPLETED && it.dropoffAddress.isNotBlank() }
            .distinctBy { it.dropoffAddress }
            .take(3)
            .map { RecentDestination(it.dropoffAddress, it.dropoffLat, it.dropoffLng, it.createdAt) }
            .toList()

    private companion object {
        val ACTIVE_STATUSES = setOf(TripStatus.SEARCHING, TripStatus.ACCEPTED, TripStatus.ARRIVED, TripStatus.ONGOING)
    }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenSync: FcmTokenSync,
    private val addressLookup: StreetAddressLookup,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observeUser().collect { result ->
                when (result) {
                    is Resource.Success -> _state.update { it.copy(user = result.data) }
                    is Resource.Error -> if (result.message.contains("not authenticated", ignoreCase = true)) {
                        _state.update { it.copy(signedOut = true) }
                    }
                    else -> Unit
                }
            }
        }
        refreshRides()
    }

    fun selectTab(tab: HomeTab) {
        _state.update { it.copy(tab = tab) }
        if (tab == HomeTab.Rides) refreshRides()
    }

    /** Called when coming back to the screen, e.g. after booking a ride. */
    fun refreshRides() {
        viewModelScope.launch {
            val result = repository.getRideHistory()
            _state.update {
                it.copy(rides = (result as? Resource.Success)?.data ?: it.rides, ridesLoading = false)
            }
        }
    }

    fun onLocation(lat: Double, lng: Double) {
        val me = LatLng(lat, lng)
        val firstFix = _state.value.myLocation == null
        _state.update { it.copy(myLocation = me, locationDenied = false) }
        // The first fix also sets the pickup; after that the rider controls it with the pin.
        if (firstFix && _state.value.pickup == null) onPickupMoved(me)
    }

    private var lookupJob: Job? = null

    /** The map stopped moving with the pin over [target]. */
    fun onPickupMoved(target: LatLng) {
        val current = _state.value.pickup
        if (current != null && _state.value.pickupAddress != null && distanceMeters(current, target) < 12) return
        _state.update { it.copy(pickup = target, pickupResolving = true) }
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            val address = addressLookup.lookup(target.latitude, target.longitude)
            _state.update { it.copy(pickupAddress = address ?: it.pickupAddress, pickupResolving = false) }
        }
    }

    /** The rider started dragging the map: the old address no longer applies. */
    fun onPickupDragStarted() = _state.update { it.copy(pickupResolving = true) }

    fun onLocationDenied() = _state.update { it.copy(locationDenied = true) }

    fun signOut() {
        viewModelScope.launch {
            fcmTokenSync.clearForCurrentUser()
            authRepository.signOut()
            _state.update { it.copy(signedOut = true) }
        }
    }

    private fun distanceMeters(a: LatLng, b: LatLng): Float {
        val out = FloatArray(1)
        android.location.Location.distanceBetween(a.latitude, a.longitude, b.latitude, b.longitude, out)
        return out[0]
    }
}
