package com.taxiapp.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.taxiapp.R
import com.taxiapp.data.model.Trip
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.ui.onboarding.OnboardingActivity
import com.taxiapp.ui.ride.RideSelectionActivity
import com.taxiapp.ui.ride.TripTrackingActivity
import com.taxiapp.ui.search.DestinationSearchActivity
import com.taxiapp.ui.theme.TaxiTheme
import dagger.hilt.android.AndroidEntryPoint

/** Rider home: map with "where to", ride history and account. */
@AndroidEntryPoint
class HomeActivity : ComponentActivity() {

    private val viewModel: HomeViewModel by viewModels()
    private val locationClient by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) fetchLocation() else viewModel.onLocationDenied()
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val address = data.getStringExtra(DestinationSearchActivity.EXTRA_ADDRESS) ?: return@registerForActivityResult
            openRideSelection(
                address,
                data.getDoubleExtra(DestinationSearchActivity.EXTRA_LAT, 0.0),
                data.getDoubleExtra(DestinationSearchActivity.EXTRA_LNG, 0.0),
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()

                LaunchedEffect(state.signedOut) {
                    if (state.signedOut) {
                        startActivity(
                            Intent(this@HomeActivity, OnboardingActivity::class.java)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
                        )
                    }
                }

                HomeScreen(
                    state = state,
                    onTab = viewModel::selectTab,
                    onSearch = ::openSearch,
                    onRecentPlace = ::openRecentPlace,
                    onPickupMoved = viewModel::onPickupMoved,
                    onPickupDragStarted = viewModel::onPickupDragStarted,
                    onRefreshLocation = ::fetchLocation,
                    onRequestLocation = ::requestLocation,
                    onOpenTrip = ::openTrip,
                    onSignOut = viewModel::signOut,
                )
            }
        }

        requestLocation()
    }

    override fun onResume() {
        super.onResume()
        // Pick up trips booked or finished while we were away.
        viewModel.refreshRides()
    }

    private fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun requestLocation() {
        if (hasLocationPermission()) {
            fetchLocation()
        } else {
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLocation() {
        if (!hasLocationPermission()) return
        // Show the last fix straight away, then refine with a fresh one.
        locationClient.lastLocation.addOnSuccessListener { it?.let { loc -> viewModel.onLocation(loc.latitude, loc.longitude) } }
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { it?.let { loc -> viewModel.onLocation(loc.latitude, loc.longitude) } }
    }

    private fun openSearch(prefill: String?) {
        searchLauncher.launch(
            Intent(this, DestinationSearchActivity::class.java).apply {
                if (!prefill.isNullOrBlank()) putExtra(DestinationSearchActivity.EXTRA_PREFILL, prefill)
                val s = viewModel.state.value
                s.pickupAddress?.let { putExtra(DestinationSearchActivity.EXTRA_PICKUP, it) }
                (s.pickup ?: s.myLocation)?.let {
                    putExtra(DestinationSearchActivity.EXTRA_PICKUP_LAT, it.latitude)
                    putExtra(DestinationSearchActivity.EXTRA_PICKUP_LNG, it.longitude)
                }
            },
        )
    }

    private fun openRecentPlace(place: RecentDestination) {
        if (place.lat == 0.0 && place.lng == 0.0) openSearch(place.address)
        else openRideSelection(place.address, place.lat, place.lng)
    }

    private fun openRideSelection(address: String, lat: Double, lng: Double) {
        val state = viewModel.state.value
        val me = state.pickup ?: state.myLocation
        if (me == null) {
            requestLocation()
            return
        }
        startActivity(
            Intent(this, RideSelectionActivity::class.java).apply {
                putExtra(RideSelectionActivity.EXTRA_PICKUP_ADDRESS, state.pickupAddress ?: getString(R.string.home_current_location))
                putExtra(RideSelectionActivity.EXTRA_DROPOFF_ADDRESS, address)
                putExtra(RideSelectionActivity.EXTRA_PICKUP_LAT, me.latitude)
                putExtra(RideSelectionActivity.EXTRA_PICKUP_LNG, me.longitude)
                putExtra(RideSelectionActivity.EXTRA_DROPOFF_LAT, lat)
                putExtra(RideSelectionActivity.EXTRA_DROPOFF_LNG, lng)
            },
        )
    }

    private fun openTrip(trip: Trip) {
        startActivity(
            Intent(this, TripTrackingActivity::class.java).apply {
                putExtra(TripTrackingActivity.EXTRA_TRIP_ID, trip.tripId)
                putExtra(TripTrackingActivity.EXTRA_IS_DRIVER, false)
            },
        )
    }
}
