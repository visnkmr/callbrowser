package com.example.callbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callbrowser.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var callAdapter: CallLogAdapter
    private var allCalls = listOf<CallLogEntry>()
    private var allMessages = listOf<MessageEntry>()
    private var uniqueCalls = listOf<CallLogEntry>()

    private var currentCallType = CALL_TYPE_ALL
    private var currentContactFilter = CONTACT_ALL
    private var currentContentType = CONTENT_TYPE_ALL
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val CALL_TYPE_ALL = 0
        private const val CALL_TYPE_INCOMING = 1
        private const val CALL_TYPE_OUTGOING = 2
        private const val CALL_TYPE_MISSED = 3
        private const val CONTACT_ALL = 0
        private const val CONTACT_SAVED = 1
        private const val CONTACT_UNSAVED = 2
        private const val CONTENT_TYPE_ALL = 0
        private const val CONTENT_TYPE_CALLS = 1
        private const val CONTENT_TYPE_MESSAGES = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupSpinners()
        setupRecyclerView()
        setupButtons()
        
        checkPermissions()
    }

    private fun setupSpinners() {
        val contentTypeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.content_types,
            android.R.layout.simple_spinner_item
        )
        contentTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerContentType.adapter = contentTypeAdapter
        binding.spinnerContentType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentContentType = position
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val callTypeAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.call_types,
            android.R.layout.simple_spinner_item
        )
        callTypeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCallType.adapter = callTypeAdapter
        binding.spinnerCallType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentCallType = position
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val contactFilterAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.contact_filters,
            android.R.layout.simple_spinner_item
        )
        contactFilterAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerContactFilter.adapter = contactFilterAdapter
        binding.spinnerContactFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentContactFilter = position
                applyFilters()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        callAdapter = CallLogAdapter { callEntry ->
            // Open CallHistoryActivity when item is clicked
            val intent = Intent(this, CallHistoryActivity::class.java).apply {
                putExtra(CallHistoryActivity.EXTRA_PHONE_NUMBER, callEntry.number)
                putExtra(CallHistoryActivity.EXTRA_CONTACT_NAME, callEntry.name)
            }
            startActivity(intent)
        }
        binding.recyclerViewCalls.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = callAdapter
        }
    }

    private fun setupButtons() {
        binding.buttonGrantPermissions.setOnClickListener {
            requestPermissions()
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_SMS
        )

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            showMainContent()
            loadCallLogs()
        } else {
            showPermissionRequest()
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_CALL_LOG,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.READ_SMS
            ),
            PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                showMainContent()
                loadCallLogs()
            } else {
                showPermissionRequest()
            }
        }
    }

    private fun showPermissionRequest() {
        binding.layoutPermissions.visibility = View.VISIBLE
        binding.layoutMainContent.visibility = View.GONE
    }

    private fun showMainContent() {
        binding.layoutPermissions.visibility = View.GONE
        binding.layoutMainContent.visibility = View.VISIBLE
    }

    private fun loadCallLogs() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE

            val contactNumbers = withContext(Dispatchers.IO) {
                getSavedContactNumbers()
            }

            allCalls = withContext(Dispatchers.IO) {
                fetchCallLogs(contactNumbers)
            }

            allMessages = withContext(Dispatchers.IO) {
                fetchMessages(contactNumbers)
            }

            // Merge calls and messages by phone number
            val normalizedCalls = allCalls.groupBy { normalizePhoneNumber(it.number) }
            val normalizedMessages = allMessages.groupBy { normalizePhoneNumber(it.number) }
            val allNumbers = (normalizedCalls.keys + normalizedMessages.keys).toSet()

            uniqueCalls = allNumbers.map { normalizedNumber ->
                val calls = normalizedCalls[normalizedNumber] ?: emptyList()
                val messages = normalizedMessages[normalizedNumber] ?: emptyList()

                val mostRecentCall = calls.maxByOrNull { it.date }
                val mostRecentMessage = messages.maxByOrNull { it.date }

                // Determine which is more recent - call or message
                val useCall = when {
                    mostRecentCall == null -> false
                    mostRecentMessage == null -> true
                    else -> mostRecentCall.date >= mostRecentMessage.date
                }

                if (useCall && mostRecentCall != null) {
                    mostRecentCall.copy(
                        callCount = calls.size,
                        messageCount = messages.size
                    )
                } else if (mostRecentMessage != null) {
                    // Create a CallLogEntry from message data for display purposes
                    CallLogEntry(
                        id = mostRecentMessage.id,
                        number = mostRecentMessage.number,
                        name = mostRecentMessage.name,
                        type = -1, // Special type to indicate this is from message
                        date = mostRecentMessage.date,
                        duration = 0,
                        isContactSaved = mostRecentMessage.isContactSaved,
                        callCount = calls.size,
                        messageCount = messages.size
                    )
                } else {
                    null
                }
            }.filterNotNull().sortedByDescending { it.date }

            applyFilters()
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun fetchCallLogs(contactNumbers: Set<String>): List<CallLogEntry> {
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
            null,
            null,
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
                val number = it.getString(numberIndex) ?: "Unknown"
                val normalizedNumber = normalizePhoneNumber(number)

                val call = CallLogEntry(
                    id = it.getString(idIndex),
                    number = number,
                    name = it.getString(nameIndex),
                    type = it.getInt(typeIndex),
                    date = it.getLong(dateIndex),
                    duration = it.getLong(durationIndex),
                    isContactSaved = contactNumbers.any { contact ->
                        normalizedNumber.contains(contact) || contact.contains(normalizedNumber)
                    }
                )
                calls.add(call)
            }
        }

        return calls
    }

    private fun fetchMessages(contactNumbers: Set<String>): List<MessageEntry> {
        val messages = mutableListOf<MessageEntry>()

        val cursor = contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.TYPE,
                Telephony.Sms.DATE,
                Telephony.Sms.BODY,
                Telephony.Sms.READ
            ),
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )

        cursor?.use {
            val idIndex = it.getColumnIndex(Telephony.Sms._ID)
            val addressIndex = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val typeIndex = it.getColumnIndex(Telephony.Sms.TYPE)
            val dateIndex = it.getColumnIndex(Telephony.Sms.DATE)
            val bodyIndex = it.getColumnIndex(Telephony.Sms.BODY)
            val readIndex = it.getColumnIndex(Telephony.Sms.READ)

            while (it.moveToNext()) {
                val address = it.getString(addressIndex) ?: continue
                val normalizedNumber = normalizePhoneNumber(address)

                val message = MessageEntry(
                    id = it.getString(idIndex),
                    number = address,
                    name = null, // SMS doesn't have cached name like call log
                    type = it.getInt(typeIndex),
                    date = it.getLong(dateIndex),
                    body = it.getString(bodyIndex) ?: "",
                    isContactSaved = contactNumbers.any { contact ->
                        normalizedNumber.contains(contact) || contact.contains(normalizedNumber)
                    },
                    read = it.getInt(readIndex) == 1
                )
                messages.add(message)
            }
        }

        return messages
    }

    private fun getSavedContactNumbers(): Set<String> {
        val numbers = mutableSetOf<String>()
        
        val cursor = contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            null,
            null,
            null
        )
        
        cursor?.use {
            val numberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (it.moveToNext()) {
                it.getString(numberIndex)?.let { number ->
                    numbers.add(normalizePhoneNumber(number))
                }
            }
        }
        
        return numbers
    }

    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }

    private fun applyFilters() {
        val filteredItems = uniqueCalls.filter { item ->
            // Content type filter (Calls/Messages/All)
            val contentMatch = when (currentContentType) {
                CONTENT_TYPE_CALLS -> item.callCount > 0
                CONTENT_TYPE_MESSAGES -> item.messageCount > 0 && item.type == -1
                else -> true
            }

            // Call type filter (only applies to items with calls)
            val typeMatch = when (currentCallType) {
                CALL_TYPE_INCOMING -> item.type == CallLogEntry.TYPE_INCOMING
                CALL_TYPE_OUTGOING -> item.type == CallLogEntry.TYPE_OUTGOING
                CALL_TYPE_MISSED -> item.type == CallLogEntry.TYPE_MISSED
                else -> true
            }

            // Contact filter
            val contactMatch = when (currentContactFilter) {
                CONTACT_SAVED -> item.isContactSaved
                CONTACT_UNSAVED -> !item.isContactSaved
                else -> true
            }

            contentMatch && typeMatch && contactMatch
        }

        callAdapter.submitList(filteredItems)
        binding.textViewEmpty.visibility = if (filteredItems.isEmpty()) View.VISIBLE else View.GONE
        binding.textViewResultCount.text = "${filteredItems.size} contacts"
    }
}