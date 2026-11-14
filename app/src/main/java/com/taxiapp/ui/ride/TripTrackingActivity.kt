package com.taxiapp.ui.ride

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import com.taxiapp.R
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.ui.theme.TaxiTheme
import com.taxiapp.util.InvoiceGenerator
import dagger.hilt.android.AndroidEntryPoint

/** A trip from booking to receipt, for the rider or the driver. */
@AndroidEntryPoint
class TripTrackingActivity : ComponentActivity() {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
        const val EXTRA_IS_DRIVER = "is_driver"
    }

    private val viewModel: TripTrackingViewModel by viewModels()
    private val isDriver by lazy { intent.getBooleanExtra(EXTRA_IS_DRIVER, false) }
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }
    private var sharingLocation = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            result.lastLocation?.let { viewModel.onDriverLocation(it.latitude, it.longitude) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (intent.getStringExtra(EXTRA_TRIP_ID).isNullOrBlank()) {
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = leave()
        })

        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.closed) { if (state.closed) leave() }
                // Share the driver's position only while they're on an active trip.
                LaunchedEffect(state.isActive) { updateLocationSharing(state.isActive) }

                TripTrackingScreen(
                    state = state,
                    onBack = ::leave,
                    onCancel = viewModel::cancel,
                    onArrived = viewModel::markArrived,
                    onStart = viewModel::startTrip,
                    onComplete = viewModel::completeTrip,
                    onNavigate = ::navigate,
                    onDownloadReceipt = ::saveReceipt,
                    onDone = ::leave,
                    onMessageShown = viewModel::onMessageShown,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateLocationSharing(viewModel.state.value.isActive)
    }

    override fun onStop() {
        super.onStop()
        stopLocationSharing()
    }

    @SuppressLint("MissingPermission")
    private fun updateLocationSharing(active: Boolean) {
        if (!isDriver || !active) {
            stopLocationSharing()
            return
        }
        if (sharingLocation || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) return
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 4_000L)
            .setMinUpdateDistanceMeters(8f)
            .build()
        locationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
        sharingLocation = true
    }

    private fun stopLocationSharing() {
        if (!sharingLocation) return
        locationClient.removeLocationUpdates(locationCallback)
        sharingLocation = false
    }

    /** Back to the home screen underneath, or start it if we were opened from a notification. */
    private fun leave() {
        if (isTaskRoot) {
            startActivity(Intent(this, if (isDriver) DriverHomeActivity::class.java else HomeActivity::class.java))
        }
        finish()
    }

    private fun navigate(trip: Trip) {
        val toPickup = trip.status == TripStatus.ACCEPTED || trip.status == TripStatus.ARRIVED
        val lat = if (toPickup) trip.pickupLat else trip.dropoffLat
        val lng = if (toPickup) trip.pickupLng else trip.dropoffLng
        val googleMaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lng&mode=d"))
            .setPackage("com.google.android.apps.maps")
        try {
            startActivity(googleMaps)
        } catch (_: ActivityNotFoundException) {
            val any = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng"))
            try {
                startActivity(Intent.createChooser(any, getString(R.string.drive_navigate)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(this, R.string.drive_no_maps, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveReceipt(trip: Trip) {
        InvoiceGenerator.generate(this, trip)
            .onSuccess { uri ->
                Toast.makeText(this, R.string.receipt_saved, Toast.LENGTH_SHORT).show()
                val open = Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try { startActivity(open) } catch (_: ActivityNotFoundException) { /* saved; no PDF viewer */ }
            }
            .onFailure { Toast.makeText(this, R.string.receipt_failed, Toast.LENGTH_SHORT).show() }
    }
}
