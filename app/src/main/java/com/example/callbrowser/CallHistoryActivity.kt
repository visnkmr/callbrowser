package com.example.callbrowser

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callbrowser.databinding.ActivityCallHistoryBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class CallHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCallHistoryBinding
    private lateinit var adapter: CallHistoryAdapter
    private var currentPhoneNumber: String = ""
    private var currentContactName: String? = null
    
    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        private const val CALL_PERMISSION_REQUEST = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCallHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
            
            val calls = withContext(Dispatchers.IO) {
                fetchCallsForNumber(phoneNumber)
            }
            
            adapter.submitList(calls)
            binding.progressBar.visibility = View.GONE
            
            if (calls.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
            } else {
                binding.textViewEmpty.visibility = View.GONE
                binding.textViewTotalCalls.text = "${calls.size} calls"
            }
        }
    }

    private fun fetchCallsForNumber(phoneNumber: String): List<CallLogEntry> {
        val calls = mutableListOf<CallLogEntry>()
        
        val cursor = contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            "${CallLog.Calls.NUMBER} = ? OR ${CallLog.Calls.NUMBER} = ?",
            arrayOf(phoneNumber, normalizePhoneNumber(phoneNumber)),
            CallLog.Calls.DATE + " DESC"
        )
        
        cursor?.use {
            val idIndex = it.getColumnIndex(CallLog.Calls._ID)
            val numberIndex = it.getColumnIndex(CallLog.Calls.NUMBER)
            val nameIndex = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
            val typeIndex = it.getColumnIndex(CallLog.Calls.TYPE)
            val dateIndex = it.getColumnIndex(CallLog.Calls.DATE)
            val durationIndex = it.getColumnIndex(CallLog.Calls.DURATION)
            
            while (it.moveToNext()) {
                val number = it.getString(numberIndex) ?: continue
                
                val call = CallLogEntry(
                    id = it.getString(idIndex),
                    number = number,
                    name = it.getString(nameIndex),
                    type = it.getInt(typeIndex),
                    date = it.getLong(dateIndex),
                    duration = it.getLong(durationIndex),
                    isContactSaved = false
                )
                calls.add(call)
            }
        }
        
        return calls
    }

    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }
}