package com.example.callbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.callbrowser.databinding.ItemCallLogBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallLogAdapter(
    private val onItemClick: (CallLogEntry) -> Unit
) : ListAdapter<CallLogEntry, CallLogAdapter.CallViewHolder>(CallDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallViewHolder {
        val binding = ItemCallLogBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallViewHolder(
        private val binding: ItemCallLogBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(call: CallLogEntry) {
            binding.apply {
                textViewName.text = call.name ?: call.number
                textViewNumber.text = "${call.number} (${call.callCount})"
                
                val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                textViewDate.text = dateFormat.format(Date(call.date))
                
                textViewDuration.text = formatDuration(call.duration)
                
                val typeColor = when (call.type) {
                    CallLogEntry.TYPE_INCOMING -> root.context.getColor(R.color.incoming_green)
                    CallLogEntry.TYPE_OUTGOING -> root.context.getColor(R.color.outgoing_blue)
                    CallLogEntry.TYPE_MISSED -> root.context.getColor(R.color.missed_red)
                    else -> root.context.getColor(R.color.gray)
                }
                
                val typeText = when (call.type) {
                    CallLogEntry.TYPE_INCOMING -> "Incoming"
                    CallLogEntry.TYPE_OUTGOING -> "Outgoing"
                    CallLogEntry.TYPE_MISSED -> "Missed"
                    CallLogEntry.TYPE_VOICEMAIL -> "Voicemail"
                    CallLogEntry.TYPE_REJECTED -> "Rejected"
                    CallLogEntry.TYPE_BLOCKED -> "Blocked"
                    else -> "Unknown"
                }
                
                textViewType.text = typeText
                textViewType.setTextColor(typeColor)
                imageViewCallType.setColorFilter(typeColor)
                
                // Add ripple effect for better click feedback
                root.isClickable = true
                root.isFocusable = true
            }
        }

        private fun formatDuration(seconds: Long): String {
            if (seconds == 0L) return ""
            val hours = TimeUnit.SECONDS.toHours(seconds)
            val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
                else -> String.format("%d:%02d", minutes, secs)
            }
        }
    }

    class CallDiffCallback : DiffUtil.ItemCallback<CallLogEntry>() {
        override fun areItemsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}