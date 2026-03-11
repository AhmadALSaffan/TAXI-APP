package com.taxiapp.ui.ride

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.bumptech.glide.Glide
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.R
import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.databinding.ActivityTripTrackingBinding
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TripTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_TRIP_ID = "trip_id"
    }

    private lateinit var binding: ActivityTripTrackingBinding
    private val viewModel: TripTrackingViewModel by viewModels()
    private val routeViewModel: RouteViewModel    by viewModels()

    private var googleMap: GoogleMap? = null
    private var currentTrip: Trip?   = null
    private var routeFetched         = false

    private val apiKey by lazy { getString(R.string.google_maps_key) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: run { finish(); return }

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setupBottomSheet()
        setupClickListeners(tripId)
        observeViewModel(tripId)
        hideSystemBars()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (_: Exception) {}
        map.uiSettings.apply {
            isZoomControlsEnabled     = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled       = false
        }
        currentTrip?.let { trip ->
            placeMarkers(map, trip)
            val current = routeViewModel.routePoints.value
            if (current is Resource.Success) drawRoadPolyline(map, current.data)
        }
    }

    private fun setupBottomSheet() {
        val behavior = BottomSheetBehavior.from(binding.bottomSheet)
        behavior.apply {
            peekHeight      = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            state           = BottomSheetBehavior.STATE_COLLAPSED
            isHideable      = false
            isFitToContents = false
        }
    }

    private fun setupClickListeners(tripId: String) {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnCall.setOnClickListener {
            Snackbar.make(binding.root, "Calling driver...", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnMessage.setOnClickListener {
            Snackbar.make(binding.root, "Messaging driver...", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnCancel.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle("Cancel Ride?")
                .setMessage("Are you sure you want to cancel this ride?")
                .setNegativeButton("No") { d, _ -> d.dismiss() }
                .setPositiveButton("Yes, Cancel") { _, _ -> viewModel.cancelTrip(tripId) }
                .show()
        }
        binding.btnDownloadInvoice.setOnClickListener {
            Snackbar.make(binding.root, "Invoice downloaded", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnBookSimilar.setOnClickListener {
            startActivity(Intent(this, HomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
            })
        }
    }

    private fun observeViewModel(tripId: String) {
        viewModel.observeTrip(tripId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    routeViewModel.routePoints.collect { state ->
                        if (state is Resource.Success) {
                            val map = googleMap ?: return@collect
                            currentTrip?.let { placeMarkers(map, it) }
                            drawRoadPolyline(map, state.data)
                        }
                    }
                }

                launch {
                    viewModel.tripState.collect { state ->
                        when (state) {
                            is Resource.Loading -> showLoading(true)
                            is Resource.Success -> {
                                showLoading(false)
                                currentTrip = state.data
                                bindTrip(state.data)
                                googleMap?.let { placeMarkers(it, state.data) }

                                if (!routeFetched &&
                                    state.data.pickupLat != 0.0 &&
                                    state.data.dropoffLat != 0.0
                                ) {
                                    routeFetched = true
                                    routeViewModel.fetchRoute(
                                        state.data.pickupLat,
                                        state.data.pickupLng,
                                        state.data.dropoffLat,
                                        state.data.dropoffLng,
                                        apiKey
                                    )
                                }
                            }
                            is Resource.Error -> {
                                showLoading(false)
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.cancelState.collect { state ->
                        when (state) {
                            is Resource.Success -> {
                                viewModel.resetCancelState()
                                startActivity(Intent(this@TripTrackingActivity, HomeActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                                })
                            }
                            is Resource.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetCancelState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun placeMarkers(map: GoogleMap, trip: Trip) {
        if (trip.pickupLat == 0.0 && trip.dropoffLat == 0.0) return
        map.clear()
        val pickup  = LatLng(trip.pickupLat,  trip.pickupLng)
        val dropoff = LatLng(trip.dropoffLat, trip.dropoffLng)
        map.addMarker(MarkerOptions().position(pickup).title("Pickup"))
        map.addMarker(MarkerOptions().position(dropoff).title("Dropoff"))
        val bounds = LatLngBounds.Builder().include(pickup).include(dropoff).build()
        map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 160))
    }

    private fun drawRoadPolyline(map: GoogleMap, points: List<LatLng>) {
        map.addPolyline(
            PolylineOptions()
                .addAll(points)
                .width(8f)
                .color(android.graphics.Color.parseColor("#2233FF"))
                .geodesic(false)
        )
    }

    private fun bindTrip(trip: Trip) {
        binding.tvPickup.text  = trip.pickupAddress
        binding.tvDropoff.text = trip.dropoffAddress

        val fmt = SimpleDateFormat("hh:mm a", Locale.getDefault())
        if (trip.startedAt > 0)   binding.tvPickupTime.text  = "Picked up at ${fmt.format(Date(trip.startedAt))}"
        if (trip.completedAt > 0) binding.tvDropoffTime.text = "Dropped off at ${fmt.format(Date(trip.completedAt))}"

        binding.tvTripStatus.text = statusLabel(trip.status)

        val isCompleted = trip.status == TripStatus.COMPLETED
        val isSearching = trip.status == TripStatus.SEARCHING

        binding.btnCancel.visibility          = if (isCompleted) View.GONE  else View.VISIBLE
        binding.btnDownloadInvoice.visibility = if (isCompleted) View.VISIBLE else View.GONE
        binding.btnBookSimilar.visibility     = if (isCompleted) View.VISIBLE else View.GONE
        binding.btnCall.visibility            = if (isSearching) View.GONE  else View.VISIBLE
        binding.btnMessage.visibility         = if (isSearching) View.GONE  else View.VISIBLE

        if (trip.driverName.isNotBlank()) {
            binding.tvDriverName.text   = trip.driverName
            binding.tvVehicleInfo.text  = "${trip.driverVehicle} · ${trip.driverPlate}"
            binding.tvDriverRating.text = "${trip.driverRating} ★"
            binding.tvDriverRating.visibility = View.VISIBLE
            binding.tvEtaBadge.text     = "${trip.durationMinutes} min away"
            if (trip.driverPhotoUrl.isNotBlank()) {
                Glide.with(this).load(trip.driverPhotoUrl).circleCrop()
                    .placeholder(R.drawable.ic_user_avatar_placeholder)
                    .into(binding.imgDriverPhoto)
            }
        }

        if (trip.distanceMiles > 0) {
            binding.tvDistance.text = "${trip.distanceMiles} mi"
            binding.tvDuration.text = "${trip.durationMinutes} min"
        }

        bindFareBreakdown(trip)
    }

    private fun bindFareBreakdown(trip: Trip) {
        binding.layoutFareRows.removeAllViews()
        addFareRow("Base Fare",                                         "$${"%.2f".format(trip.baseFare)}")
        addFareRow("Distance (${trip.distanceMiles} mi × \$1.50)",     "$${"%.2f".format(trip.distanceFare)}")
        addFareRow("Time (${trip.durationMinutes} min × \$0.25)",      "$${"%.2f".format(trip.timeFare)}")
        if (trip.tolls > 0) addFareRow("Tolls & Fees",                 "$${"%.2f".format(trip.tolls)}")
        binding.tvTotalPrice.text = "$${"%.2f".format(trip.price)}"
        binding.tvChargedTo.text  = "CHARGED TO ···· ${trip.paymentMethod.takeLast(4).ifBlank { "4242" }}"
    }

    private fun addFareRow(label: String, amount: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_fare_row, binding.layoutFareRows, false)
        row.findViewById<TextView>(R.id.tvFareLabel).text  = label
        row.findViewById<TextView>(R.id.tvFareAmount).text = amount
        binding.layoutFareRows.addView(row)
    }

    private fun statusLabel(status: String) = when (status) {
        TripStatus.SEARCHING -> "SEARCHING FOR DRIVER"
        TripStatus.ACCEPTED  -> "DRIVER ACCEPTED"
        TripStatus.ARRIVED   -> "DRIVER ARRIVED"
        TripStatus.ONGOING   -> "ON THE WAY"
        TripStatus.COMPLETED -> "TRIP COMPLETED"
        TripStatus.CANCELLED -> "CANCELLED"
        else                 -> status.uppercase()
    }

    private fun showLoading(show: Boolean) {
        if (show) binding.tvDriverName.text = "Finding driver..."
    }

    override fun onResume()    { super.onResume();    binding.mapView.onResume() }
    override fun onPause()     { super.onPause();     binding.mapView.onPause() }
    override fun onDestroy()   { super.onDestroy();   binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        binding.mapView.onSaveInstanceState(out)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}