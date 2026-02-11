package com.example.callbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callbrowser.data.repository.CommunicationRepository
import com.example.callbrowser.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var callAdapter: CallLogAdapter
    private lateinit var repository: CommunicationRepository
    private var uniqueCalls = listOf<CallLogEntry>()

    private var currentCallType = CALL_TYPE_ALL
    private var currentContactFilter = CONTACT_ALL
    private var currentContentType = CONTENT_TYPE_ALL
    private var numbersOnlyMessages = false
    private var dataCollectionJob: Job? = null

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
        private const val CONTENT_TYPE_MESSAGES_NUMBERS_ONLY = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = CommunicationRepository(this)

        setupSpinners()
        setupRecyclerView()
        setupButtons()

        checkPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancel data collection job to prevent memory leaks
        dataCollectionJob?.cancel()
    }

    override fun onResume() {
        super.onResume()
        // Start listening for system changes
        repository.startObservers()
    }

    override fun onPause() {
        super.onPause()
        // Stop listening for system changes
        repository.stopObservers()
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
                val previousContentType = currentContentType
                currentContentType = position
                val newNumbersOnlyMessages = (position == CONTENT_TYPE_MESSAGES_NUMBERS_ONLY)
                
                // Only reload if the numbersOnlyMessages filter actually changed
                if (newNumbersOnlyMessages != numbersOnlyMessages || position == CONTENT_TYPE_MESSAGES_NUMBERS_ONLY || previousContentType == CONTENT_TYPE_MESSAGES_NUMBERS_ONLY) {
                    numbersOnlyMessages = newNumbersOnlyMessages
                    reloadData()
                } else {
                    applyFilters()
                }
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

        binding.buttonViewContacts.setOnClickListener {
            startActivity(Intent(this, ContactsActivity::class.java))
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
            loadData()
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
                loadData()
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

    private fun loadData() {
        // Show loading state
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewLoading.visibility = View.VISIBLE
        binding.recyclerViewCalls.visibility = View.GONE
        binding.textViewEmpty.visibility = View.GONE

        // Cancel any existing collection job
        dataCollectionJob?.cancel()

        // Collect data from Room (instant)
        dataCollectionJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getCombinedCommunicationList(numbersOnlyMessages).collectLatest { calls ->
                    uniqueCalls = calls
                    applyFilters()
                    
                    // Hide loading indicators
                    binding.progressBar.visibility = View.GONE
                    binding.textViewLoading.visibility = View.GONE
                    binding.recyclerViewCalls.visibility = View.VISIBLE
                }
            }
        }

        // Perform initial sync in background (one-time)
        lifecycleScope.launch(Dispatchers.IO) {
            repository.performInitialSync()
        }
    }

    private fun reloadData() {
        // Show loading state when filter changes
        binding.progressBar.visibility = View.VISIBLE
        binding.textViewLoading.visibility = View.VISIBLE
        binding.recyclerViewCalls.visibility = View.GONE
        binding.textViewEmpty.visibility = View.GONE
        
        // Cancel existing job
        dataCollectionJob?.cancel()
        
        // Start fresh collection with new filter
        dataCollectionJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.getCombinedCommunicationList(numbersOnlyMessages).collectLatest { calls ->
                    uniqueCalls = calls
                    applyFilters()
                    
                    // Hide loading indicators
                    binding.progressBar.visibility = View.GONE
                    binding.textViewLoading.visibility = View.GONE
                    binding.recyclerViewCalls.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun applyFilters() {
        val filteredItems = uniqueCalls.filter { item ->
            // Content type filter (Calls/Messages/All)
            val contentMatch = when (currentContentType) {
                CONTENT_TYPE_CALLS -> item.callCount > 0
                CONTENT_TYPE_MESSAGES -> item.messageCount > 0 && item.type == -1
                CONTENT_TYPE_MESSAGES_NUMBERS_ONLY -> item.messageCount > 0 && item.type == -1
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