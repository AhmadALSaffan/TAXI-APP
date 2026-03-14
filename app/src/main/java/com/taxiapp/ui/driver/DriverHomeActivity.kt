package com.taxiapp.ui.driver

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.taxiapp.R
import com.taxiapp.data.model.Trip
import com.taxiapp.databinding.ActivityDriverHomeBinding
import com.taxiapp.ui.ride.TripTrackingActivity
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class DriverHomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityDriverHomeBinding
    private val viewModel: DriverViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var tripAdapter: NearbyTripAdapter
    private var locationCallback: LocationCallback? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) enableMyLocation()
        else Snackbar.make(binding.root, "Location permission required", Snackbar.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDriverHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setupBottomSheet()
        setupRecyclerView()
        setupClickListeners()
        observeViewModel()
        hideSystemBars()
        laodImage()
    }

    private fun laodImage() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        FirebaseDatabase.getInstance().getReference("users").child(uid.toString()).get().addOnSuccessListener {
            val image = it.child("photoUrl").value.toString()
            Glide.with(this@DriverHomeActivity).load(image).into(binding.imgDriverAvatar)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (_: Exception) {}
        map.uiSettings.apply {
            isZoomControlsEnabled     = false
            isMyLocationButtonEnabled = false
            isCompassEnabled          = false
            isMapToolbarEnabled       = false
        }
        requestLocationPermission()
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

    private fun setupRecyclerView() {
        tripAdapter = NearbyTripAdapter { trip -> viewModel.acceptTrip(trip) }
        binding.rvNearbyTrips.apply {
            layoutManager = LinearLayoutManager(this@DriverHomeActivity)
            adapter       = tripAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            Snackbar.make(binding.root, "Menu coming soon", Snackbar.LENGTH_SHORT).show()
        }
        binding.btnZoomIn.setOnClickListener  { googleMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
        binding.btnMyLocation.setOnClickListener { moveToMyLocation() }

        binding.switchOnline.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleOnline()
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    viewModel.isOnline.collect { online ->
                        binding.tvOnlineLabel.text = if (online) "Online" else "Offline"
                        binding.tvOnlineLabel.setTextColor(
                            ContextCompat.getColor(
                                this@DriverHomeActivity,
                                if (online) R.color.success_green else R.color.gray_light
                            )
                        )
                        binding.tvStatusBadge.text = if (online) "● SYSTEM ACTIVE" else "○ OFFLINE"
                    }
                }

                launch {
                    viewModel.nearbyTrips.collect { state ->
                        when (state) {
                            is Resource.Loading -> {
                                binding.rvNearbyTrips.visibility    = View.GONE
                                binding.layoutEmptyState.visibility = View.GONE
                            }
                            is Resource.Success -> {
                                val trips = state.data
                                val count = trips.size
                                binding.tvTripCount.text = "$count ${if (count == 1) "trip" else "trips"} within 15 km"
                                if (trips.isEmpty()) {
                                    binding.rvNearbyTrips.visibility    = View.GONE
                                    binding.layoutEmptyState.visibility = View.VISIBLE
                                    val online = viewModel.isOnline.value
                                    binding.tvEmptyTitle.text    = if (online) "No nearby trips" else "You are offline"
                                    binding.tvEmptySubtitle.text = if (online)
                                        "Waiting for trip requests within 15 km"
                                    else
                                        "Go online to start receiving trip requests"
                                } else {
                                    binding.rvNearbyTrips.visibility    = View.VISIBLE
                                    binding.layoutEmptyState.visibility = View.GONE
                                    tripAdapter.submitList(trips)
                                }
                            }
                            is Resource.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.acceptState.collect { state ->
                        when (state) {
                            is Resource.Success -> {
                                val tripId = state.data
                                viewModel.resetAcceptState()
                                startActivity(
                                    Intent(this@DriverHomeActivity, TripTrackingActivity::class.java).apply {
                                        putExtra(TripTrackingActivity.EXTRA_TRIP_ID, tripId)
                                        putExtra(TripTrackingActivity.EXTRA_IS_DRIVER, true)
                                    }
                                )
                            }
                            is Resource.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetAcceptState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun requestLocationPermission() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        try {
            googleMap?.isMyLocationEnabled = true
            startLocationUpdates()
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateIntervalMillis(3_000L)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return
                viewModel.setLocation(loc.latitude, loc.longitude)
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                request, locationCallback!!, Looper.getMainLooper()
            )
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    viewModel.locationReady(it.latitude, it.longitude)
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 14f)
                    )
                }
            }
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    private fun moveToMyLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                loc?.let {
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    )
                }
            }
        } catch (_: SecurityException) {}
    }

    override fun onPause() {
        super.onPause()
        binding.mapView.onPause()
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    override fun onResume() {
        super.onResume()
        binding.mapView.onResume()
    }

    override fun onDestroy()   { super.onDestroy();   binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        binding.mapView.onSaveInstanceState(out)
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}