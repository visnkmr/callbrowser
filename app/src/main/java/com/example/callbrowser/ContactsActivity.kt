package com.example.callbrowser

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callbrowser.data.local.AppDatabase
import com.example.callbrowser.data.local.entity.ContactEntity
import com.example.callbrowser.data.repository.ContactRepository
import com.example.callbrowser.databinding.ActivityContactsBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityContactsBinding
    private lateinit var contactsAdapter: ContactsAdapter
    private lateinit var contactRepository: ContactRepository
    private var currentSortOption = ContactRepository.SortOption.LAST_UPDATED

    companion object {
        const val SORT_BY_NAME_ASC = 0
        const val SORT_BY_NAME_DESC = 1
        const val SORT_BY_LAST_UPDATED = 2
        const val SORT_BY_MOST_CONTACTED = 3
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val database = AppDatabase.getDatabase(this)
        contactRepository = ContactRepository(this, database.contactDao())

        setupToolbar()
        setupSpinner()
        setupRecyclerView()

        if (hasContactsPermission()) {
            loadContacts()
        } else {
            showEmpty()
        }
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupSpinner() {
        val sortAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.sort_options,
            android.R.layout.simple_spinner_item
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerSortBy.adapter = sortAdapter
        binding.spinnerSortBy.setSelection(SORT_BY_LAST_UPDATED)

        binding.spinnerSortBy.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentSortOption = when (position) {
                    SORT_BY_NAME_ASC -> ContactRepository.SortOption.NAME_ASC
                    SORT_BY_NAME_DESC -> ContactRepository.SortOption.NAME_DESC
                    SORT_BY_LAST_UPDATED -> ContactRepository.SortOption.LAST_UPDATED
                    SORT_BY_MOST_CONTACTED -> ContactRepository.SortOption.MOST_CONTACTED
                    else -> ContactRepository.SortOption.NAME_ASC
                }
                loadContacts()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupRecyclerView() {
        contactsAdapter = ContactsAdapter { contact ->
            contact.phoneNumber?.let { phone ->
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phone")
                }
                startActivity(intent)
            }
        }

        binding.recyclerViewContacts.apply {
            layoutManager = LinearLayoutManager(this@ContactsActivity)
            adapter = contactsAdapter
        }
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun loadContacts() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                contactRepository.getContacts(currentSortOption).collectLatest { contacts ->
                    contactsAdapter.submitList(contacts.map { it.toContactEntry() })
                    binding.textViewResultCount.text = "${contacts.size} contacts"
                    binding.textViewEmpty.visibility = if (contacts.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        // Perform initial sync if needed
        lifecycleScope.launch(Dispatchers.IO) {
            if (!contactRepository.hasContacts()) {
                contactRepository.performFullSync()
            }
        }
    }

    private fun syncContacts() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val success = contactRepository.syncContacts()
            withContext(Dispatchers.Main) {
                binding.progressBar.visibility = View.GONE
            }
        }
    }

    private fun ContactEntity.toContactEntry(): ContactEntry {
        return ContactEntry(
            id = contactId,
            name = name,
            phoneNumber = phoneNumber,
            email = email,
            photoUri = photoUri,
            lastUpdatedTimestamp = lastUpdatedTimestamp,
            timesContacted = timesContacted,
            lastTimeContacted = lastTimeContacted
        )
    }

    private fun showEmpty() {
        binding.textViewEmpty.visibility = View.VISIBLE
        binding.textViewResultCount.text = "0 contacts"
    }
}
