package com.example.callbrowser

data class ContactEntry(
    val id: Long,
    val name: String?,
    val phoneNumber: String?,
    val email: String?,
    val photoUri: String?,
    val lastUpdatedTimestamp: Long,
    val timesContacted: Int,
    val lastTimeContacted: Long
)
