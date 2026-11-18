package com.example.sync_up

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventScheduleValidatorTest {
    @Test
    fun endMustBeAfterStartAcrossDatesAndTimes() {
        assertTrue(EventScheduleValidator.isEndAfterStart("2026-11-20", "09:00", "2026-11-20", "10:00"))
        assertTrue(EventScheduleValidator.isEndAfterStart("2026-11-20", "23:00", "2026-11-21", "01:00"))
        assertFalse(EventScheduleValidator.isEndAfterStart("2026-11-20", "09:00", "2026-11-20", "09:00"))
        assertFalse(EventScheduleValidator.isEndAfterStart("2026-11-20", "09:00", "2026-11-19", "23:00"))
    }
}
