package com.taxiapp.data.repository

import android.util.Log
import com.google.firebase.functions.FirebaseFunctions
import com.taxiapp.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Routes come from the `getRoute` Cloud Function so the Directions API key
 * never ships inside the APK.
 */
@Singleton
class DirectionsRepository @Inject constructor(
    private val functions: FirebaseFunctions
) {

    suspend fun getRoutePolyline(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): Resource<List<Pair<Double, Double>>> {
        val google = try {
            val result = functions.getHttpsCallable("getRoute")
                .call(
                    mapOf(
                        "originLat" to originLat,
                        "originLng" to originLng,
                        "destLat"   to destLat,
                        "destLng"   to destLng
                    )
                )
                .await()
            (result.getData() as? Map<*, *>)?.get("points") as? String
        } catch (e: Exception) {
            // Usually: the getRoute function isn't deployed, or MAPS_SERVER_KEY has no billing.
            Log.w(TAG, "getRoute failed; trying the OSRM fallback", e)
            null
        }
        if (!google.isNullOrEmpty()) return Resource.Success(decodePolyline(google))

        val osrm = osrmRoute(originLat, originLng, destLat, destLng)
        return if (osrm.isNullOrEmpty()) Resource.Error("No route found") else Resource.Success(osrm)
    }

    /**
     * TEMPORARY, for testing only, while Google routing isn't set up: the public
     * OSRM demo server (OpenStreetMap roads, no key). It sends the trip's two end
     * points to router.project-osrm.org, whose usage policy allows light use only.
     * Remove this once `getRoute` works.
     */
    private suspend fun osrmRoute(oLat: Double, oLng: Double, dLat: Double, dLng: Double): List<Pair<Double, Double>>? =
        withContext(Dispatchers.IO) {
            try {
                val url = URL(
                    String.format(
                        Locale.US,
                        "https://router.project-osrm.org/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=full&geometries=polyline",
                        oLng, oLat, dLng, dLat,
                    ),
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 8_000
                    setRequestProperty("User-Agent", "TaxiApp-Android")
                }
                try {
                    if (conn.responseCode != 200) return@withContext null
                    val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                    if (json.optString("code") != "Ok") return@withContext null
                    val geometry = json.getJSONArray("routes").getJSONObject(0).getString("geometry")
                    decodePolyline(geometry)
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "OSRM route failed", e)
                null
            }
        }

    private fun decodePolyline(encoded: String): List<Pair<Double, Double>> {
        val points  = mutableListOf<Pair<Double, Double>>()
        var index   = 0
        var lat     = 0
        var lng     = 0

        while (index < encoded.length) {
            var shift  = 0
            var result = 0
            var b: Int
            do {
                b       = encoded[index++].code - 63
                result  = result or ((b and 0x1f) shl shift)
                shift  += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift  = 0
            result = 0
            do {
                b       = encoded[index++].code - 63
                result  = result or ((b and 0x1f) shl shift)
                shift  += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points.add(Pair(lat / 1e5, lng / 1e5))
        }
        return points
    }

    private companion object {
        const val TAG = "DirectionsRepository"
    }
}
