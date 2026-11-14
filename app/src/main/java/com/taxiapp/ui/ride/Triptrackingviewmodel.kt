package com.taxiapp.ui.ride

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.data.repository.DirectionsRepository
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class TripUiState(
    val isDriver: Boolean,
    val trip: Trip? = null,
    val notFound: Boolean = false,
    val route: List<LatLng> = emptyList(),
    val routeLoading: Boolean = true,
    /** A status change or cancel is being saved. */
    val busy: Boolean = false,
    val message: String? = null,
    /** The screen has nothing more to show (e.g. the rider cancelled). */
    val closed: Boolean = false,
) {
    val status: String get() = trip?.status.orEmpty()
    val isActive: Boolean get() = status in setOf(TripStatus.ACCEPTED, TripStatus.ARRIVED, TripStatus.ONGOING)
    val canCancel: Boolean get() = status in setOf(TripStatus.SEARCHING, TripStatus.ACCEPTED, TripStatus.ARRIVED)
}

@HiltViewModel
class TripTrackingViewModel @Inject constructor(
    private val database: FirebaseDatabase,
    private val directions: DirectionsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val tripId: String = savedState.get<String>(TripTrackingActivity.EXTRA_TRIP_ID).orEmpty()

    private val _state = MutableStateFlow(TripUiState(isDriver = savedState.get<Boolean>(TripTrackingActivity.EXTRA_IS_DRIVER) ?: false))
    val state: StateFlow<TripUiState> = _state.asStateFlow()

    private val tripRef get() = database.getReference("trips/$tripId")
    private var routeRequested = false

    init {
        if (tripId.isBlank()) _state.update { it.copy(notFound = true) } else observe()
    }

    private fun observe() {
        viewModelScope.launch {
            callbackFlow {
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        trySend(Resource.Success(snapshot.getValue(Trip::class.java)))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        trySend(Resource.Error(error.message))
                    }
                }
                tripRef.addValueEventListener(listener)
                awaitClose { tripRef.removeEventListener(listener) }
            }.collect { result ->
                when (result) {
                    is Resource.Success -> {
                        val trip = result.data
                        if (trip == null) {
                            _state.update { it.copy(notFound = true) }
                        } else {
                            _state.update { it.copy(trip = trip, notFound = false) }
                            fetchRouteOnce(trip)
                        }
                    }
                    is Resource.Error -> _state.update { it.copy(notFound = it.trip == null, message = result.message) }
                    else -> Unit
                }
            }
        }
    }

    private fun fetchRouteOnce(trip: Trip) {
        if (routeRequested || trip.pickupLat == 0.0 || trip.dropoffLat == 0.0) return
        routeRequested = true
        viewModelScope.launch {
            val result = directions.getRoutePolyline(trip.pickupLat, trip.pickupLng, trip.dropoffLat, trip.dropoffLng)
            val route = (result as? Resource.Success)?.data?.map { LatLng(it.first, it.second) }.orEmpty()
            _state.update { it.copy(route = route, routeLoading = false) }
        }
    }

    private fun write(updates: Map<String, Any>, closeAfter: Boolean = false) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                tripRef.updateChildren(updates).await()
                _state.update { it.copy(busy = false, closed = closeAfter) }
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "Couldn’t update the trip") }
            }
        }
    }

    /** Rider or driver calls the trip off. The rider leaves the screen straight away. */
    fun cancel() = write(mapOf("status" to TripStatus.CANCELLED), closeAfter = !_state.value.isDriver)

    fun markArrived() = write(mapOf("status" to TripStatus.ARRIVED))

    fun startTrip() = write(mapOf("status" to TripStatus.ONGOING, "startedAt" to System.currentTimeMillis()))

    fun completeTrip(cashReceived: Boolean) {
        val updates = buildMap<String, Any> {
            put("status", TripStatus.COMPLETED)
            put("completedAt", System.currentTimeMillis())
            if (cashReceived) put("cashPaid", true)
        }
        write(updates)
    }

    /** The assigned driver shares where they are so the rider can follow along. */
    fun onDriverLocation(lat: Double, lng: Double) {
        val s = _state.value
        if (!s.isDriver || !s.isActive) return
        tripRef.updateChildren(mapOf("driverLat" to lat, "driverLng" to lng))
    }

    fun onMessageShown() = _state.update { it.copy(message = null) }
}
