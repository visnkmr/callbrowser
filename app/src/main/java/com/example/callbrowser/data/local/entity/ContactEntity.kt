package com.example.callbrowser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "contacts",
    indices = [
        Index(value = ["contactId"], unique = true),
        Index(value = ["lastUpdatedTimestamp"]),
        Index(value = ["name"])
    ]
)
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val contactId: Long,
    val name: String?,
    val phoneNumber: String?,
    val email: String?,
    val photoUri: String?,
    val lastUpdatedTimestamp: Long,
    val timesContacted: Int,
    val lastTimeContacted: Long,
    val syncTimestamp: Long = System.currentTimeMillis()
)
