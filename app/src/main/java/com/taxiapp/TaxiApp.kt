package com.taxiapp

import android.app.Application
import com.google.android.libraries.places.api.Places
import com.google.firebase.database.FirebaseDatabase
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TaxiApp : Application() {

    override fun onCreate() {
        super.onCreate()

        FirebaseDatabase.getInstance().setPersistenceEnabled(true)

        Places.initialize(applicationContext, getString(R.string.google_maps_key))
    }
}