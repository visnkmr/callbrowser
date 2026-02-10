package com.example.callbrowser.data.sync

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.example.callbrowser.data.local.dao.MessageDao
import com.example.callbrowser.data.local.entity.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsObserver(
    private val context: Context,
    private val messageDao: MessageDao,
    private val onSyncComplete: () -> Unit = {}
) : ContentObserver(Handler(Looper.getMainLooper())) {
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastSyncTime = 0L
    
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        // Debounce rapid changes
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSyncTime > 1000) {
            lastSyncTime = currentTime
            syncRecentMessages()
        }
    }
    
    private fun syncRecentMessages() {
        scope.launch {
            try {
                val lastTimestamp = messageDao.getLastSyncTimestamp() ?: 0L
                val newMessages = fetchRecentMessages(lastTimestamp)
                if (newMessages.isNotEmpty()) {
                    messageDao.insertMessages(newMessages)
                    withContext(Dispatchers.Main) {
                        onSyncComplete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun fetchRecentMessages(sinceTimestamp: Long): List<MessageEntity> {
        val messages = mutableListOf<MessageEntity>()
        
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                Telephony.Sms.ADDRESS,
                Telephony.Sms.TYPE,
                Telephony.Sms.DATE,
                Telephony.Sms.BODY,
                Telephony.Sms.READ
            ),
            "${Telephony.Sms.DATE} > ?",
            arrayOf(sinceTimestamp.toString()),
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
                val normalizedAddress = normalizePhoneNumber(address)
                
                messages.add(
                    MessageEntity(
                        id = it.getString(idIndex),
                        address = address,
                        normalizedAddress = normalizedAddress,
                        name = null, // Will be updated by repository
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        body = it.getString(bodyIndex) ?: "",
                        isContactSaved = false, // Will be updated by repository
                        read = it.getInt(readIndex) == 1
                    )
                )
            }
        }
        
        return messages
    }
    
    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }
    
    fun register() {
        context.contentResolver.registerContentObserver(
            Telephony.Sms.CONTENT_URI,
            true,
            this
        )
    }
    
    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}