package com.taxiapp.data.model

import com.google.firebase.database.IgnoreExtraProperties

data class RideOption(
    val id: String,
    val name: String,
    val subtitle: String,
    val price: Double,
    val originalPrice: Double? = null,
    val etaMinutes: Int,
    val iconRes: Int
)

data class PaymentMethod(
    val id: String,
    val label: String,
    val type: String,
    val brand: String
)

data class FareBreakdown(
    val baseFare: Double,
    val distanceFare: Double,
    val timeFare: Double,
    val tolls: Double,
    val total: Double,
    val distanceMiles: Double,
    val durationMinutes: Int
)

@IgnoreExtraProperties
data class Trip(
    val tripId: String = "",
    val passengerId: String = "",
    val passengerName: String = "",
    val pickupAddress: String = "",
    val dropoffAddress: String = "",
    val pickupLat: Double = 0.0,
    val pickupLng: Double = 0.0,
    val dropoffLat: Double = 0.0,
    val dropoffLng: Double = 0.0,
    val rideType: String = "",
    val price: Double = 0.0,
    val baseFare: Double = 0.0,
    val distanceFare: Double = 0.0,
    val timeFare: Double = 0.0,
    val tolls: Double = 0.0,
    val distanceMiles: Double = 0.0,
    val durationMinutes: Int = 0,
    val paymentMethod: String = "",
    val status: String = TripStatus.SEARCHING,
    val createdAt: Long = 0L,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val driverId: String = "",
    val driverName: String = "",
    val driverRating: Double = 0.0,
    val driverVehicle: String = "",
    val driverPlate: String = "",
    val driverPhotoUrl: String = "",
    val driverLat: Double = 0.0,
    val driverLng: Double = 0.0,
    val cashPaid: Boolean = false
)

object TripStatus {
    const val SEARCHING = "searching"
    const val ACCEPTED  = "accepted"
    const val ARRIVED   = "arrived"
    const val ONGOING   = "ongoing"
    const val COMPLETED = "completed"
    const val CANCELLED = "cancelled"
}