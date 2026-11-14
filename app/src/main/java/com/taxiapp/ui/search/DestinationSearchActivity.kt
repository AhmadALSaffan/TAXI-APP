package com.taxiapp.ui.search

import android.app.Activity
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

/** Where-to search. Returns the chosen place to the caller. */
@AndroidEntryPoint
class DestinationSearchActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ADDRESS = "result_address"
        const val EXTRA_LAT = "result_lat"
        const val EXTRA_LNG = "result_lng"
        const val EXTRA_PREFILL = "prefill_query"
        const val EXTRA_PICKUP = "pickup_address"
        const val EXTRA_PICKUP_LAT = "pickup_lat"
        const val EXTRA_PICKUP_LNG = "pickup_lng"
    }

    private val viewModel: DestinationSearchViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            TaxiTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                val chosen by viewModel.chosen.collectAsStateWithLifecycle()
                LaunchedEffect(chosen) {
                    val place = chosen ?: return@LaunchedEffect
                    setResult(
                        Activity.RESULT_OK,
                        Intent()
                            .putExtra(EXTRA_ADDRESS, place.address)
                            .putExtra(EXTRA_LAT, place.lat)
                            .putExtra(EXTRA_LNG, place.lng),
                    )
                    finish()
                }
                DestinationSearchScreen(
                    state = state,
                    pickupAddress = intent.getStringExtra(EXTRA_PICKUP),
                    onQueryChange = viewModel::onQueryChange,
                    onPick = viewModel::pick,
                    onPickRecent = viewModel::pickRecent,
                    onBack = ::finish,
                    onOpenMap = viewModel::openMap,
                    onCloseMap = viewModel::closeMap,
                    onPinDragStarted = viewModel::onPinDragStarted,
                    onPinMoved = viewModel::onPinMoved,
                    onConfirmPin = viewModel::confirmPin,
                )
            }
        }
    }
}
