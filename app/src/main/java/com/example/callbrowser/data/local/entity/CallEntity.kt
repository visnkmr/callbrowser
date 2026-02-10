package com.example.callbrowser.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calls",
    indices = [
        Index(value = ["number"]),
        Index(value = ["date"]),
        Index(value = ["normalizedNumber"])
    ]
)
data class CallEntity(
    @PrimaryKey
    val id: String,
    val number: String,
    val normalizedNumber: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val isContactSaved: Boolean,
    val syncTimestamp: Long = System.currentTimeMillis()
)