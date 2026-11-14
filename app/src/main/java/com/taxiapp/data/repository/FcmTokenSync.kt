package com.taxiapp.data.repository

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores this device's FCM token on the signed-in user so the Cloud Functions
 * can send them trip notifications. Call after every sign-in.
 */
@Singleton
class FcmTokenSync @Inject constructor(
    private val auth: FirebaseAuth,
    private val database: FirebaseDatabase,
) {
    suspend fun syncForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val token = FirebaseMessaging.getInstance().token.await()
            database.getReference("users/$uid/fcmToken").setValue(token).await()
        } catch (e: Exception) {
            Log.w("FcmTokenSync", "Could not save FCM token", e)
        }
    }

    /** Call before signing out so this device stops getting the user's trip notifications. */
    suspend fun clearForCurrentUser() {
        val uid = auth.currentUser?.uid ?: return
        try {
            database.getReference("users/$uid/fcmToken").removeValue().await()
        } catch (e: Exception) {
            Log.w("FcmTokenSync", "Could not clear FCM token", e)
        }
    }
}
