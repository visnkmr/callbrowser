package com.example.callbrowser.data.repository

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.Telephony
import com.example.callbrowser.CallLogEntry
import com.example.callbrowser.MessageEntry
import com.example.callbrowser.data.local.AppDatabase
import com.example.callbrowser.data.local.entity.CallEntity
import com.example.callbrowser.data.local.entity.MessageEntity
import com.example.callbrowser.data.sync.CallLogObserver
import com.example.callbrowser.data.sync.SmsObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CommunicationRepository(private val context: Context) {
    
    private val database = AppDatabase.getDatabase(context)
    private val callDao = database.callDao()
    private val messageDao = database.messageDao()
    
    private var callLogObserver: CallLogObserver? = null
    private var smsObserver: SmsObserver? = null
    
    // Combined data flow for main list
    fun getCombinedCommunicationList(): Flow<List<CallLogEntry>> {
        return combine(
            callDao.getUniqueNumbersWithCallCount(),
            messageDao.getUniqueNumbersWithMessageCount()
        ) { callSummaries, messageSummaries ->
            
            // Create a map of normalized numbers to message summaries
            val messageMap = messageSummaries.associateBy { it.normalizedAddress }
            
            // Create a map of normalized numbers to call summaries
            val callMap = callSummaries.associateBy { it.normalizedNumber }
            
            // Get all unique numbers
            val allNumbers = (callMap.keys + messageMap.keys).toSet()
            
            allNumbers.mapNotNull { normalizedNumber ->
                val callSummary = callMap[normalizedNumber]
                val messageSummary = messageMap[normalizedNumber]
                
                // Determine which is more recent
                val useCall = when {
                    callSummary == null -> false
                    messageSummary == null -> true
                    else -> callSummary.date >= messageSummary.date
                }
                
                if (useCall && callSummary != null) {
                    CallLogEntry(
                        id = normalizedNumber, // Use number as ID for summary
                        number = callSummary.number,
                        name = callSummary.name,
                        type = callSummary.type,
                        date = callSummary.date,
                        duration = callSummary.totalDuration / maxOf(callSummary.callCount, 1), // Average duration
                        isContactSaved = callSummary.isContactSaved,
                        callCount = callSummary.callCount,
                        messageCount = messageSummary?.messageCount ?: 0
                    )
                } else if (messageSummary != null) {
                    CallLogEntry(
                        id = normalizedNumber,
                        number = messageSummary.address,
                        name = messageSummary.name,
                        type = -1, // Indicates message-only
                        date = messageSummary.date,
                        duration = 0,
                        isContactSaved = messageSummary.isContactSaved,
                        callCount = callSummary?.callCount ?: 0,
                        messageCount = messageSummary.messageCount
                    )
                } else {
                    null
                }
            }.sortedByDescending { it.date }
        }
    }
    
    // Get detailed history for a specific number
    suspend fun getDetailedHistory(phoneNumber: String): List<Any> {
        val normalizedNumber = normalizePhoneNumber(phoneNumber)
        
        val calls = callDao.getCallsForNumberSync(normalizedNumber).map { entity ->
            CallLogEntry(
                id = entity.id,
                number = entity.number,
                name = entity.name,
                type = entity.type,
                date = entity.date,
                duration = entity.duration,
                isContactSaved = entity.isContactSaved
            )
        }
        
        val messages = messageDao.getMessagesForNumberSync(normalizedNumber).map { entity ->
            MessageEntry(
                id = entity.id,
                number = entity.address,
                name = entity.name,
                type = entity.type,
                date = entity.date,
                body = entity.body,
                isContactSaved = entity.isContactSaved,
                read = entity.read
            )
        }
        
        // Combine and sort by date
        return (calls + messages).sortedByDescending {
            when (it) {
                is CallLogEntry -> it.date
                is MessageEntry -> it.date
                else -> 0L
            }
        }
    }
    
    // Initial full sync
    suspend fun performInitialSync() = withContext(Dispatchers.IO) {
        val contactNumbers = fetchContactNumbers()
        
        // Sync calls
        syncAllCalls(contactNumbers)
        
        // Sync messages
        syncAllMessages(contactNumbers)
    }
    
    private suspend fun syncAllCalls(contactNumbers: Set<String>) {
        val existingIds = callDao.getAllCalls().map { it.map { call -> call.id } }.firstOrNull() ?: emptyList()
        
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
            null,
            null,
            CallLog.Calls.DATE + " DESC"
        )
        
        val calls = mutableListOf<CallEntity>()
        
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
                val id = it.getString(idIndex)
                
                // Skip if already exists
                if (id in existingIds) continue
                
                calls.add(
                    CallEntity(
                        id = id,
                        number = number,
                        normalizedNumber = normalizedNumber,
                        name = it.getString(nameIndex),
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        duration = it.getLong(durationIndex),
                        isContactSaved = contactNumbers.any { contact ->
                            normalizedNumber.contains(contact) || contact.contains(normalizedNumber)
                        }
                    )
                )
            }
        }
        
        if (calls.isNotEmpty()) {
            callDao.insertCalls(calls)
        }
    }
    
    private suspend fun syncAllMessages(contactNumbers: Set<String>) {
        val existingIds = messageDao.getAllMessages().map { it.map { msg -> msg.id } }.firstOrNull() ?: emptyList()
        
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
            null,
            null,
            Telephony.Sms.DATE + " DESC"
        )
        
        val messages = mutableListOf<MessageEntity>()
        
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
                val id = it.getString(idIndex)
                
                // Skip if already exists
                if (id in existingIds) continue
                
                messages.add(
                    MessageEntity(
                        id = id,
                        address = address,
                        normalizedAddress = normalizedAddress,
                        name = null,
                        type = it.getInt(typeIndex),
                        date = it.getLong(dateIndex),
                        body = it.getString(bodyIndex) ?: "",
                        isContactSaved = contactNumbers.any { contact ->
                            normalizedAddress.contains(contact) || contact.contains(normalizedAddress)
                        },
                        read = it.getInt(readIndex) == 1
                    )
                )
            }
        }
        
        if (messages.isNotEmpty()) {
            messageDao.insertMessages(messages)
        }
    }
    
    private fun fetchContactNumbers(): Set<String> {
        val numbers = mutableSetOf<String>()
        
        val cursor = context.contentResolver.query(
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
    
    fun startObservers() {
        if (callLogObserver == null) {
            callLogObserver = CallLogObserver(context, callDao).apply {
                register()
            }
        }
        
        if (smsObserver == null) {
            smsObserver = SmsObserver(context, messageDao).apply {
                register()
            }
        }
    }
    
    fun stopObservers() {
        callLogObserver?.unregister()
        callLogObserver = null
        
        smsObserver?.unregister()
        smsObserver = null
    }
    
    private fun normalizePhoneNumber(number: String): String {
        return number.replace(Regex("[^0-9]"), "")
    }
}