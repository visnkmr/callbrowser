package com.example.callbrowser

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.callbrowser.databinding.ItemCallHistoryBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallHistoryAdapter : ListAdapter<CallLogEntry, CallHistoryAdapter.CallHistoryViewHolder>(CallHistoryDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CallHistoryViewHolder {
        val binding = ItemCallHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CallHistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CallHistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CallHistoryViewHolder(
        private val binding: ItemCallHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(call: CallLogEntry) {
            binding.apply {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                textViewDateTime.text = dateFormat.format(Date(call.date))
                
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
                
                textViewCallType.text = typeText
                textViewCallType.setTextColor(typeColor)
                imageViewCallType.setColorFilter(typeColor)
            }
        }

        private fun formatDuration(seconds: Long): String {
            if (seconds == 0L) return "0 sec"
            val hours = TimeUnit.SECONDS.toHours(seconds)
            val minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60
            val secs = seconds % 60
            
            return when {
                hours > 0 -> String.format("%dh %dm %ds", hours, minutes, secs)
                minutes > 0 -> String.format("%dm %ds", minutes, secs)
                else -> String.format("%ds", secs)
            }
        }
    }

    class CallHistoryDiffCallback : DiffUtil.ItemCallback<CallLogEntry>() {
        override fun areItemsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CallLogEntry, newItem: CallLogEntry): Boolean {
            return oldItem == newItem
        }
    }
}