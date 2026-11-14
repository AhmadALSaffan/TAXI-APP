package com.taxiapp.util

import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.Trip
import java.util.Locale

/** All prices are in Belarusian roubles. */
const val CURRENCY = "BYN"

/** "12.60 BYN" */
fun formatMoney(value: Double): String = String.format(Locale.US, "%.2f %s", value, CURRENCY)

/** "9.3" — trips store miles (older records); everything is shown in km. */
fun formatKm(miles: Double): String = String.format(Locale.US, "%.1f", miles / PricingEngine.KM_TO_MILES)

// Extension functions, not properties on the models: Firebase would otherwise
// save them as extra fields, which the database rules reject.
fun Trip.distanceKmText(): String = formatKm(distanceMiles)
fun FareBreakdown.distanceKmText(): String = formatKm(distanceMiles)
