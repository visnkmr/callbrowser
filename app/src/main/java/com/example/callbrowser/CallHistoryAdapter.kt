package com.example.callbrowser

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.callbrowser.databinding.ItemCallHistoryBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CallHistoryAdapter : ListAdapter<Any, CallHistoryAdapter.CallHistoryViewHolder>(CallHistoryDiffCallback()) {

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

        fun bind(item: Any) {
            when (item) {
                is CallLogEntry -> bindCall(item)
                is MessageEntry -> bindMessage(item)
            }
        }

        private fun bindCall(call: CallLogEntry) {
            binding.apply {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                textViewDateTime.text = dateFormat.format(Date(call.date))

                textViewDuration.visibility = View.VISIBLE
                textViewDuration.text = formatDuration(call.duration)

                textViewMessageBody.visibility = View.GONE

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
                imageViewCallType.setImageResource(R.drawable.ic_call)
                imageViewCallType.setColorFilter(typeColor)
            }
        }

        private fun bindMessage(message: MessageEntry) {
            binding.apply {
                val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
                textViewDateTime.text = dateFormat.format(Date(message.date))

                textViewDuration.visibility = View.GONE

                textViewMessageBody.visibility = View.VISIBLE
                textViewMessageBody.text = message.body

                val typeColor = root.context.getColor(R.color.message_purple)

                val typeText = when (message.type) {
                    MessageEntry.TYPE_INBOX -> "Received"
                    MessageEntry.TYPE_SENT -> "Sent"
                    MessageEntry.TYPE_DRAFT -> "Draft"
                    MessageEntry.TYPE_OUTBOX -> "Outbox"
                    MessageEntry.TYPE_FAILED -> "Failed"
                    MessageEntry.TYPE_QUEUED -> "Queued"
                    else -> "Message"
                }

                textViewCallType.text = typeText
                textViewCallType.setTextColor(typeColor)
                imageViewCallType.setImageResource(R.drawable.ic_message)
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

    class CallHistoryDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is CallLogEntry && newItem is CallLogEntry -> oldItem.id == newItem.id
                oldItem is MessageEntry && newItem is MessageEntry -> oldItem.id == newItem.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return when {
                oldItem is CallLogEntry && newItem is CallLogEntry -> oldItem == newItem
                oldItem is MessageEntry && newItem is MessageEntry -> oldItem == newItem
                else -> false
            }
        }
    }
}