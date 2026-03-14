package com.taxiapp.ui.ride

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.TextView
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.taxiapp.R
import com.taxiapp.data.model.Trip
import com.taxiapp.data.model.TripStatus
import com.taxiapp.databinding.ActivityTripTrackingBinding
import com.taxiapp.ui.driver.DriverHomeActivity
import com.taxiapp.ui.home.HomeActivity
import com.taxiapp.util.InvoiceGenerator
import com.taxiapp.util.Resource
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class TripTrackingActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_TRIP_ID   = "trip_id"
        const val EXTRA_IS_DRIVER = "is_driver"
    }

    private lateinit var binding: ActivityTripTrackingBinding
    private val viewModel: TripTrackingViewModel by viewModels()
    private val routeViewModel: RouteViewModel    by viewModels()

    private var googleMap: GoogleMap? = null
    private var currentTrip: Trip?    = null
    private var previousStatus        = ""
    private var routeFetched          = false
    private var driverMarker: Marker? = null
    private var isDriver              = false

    private val apiKey by lazy { getString(R.string.google_maps_key) }
    private val fmt    by lazy { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    private var pendingInvoiceTrip: Trip? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingInvoiceTrip?.let { doGenerateInvoice(it) }
        else Snackbar.make(binding.root, "Storage permission required to save invoice", Snackbar.LENGTH_LONG).show()
        pendingInvoiceTrip = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTripTrackingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val tripId = intent.getStringExtra(EXTRA_TRIP_ID) ?: run { finish(); return }
        isDriver   = intent.getBooleanExtra(EXTRA_IS_DRIVER, false)

        binding.mapView.onCreate(savedInstanceState)
        binding.mapView.getMapAsync(this)

        setupBottomSheet()
        setupClickListeners(tripId)
        setupSlideToFinish(tripId)
        observeViewModel(tripId)
        hideSystemBars()
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        try { map.setMapStyle(MapStyleOptions.loadRawResourceStyle(this, R.raw.map_style_dark)) }
        catch (_: Exception) {}
        map.uiSettings.apply {
            isZoomControlsEnabled     = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled       = false
        }
        currentTrip?.let { trip ->
            placeMarkers(map, trip)
            val cur = routeViewModel.routePoints.value
            if (cur is Resource.Success) drawRoadPolyline(map, cur.data)
        }
    }

    private fun setupBottomSheet() {
        BottomSheetBehavior.from(binding.bottomSheet).apply {
            peekHeight      = resources.getDimensionPixelSize(R.dimen.bottom_sheet_peek_height)
            state           = BottomSheetBehavior.STATE_COLLAPSED
            isHideable      = false
            isFitToContents = false
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupSlideToFinish(tripId: String) {
        var dragStartX  = 0f
        var thumbStartX = 0f

        binding.ivSlideThumb.setOnTouchListener { _, event ->
            val track    = binding.layoutSlideToFinish
            val thumb    = binding.ivSlideThumb
            val maxSlide = track.width - thumb.width - 8.dpToPx()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX  = event.rawX
                    thumbStartX = thumb.translationX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newX = (thumbStartX + event.rawX - dragStartX).coerceIn(0f, maxSlide.toFloat())
                    thumb.translationX         = newX
                    binding.tvSlideLabel.alpha = 1f - (newX / maxSlide)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (thumb.translationX / maxSlide >= 0.85f) onSlideCompleted(tripId)
                    else resetSlideThumb()
                    true
                }
                else -> false
            }
        }
    }

    private fun onSlideCompleted(tripId: String) {
        val trip = currentTrip ?: return
        showDriverPaymentDialog(trip, tripId)
    }

    private fun showDriverPaymentDialog(trip: Trip, tripId: String) {
        val isCash  = trip.paymentMethod.lowercase() == "cash"
        val view    = layoutInflater.inflate(R.layout.dialog_payment_confirm, null)

        view.findViewById<TextView>(R.id.tvPaymentMethodLabel).text = if (isCash) "Payment Method" else "Payment Method"
        view.findViewById<TextView>(R.id.tvPaymentMethodValue).text = if (isCash) "Cash" else "Card ···· ${trip.paymentMethod.takeLast(4)}"
        view.findViewById<TextView>(R.id.tvTotalAmount).text        = "$${"%.2f".format(trip.price)}"
        view.findViewById<TextView>(R.id.tvBaseFare).text           = "$${"%.2f".format(trip.baseFare)}"
        view.findViewById<TextView>(R.id.tvDistanceFare).text       = "$${"%.2f".format(trip.distanceFare)}"
        view.findViewById<TextView>(R.id.tvTimeFare).text           = "$${"%.2f".format(trip.timeFare)}"
        view.findViewById<TextView>(R.id.tvTotalRow).text           = "$${"%.2f".format(trip.price)}"

        val tollsRow = view.findViewById<View>(R.id.layoutTollsRow)
        if (trip.tolls > 0) {
            tollsRow.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvTollsAmount).text = "$${"%.2f".format(trip.tolls)}"
        }

        val cashNote = view.findViewById<TextView>(R.id.tvCashNote)
        if (isCash) {
            cashNote.visibility = View.VISIBLE
            cashNote.text       = "Collect $${"%.2f".format(trip.price)} cash from the passenger"
        }

        val builder = MaterialAlertDialogBuilder(this)
            .setTitle(if (isCash) "Collect Payment" else "Finish Trip")
            .setView(view)

        if (isCash) {
            builder
                .setNegativeButton("Wait") { d, _ -> resetSlideThumb(); d.dismiss() }
                .setPositiveButton("Cash Received — Finish") { _, _ ->
                    viewModel.markCashPaid(tripId)
                    viewModel.completeTrip(tripId)
                }
        } else {
            builder
                .setNegativeButton("Cancel") { d, _ -> resetSlideThumb(); d.dismiss() }
                .setPositiveButton("Finish Trip") { _, _ ->
                    viewModel.completeTrip(tripId)
                }
        }

        builder.show()
    }

    private fun showPassengerCompletedDialog(trip: Trip) {
        val view = layoutInflater.inflate(R.layout.dialog_trip_complete, null)

        view.findViewById<TextView>(R.id.tvCompletedTotal).text   = "$${"%.2f".format(trip.price)}"
        view.findViewById<TextView>(R.id.tvCompletedPickup).text  = trip.pickupAddress
        view.findViewById<TextView>(R.id.tvCompletedDropoff).text = trip.dropoffAddress
        view.findViewById<TextView>(R.id.tvCBaseFare).text        = "$${"%.2f".format(trip.baseFare)}"
        view.findViewById<TextView>(R.id.tvCDistanceFare).text    = "$${"%.2f".format(trip.distanceFare)}"
        view.findViewById<TextView>(R.id.tvCTimeFare).text        = "$${"%.2f".format(trip.timeFare)}"
        view.findViewById<TextView>(R.id.tvCTotal).text           = "$${"%.2f".format(trip.price)}"
        view.findViewById<TextView>(R.id.tvCPaymentMethod).text   = when {
            trip.paymentMethod.lowercase() == "cash" -> "CASH PAYMENT"
            trip.paymentMethod.length >= 4            -> "CHARGED TO ···· ${trip.paymentMethod.takeLast(4)}"
            else                                      -> "CARD PAYMENT"
        }

        if (trip.completedAt > 0)
            view.findViewById<TextView>(R.id.tvCompletedTime).text = "Completed at ${fmt.format(Date(trip.completedAt))}"

        val tollsRow = view.findViewById<View>(R.id.layoutCTollsRow)
        if (trip.tolls > 0) {
            tollsRow.visibility = View.VISIBLE
            view.findViewById<TextView>(R.id.tvCTollsAmount).text = "$${"%.2f".format(trip.tolls)}"
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setCancelable(false)
            .create()

        view.findViewById<View>(R.id.btnDownloadInvoiceDialog).setOnClickListener {
            downloadInvoice(trip)
            dialog.dismiss()
            val intent = Intent(this@TripTrackingActivity, HomeActivity::class.java)
            startActivity(intent)
        }

        dialog.show()
    }

    private fun downloadInvoice(trip: Trip) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            pendingInvoiceTrip = trip
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            doGenerateInvoice(trip)
        }
    }

    private fun doGenerateInvoice(trip: Trip) {
        val result = InvoiceGenerator.generate(this, trip)
        result.onSuccess { fileName ->
            Snackbar.make(binding.root, "Invoice saved to Downloads: ", Snackbar.LENGTH_LONG).show()
        }
        result.onFailure {
            Snackbar.make(binding.root, "Failed to generate invoice", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun resetSlideThumb() {
        binding.ivSlideThumb.animate().translationX(0f).setDuration(200).start()
        binding.tvSlideLabel.animate().alpha(1f).setDuration(200).start()
    }

    private fun setupClickListeners(tripId: String) {
        binding.btnBack.setOnClickListener    {
            val intent = Intent(this@TripTrackingActivity, HomeActivity::class.java)
            startActivity(intent)
            finish()
        }
        binding.btnCall.setOnClickListener    { Snackbar.make(binding.root, "Calling...",   Snackbar.LENGTH_SHORT).show() }
        binding.btnMessage.setOnClickListener { Snackbar.make(binding.root, "Messaging...", Snackbar.LENGTH_SHORT).show() }

        binding.btnNavigate.setOnClickListener {
            val trip = currentTrip ?: return@setOnClickListener
            openGoogleMapsNavigation(LatLng(trip.driverLat, trip.driverLng), navigationTarget(trip))
        }

        binding.btnOtherApp.setOnClickListener {
            val trip = currentTrip ?: return@setOnClickListener
            showNavigationChooser(LatLng(trip.driverLat, trip.driverLng), navigationTarget(trip))
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

    private fun navigationTarget(trip: Trip): LatLng =
        if (trip.status == TripStatus.ACCEPTED || trip.status == TripStatus.ARRIVED)
            LatLng(trip.pickupLat, trip.pickupLng)
        else
            LatLng(trip.dropoffLat, trip.dropoffLng)

    private fun openGoogleMapsNavigation(origin: LatLng, dest: LatLng) {
        val uri    = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
                    "&origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${dest.latitude},${dest.longitude}" +
                    "&travelmode=driving"
        )
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(packageManager) != null) startActivity(intent)
        else showNavigationChooser(origin, dest)
    }

    private fun showNavigationChooser(origin: LatLng, dest: LatLng) {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1" +
                    "&origin=${origin.latitude},${origin.longitude}" +
                    "&destination=${dest.latitude},${dest.longitude}" +
                    "&travelmode=driving"
        )
        startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "Navigate with..."))
    }

    private fun observeViewModel(tripId: String) {
        viewModel.observeTrip(tripId)

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {

                launch {
                    routeViewModel.routePoints.collect { state ->
                        if (state is Resource.Success) {
                            googleMap?.let { map ->
                                currentTrip?.let { placeMarkers(map, it) }
                                drawRoadPolyline(map, state.data)
                            }
                        }
                    }
                }

                launch {
                    viewModel.tripState.collect { state ->
                        when (state) {
                            is Resource.Loading -> binding.tvDriverName.text = "Finding driver..."
                            is Resource.Success -> {
                                val trip = state.data
                                currentTrip = trip
                                bindTrip(trip)
                                googleMap?.let { updateDriverMarker(trip) }

                                if (!routeFetched && trip.pickupLat != 0.0 && trip.dropoffLat != 0.0) {
                                    routeFetched = true
                                    googleMap?.let { placeMarkers(it, trip) }
                                    routeViewModel.fetchRoute(
                                        trip.pickupLat, trip.pickupLng,
                                        trip.dropoffLat, trip.dropoffLng,
                                        apiKey
                                    )
                                }

                                if (!isDriver
                                    && previousStatus != TripStatus.COMPLETED
                                    && trip.status    == TripStatus.COMPLETED
                                ) {
                                    showPassengerCompletedDialog(trip)
                                }

                                previousStatus = trip.status
                            }
                            is Resource.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                            }
                        }
                    }
                }

                launch {
                    viewModel.cancelState.collect { state ->
                        when (state) {
                            is Resource.Success -> { viewModel.resetCancelState(); goHome() }
                            is Resource.Error   -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                viewModel.resetCancelState()
                            }
                            else -> {}
                        }
                    }
                }

                launch {
                    viewModel.completeState.collect { state ->
                        when (state) {
                            is Resource.Success -> {
                                viewModel.resetCompleteState()
                                if (isDriver) goHome()
                            }
                            is Resource.Error -> {
                                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                                resetSlideThumb()
                                viewModel.resetCompleteState()
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }

    private fun bindTrip(trip: Trip) {
        val isCompleted = trip.status == TripStatus.COMPLETED
        val isSearching = trip.status == TripStatus.SEARCHING
        val isCancelled = trip.status == TripStatus.CANCELLED
        val isActive    = !isCompleted && !isSearching && !isCancelled

        binding.tvPickup.text     = trip.pickupAddress
        binding.tvDropoff.text    = trip.dropoffAddress
        binding.tvTripStatus.text = statusLabel(trip.status)

        if (trip.startedAt > 0)   binding.tvPickupTime.text  = "Picked up at ${fmt.format(Date(trip.startedAt))}"
        if (trip.completedAt > 0) binding.tvDropoffTime.text = "Dropped off at ${fmt.format(Date(trip.completedAt))}"

        if (isDriver) {
            binding.layoutContactButtons.visibility = View.GONE
            binding.btnCancel.visibility            = View.GONE
            binding.btnDownloadInvoice.visibility   = View.GONE
            binding.btnBookSimilar.visibility       = View.GONE
            binding.layoutDriverNav.visibility      = if (isCompleted || isCancelled) View.GONE else View.VISIBLE
            binding.layoutSlideToFinish.visibility  = if (isActive) View.VISIBLE else View.GONE
            binding.btnNavigate.text = if (trip.status == TripStatus.ACCEPTED || trip.status == TripStatus.ARRIVED)
                "Navigate to Pickup" else "Navigate to Dropoff"
        } else {
            binding.layoutDriverNav.visibility     = View.GONE
            binding.layoutSlideToFinish.visibility = View.GONE
            when {
                isCompleted || isCancelled -> {
                    binding.layoutContactButtons.visibility = View.GONE
                    binding.btnCancel.visibility            = View.GONE
                    binding.btnDownloadInvoice.visibility   = if (isCompleted) View.VISIBLE else View.GONE
                    binding.btnBookSimilar.visibility       = if (isCompleted) View.VISIBLE else View.GONE
                }
                isSearching -> {
                    binding.layoutContactButtons.visibility = View.GONE
                    binding.btnCancel.visibility            = View.VISIBLE
                    binding.btnDownloadInvoice.visibility   = View.GONE
                    binding.btnBookSimilar.visibility       = View.GONE
                }
                else -> {
                    binding.layoutContactButtons.visibility = View.VISIBLE
                    binding.btnCancel.visibility            = View.VISIBLE
                    binding.btnDownloadInvoice.visibility   = View.GONE
                    binding.btnBookSimilar.visibility       = View.GONE
                }
            }
        }

        if (trip.driverName.isNotBlank()) {
            binding.tvDriverName.text         = trip.driverName
            binding.tvVehicleInfo.text        = "${trip.driverVehicle} · ${trip.driverPlate}"
            binding.tvDriverRating.text       = "${trip.driverRating} ★"
            binding.tvDriverRating.visibility = View.VISIBLE
            binding.tvEtaBadge.text           = if (isCompleted) "Completed" else "${trip.durationMinutes} min away"
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
        addFareRow("Base Fare",     "$${"%.2f".format(trip.baseFare)}")
        addFareRow("Distance Fare", "$${"%.2f".format(trip.distanceFare)}")
        addFareRow("Time Fare",     "$${"%.2f".format(trip.timeFare)}")
        if (trip.tolls > 0) addFareRow("Tolls & Fees", "$${"%.2f".format(trip.tolls)}")
        binding.tvTotalPrice.text = "$${"%.2f".format(trip.price)}"
        binding.tvChargedTo.text  = when {
            trip.paymentMethod.lowercase() == "cash" -> "CASH PAYMENT"
            trip.paymentMethod.length >= 4            -> "CHARGED TO ···· ${trip.paymentMethod.takeLast(4)}"
            else                                      -> "CARD PAYMENT"
        }
    }

    private fun addFareRow(label: String, amount: String) {
        val row = LayoutInflater.from(this)
            .inflate(R.layout.item_fare_row, binding.layoutFareRows, false)
        row.findViewById<TextView>(R.id.tvFareLabel).text  = label
        row.findViewById<TextView>(R.id.tvFareAmount).text = amount
        binding.layoutFareRows.addView(row)
    }

    private fun updateDriverMarker(trip: Trip) {
        if (trip.driverLat == 0.0 || trip.driverLng == 0.0) return
        val pos = LatLng(trip.driverLat, trip.driverLng)
        val map = googleMap ?: return
        if (driverMarker == null) {
            driverMarker = map.addMarker(
                MarkerOptions().position(pos).title("Driver")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
        } else {
            driverMarker?.position = pos
        }
    }

    private fun placeMarkers(map: GoogleMap, trip: Trip) {
        if (trip.pickupLat == 0.0 && trip.dropoffLat == 0.0) return
        map.clear()
        driverMarker = null
        val pickup  = LatLng(trip.pickupLat,  trip.pickupLng)
        val dropoff = LatLng(trip.dropoffLat, trip.dropoffLng)
        map.addMarker(MarkerOptions().position(pickup).title("Pickup"))
        map.addMarker(MarkerOptions().position(dropoff).title("Dropoff"))
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds.Builder().include(pickup).include(dropoff).build(), 160
            )
        )
    }

    private fun drawRoadPolyline(map: GoogleMap, points: List<LatLng>) {
        map.addPolyline(
            PolylineOptions().addAll(points)
                .width(8f)
                .color(android.graphics.Color.parseColor("#2233FF"))
                .geodesic(false)
        )
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

    private fun goHome() {
        val dest = if (isDriver) DriverHomeActivity::class.java else HomeActivity::class.java
        startActivity(Intent(this, dest).apply { flags = Intent.FLAG_ACTIVITY_CLEAR_TOP })
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()

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
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}