package com.example.callbrowser.data.repository

import android.content.Context
import android.provider.ContactsContract
import com.example.callbrowser.data.local.dao.ContactDao
import com.example.callbrowser.data.local.entity.ContactEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ContactRepository(
    private val context: Context,
    private val contactDao: ContactDao
) {

    fun getContacts(sortBy: SortOption = SortOption.NAME_ASC): Flow<List<ContactEntity>> {
        return when (sortBy) {
            SortOption.NAME_ASC -> contactDao.getAllContacts()
            SortOption.NAME_DESC -> contactDao.getAllContacts().map { it.sortedByDescending { c -> c.name?.lowercase() ?: "" } }
            SortOption.LAST_UPDATED -> contactDao.getAllContactsByLastUpdated()
            SortOption.MOST_CONTACTED -> contactDao.getAllContactsByTimesContacted()
        }
    }

    suspend fun syncContacts(): Boolean = withContext(Dispatchers.IO) {
        try {
            val lastSyncTimestamp = contactDao.getLastSyncTimestamp() ?: 0L
            val contacts = fetchContactsFromProvider(lastSyncTimestamp)

            if (contacts.isNotEmpty()) {
                contactDao.insertContacts(contacts)
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun performFullSync(): Boolean = withContext(Dispatchers.IO) {
        try {
            val contacts = fetchContactsFromProvider(0L)
            contactDao.deleteAllContacts()
            contactDao.insertContacts(contacts)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun hasContacts(): Boolean = withContext(Dispatchers.IO) {
        contactDao.getContactCount() > 0
    }

    private fun fetchContactsFromProvider(sinceTimestamp: Long): List<ContactEntity> {
        val contacts = mutableListOf<ContactEntity>()

        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
            ContactsContract.Contacts.PHOTO_URI,
            ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP,
            ContactsContract.Contacts.TIMES_CONTACTED,
            ContactsContract.Contacts.LAST_TIME_CONTACTED,
            ContactsContract.Contacts.HAS_PHONE_NUMBER
        )

        val selection = if (sinceTimestamp > 0) {
            "${ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP} > ?"
        } else null

        val selectionArgs = if (sinceTimestamp > 0) {
            arrayOf(sinceTimestamp.toString())
        } else null

        context.contentResolver.query(
            ContactsContract.Contacts.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY + " ASC"
        )?.use { cursor ->
            val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
            val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
            val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
            val updatedIndex = cursor.getColumnIndex(ContactsContract.Contacts.CONTACT_LAST_UPDATED_TIMESTAMP)
            val timesContactedIndex = cursor.getColumnIndex(ContactsContract.Contacts.TIMES_CONTACTED)
            val lastContactedIndex = cursor.getColumnIndex(ContactsContract.Contacts.LAST_TIME_CONTACTED)
            val hasPhoneIndex = cursor.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

            while (cursor.moveToNext()) {
                val contactId = cursor.getLong(idIndex)
                val name = cursor.getString(nameIndex)
                val photoUri = cursor.getString(photoIndex)
                val lastUpdated = if (updatedIndex != -1) cursor.getLong(updatedIndex) else 0L
                val timesContacted = cursor.getInt(timesContactedIndex)
                val lastTimeContacted = cursor.getLong(lastContactedIndex)
                val hasPhone = cursor.getInt(hasPhoneIndex) > 0

                var phoneNumber: String? = null
                if (hasPhone) {
                    phoneNumber = getPrimaryPhoneNumber(contactId)
                }

                contacts.add(
                    ContactEntity(
                        contactId = contactId,
                        name = name,
                        phoneNumber = phoneNumber,
                        email = null,
                        photoUri = photoUri,
                        lastUpdatedTimestamp = lastUpdated,
                        timesContacted = timesContacted,
                        lastTimeContacted = lastTimeContacted
                    )
                )
            }
        }

        return contacts
    }

    private fun getPrimaryPhoneNumber(contactId: Long): String? {
        val phoneProjection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId.toString())

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            phoneProjection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0)
            }
        }
        return null
    }

    enum class SortOption {
        NAME_ASC,
        NAME_DESC,
        LAST_UPDATED,
        MOST_CONTACTED
    }
}
