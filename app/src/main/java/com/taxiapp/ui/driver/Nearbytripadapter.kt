package com.taxiapp.ui.driver

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.data.model.Trip
import com.taxiapp.databinding.ItemNearbyTripBinding
import com.taxiapp.util.PricingEngine

class NearbyTripAdapter(
    private val onAccept: (Trip) -> Unit
) : RecyclerView.Adapter<NearbyTripAdapter.ViewHolder>() {

    private val trips = mutableListOf<Trip>()

    inner class ViewHolder(val binding: ItemNearbyTripBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNearbyTripBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val trip = trips[position]
        with(holder.binding) {
            tvPickupAddress.text  = trip.pickupAddress
            tvDropoffAddress.text = trip.dropoffAddress
            tvPrice.text          = "$${"%.2f".format(trip.price)}"
            tvRideType.text       = trip.rideType.replaceFirstChar { it.uppercase() }
            tvDistance.text       = "${"%.1f".format(trip.distanceMiles)} mi"
            tvDuration.text       = "${trip.durationMinutes} min"
            btnAccept.setOnClickListener { onAccept(trip) }
        }
    }

    override fun getItemCount() = trips.size

    fun submitList(newTrips: List<Trip>) {
        trips.clear()
        trips.addAll(newTrips)
        notifyDataSetChanged()
    }
}