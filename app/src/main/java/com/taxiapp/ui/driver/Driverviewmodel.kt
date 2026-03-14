package com.taxiapp.ui.driver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxiapp.data.model.Trip
import com.taxiapp.data.repository.DriverRepository
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DriverViewModel @Inject constructor(
    private val repository: DriverRepository
) : ViewModel() {

    private val _isOnline     = MutableStateFlow(false)
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _nearbyTrips  = MutableStateFlow<Resource<List<Trip>>>(Resource.Loading as Resource<List<Trip>>)
    val nearbyTrips: StateFlow<Resource<List<Trip>>> = _nearbyTrips.asStateFlow()

    private val _acceptState  = MutableStateFlow<Resource<String>?>(null)
    val acceptState: StateFlow<Resource<String>?> = _acceptState.asStateFlow()

    private var driverLat     = 0.0
    private var driverLng     = 0.0
    private var activeTripId: String? = null
    private var tripsJob: Job?        = null

    fun setLocation(lat: Double, lng: Double) {
        driverLat = lat
        driverLng = lng
        viewModelScope.launch { repository.updateDriverLocation(lat, lng, activeTripId) }
    }

    fun toggleOnline() {
        val newState = !_isOnline.value
        _isOnline.value = newState
        viewModelScope.launch { repository.setDriverAvailability(newState) }
        if (newState && driverLat != 0.0) startObservingTrips()
        else stopObservingTrips()
    }

    fun locationReady(lat: Double, lng: Double) {
        driverLat = lat
        driverLng = lng
        viewModelScope.launch { repository.updateDriverLocation(lat, lng, activeTripId) }
        if (_isOnline.value && tripsJob == null) startObservingTrips()
    }

    private fun startObservingTrips() {
        if (tripsJob?.isActive == true) return
        tripsJob = viewModelScope.launch {
            repository.observeNearbyTrips(driverLat, driverLng).collect {
                _nearbyTrips.value = it
            }
        }
    }

    private fun stopObservingTrips() {
        tripsJob?.cancel()
        tripsJob = null
        _nearbyTrips.value = Resource.Success(emptyList())
    }

    fun acceptTrip(trip: Trip) {
        viewModelScope.launch {
            _acceptState.value = Resource.Loading as Resource<String>
            val result = repository.acceptTrip(trip, driverLat, driverLng)
            if (result is Resource.Success) activeTripId = result.data
            _acceptState.value = result
        }
    }

    fun resetAcceptState() { _acceptState.value = null }
}