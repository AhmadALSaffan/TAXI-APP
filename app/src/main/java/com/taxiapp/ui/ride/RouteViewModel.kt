package com.taxiapp.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.maps.model.LatLng
import com.taxiapp.data.repository.DirectionsRepository
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val directionsRepository: DirectionsRepository
) : ViewModel() {

    private val _routePoints = MutableStateFlow<Resource<List<LatLng>>>(Resource.Loading as Resource<List<LatLng>>)
    val routePoints: StateFlow<Resource<List<LatLng>>> = _routePoints.asStateFlow()

    fun fetchRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String
    ) {
        viewModelScope.launch {
            _routePoints.value = Resource.Loading as Resource<List<LatLng>>
            val result = directionsRepository.getRoutePolyline(originLat, originLng, destLat, destLng, apiKey)
            _routePoints.value = when (result) {
                is Resource.Success -> Resource.Success(result.data.map { LatLng(it.first, it.second) })
                is Resource.Error   -> Resource.Error(result.message)
                else                -> Resource.Error("Unknown error")
            }
        }
    }
}