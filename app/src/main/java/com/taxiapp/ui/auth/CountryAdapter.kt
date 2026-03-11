package com.taxiapp.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.databinding.ItemCountryBinding

data class Country(
    val flag: String,
    val name: String,
    val dialCode: String
)

class CountryAdapter(
    private val countries: List<Country>,
    private val onItemClick: (Country) -> Unit
) : RecyclerView.Adapter<CountryAdapter.CountryViewHolder>() {

    private var filteredList = countries.toMutableList()


    inner class CountryViewHolder(
        private val binding: ItemCountryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(country: Country) {
            binding.tvItemFlag1.text     = country.flag
            binding.tvItemCountryName1.text = country.name
            binding.tvItemDialCode1.text = country.dialCode
            binding.root.setOnClickListener { onItemClick(country) }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder {
        val binding = ItemCountryBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CountryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
        holder.bind(filteredList[position])
    }

    override fun getItemCount(): Int = filteredList.size


    fun filter(query: String) {
        filteredList = if (query.isEmpty()) {
            countries.toMutableList()
        } else {
            countries.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.dialCode.contains(query)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }
}

