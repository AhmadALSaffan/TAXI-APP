package com.taxiapp.ui.ride

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.taxiapp.data.model.PaymentMethod
import com.taxiapp.databinding.ItemPaymentMethodBinding

class PaymentMethodAdapter(
    private val methods: List<PaymentMethod>,
    private val selectedId: String,
    private val onSelected: (PaymentMethod) -> Unit
) : RecyclerView.Adapter<PaymentMethodAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemPaymentMethodBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPaymentMethodBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val method = methods[position]
        with(holder.binding) {
            tvPaymentLabel.text   = method.label
            imgSelected.visibility = if (method.id == selectedId) View.VISIBLE else View.GONE
            root.setOnClickListener { onSelected(method) }
        }
    }

    override fun getItemCount() = methods.size
}