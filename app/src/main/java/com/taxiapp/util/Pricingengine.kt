package com.taxiapp.util

import com.taxiapp.data.model.FareBreakdown
import kotlin.math.*

object PricingEngine {

    private data class RateTier(
        val baseFare: Double,
        val perMile: Double,
        val perMinute: Double,
        val minimumFare: Double
    )

    private val rates = mapOf(
        "economy" to RateTier(baseFare = 2.50, perMile = 1.50, perMinute = 0.25, minimumFare = 5.00),
        "premium" to RateTier(baseFare = 5.00, perMile = 2.75, perMinute = 0.45, minimumFare = 10.00),
        "xl"      to RateTier(baseFare = 4.00, perMile = 2.25, perMinute = 0.35, minimumFare = 8.00)
    )

    fun calculate(
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double,
        rideType: String
    ): FareBreakdown {
        val distanceKm    = haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng)
        val distanceMiles = distanceKm * 0.621371
        val durationMin   = estimateDurationMinutes(distanceKm)
        return calculateFromDistanceAndTime(distanceMiles, durationMin, rideType)
    }

    fun calculateFromDistanceAndTime(
        distanceMiles: Double,
        durationMinutes: Int,
        rideType: String
    ): FareBreakdown {
        val tier         = rates[rideType] ?: rates["economy"]!!
        val baseFare     = tier.baseFare
        val distanceFare = distanceMiles * tier.perMile
        val timeFare     = durationMinutes * tier.perMinute
        val tolls        = if (distanceMiles > 10.0) 1.25 else 0.0
        val subtotal     = baseFare + distanceFare + timeFare + tolls
        val total        = maxOf(subtotal, tier.minimumFare)

        return FareBreakdown(
            baseFare        = r2(baseFare),
            distanceFare    = r2(distanceFare),
            timeFare        = r2(timeFare),
            tolls           = r2(tolls),
            total           = r2(total),
            distanceMiles   = r2(distanceMiles),
            durationMinutes = durationMinutes
        )
    }

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r    = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        return r * 2 * atan2(sqrt(a), sqrt(1 - a))
    }

    fun estimateDurationMinutes(distanceKm: Double): Int =
        ((distanceKm / 30.0) * 60).toInt().coerceAtLeast(1)

    private fun r2(v: Double) = Math.round(v * 100) / 100.0
}