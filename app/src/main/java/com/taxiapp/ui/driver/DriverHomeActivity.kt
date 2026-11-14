package com.taxiapp.ui.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.taxiapp.ui.onboarding.OnboardingActivity
import com.taxiapp.ui.ride.TripTrackingActivity
import com.taxiapp.ui.theme.TaxiTheme
import dagger.hilt.android.AndroidEntryPoint

/** Driver home: go online, see nearby requests, accept one. */
@AndroidEntryPoint
class DriverHomeActivity : ComponentActivity() {

    private val viewModel: DriverViewModel by viewModels()
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { viewModel.onLocation(it.latitude, it.longitude) }
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) startLocationUpdates() else viewModel.onLocationDenied()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.openTripId) {
                    state.openTripId?.let { tripId ->
                        viewModel.onTripOpened()
                        startActivity(
                            Intent(this@DriverHomeActivity, TripTrackingActivity::class.java).apply {
                                putExtra(TripTrackingActivity.EXTRA_TRIP_ID, tripId)
                                putExtra(TripTrackingActivity.EXTRA_IS_DRIVER, true)
                            },
                        )
                    }
                }
                LaunchedEffect(state.signedOut) {
                    if (state.signedOut) {
                        startActivity(
                            Intent(this@DriverHomeActivity, OnboardingActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                    }
                }

                DriverHomeScreen(
                    state = state,
                    onSetOnline = viewModel::setOnline,
                    onAccept = viewModel::accept,
                    onResumeTrip = viewModel::resumeActiveTrip,
                    onRefreshLocation = ::startLocationUpdates,
                    onRequestLocation = ::requestLocation,
                    onSignOut = viewModel::signOut,
                    onMessageShown = viewModel::onMessageShown,
                )
            }
        }

        requestLocation()
    }

    override fun onResume() {
        super.onResume()
        // Location updates stop in onPause; resume them whenever the screen is visible again.
        if (hasLocationPermission()) startLocationUpdates()
    }

    override fun onPause() {
        super.onPause()
        locationClient.removeLocationUpdates(locationCallback)
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) {
            startLocationUpdates()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!hasLocationPermission()) return
        locationClient.lastLocation.addOnSuccessListener { it?.let { loc -> viewModel.onLocation(loc.latitude, loc.longitude) } }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .build()
        locationClient.removeLocationUpdates(locationCallback)
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
    }
}
