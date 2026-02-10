package com.example.callbrowser.data.sync

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import com.example.callbrowser.data.local.dao.CallDao
import com.example.callbrowser.data.local.entity.CallEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CallLogObserver(
    private val context: Context,
    private val callDao: CallDao,
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
            syncRecentCalls()
        }
    }
    
    private fun syncRecentCalls() {
        scope.launch {
            try {
                val lastTimestamp = callDao.getLastSyncTimestamp() ?: 0L
                val newCalls = fetchRecentCalls(lastTimestamp)
                if (newCalls.isNotEmpty()) {
                    callDao.insertCalls(newCalls)
                    withContext(Dispatchers.Main) {
                        onSyncComplete()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun fetchRecentCalls(sinceTimestamp: Long): List<CallEntity> {
        val calls = mutableListOf<CallEntity>()
        
        val cursor = context.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION
            ),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(sinceTimestamp.toString()),
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
                val normalizedNumber = normalizePhoneNumber(number)
                
                calls.add(
                    CallEntity(
                        id = it.getString(idIndex),
                        number = number,
                        normalizedNumber = normalizedNumber,
                        name = it.getString(nameIndex),
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        duration = it.getLong(durationIndex),
                        isContactSaved = false // Will be updated by repository
                    )
                )
            }
        }
        
        return calls
    }
    
    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }
    
    fun register() {
        context.contentResolver.registerContentObserver(
            CallLog.Calls.CONTENT_URI,
            true,
            this
        )
    }
    
    fun unregister() {
        context.contentResolver.unregisterContentObserver(this)
    }
}