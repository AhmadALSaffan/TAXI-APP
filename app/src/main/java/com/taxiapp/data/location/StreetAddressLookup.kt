package com.taxiapp.data.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.taxiapp.data.model.PlaceResult
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** Turns coordinates into a short street address ("418 King St") using the device geocoder. */
@Singleton
class StreetAddressLookup @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    suspend fun lookup(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(5_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
                }
            }
        } ?: return null

        val street = listOfNotNull(address.subThoroughfare, address.thoroughfare).joinToString(" ")
        return street.ifBlank { address.getAddressLine(0)?.substringBefore(",") }?.takeIf { it.isNotBlank() }
    }

    /**
     * Finds places by name with the device geocoder. Free and needs no API key, so it
     * keeps search working when Google Places isn't available. Results near [near]
     * come first.
     */
    suspend fun search(query: String, near: Pair<Double, Double>?, limit: Int = 6): List<PlaceResult> {
        if (!Geocoder.isPresent()) return emptyList()
        val geocoder = Geocoder(context, Locale.getDefault())
        suspend fun findUnsafe(box: DoubleArray?): List<Address> = withTimeoutOrNull(6_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    val listener = object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.toList())
                        override fun onError(errorMessage: String?) = cont.resume(emptyList())
                    }
                    if (box != null) geocoder.getFromLocationName(query, limit, box[0], box[1], box[2], box[3], listener)
                    else geocoder.getFromLocationName(query, limit, listener)
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching {
                        if (box != null) geocoder.getFromLocationName(query, limit, box[0], box[1], box[2], box[3])
                        else geocoder.getFromLocationName(query, limit)
                    }.getOrNull().orEmpty()
                }
            }
        }.orEmpty()
        suspend fun find(box: DoubleArray?): List<Address> = try {
            findUnsafe(box)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            emptyList() // bad input or a geocoder service problem: just no results
        }

        val nearby = near?.let { (lat, lng) -> find(doubleArrayOf(lat - 0.7, lng - 0.7, lat + 0.7, lng + 0.7)) }.orEmpty()
        val found = nearby.ifEmpty { find(null) }
        return found.filter { it.hasLatitude() && it.hasLongitude() }.mapIndexed { i, a ->
            val line = a.getAddressLine(0).orEmpty()
            val title = a.featureName?.takeIf { it.isNotBlank() && it != a.subThoroughfare }
                ?: listOfNotNull(a.subThoroughfare, a.thoroughfare).joinToString(" ").ifBlank { line.substringBefore(",") }
            val rest = line.removePrefix(title).trimStart(',', ' ').ifBlank { listOfNotNull(a.locality, a.countryName).joinToString(", ") }
            PlaceResult(placeId = "geo:$i:${a.latitude},${a.longitude}", primaryText = title, secondaryText = rest, lat = a.latitude, lng = a.longitude)
        }.distinctBy { it.primaryText + it.secondaryText }
    }

    /** Full one-line address for a spot, e.g. a destination picked on the map. */
    suspend fun describe(lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val geocoder = Geocoder(context, Locale.getDefault())
        val address = withTimeoutOrNull(5_000) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(lat, lng, 1, object : Geocoder.GeocodeListener {
                        override fun onGeocode(addresses: MutableList<Address>) = cont.resume(addresses.firstOrNull())
                        override fun onError(errorMessage: String?) = cont.resume(null)
                    })
                }
            } else {
                withContext(Dispatchers.IO) {
                    @Suppress("DEPRECATION")
                    runCatching { geocoder.getFromLocation(lat, lng, 1)?.firstOrNull() }.getOrNull()
                }
            }
        }
        return address?.getAddressLine(0)?.takeIf { it.isNotBlank() }
    }
}
