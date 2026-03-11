package com.taxiapp.ui.ride

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.taxiapp.R
import com.taxiapp.data.model.FareBreakdown
import com.taxiapp.data.model.RideOption
import com.taxiapp.databinding.ItemRideOptionBinding

class RideOptionAdapter(
    private val options: List<RideOption>,
    private val onSelected: (RideOption) -> Unit
) : RecyclerView.Adapter<RideOptionAdapter.ViewHolder>() {

    private var selectedId  = options.firstOrNull()?.id ?: ""
    private var breakdowns  = mapOf<String, FareBreakdown>()

    inner class ViewHolder(val binding: ItemRideOptionBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemRideOptionBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val option     = options[position]
        val ctx        = holder.binding.root.context
        val isSelected = option.id == selectedId
        val fare       = breakdowns[option.id]

        with(holder.binding) {
            tvRideName.text     = option.name
            tvRideSubtitle.text = buildSubtitle(option, fare)

            if (fare != null) {
                tvPrice.text = "$${String.format("%.2f", fare.total)}"
                if (option.originalPrice != null) {
                    tvOriginalPrice.visibility = View.VISIBLE
                    tvOriginalPrice.text       = "$${String.format("%.2f", option.originalPrice)}"
                    tvOriginalPrice.paintFlags = tvOriginalPrice.paintFlags or
                            android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
                } else {
                    tvOriginalPrice.visibility = View.GONE
                }
            } else {
                tvPrice.text           = "..."
                tvOriginalPrice.visibility = View.GONE
            }

            imgRideType.setImageResource(option.iconRes)

            val card = cardRideOption as MaterialCardView
            card.setCardBackgroundColor(
                ContextCompat.getColor(
                    ctx,
                    if (isSelected) R.color.surface_dark_blue else R.color.surface_dark
                )
            )
            card.strokeWidth = if (isSelected) 2.dpToPx(ctx) else 0
            card.strokeColor = ContextCompat.getColor(ctx, R.color.brand_blue)

            root.setOnClickListener {
                val previous = selectedId
                selectedId   = option.id
                notifyItemChanged(options.indexOfFirst { it.id == previous })
                notifyItemChanged(position)
                onSelected(option)
            }
        }
    }

    override fun getItemCount() = options.size

    fun updatePrices(newBreakdowns: Map<String, FareBreakdown>) {
        breakdowns = newBreakdowns
        notifyDataSetChanged()
    }

    fun updateSelection(id: String) {
        val previous = selectedId
        selectedId   = id
        notifyItemChanged(options.indexOfFirst { it.id == previous })
        notifyItemChanged(options.indexOfFirst { it.id == id })
    }

    private fun buildSubtitle(option: RideOption, fare: FareBreakdown?): String {
        val etaPart      = "${option.etaMinutes} min away"
        val distancePart = fare?.let { " · ${"%.1f".format(it.distanceMiles)} mi" } ?: ""
        return "$etaPart$distancePart · ${option.subtitle}"
    }

    private fun Int.dpToPx(ctx: android.content.Context): Int =
        (this * ctx.resources.displayMetrics.density).toInt()
}