package com.example.myapplication

import org.junit.Assert.*
import org.junit.Test

class AutoSilentUnitTest {

    // --- 1. Phone Normalization Test ---
    @Test
    fun testPhoneNumberNormalization() {
        val rawInput1 = "+91 9876543210"
        val rawInput2 = "9876543210"
        val rawInput3 = "+91-98765-43210"
        val rawInput4 = "09876543210"

        val clean1 = rawInput1.filter { it.isDigit() }.takeLast(10)
        val clean2 = rawInput2.filter { it.isDigit() }.takeLast(10)
        val clean3 = rawInput3.filter { it.isDigit() }.takeLast(10)
        val clean4 = rawInput4.filter { it.isDigit() }.takeLast(10)

        assertEquals("9876543210", clean1)
        assertEquals(clean1, clean2)
        assertEquals(clean1, clean3)
        assertEquals(clean1, clean4)
    }

    // --- 2. Emergency Contact Matching Test ---
    @Test
    fun testEmergencyContactMatching() {
        val contacts = listOf(
            EmergencyContact("Father", "+91 9876543210"),
            EmergencyContact("Mother", "9123456789")
        )

        val incomingFather = "09876543210".filter { it.isDigit() }.takeLast(10)
        val incomingUnknown = "9998887776".filter { it.isDigit() }.takeLast(10)

        val isFatherEmergency = contacts.any {
            it.phone.filter { c -> c.isDigit() }.takeLast(10) == incomingFather
        }
        val isUnknownEmergency = contacts.any {
            it.phone.filter { c -> c.isDigit() }.takeLast(10) == incomingUnknown
        }

        assertTrue("Father should be recognized as an Emergency Contact", isFatherEmergency)
        assertFalse("Unknown number should NOT be recognized as Emergency Contact", isUnknownEmergency)
    }

    // --- 3. Emergency Call Bypass Threshold Rules ---
    @Test
    fun testEmergencyThresholdRules() {
        // Rule: Emergency Contact triggers at >= 3 calls in 5 minutes
        val isEmergencyContact = true
        assertFalse("2 calls from family should NOT trigger", evaluateEmergencyTrigger(isEmergencyContact, 2))
        assertTrue("3 calls from family MUST trigger emergency", evaluateEmergencyTrigger(isEmergencyContact, 3))
        assertTrue("4 calls from family MUST trigger emergency", evaluateEmergencyTrigger(isEmergencyContact, 4))

        // Rule: Unknown Caller triggers at >= 5 calls in 5 minutes
        val isUnknownCaller = false
        assertFalse("3 calls from unknown should NOT trigger", evaluateEmergencyTrigger(isUnknownCaller, 3))
        assertFalse("4 calls from unknown should NOT trigger", evaluateEmergencyTrigger(isUnknownCaller, 4))
        assertTrue("5 calls from unknown MUST trigger emergency", evaluateEmergencyTrigger(isUnknownCaller, 5))
        assertTrue("6 calls from unknown MUST trigger emergency", evaluateEmergencyTrigger(isUnknownCaller, 6))
    }

    private fun evaluateEmergencyTrigger(isEmergencyContact: Boolean, callCount: Int): Boolean {
        return if (isEmergencyContact) {
            callCount >= 3
        } else {
            callCount >= 5
        }
    }

    // --- 4. Sliding 5-Minute Window Purge Test ---
    @Test
    fun testSlidingWindow5MinutePurge() {
        val now = 1000000000L
        val fiveMinutesMs = 5 * 60 * 1000L // 300,000 ms

        val timestamps = listOf(
            now - 400000L, // 6.6 mins ago (expired)
            now - 350000L, // 5.8 mins ago (expired)
            now - 200000L, // 3.3 mins ago (valid)
            now - 100000L, // 1.6 mins ago (valid)
            now            // current call (valid)
        )

        val activeCalls = timestamps.filter { now - it <= fiveMinutesMs }
        assertEquals("Only 3 calls within the last 5 minutes should remain", 3, activeCalls.size)
    }

    // --- 5. College Timetable Window Calculation Test ---
    @Test
    fun testScheduleWindowCalculation() {
        // College hours: 09:00 (540m) to 16:30 (990m)
        val startH = 9
        val startM = 0
        val endH = 16
        val endM = 30

        assertTrue("10:15 AM should be inside class hours", isInsideSchedule(10, 15, startH, startM, endH, endM))
        assertTrue("09:00 AM (exact start) should be inside class hours", isInsideSchedule(9, 0, startH, startM, endH, endM))
        assertTrue("16:30 PM (exact end) should be inside class hours", isInsideSchedule(16, 30, startH, startM, endH, endM))

        assertFalse("08:45 AM should be outside class hours", isInsideSchedule(8, 45, startH, startM, endH, endM))
        assertFalse("17:00 PM should be outside class hours", isInsideSchedule(17, 0, startH, startM, endH, endM))
        assertFalse("21:30 PM should be outside class hours", isInsideSchedule(21, 30, startH, startM, endH, endM))
    }

    private fun isInsideSchedule(currentH: Int, currentM: Int, startH: Int, startM: Int, endH: Int, endM: Int): Boolean {
        val currentMinutes = currentH * 60 + currentM
        val startMinutes = startH * 60 + startM
        val endMinutes = endH * 60 + endM

        return if (startMinutes <= endMinutes) {
            currentMinutes in startMinutes..endMinutes
        } else {
            currentMinutes >= startMinutes || currentMinutes <= endMinutes
        }
    }
}
