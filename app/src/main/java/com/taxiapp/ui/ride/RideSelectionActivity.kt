package com.taxiapp.ui.ride

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.taxiapp.ui.theme.TaxiTheme
import dagger.hilt.android.AndroidEntryPoint

/** Pick a ride type and payment, then book. */
@AndroidEntryPoint
class RideSelectionActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PICKUP_ADDRESS = "pickup_address"
        const val EXTRA_DROPOFF_ADDRESS = "dropoff_address"
        const val EXTRA_PICKUP_LAT = "pickup_lat"
        const val EXTRA_PICKUP_LNG = "pickup_lng"
        const val EXTRA_DROPOFF_LAT = "dropoff_lat"
        const val EXTRA_DROPOFF_LNG = "dropoff_lng"
    }

    private val viewModel: RideSelectionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(state.bookedTripId) {
                    val tripId = state.bookedTripId ?: return@LaunchedEffect
                    startActivity(
                        Intent(this@RideSelectionActivity, TripTrackingActivity::class.java)
                            .putExtra(TripTrackingActivity.EXTRA_TRIP_ID, tripId)
                            .putExtra(TripTrackingActivity.EXTRA_IS_DRIVER, false),
                    )
                    // Back from tracking should go home, not to a booked ride.
                    finish()
                }
                RideSelectionScreen(
                    state = state,
                    onBack = ::finish,
                    onSelect = viewModel::select,
                    onSelectPayment = viewModel::selectPayment,
                    onConfirm = viewModel::confirm,
                    onErrorShown = viewModel::onErrorShown,
                )
            }
        }
    }
}
