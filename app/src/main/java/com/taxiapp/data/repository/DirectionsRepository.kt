package com.taxiapp.data.repository

import com.taxiapp.util.Resource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DirectionsRepository @Inject constructor() {

    suspend fun getRoutePolyline(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double,
        apiKey: String
    ): Resource<List<Pair<Double, Double>>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    "&mode=driving" +
                    "&key=$apiKey"

            val response = URL(url).readText()
            val json     = JSONObject(response)
            val status   = json.getString("status")

            if (status != "OK") return@withContext Resource.Error("Directions API: $status")

            val encodedPolyline = json
                .getJSONArray("routes")
                .getJSONObject(0)
                .getJSONObject("overview_polyline")
                .getString("points")

            Resource.Success(decodePolyline(encodedPolyline))
        } catch (e: Exception) {
            Resource.Error(e.message ?: "Failed to fetch route")
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
}