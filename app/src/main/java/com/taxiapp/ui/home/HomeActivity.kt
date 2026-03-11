package com.taxiapp.ui.home

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.R
import com.taxiapp.data.model.User
import com.taxiapp.data.repository.RecentDestination
import com.taxiapp.databinding.ActivityHomeBinding
import com.taxiapp.ui.auth.WelcomeActivity
import com.taxiapp.ui.ride.RideSelectionActivity
import com.taxiapp.ui.search.DestinationSearchActivity
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HomeActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityHomeBinding
    private val viewModel: HomeViewModel by viewModels()

    private var googleMap: GoogleMap? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private var lastKnownLat = 0.0
    private var lastKnownLng = 0.0
    private var lastKnownAddress = "Current Location"

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
                || permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) enableMyLocation()
        else Snackbar.make(
            binding.root,
            "Location permission needed to show your position on the map",
            Snackbar.LENGTH_LONG
        ).show()
    }

    private val searchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data    = result.data ?: return@registerForActivityResult
            val address = data.getStringExtra(DestinationSearchActivity.EXTRA_ADDRESS) ?: return@registerForActivityResult
            val lat     = data.getDoubleExtra(DestinationSearchActivity.EXTRA_LAT, 0.0)
            val lng     = data.getDoubleExtra(DestinationSearchActivity.EXTRA_LNG, 0.0)
            onDestinationSelected(address, lat, lng)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setupMap(savedInstanceState)
        setupBottomSheet()
        setupClickListeners()
        observeViewModel()
        hideSystemBars()
    }

    override fun onResume()  { super.onResume();  binding.mapView.onResume() }
    override fun onPause()   { super.onPause();   binding.mapView.onPause() }
    override fun onDestroy() { super.onDestroy(); binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        binding.mapView.onSaveInstanceState(outState)
    }

    private fun setupMap(savedInstanceState: Bundle?) {
        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        try {
            map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark))
        } catch (_: Exception) {}

        map.uiSettings.apply {
            isZoomControlsEnabled    = false
            isMyLocationButtonEnabled = false
            isCompassEnabled         = false
            isMapToolbarEnabled      = false
        }

        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        val fine   = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
            enableMyLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        try {
            googleMap?.isMyLocationEnabled = true
            fetchAndStoreCurrentLocation()
        } catch (_: SecurityException) {}
    }

    @SuppressLint("MissingPermission")
    private fun fetchAndStoreCurrentLocation() {
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    lastKnownLat = it.latitude
                    lastKnownLng = it.longitude
                    googleMap?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(it.latitude, it.longitude), 15f)
                    )
                }
            }
        } catch (_: SecurityException) {}
    }

    private fun setupBottomSheet() {
        bottomSheetBehavior = BottomSheetBehavior.from(binding.bottomPanel)
        bottomSheetBehavior.apply {
            peekHeight      = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            state           = BottomSheetBehavior.STATE_COLLAPSED
            isHideable      = false
            isFitToContents = true
        }
    }

    private fun setupClickListeners() {
        binding.btnMenu.setOnClickListener {
            Snackbar.make(binding.root, "Menu coming soon", Snackbar.LENGTH_SHORT).show()
        }

        binding.imgUserAvatar.setOnClickListener {
            // TODO: ProfileActivity
        }

        binding.etDestination.setOnClickListener { openDestinationSearch() }
        binding.searchLayout.setOnClickListener { openDestinationSearch() }
        binding.btnSearchGo.setOnClickListener   { openDestinationSearch() }

        binding.btnZoomIn.setOnClickListener  { googleMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomOut()) }

        binding.btnMyLocation.setOnClickListener { fetchAndStoreCurrentLocation() }

        binding.btnShortcutHome.setOnClickListener {
            val address = (viewModel.userState.value as? Resource.Success<User>)?.data?.homeAddress
            if (!address.isNullOrBlank()) openDestinationSearch(address)
            else Snackbar.make(binding.root, "No home address saved", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnShortcutWork.setOnClickListener {
            val address = (viewModel.userState.value as? Resource.Success<User>)?.data?.workAddress
            if (!address.isNullOrBlank()) openDestinationSearch(address)
            else Snackbar.make(binding.root, "No work address saved", Snackbar.LENGTH_SHORT).show()
        }

        binding.btnShortcutRecent.setOnClickListener { openDestinationSearch() }
        binding.btnShortcutMore.setOnClickListener   { openDestinationSearch() }

        binding.btnConfirmDestination.setOnClickListener {
            val destination = viewModel.selectedDestination.value
            if (destination.isNullOrBlank()) {
                Snackbar.make(binding.root, "Please enter a destination", Snackbar.LENGTH_SHORT).show()
            } else {
                openDestinationSearch(destination)
            }
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home    -> true
                R.id.nav_history -> { true }
                R.id.nav_wallet  -> { true }
                R.id.nav_profile -> { true }
                else             -> false
            }
        }
    }

    private fun openDestinationSearch(prefill: String? = null) {
        val intent = Intent(this, DestinationSearchActivity::class.java)
        if (!prefill.isNullOrBlank()) {
            intent.putExtra(DestinationSearchActivity.EXTRA_PREFILL, prefill)
        }
        searchLauncher.launch(intent)
    }

    private fun onDestinationSelected(address: String, lat: Double, lng: Double) {
        viewModel.setSelectedDestination(address)
        binding.etDestination.setText(address)

        val intent = Intent(this, RideSelectionActivity::class.java).apply {
            putExtra(RideSelectionActivity.EXTRA_PICKUP_ADDRESS,  lastKnownAddress)
            putExtra(RideSelectionActivity.EXTRA_DROPOFF_ADDRESS, address)
            putExtra(RideSelectionActivity.EXTRA_PICKUP_LAT,      lastKnownLat)
            putExtra(RideSelectionActivity.EXTRA_PICKUP_LNG,      lastKnownLng)
            putExtra(RideSelectionActivity.EXTRA_DROPOFF_LAT,     lat)
            putExtra(RideSelectionActivity.EXTRA_DROPOFF_LNG,     lng)
        }
        startActivity(intent)
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.userState.collect { state ->
                        when (state) {
                            is Resource.Success -> bindUser(state.data)
                            is Resource.Error   -> {
                                if (state.message.contains("not authenticated", ignoreCase = true)) {
                                    navigateToWelcome()
                                }
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.recentDestinations.collect { list ->
                        populateRecentChips(list)
                    }
                }
            }
        }
    }

    private fun bindUser(user: User) {
        if (user.photoUrl.isNotBlank()) {
            Glide.with(this)
                .load(user.photoUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_user_avatar_placeholder)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.imgUserAvatar)
        }
    }

    private fun populateRecentChips(destinations: List<RecentDestination>) {
        // TODO: populate a ChipGroup with recent destinations when added to layout
    }

    private fun navigateToWelcome() {
        startActivity(
            Intent(this, WelcomeActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        )
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}