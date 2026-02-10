package com.example.callbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callbrowser.data.repository.CommunicationRepository
import com.example.callbrowser.databinding.ActivityCallHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class CallHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallHistoryBinding
    private lateinit var adapter: CallHistoryAdapter
    private lateinit var repository: CommunicationRepository
    private var currentPhoneNumber: String = ""
    private var currentContactName: String? = null

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        private const val CALL_PERMISSION_REQUEST = 1001
        private const val HISTORY_ITEM_LIMIT = 500
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CommunicationRepository(this)

        currentPhoneNumber = intent.getStringExtra(EXTRA_PHONE_NUMBER) ?: run {
            Toast.makeText(this, "No phone number provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        currentContactName = intent.getStringExtra(EXTRA_CONTACT_NAME)

        setupToolbar(currentContactName, currentPhoneNumber)
        setupRecyclerView()
        setupActionButtons()

        if (checkPermissions()) {
            loadCallHistory(currentPhoneNumber)
        } else {
            Toast.makeText(this, "Permission required to view call history", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun setupToolbar(contactName: String?, phoneNumber: String) {
        binding.apply {
            toolbar.setNavigationOnClickListener { finish() }
            textViewContactName.text = contactName ?: phoneNumber
            textViewPhoneNumber.text = phoneNumber

            // Show name only if different from number
            if (contactName != null && contactName != phoneNumber) {
                textViewContactName.visibility = View.VISIBLE
                textViewPhoneNumber.visibility = View.VISIBLE
            } else {
                textViewContactName.text = phoneNumber
                textViewPhoneNumber.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CallHistoryAdapter()
        binding.recyclerViewCallHistory.apply {
            layoutManager = LinearLayoutManager(this@CallHistoryActivity)
            adapter = this@CallHistoryActivity.adapter
            setHasFixedSize(true)
        }
    }

    private fun setupActionButtons() {
        binding.buttonCall.setOnClickListener {
            makeCall()
        }

        binding.buttonAddContact.setOnClickListener {
            addToContacts()
        }

        // Hide Add Contact button if already a saved contact
        if (currentContactName != null && currentContactName != currentPhoneNumber) {
            binding.buttonAddContact.visibility = View.GONE
        }
    }

    private fun makeCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CALL_PHONE),
                CALL_PERMISSION_REQUEST
            )
        } else {
            initiateCall()
        }
    }

    private fun initiateCall() {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$currentPhoneNumber")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to make call", Toast.LENGTH_SHORT).show()
        }
    }

    private fun addToContacts() {
        try {
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.PHONE, currentPhoneNumber)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open contacts", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CALL_PERMISSION_REQUEST -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initiateCall()
                } else {
                    Toast.makeText(this, "Call permission denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadCallHistory(phoneNumber: String) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            val combinedList = withContext(Dispatchers.IO) {
                repository.getDetailedHistory(phoneNumber, HISTORY_ITEM_LIMIT)
            }

            adapter.submitList(combinedList)
            binding.progressBar.visibility = View.GONE

            if (combinedList.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
            } else {
                binding.textViewEmpty.visibility = View.GONE

                // Calculate stats from all items (not limited)
                val calls = combinedList.filterIsInstance<CallLogEntry>()
                val messages = combinedList.filterIsInstance<MessageEntry>()

                val totalCalls = calls.size
                val totalMessages = messages.size
                val totalDuration = calls.sumOf { it.duration }

                binding.textViewTotalCalls.text = "$totalCalls calls"
                binding.textViewTotalMessages.text = "$totalMessages messages"
                binding.textViewTotalTalkTime.text = "Total: ${formatDuration(totalDuration)}"

                // Show warning if list was limited
                if (combinedList.size >= HISTORY_ITEM_LIMIT) {
                    Toast.makeText(
                        this@CallHistoryActivity,
                        "Showing last $HISTORY_ITEM_LIMIT items only",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun formatDuration(seconds: Long): String {
        if (seconds == 0L) return "0s"
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