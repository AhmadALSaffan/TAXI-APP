package com.taxiapp.data.repository

import android.util.Base64
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.taxiapp.data.model.Trip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FcmRepository @Inject constructor(
    private val database: FirebaseDatabase
) {

    companion object {
        private const val TAG = "FcmRepository"
    }

    suspend fun saveToken() {
        withContext(Dispatchers.IO) {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                val uid   = FirebaseAuth.getInstance().currentUser?.uid
                if (uid == null) {
                    Log.w(TAG, "saveToken: no authenticated user")
                    return@withContext
                }
                database.getReference("users/$uid/fcmToken").setValue(token).await()
                Log.d(TAG, "saveToken: saved for uid=$uid")
            } catch (e: Exception) {
                Log.e(TAG, "saveToken failed: ${e.message}", e)
            }
        }
    }

    suspend fun sendDriverAcceptedNotification(trip: Trip, driverName: String) {
        withContext(Dispatchers.IO) {
            try {
                val configSnap = database.getReference("config").get().await()

                val clientEmail = configSnap.child("clientEmail").getValue(String::class.java)
                val privateKey  = configSnap.child("privateKey").getValue(String::class.java)
                val projectId   = configSnap.child("projectId").getValue(String::class.java)

                if (clientEmail.isNullOrBlank() || privateKey.isNullOrBlank() || projectId.isNullOrBlank()) {
                    Log.e(TAG, "sendNotification: missing config — set clientEmail, privateKey, projectId in /config")
                    return@withContext
                }

                if (trip.passengerId.isBlank()) {
                    Log.e(TAG, "sendNotification: passengerId is blank")
                    return@withContext
                }

                val tokenSnap = database.getReference("users/${trip.passengerId}/fcmToken").get().await()
                val fcmToken  = tokenSnap.getValue(String::class.java)
                if (fcmToken.isNullOrBlank()) {
                    Log.e(TAG, "sendNotification: passenger FCM token missing")
                    return@withContext
                }

                Log.d(TAG, "sendNotification: generating OAuth2 token...")
                val accessToken = getOAuthToken(clientEmail, privateKey)
                if (accessToken.isNullOrBlank()) {
                    Log.e(TAG, "sendNotification: failed to get OAuth2 access token")
                    return@withContext
                }

                Log.d(TAG, "sendNotification: sending via FCM v1 API...")

                val payload = JSONObject().apply {
                    put("message", JSONObject().apply {
                        put("token", fcmToken)
                        put("notification", JSONObject().apply {
                            put("title", "Driver on the way!")
                            put("body", "$driverName accepted your trip and is heading to you.")
                        })
                        put("data", JSONObject().apply {
                            put("trip_id", trip.tripId)
                            put("type", "DRIVER_ACCEPTED")
                        })
                        put("android", JSONObject().apply {
                            put("priority", "high")
                        })
                    })
                }

                val url  = URL("https://fcm.googleapis.com/v1/projects/$projectId/messages:send")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput      = true
                conn.doInput       = true
                conn.setRequestProperty("Content-Type",  "application/json")
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
                conn.connectTimeout = 10_000
                conn.readTimeout    = 10_000

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }

                val responseCode = conn.responseCode
                val responseBody = try {
                    BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
                } catch (_: Exception) {
                    BufferedReader(InputStreamReader(conn.errorStream)).use { it.readText() }
                }

                Log.d(TAG, "sendNotification: HTTP $responseCode — $responseBody")
                conn.disconnect()

            } catch (e: Exception) {
                Log.e(TAG, "sendNotification failed: ${e.message}", e)
            }
        }
    }

    private fun getOAuthToken(clientEmail: String, privateKeyPem: String): String? {
        return try {
            val now     = System.currentTimeMillis() / 1000
            val expiry  = now + 3600

            val header  = Base64.encodeToString(
                """{"alg":"RS256","typ":"JWT"}""".toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            val claims  = Base64.encodeToString(
                """{"iss":"$clientEmail","scope":"https://www.googleapis.com/auth/firebase.messaging","aud":"https://oauth2.googleapis.com/token","exp":$expiry,"iat":$now}""".toByteArray(),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val signingInput = "$header.$claims"

            val cleanKey = privateKeyPem
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("\\n", "")
                .replace("\n", "")
                .trim()

            val keyBytes   = Base64.decode(cleanKey, Base64.DEFAULT)
            val keySpec    = PKCS8EncodedKeySpec(keyBytes)
            val privateKey = KeyFactory.getInstance("RSA").generatePrivate(keySpec)

            val signer = Signature.getInstance("SHA256withRSA")
            signer.initSign(privateKey)
            signer.update(signingInput.toByteArray())
            val signatureBytes = signer.sign()

            val signature = Base64.encodeToString(signatureBytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
            val jwt       = "$signingInput.$signature"

            val tokenUrl  = URL("https://oauth2.googleapis.com/token")
            val tokenConn = tokenUrl.openConnection() as HttpURLConnection
            tokenConn.requestMethod = "POST"
            tokenConn.doOutput      = true
            tokenConn.doInput       = true
            tokenConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            tokenConn.connectTimeout = 10_000
            tokenConn.readTimeout    = 10_000

            val body = "grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=$jwt"
            OutputStreamWriter(tokenConn.outputStream).use { it.write(body) }

            val response = BufferedReader(InputStreamReader(tokenConn.inputStream)).use { it.readText() }
            tokenConn.disconnect()

            JSONObject(response).getString("access_token")
        } catch (e: Exception) {
            Log.e(TAG, "getOAuthToken failed: ${e.message}", e)
            null
        }
    }
}