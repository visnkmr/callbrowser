package com.example.callbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.callbrowser.databinding.ItemContactBinding
import java.text.SimpleDateFormat
import java.util.*

class ContactsAdapter(
    private val onItemClick: (ContactEntry) -> Unit
) : ListAdapter<ContactEntry, ContactsAdapter.ContactViewHolder>(ContactDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val binding = ItemContactBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ContactViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ContactViewHolder(
        private val binding: ItemContactBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(contact: ContactEntry) {
            binding.apply {
                textViewName.text = contact.name ?: "Unknown"
                textViewPhoneNumber.text = contact.phoneNumber ?: "No phone number"

                // Format last updated timestamp
                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                textViewLastUpdated.text = "Updated: ${dateFormat.format(Date(contact.lastUpdatedTimestamp))}"

                // Show times contacted
                textViewTimesContacted.text = "Contacted: ${contact.timesContacted} times"

                // Set initial letter avatar
                val initial = contact.name?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                textViewInitial.text = initial

                root.isClickable = true
                root.isFocusable = true
            }
        }
    }

    class ContactDiffCallback : DiffUtil.ItemCallback<ContactEntry>() {
        override fun areItemsTheSame(oldItem: ContactEntry, newItem: ContactEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ContactEntry, newItem: ContactEntry): Boolean {
            return oldItem == newItem
        }
    }
}
