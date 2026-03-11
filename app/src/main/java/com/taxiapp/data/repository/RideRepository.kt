package com.taxiapp.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.util.Resource
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RideRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase
) {

    private val uid get() = auth.currentUser?.uid ?: ""

    suspend fun createTrip(trip: Trip): Resource<String> {
        return try {
            val tripsRef = database.getReference("trips")
            val newTripRef = tripsRef.push()
            val tripId = newTripRef.key ?: return Resource.Error("Failed to generate trip ID")

            val finalTrip = trip.copy(
                tripId    = tripId,
                passengerId = uid,
                status    = TripStatus.SEARCHING,
                createdAt = System.currentTimeMillis()
            )

            newTripRef.setValue(finalTrip).await()

            database.getReference("users/$uid/rides/$tripId").setValue(true).await()

            Resource.Success(tripId)
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to create trip")
        }
    }

    suspend fun getPaymentMethods(): Resource<List<PaymentMethod>> {
        return try {
            val snapshot = database
                .getReference("users/$uid/paymentMethods")
                .get()
                .await()

            val methods = snapshot.children.mapNotNull { child ->
                PaymentMethod(
                    id    = child.key ?: return@mapNotNull null,
                    label = child.child("label").getValue(String::class.java) ?: "",
                    type  = child.child("type").getValue(String::class.java) ?: "card",
                    brand = child.child("brand").getValue(String::class.java) ?: "" // ← NEW
                )
            }

            if (methods.isEmpty()) Resource.Success(defaultPaymentMethods())
            else Resource.Success(methods)

        } catch (e: Exception) {
            Resource.Success(defaultPaymentMethods())
        }
    }

    private fun defaultPaymentMethods() = listOf(
        PaymentMethod("cash",  "Cash",         "cash"),
        PaymentMethod("wallet","App Wallet",   "wallet")
    )
}