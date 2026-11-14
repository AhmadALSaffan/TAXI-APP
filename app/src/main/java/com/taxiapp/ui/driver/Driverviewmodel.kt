package com.taxiapp.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.User
import com.taxiapp.data.repository.AuthRepository
import com.taxiapp.data.repository.DriverRepository
import com.taxiapp.data.repository.DriverTripsSummary
import com.taxiapp.data.repository.FcmTokenSync
import com.taxiapp.data.repository.HomeRepository
import com.taxiapp.util.PricingEngine
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DriverUiState(
    val user: User? = null,
    val isOnline: Boolean = false,
    val location: LatLng? = null,
    val locationDenied: Boolean = false,
    val requests: List<TripRequest> = emptyList(),
    val searching: Boolean = false,
    val acceptingTripId: String? = null,
    val summary: DriverTripsSummary = DriverTripsSummary(),
    val summaryLoaded: Boolean = false,
    val message: String? = null,
    /** Trip to open in the tracking screen (after accepting, or resuming). */
    val openTripId: String? = null,
    val signedOut: Boolean = false,
)

/** A nearby trip plus how far its pickup is from the driver. */
data class TripRequest(val trip: Trip, val pickupKm: Double)

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val repository: DriverRepository,
    private val homeRepository: HomeRepository,
    private val authRepository: AuthRepository,
    private val fcmTokenSync: FcmTokenSync,
) : ViewModel() {

    private val _state = MutableStateFlow(DriverUiState())
    val state: StateFlow<DriverUiState> = _state.asStateFlow()

    private var requestsJob: Job? = null

    init {
        viewModelScope.launch {
            homeRepository.observeUser().collect { result ->
                when (result) {
                    is Resource.Success -> _state.update { it.copy(user = result.data) }
                    is Resource.Error -> if (result.message.contains("not authenticated", ignoreCase = true)) {
                        _state.update { it.copy(signedOut = true) }
                    }
                    else -> Unit
                }
            }
        }
        viewModelScope.launch {
            repository.observeMyTrips().collect { summary -> _state.update { it.copy(summary = summary, summaryLoaded = true) } }
        }
    }

    fun onLocation(lat: Double, lng: Double) {
        val firstFix = _state.value.location == null
        _state.update { it.copy(location = LatLng(lat, lng), locationDenied = false) }
        viewModelScope.launch {
            repository.updateDriverLocation(lat, lng, _state.value.summary.activeTrip?.tripId)
        }
        if (firstFix && _state.value.isOnline) startRequests()
        else refreshDistances()
    }

    fun onLocationDenied() = _state.update { it.copy(locationDenied = true) }

    fun setOnline(online: Boolean) {
        _state.update { it.copy(isOnline = online) }
        viewModelScope.launch { repository.setDriverAvailability(online) }
        if (online) startRequests() else stopRequests()
    }

    fun accept(trip: Trip) {
        val location = _state.value.location ?: return
        _state.update { it.copy(acceptingTripId = trip.tripId) }
        viewModelScope.launch {
            when (val result = repository.acceptTrip(trip, location.latitude, location.longitude)) {
                is Resource.Success -> _state.update { it.copy(acceptingTripId = null, openTripId = result.data) }
                is Resource.Error -> _state.update {
                    it.copy(acceptingTripId = null, message = "This trip is no longer available.")
                }
                else -> Unit
            }
        }
    }

    fun resumeActiveTrip() {
        _state.value.summary.activeTrip?.let { trip -> _state.update { it.copy(openTripId = trip.tripId) } }
    }

    fun onTripOpened() = _state.update { it.copy(openTripId = null) }
    fun onMessageShown() = _state.update { it.copy(message = null) }

    fun signOut() {
        viewModelScope.launch {
            if (_state.value.isOnline) repository.setDriverAvailability(false)
            fcmTokenSync.clearForCurrentUser()
            authRepository.signOut()
            _state.update { it.copy(signedOut = true) }
        }
    }

    private fun startRequests() {
        val me = _state.value.location ?: run {
            _state.update { it.copy(searching = true) }
            return
        }
        if (requestsJob?.isActive == true) return
        _state.update { it.copy(searching = true) }
        requestsJob = viewModelScope.launch {
            repository.observeNearbyTrips(me.latitude, me.longitude).collect { result ->
                when (result) {
                    is Resource.Success -> _state.update { s ->
                        s.copy(searching = false, requests = toRequests(result.data, s.location ?: me))
                    }
                    is Resource.Error -> _state.update { it.copy(searching = false, message = result.message) }
                    else -> Unit
                }
            }
        }
    }

    private fun stopRequests() {
        requestsJob?.cancel()
        requestsJob = null
        _state.update { it.copy(requests = emptyList(), searching = false) }
    }

    private fun refreshDistances() {
        val me = _state.value.location ?: return
        _state.update { s -> s.copy(requests = toRequests(s.requests.map { it.trip }, me)) }
    }

    private fun toRequests(trips: List<Trip>, me: LatLng) = trips
        .map { TripRequest(it, PricingEngine.haversineKm(me.latitude, me.longitude, it.pickupLat, it.pickupLng)) }
        .sortedBy { it.pickupKm }
}
