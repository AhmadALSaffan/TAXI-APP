package com.taxiapp.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.taxiapp.R
import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.data.model.RideOption
import com.taxiapp.data.model.Trip
import com.taxiapp.data.repository.RideRepository
import com.taxiapp.util.PricingEngine
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RideSelectionViewModel @Inject constructor(
    private val repository: RideRepository
) : ViewModel() {

    val rideOptions: List<RideOption> = listOf(
        RideOption(id = "economy", name = "Economy",  subtitle = "1-3 seats",    price = 0.0, originalPrice = null, etaMinutes = 3, iconRes = R.drawable.ic_car_filled),
        RideOption(id = "premium", name = "Premium",  subtitle = "Luxury sedan",  price = 0.0, originalPrice = null, etaMinutes = 5, iconRes = R.drawable.ic_car_filled),
        RideOption(id = "xl",      name = "Ride XL",  subtitle = "Up to 6 seats", price = 0.0, originalPrice = null, etaMinutes = 8, iconRes = R.drawable.ic_car_filled)
    )

    private val _selectedRide = MutableStateFlow(rideOptions.first())
    val selectedRide: StateFlow<RideOption> = _selectedRide.asStateFlow()

    private val _selectedPayment = MutableStateFlow(PaymentMethod("visa", "VISA  ····  4242", "card", "visa"))
    val selectedPayment: StateFlow<PaymentMethod> = _selectedPayment.asStateFlow()

    private val _paymentMethods = MutableStateFlow<List<PaymentMethod>>(emptyList())
    val paymentMethods: StateFlow<List<PaymentMethod>> = _paymentMethods.asStateFlow()

    private val _fareBreakdowns = MutableStateFlow<Map<String, FareBreakdown>>(emptyMap())
    val fareBreakdowns: StateFlow<Map<String, FareBreakdown>> = _fareBreakdowns.asStateFlow()

    private val _tripState = MutableStateFlow<Resource<String>?>(null)
    val tripState: StateFlow<Resource<String>?> = _tripState.asStateFlow()

    init {
        loadPaymentMethods()
    }

    fun computePrices(pickupLat: Double, pickupLng: Double, dropoffLat: Double, dropoffLng: Double) {
        _fareBreakdowns.value = rideOptions.associate { option ->
            option.id to PricingEngine.calculate(pickupLat, pickupLng, dropoffLat, dropoffLng, option.id)
        }
    }

    fun getFareForRide(rideId: String): FareBreakdown? = _fareBreakdowns.value[rideId]

    private fun loadPaymentMethods() {
        viewModelScope.launch {
            when (val result = repository.getPaymentMethods()) {
                is Resource.Success -> {
                    _paymentMethods.value = result.data
                    _selectedPayment.value = result.data.firstOrNull() ?: _selectedPayment.value
                }
                else -> {}
            }
        }
    }

    fun selectRide(ride: RideOption) { _selectedRide.value = ride }
    fun selectPayment(method: PaymentMethod) { _selectedPayment.value = method }

    fun confirmTrip(
        pickupAddress: String, dropoffAddress: String,
        pickupLat: Double, pickupLng: Double,
        dropoffLat: Double, dropoffLng: Double
    ) {
        viewModelScope.launch {
            _tripState.value = Resource.Loading
            val fare = getFareForRide(_selectedRide.value.id)
                ?: PricingEngine.calculate(pickupLat, pickupLng, dropoffLat, dropoffLng, _selectedRide.value.id)

            val trip = Trip(
                pickupAddress   = pickupAddress,
                dropoffAddress  = dropoffAddress,
                pickupLat       = pickupLat,
                pickupLng       = pickupLng,
                dropoffLat      = dropoffLat,
                dropoffLng      = dropoffLng,
                rideType        = _selectedRide.value.id,
                price           = fare.total,
                baseFare        = fare.baseFare,
                distanceFare    = fare.distanceFare,
                timeFare        = fare.timeFare,
                tolls           = fare.tolls,
                distanceMiles   = fare.distanceMiles,
                durationMinutes = fare.durationMinutes,
                paymentMethod   = _selectedPayment.value.id
            )
            _tripState.value = repository.createTrip(trip)
        }
    }

    fun resetTripState() { _tripState.value = null }
}