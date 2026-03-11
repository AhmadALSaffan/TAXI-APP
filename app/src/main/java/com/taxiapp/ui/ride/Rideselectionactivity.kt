package com.taxiapp.ui.ride

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.R
import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.data.model.RideOption
import com.taxiapp.databinding.ActivityRideselectionactivityBinding
import com.taxiapp.databinding.DialogPaymentMethodBinding
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RideSelectionActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_PICKUP_ADDRESS  = "pickup_address"
        const val EXTRA_DROPOFF_ADDRESS = "dropoff_address"
        const val EXTRA_PICKUP_LAT      = "pickup_lat"
        const val EXTRA_PICKUP_LNG      = "pickup_lng"
        const val EXTRA_DROPOFF_LAT     = "dropoff_lat"
        const val EXTRA_DROPOFF_LNG     = "dropoff_lng"
    }

    private lateinit var binding: ActivityRideselectionactivityBinding
    private val viewModel: RideSelectionViewModel by viewModels()
    private val routeViewModel: RouteViewModel     by viewModels()

    private var googleMap: GoogleMap? = null
    private lateinit var rideAdapter: RideOptionAdapter

    private val pickupAddress  by lazy { intent.getStringExtra(EXTRA_PICKUP_ADDRESS)  ?: "" }
    private val dropoffAddress by lazy { intent.getStringExtra(EXTRA_DROPOFF_ADDRESS) ?: "" }
    private val pickupLat      by lazy { intent.getDoubleExtra(EXTRA_PICKUP_LAT, 0.0) }
    private val pickupLng      by lazy { intent.getDoubleExtra(EXTRA_PICKUP_LNG, 0.0) }
    private val dropoffLat     by lazy { intent.getDoubleExtra(EXTRA_DROPOFF_LAT, 0.0) }
    private val dropoffLng     by lazy { intent.getDoubleExtra(EXTRA_DROPOFF_LNG, 0.0) }

    private val apiKey by lazy { getString(R.string.google_maps_key) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideselectionactivityBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        binding.tvPickupAddress.text  = pickupAddress
        binding.tvDropoffAddress.text = dropoffAddress

        setupRideList()
        setupClickListeners()
        observeViewModel()
        hideSystemBars()

        viewModel.computePrices(pickupLat, pickupLng, dropoffLat, dropoffLng)

        if (pickupLat != 0.0 && dropoffLat != 0.0) {
            routeViewModel.fetchRoute(pickupLat, pickupLng, dropoffLat, dropoffLng, apiKey)
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
            isMapToolbarEnabled       = false
        }

        placeMarkers(map)

        val current = routeViewModel.routePoints.value
        if (current is Resource.Success) drawRoadPolyline(map, current.data)
    }

    private fun placeMarkers(map: GoogleMap) {
        if (pickupLat == 0.0 && dropoffLat == 0.0) return
        map.addMarker(MarkerOptions().position(LatLng(pickupLat, pickupLng)).title("Pickup"))
        map.addMarker(MarkerOptions().position(LatLng(dropoffLat, dropoffLng)).title("Dropoff"))

        val bounds = LatLngBounds.Builder()
            .include(LatLng(pickupLat, pickupLng))
            .include(LatLng(dropoffLat, dropoffLng))
            .build()
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

    private fun setupRideList() {
        rideAdapter = RideOptionAdapter(viewModel.rideOptions) { selected ->
            viewModel.selectRide(selected)
        }
        binding.rvRideOptions.apply {
            layoutManager = LinearLayoutManager(this@RideSelectionActivity)
            adapter       = rideAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnZoomIn.setOnClickListener  { googleMap?.animateCamera(CameraUpdateFactory.zoomIn()) }
        binding.btnZoomOut.setOnClickListener { googleMap?.animateCamera(CameraUpdateFactory.zoomOut()) }
        binding.btnMyLocation.setOnClickListener {
            if (pickupLat != 0.0)
                googleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(pickupLat, pickupLng), 15f))
        }
        binding.layoutPayment.setOnClickListener { showPaymentDialog() }
        binding.btnConfirmRide.setOnClickListener {
            viewModel.confirmTrip(pickupAddress, dropoffAddress, pickupLat, pickupLng, dropoffLat, dropoffLng)
        }
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    routeViewModel.routePoints.collect { state ->
                        if (state is Resource.Success) {
                            val map = googleMap ?: return@collect
                            map.clear()
                            placeMarkers(map)
                            drawRoadPolyline(map, state.data)
                        }
                    }
                }

                launch {
                    viewModel.fareBreakdowns.collect { breakdowns ->
                        if (breakdowns.isNotEmpty()) {
                            rideAdapter.updatePrices(breakdowns)
                            val fare = breakdowns[viewModel.selectedRide.value.id]
                            updateConfirmButton(viewModel.selectedRide.value, fare)
                        }
                    }
                }

                launch {
                    viewModel.selectedRide.collect { ride ->
                        val fare = viewModel.fareBreakdowns.value[ride.id]
                        updateConfirmButton(ride, fare)
                        rideAdapter.updateSelection(ride.id)
                    }
                }

                launch {
                    viewModel.selectedPayment.collect { method ->
                        bindPaymentMethod(method)
                    }
                }

                launch {
                    viewModel.tripState.collect { state ->
                        when (state) {
                            is Resource.Loading -> showLoading(true)
                            is Resource.Success -> {
                                showLoading(false)
                                navigateToTripTracking(state.data)
                                viewModel.resetTripState()
                            }
                            is Resource.Error -> {
                                showLoading(false)
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetTripState()
                            }
                            null -> showLoading(false)
                        }
                    }
                }
            }
        }
    }

    private fun bindPaymentMethod(method: PaymentMethod) {
        binding.tvPaymentMethod.text = method.label

        if (method.type == "card") {
            when (method.brand.lowercase()) {
                "visa" -> {
                    binding.ivCardLogo.setImageResource(R.drawable.ic_visa_logo)
                    binding.ivCardLogo.visibility    = View.VISIBLE
                    binding.ivPaymentIcon.visibility = View.GONE
                }
                "mastercard" -> {
                    binding.ivCardLogo.setImageResource(R.drawable.ic_mastercard_logo)
                    binding.ivCardLogo.visibility    = View.VISIBLE
                    binding.ivPaymentIcon.visibility = View.GONE
                }
                else -> {
                    binding.ivCardLogo.visibility    = View.GONE
                    binding.ivPaymentIcon.visibility = View.VISIBLE
                }
            }
        } else {
            binding.ivCardLogo.visibility    = View.GONE
            binding.ivPaymentIcon.visibility = View.VISIBLE
        }
    }

    private fun updateConfirmButton(ride: RideOption, fare: FareBreakdown?) {
        val priceText = fare?.let { " — $${"%.2f".format(it.total)}" } ?: ""
        binding.btnConfirmRide.text = "CONFIRM ${ride.name.uppercase()} RIDE$priceText"
        binding.tvEta.text          = "${ride.etaMinutes} min away"
    }

    private fun showPaymentDialog() {
        val dialog        = BottomSheetDialog(this, R.style.CountryPickerTheme)
        val dialogBinding = DialogPaymentMethodBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)

        val adapter = PaymentMethodAdapter(
            viewModel.paymentMethods.value,
            viewModel.selectedPayment.value.id
        ) { selected ->
            viewModel.selectPayment(selected)
            dialog.dismiss()
        }
        dialogBinding.rvPaymentMethods.apply {
            layoutManager = LinearLayoutManager(this@RideSelectionActivity)
            this.adapter  = adapter
        }
        dialogBinding.btnAddPayment.setOnClickListener {
            dialog.dismiss()
            Snackbar.make(binding.root, "Add payment coming soon", Snackbar.LENGTH_SHORT).show()
        }
        dialog.show()
    }

    private fun showLoading(show: Boolean) {
        binding.btnConfirmRide.isEnabled = !show
        binding.btnConfirmRide.alpha     = if (show) 0.6f else 1.0f
    }

    private fun navigateToTripTracking(tripId: String) {
        startActivity(
            Intent(this, TripTrackingActivity::class.java).apply {
                putExtra(TripTrackingActivity.EXTRA_TRIP_ID, tripId)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
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

    override fun onResume()    { super.onResume();    binding.mapView.onResume() }
    override fun onPause()     { super.onPause();     binding.mapView.onPause() }
    override fun onDestroy()   { super.onDestroy();   binding.mapView.onDestroy() }
    override fun onLowMemory() { super.onLowMemory(); binding.mapView.onLowMemory() }
    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        binding.mapView.onSaveInstanceState(out)
    }
}