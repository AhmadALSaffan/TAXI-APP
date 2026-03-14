package com.taxiapp.ui.ride

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.util.Resource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

@HiltViewModel
class TripTrackingViewModel @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) : ViewModel() {

    private val _tripState     = MutableStateFlow<Resource<Trip>>(Resource.Loading as Resource<Trip>)
    val tripState: StateFlow<Resource<Trip>> = _tripState.asStateFlow()

    private val _cancelState   = MutableStateFlow<Resource<Unit>?>(null)
    val cancelState: StateFlow<Resource<Unit>?> = _cancelState.asStateFlow()

    private val _completeState = MutableStateFlow<Resource<Unit>?>(null)
    val completeState: StateFlow<Resource<Unit>?> = _completeState.asStateFlow()

    fun observeTrip(tripId: String) {
        viewModelScope.launch {
            callbackFlow<Resource<Trip>> {
                val ref = database.getReference("trips/$tripId")
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        val trip = snapshot.getValue(Trip::class.java)
                        if (trip != null) trySend(Resource.Success(trip))
                        else trySend(Resource.Error("Trip not found"))
                    }
                    override fun onCancelled(error: DatabaseError) {
                        trySend(Resource.Error(error.message))
                    }
                }
                ref.addValueEventListener(listener)
                awaitClose { ref.removeEventListener(listener) }
            }.collect { _tripState.value = it }
        }
    }

    fun cancelTrip(tripId: String) {
        viewModelScope.launch {
            _cancelState.value = Resource.Loading as Resource<Unit>
            try {
                database.getReference("trips/$tripId/status").setValue(TripStatus.CANCELLED).await()
                _cancelState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _cancelState.value = Resource.Error(e.message ?: "Failed to cancel")
            }
        }
    }

    fun markCashPaid(tripId: String) {
        viewModelScope.launch {
            try {
                database.getReference("trips/$tripId/cashPaid").setValue(true).await()
            } catch (_: Exception) {}
        }
    }

    fun completeTrip(tripId: String) {
        viewModelScope.launch {
            _completeState.value = Resource.Loading as Resource<Unit>
            try {
                database.reference.updateChildren(
                    mapOf(
                        "trips/$tripId/status"      to TripStatus.COMPLETED,
                        "trips/$tripId/completedAt" to System.currentTimeMillis()
                    )
                ).await()
                _completeState.value = Resource.Success(Unit)
            } catch (e: Exception) {
                _completeState.value = Resource.Error(e.message ?: "Failed to complete trip")
            }
        }
    }

    fun resetCancelState()   { _cancelState.value   = null }
    fun resetCompleteState() { _completeState.value  = null }
}