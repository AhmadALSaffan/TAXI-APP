package com.taxiapp.ui.search

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.taxiapp.data.model.PlaceResult
import com.taxiapp.databinding.ActivityDestinationSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@AndroidEntryPoint
class DestinationSearchActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_ADDRESS  = "result_address"
        const val EXTRA_LAT      = "result_lat"
        const val EXTRA_LNG      = "result_lng"
        const val EXTRA_PREFILL  = "prefill_query"
    }

    private lateinit var binding: ActivityDestinationSearchBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var adapter: PlaceResultAdapter
    private var sessionToken = AutocompleteSessionToken.newInstance()
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDestinationSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        placesClient = Places.createClient(this)

        setupRecyclerView()
        setupSearchInput()
        setupClickListeners()
        hideSystemBars()

        val prefill = intent.getStringExtra(EXTRA_PREFILL)
        if (!prefill.isNullOrBlank()) {
            binding.etSearch.setText(prefill)
            binding.etSearch.setSelection(prefill.length)
            searchPlaces(prefill)
        } else {
            showEmpty(true)
        }

        binding.etSearch.requestFocus()
    }

    private fun setupRecyclerView() {
        adapter = PlaceResultAdapter { place ->
            fetchPlaceDetails(place)
        }
        binding.rvResults.apply {
            layoutManager = LinearLayoutManager(this@DestinationSearchActivity)
            adapter = this@DestinationSearchActivity.adapter
        }
    }

    private fun setupSearchInput() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                binding.btnClearSearch.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                searchJob?.cancel()
                if (query.length >= 2) {
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        searchPlaces(query)
                    }
                } else {
                    adapter.submitList(emptyList())
                    showEmpty(true)
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
            adapter.submitList(emptyList())
            showEmpty(true)
        }
    }

    private fun searchPlaces(query: String) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setSessionToken(sessionToken)
            .setQuery(query)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val results = response.autocompletePredictions.map { prediction ->
                    PlaceResult(
                        placeId       = prediction.placeId,
                        primaryText   = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString()
                    )
                }
                adapter.submitList(results)
                showEmpty(results.isEmpty())
            }
            .addOnFailureListener {
                showEmpty(true)
            }
    }

    private fun fetchPlaceDetails(place: PlaceResult) {
        val fields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.LAT_LNG, Place.Field.ADDRESS)
        val request = FetchPlaceRequest.newInstance(place.placeId, fields)

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val fetched  = response.place
                val latLng   = fetched.latLng ?: LatLng(0.0, 0.0)
                val address  = fetched.address ?: fetched.name ?: place.primaryText

                sessionToken = AutocompleteSessionToken.newInstance()

                val resultIntent = Intent().apply {
                    putExtra(EXTRA_ADDRESS, address)
                    putExtra(EXTRA_LAT,     latLng.latitude)
                    putExtra(EXTRA_LNG,     latLng.longitude)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
            .addOnFailureListener {
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_ADDRESS, place.primaryText)
                    putExtra(EXTRA_LAT,     0.0)
                    putExtra(EXTRA_LNG,     0.0)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
    }

    private fun showEmpty(show: Boolean) {
        binding.layoutEmpty.visibility = if (show) View.VISIBLE else View.GONE
        binding.rvResults.visibility   = if (show) View.GONE else View.VISIBLE
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.navigationBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}