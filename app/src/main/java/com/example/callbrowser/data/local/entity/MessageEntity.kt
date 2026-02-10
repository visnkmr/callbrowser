package com.example.callbrowser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["address"]),
        Index(value = ["date"]),
        Index(value = ["normalizedAddress"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val address: String,
    val normalizedAddress: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val body: String,
    val isContactSaved: Boolean,
    val read: Boolean,
    val syncTimestamp: Long = System.currentTimeMillis()
)