package com.taxiapp.ui.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.data.model.PlaceResult
import com.taxiapp.databinding.ItemPlaceResultBinding

class PlaceResultAdapter(
    private val onSelected: (PlaceResult) -> Unit
) : RecyclerView.Adapter<PlaceResultAdapter.ViewHolder>() {

    private val results = mutableListOf<PlaceResult>()

    inner class ViewHolder(val binding: ItemPlaceResultBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPlaceResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val place = results[position]
        with(holder.binding) {
            tvPrimaryText.text   = place.primaryText
            tvSecondaryText.text = place.secondaryText
            root.setOnClickListener { onSelected(place) }
        }
    }

    override fun getItemCount() = results.size

    fun submitList(newResults: List<PlaceResult>) {
        results.clear()
        results.addAll(newResults)
        notifyDataSetChanged()
    }
}