package com.taxiapp.util

import com.taxiapp.data.model.FareBreakdown
import kotlin.math.*

/**
 * Fares in Belarusian roubles (BYN), priced per kilometre and minute.
 * functions/pricing.js re-checks every trip with the same rates — change both together.
 */
object PricingEngine {

    private data class RateTier(
        val baseFare: Double,
        val perKm: Double,
        val perMinute: Double,
        val minimumFare: Double
    )

    private val rates = mapOf(
        "economy" to RateTier(baseFare = 2.00, perKm = 0.65, perMinute = 0.15, minimumFare = 4.50),
        "premium" to RateTier(baseFare = 3.00, perKm = 0.90, perMinute = 0.22, minimumFare = 6.50),
        "xl"      to RateTier(baseFare = 3.50, perKm = 1.00, perMinute = 0.25, minimumFare = 7.50)
    )

    fun calculate(
        pickupLat: Double,
        pickupLng: Double,
        dropoffLat: Double,
        dropoffLng: Double,
        rideType: String
    ): FareBreakdown {
        val distanceKm  = haversineKm(pickupLat, pickupLng, dropoffLat, dropoffLng)
        val durationMin = estimateDurationMinutes(distanceKm)
        val tier         = rates[rideType] ?: rates["economy"]!!
        val distanceFare = distanceKm * tier.perKm
        val timeFare     = durationMin * tier.perMinute
        val subtotal     = tier.baseFare + distanceFare + timeFare
        val total        = maxOf(subtotal, tier.minimumFare)

        return FareBreakdown(
            baseFare        = r2(tier.baseFare),
            distanceFare    = r2(distanceFare),
            timeFare        = r2(timeFare),
            tolls           = 0.0,
            total           = r2(total),
            // Stored in miles for compatibility with existing trip records; shown in km.
            distanceMiles   = r2(distanceKm * KM_TO_MILES),
            durationMinutes = durationMin
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

    const val KM_TO_MILES = 0.621371

    private fun r2(v: Double) = Math.round(v * 100) / 100.0
}
