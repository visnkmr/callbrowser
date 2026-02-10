package com.example.callbrowser

data class CallLogEntry(
    val id: String,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long,
    val isContactSaved: Boolean,
    val callCount: Int = 1
) {
    companion object {
        const val TYPE_INCOMING = 1
        const val TYPE_OUTGOING = 2
        const val TYPE_MISSED = 3
        const val TYPE_VOICEMAIL = 4
        const val TYPE_REJECTED = 5
        const val TYPE_BLOCKED = 6
    }
}