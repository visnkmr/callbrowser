package com.example.callbrowser.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.callbrowser.data.local.entity.ContactEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY lastUpdatedTimestamp DESC")
    fun getAllContactsByLastUpdated(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts ORDER BY timesContacted DESC")
    fun getAllContactsByTimesContacted(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE contactId = :contactId LIMIT 1")
    suspend fun getContactById(contactId: Long): ContactEntity?

    @Query("SELECT MAX(lastUpdatedTimestamp) FROM contacts")
    suspend fun getLastSyncTimestamp(): Long?

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<ContactEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Query("DELETE FROM contacts WHERE contactId = :contactId")
    suspend fun deleteContact(contactId: Long)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()

    @Query("SELECT * FROM contacts WHERE lastUpdatedTimestamp > :timestamp")
    suspend fun getContactsUpdatedAfter(timestamp: Long): List<ContactEntity>
}
