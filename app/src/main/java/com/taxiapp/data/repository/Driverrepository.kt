package com.taxiapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.data.model.User
import com.taxiapp.util.PricingEngine
import com.taxiapp.util.Resource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DriverRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {

    fun observeNearbyTrips(driverLat: Double, driverLng: Double, radiusKm: Double = 15.0): Flow<Resource<List<Trip>>> =
        callbackFlow {
            val ref = database.getReference("trips")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nearby = mutableListOf<Trip>()
                    for (child in snapshot.children) {
                        val trip = child.getValue(Trip::class.java) ?: continue
                        if (trip.status != TripStatus.SEARCHING) continue
                        val distKm = PricingEngine.haversineKm(
                            driverLat, driverLng,
                            trip.pickupLat, trip.pickupLng
                        )
                        if (distKm <= radiusKm) nearby.add(trip)
                    }
                    nearby.sortBy { trip ->
                        PricingEngine.haversineKm(driverLat, driverLng, trip.pickupLat, trip.pickupLng)
                    }
                    trySend(Resource.Success(nearby))
                }
                override fun onCancelled(error: DatabaseError) {
                    trySend(Resource.Error(error.message))
                }
            }
            ref.addValueEventListener(listener)
            awaitClose { ref.removeEventListener(listener) }
        }

    suspend fun acceptTrip(trip: Trip, driverLat: Double, driverLng: Double): Resource<String> {
        val uid = auth.currentUser?.uid ?: return Resource.Error("Not authenticated")
        return try {
            val driverSnapshot = database.getReference("users/$uid").get().await()
            val driver = driverSnapshot.getValue(User::class.java)

            val updates = mutableMapOf<String, Any>(
                "trips/${trip.tripId}/status"         to TripStatus.ACCEPTED,
                "trips/${trip.tripId}/driverId"       to uid,
                "trips/${trip.tripId}/driverName"     to (driver?.displayName ?: "Driver"),
                "trips/${trip.tripId}/driverRating"   to (driver?.rating ?: 5.0),
                "trips/${trip.tripId}/driverPhotoUrl" to (driver?.photoUrl ?: ""),
                "trips/${trip.tripId}/driverVehicle"  to (driver?.vehicle ?: "Toyota Camry"),
                "trips/${trip.tripId}/driverPlate"    to (driver?.plate ?: ""),
                "trips/${trip.tripId}/driverLat"      to driverLat,
                "trips/${trip.tripId}/driverLng"      to driverLng
            )
            database.reference.updateChildren(updates).await()
            Resource.Success(trip.tripId)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to accept trip")
        }
    }

    suspend fun updateDriverLocation(lat: Double, lng: Double, activeTripId: String? = null) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val updates = mutableMapOf<String, Any>(
                "drivers/$uid/location/lat"       to lat,
                "drivers/$uid/location/lng"       to lng,
                "drivers/$uid/location/updatedAt" to System.currentTimeMillis()
            )
            if (!activeTripId.isNullOrBlank()) {
                updates["trips/$activeTripId/driverLat"] = lat
                updates["trips/$activeTripId/driverLng"] = lng
            }
            database.reference.updateChildren(updates).await()
        } catch (_: Exception) {}
    }

    suspend fun setDriverAvailability(available: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        try {
            database.getReference("drivers/$uid/available").setValue(available).await()
        } catch (_: Exception) {}
    }
}