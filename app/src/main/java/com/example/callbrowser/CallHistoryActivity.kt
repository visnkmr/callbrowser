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
import androidx.recyclerview.widget.RecyclerView
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
    
    // Pagination state
    private var currentPage = 0
    private var isLoading = false
    private var hasMoreData = true
    private val pageSize = 50
    private val loadedItems = mutableListOf<Any>()

    companion object {
        const val EXTRA_PHONE_NUMBER = "extra_phone_number"
        const val EXTRA_CONTACT_NAME = "extra_contact_name"
        private const val CALL_PERMISSION_REQUEST = 1001
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
            loadInitialData()
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
            
            // Add scroll listener for infinite loading
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    
                    val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition()
                    
                    // Load more when user scrolls to bottom (last 10 items)
                    if (!isLoading && hasMoreData) {
                        if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount - 10
                            && firstVisibleItemPosition >= 0) {
                            loadMoreItems()
                        }
                    }
                }
            })
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

    private fun loadInitialData() {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            isLoading = true
            
            // Load stats (total counts from all data)
            val counts = withContext(Dispatchers.IO) {
                repository.getHistoryCounts(currentPhoneNumber)
            }
            
            binding.textViewTotalCalls.text = "${counts.first} calls"
            binding.textViewTotalMessages.text = "${counts.second} messages"
            binding.textViewTotalTalkTime.text = "Total: ${formatDuration(counts.third)}"
            
            // Load first page
            currentPage = 0
            loadedItems.clear()
            hasMoreData = true
            
            val pageData = withContext(Dispatchers.IO) {
                repository.getDetailedHistoryPaged(currentPhoneNumber, currentPage, pageSize)
            }
            
            if (pageData.isEmpty()) {
                binding.textViewEmpty.visibility = View.VISIBLE
                hasMoreData = false
            } else {
                binding.textViewEmpty.visibility = View.GONE
                loadedItems.addAll(pageData)
                adapter.submitList(loadedItems.toList())
                
                // Check if we have more data
                hasMoreData = pageData.size >= pageSize
            }
            
            isLoading = false
            binding.progressBar.visibility = View.GONE
        }
    }

    private fun loadMoreItems() {
        if (isLoading || !hasMoreData) return
        
        lifecycleScope.launch {
            isLoading = true
            currentPage++
            
            val pageData = withContext(Dispatchers.IO) {
                repository.getDetailedHistoryPaged(currentPhoneNumber, currentPage, pageSize)
            }
            
            if (pageData.isNotEmpty()) {
                loadedItems.addAll(pageData)
                adapter.submitList(loadedItems.toList())
                hasMoreData = pageData.size >= pageSize
            } else {
                hasMoreData = false
            }
            
            isLoading = false
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
