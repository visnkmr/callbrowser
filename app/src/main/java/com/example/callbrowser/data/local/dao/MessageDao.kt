package com.example.callbrowser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callbrowser.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    
    @Query("SELECT * FROM messages ORDER BY date DESC")
    fun getAllMessages(): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE normalizedAddress = :normalizedNumber ORDER BY date DESC")
    fun getMessagesForNumber(normalizedNumber: String): Flow<List<MessageEntity>>
    
    @Query("SELECT * FROM messages WHERE normalizedAddress = :normalizedNumber ORDER BY date DESC")
    suspend fun getMessagesForNumberSync(normalizedNumber: String): List<MessageEntity>
    
    @Query("""
        SELECT normalizedAddress, address, name, MAX(date) as date, 
               COUNT(*) as messageCount, MAX(type) as type, 
               isContactSaved, read
        FROM messages 
        WHERE address GLOB '*[0-9]*'
        GROUP BY normalizedAddress 
        ORDER BY date DESC
    """)
    fun getUniqueNumbersWithMessageCount(): Flow<List<NumberMessageSummary>>

    @Query("""
        SELECT normalizedAddress, address, name, MAX(date) as date, 
               COUNT(*) as messageCount, MAX(type) as type, 
               isContactSaved, read
        FROM messages 
        GROUP BY normalizedAddress 
        ORDER BY date DESC
    """)
    fun getUniqueNumbersWithMessageCountAll(): Flow<List<NumberMessageSummary>>
    
    @Query("SELECT COUNT(*) FROM messages WHERE normalizedAddress = :normalizedNumber")
    suspend fun getMessageCountForNumber(normalizedNumber: String): Int
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)
    
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
    
    @Query("DELETE FROM messages")
    suspend fun deleteAllMessages()
    
    @Query("SELECT MAX(date) FROM messages")
    suspend fun getLastSyncTimestamp(): Long?
    
    data class NumberMessageSummary(
        val normalizedAddress: String,
        val address: String,
        val name: String?,
        val date: Long,
        val messageCount: Int,
        val type: Int,
        val isContactSaved: Boolean,
        val read: Boolean
    )
}