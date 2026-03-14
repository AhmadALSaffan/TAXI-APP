package com.taxiapp

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@HiltAndroidApp
class TaxiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        Places.initialize(applicationContext, getString(R.string.google_maps_key))

        CoroutineScope(Dispatchers.IO).launch {
            saveTokenIfLoggedIn()
        }
    }

    private suspend fun saveTokenIfLoggedIn() {
        try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val token = FirebaseMessaging.getInstance().token.await()
            FirebaseDatabase.getInstance()
                .getReference("users/$uid/fcmToken")
                .setValue(token)
                .await()
        } catch (_: Exception) {}
    }
}