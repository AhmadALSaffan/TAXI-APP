package com.taxiapp.ui.ride

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.R
import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.data.model.RideOption
import com.taxiapp.data.model.Trip
import com.taxiapp.data.repository.DirectionsRepository
import com.taxiapp.data.repository.RideRepository
import com.taxiapp.util.PricingEngine
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RideSelectionUiState(
    val pickupAddress: String,
    val dropoffAddress: String,
    val pickup: LatLng,
    val dropoff: LatLng,
    val options: List<RideOption>,
    val fares: Map<String, FareBreakdown>,
    val selectedId: String,
    /** Road route from pickup to dropoff; empty until it arrives (or if it fails). */
    val route: List<LatLng> = emptyList(),
    val routeLoading: Boolean = true,
    val paymentMethods: List<PaymentMethod> = emptyList(),
    val payment: PaymentMethod? = null,
    val booking: Boolean = false,
    val bookedTripId: String? = null,
    val error: String? = null,
) {
    val selected: RideOption get() = options.first { it.id == selectedId }
    val selectedFare: FareBreakdown? get() = fares[selectedId]
}

@HiltViewModel
class RideSelectionViewModel @Inject constructor(
    private val repository: RideRepository,
    private val directions: DirectionsRepository,
    savedState: SavedStateHandle,
) : ViewModel() {

    private val options = listOf(
        RideOption("economy", "Economy", "4", 0.0, null, 3, R.drawable.ic_car_filled),
        RideOption("premium", "Premium", "4", 0.0, null, 5, R.drawable.ic_car_filled),
        RideOption("xl", "XL", "6", 0.0, null, 8, R.drawable.ic_car_filled),
    )

    private val _state: MutableStateFlow<RideSelectionUiState>
    val state: StateFlow<RideSelectionUiState>

    init {
        val pickup = LatLng(
            savedState.get<Double>(RideSelectionActivity.EXTRA_PICKUP_LAT) ?: 0.0,
            savedState.get<Double>(RideSelectionActivity.EXTRA_PICKUP_LNG) ?: 0.0,
        )
        val dropoff = LatLng(
            savedState.get<Double>(RideSelectionActivity.EXTRA_DROPOFF_LAT) ?: 0.0,
            savedState.get<Double>(RideSelectionActivity.EXTRA_DROPOFF_LNG) ?: 0.0,
        )
        // Prices use the same straight-line formula the server re-checks them with.
        val fares = options.associate {
            it.id to PricingEngine.calculate(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude, it.id)
        }
        _state = MutableStateFlow(
            RideSelectionUiState(
                pickupAddress = savedState.get<String>(RideSelectionActivity.EXTRA_PICKUP_ADDRESS).orEmpty(),
                dropoffAddress = savedState.get<String>(RideSelectionActivity.EXTRA_DROPOFF_ADDRESS).orEmpty(),
                pickup = pickup,
                dropoff = dropoff,
                options = options,
                fares = fares,
                selectedId = options.first().id,
            ),
        )
        state = _state.asStateFlow()

        viewModelScope.launch {
            val result = directions.getRoutePolyline(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude)
            val route = (result as? Resource.Success)?.data?.map { LatLng(it.first, it.second) }.orEmpty()
            _state.update { it.copy(route = route, routeLoading = false) }
        }
        viewModelScope.launch {
            val methods = (repository.getPaymentMethods() as? Resource.Success)?.data.orEmpty()
            _state.update { it.copy(paymentMethods = methods, payment = it.payment ?: methods.firstOrNull()) }
        }
    }

    fun select(id: String) = _state.update { it.copy(selectedId = id) }

    fun selectPayment(method: PaymentMethod) = _state.update { it.copy(payment = method) }

    fun confirm() {
        val s = _state.value
        if (s.booking) return
        val fare = s.selectedFare ?: return
        _state.update { it.copy(booking = true, error = null) }
        viewModelScope.launch {
            val trip = Trip(
                pickupAddress = s.pickupAddress,
                dropoffAddress = s.dropoffAddress,
                pickupLat = s.pickup.latitude,
                pickupLng = s.pickup.longitude,
                dropoffLat = s.dropoff.latitude,
                dropoffLng = s.dropoff.longitude,
                rideType = s.selectedId,
                price = fare.total,
                baseFare = fare.baseFare,
                distanceFare = fare.distanceFare,
                timeFare = fare.timeFare,
                tolls = fare.tolls,
                distanceMiles = fare.distanceMiles,
                durationMinutes = fare.durationMinutes,
                paymentMethod = s.payment?.id ?: "cash",
            )
            when (val result = repository.createTrip(trip)) {
                is Resource.Success -> _state.update { it.copy(booking = false, bookedTripId = result.data) }
                is Resource.Error -> _state.update { it.copy(booking = false, error = result.message) }
                else -> Unit
            }
        }
    }

    fun onErrorShown() = _state.update { it.copy(error = null) }
}
