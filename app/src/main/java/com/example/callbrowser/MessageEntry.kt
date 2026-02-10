package com.example.callbrowser

data class MessageEntry(
    val id: String,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val body: String,
    val isContactSaved: Boolean,
    val messageCount: Int = 1,
    val read: Boolean = true
) {
    companion object {
        const val TYPE_INBOX = 1
        const val TYPE_SENT = 2
        const val TYPE_DRAFT = 3
        const val TYPE_OUTBOX = 4
        const val TYPE_FAILED = 5
        const val TYPE_QUEUED = 6
    }
}