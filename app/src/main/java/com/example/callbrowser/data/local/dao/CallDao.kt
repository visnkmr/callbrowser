package com.example.callbrowser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callbrowser.data.local.entity.CallEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallDao {
    
    @Query("SELECT * FROM calls ORDER BY date DESC")
    fun getAllCalls(): Flow<List<CallEntity>>
    
    @Query("SELECT * FROM calls WHERE normalizedNumber = :normalizedNumber ORDER BY date DESC")
    fun getCallsForNumber(normalizedNumber: String): Flow<List<CallEntity>>
    
    @Query("SELECT * FROM calls WHERE normalizedNumber = :normalizedNumber ORDER BY date DESC")
    suspend fun getCallsForNumberSync(normalizedNumber: String): List<CallEntity>
    
    @Query("""
        SELECT normalizedNumber, number, name, MAX(date) as date, 
               COUNT(*) as callCount, SUM(duration) as totalDuration,
               MAX(type) as type, isContactSaved
        FROM calls 
        GROUP BY normalizedNumber 
        ORDER BY date DESC
    """)
    fun getUniqueNumbersWithCallCount(): Flow<List<NumberSummary>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalls(calls: List<CallEntity>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCall(call: CallEntity)
    
    @Query("DELETE FROM calls WHERE id = :id")
    suspend fun deleteCall(id: String)
    
    @Query("DELETE FROM calls")
    suspend fun deleteAllCalls()
    
    @Query("SELECT MAX(date) FROM calls")
    suspend fun getLastSyncTimestamp(): Long?
    
    data class NumberSummary(
        val normalizedNumber: String,
        val number: String,
        val name: String?,
        val date: Long,
        val callCount: Int,
        val totalDuration: Long,
        val type: Int,
        val isContactSaved: Boolean
    )
}