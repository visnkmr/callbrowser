package com.example.callbrowser

import org.junit.Test
import org.junit.Assert.*

class CallLogUnitTest {
    @Test
    fun testCallLogEntryCreation() {
        val call = CallLogEntry(
            id = "1",
            number = "+1234567890",
            name = "John Doe",
            type = CallLogEntry.TYPE_INCOMING,
            date = 1704067200000,
            duration = 60,
            isContactSaved = true
        )
        
        assertEquals("1", call.id)
        assertEquals("+1234567890", call.number)
        assertEquals("John Doe", call.name)
        assertEquals(CallLogEntry.TYPE_INCOMING, call.type)
        assertTrue(call.isContactSaved)
    }
}