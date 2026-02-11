package com.example.callbrowser.data.sync

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import com.example.callbrowser.data.repository.ContactRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ContactObserver(
    private val context: Context,
    private val contactRepository: ContactRepository,
    private val onSyncComplete: () -> Unit = {}
) : ContentObserver(Handler(Looper.getMainLooper())) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastSyncTime = 0L

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime > 1000) {
            lastSyncTime = currentTime
            syncContacts()
        }
    }

    private fun syncContacts() {
        scope.launch {
            try {
                contactRepository.syncContacts()
                onSyncComplete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun register() {
        context.contentResolver.registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI,
            true,
            this
        )
    }

    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}
