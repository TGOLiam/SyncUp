package com.example.sync_up.data.remote

import com.example.sync_up.DraftPhase
import com.example.sync_up.EventCreationDraft
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.ZoneId

class EventRequestMapperTest {
    @Test
    fun mapsTimeAndStableIdsForRetries() {
        val draft = EventCreationDraft().apply {
            name = "Event"
            venue = "Venue"
            startDateIso = "2026-11-20"
            startTimeIso = "09:00"
            endDateIso = "2026-11-20"
            endTimeIso = "17:00"
            phases.add(DraftPhase("Setup", 1, 30))
            channels.add("Crew")
        }
        val zone = ZoneId.of("Asia/Manila")
        val first = EventRequestMapper.createArguments("event-id", draft, zone)
        val retry = EventRequestMapper.createArguments("event-id", draft, zone)
        assertEquals(first, retry)
        assertEquals("2026-11-20T09:00+08:00", first.getValue("p_start_at").jsonPrimitive.content)
        assertEquals("01:30", first.getValue("p_phases").jsonArray[0].jsonObject.getValue("duration").jsonPrimitive.content)
    }

    @Test
    fun rejectsNonexistentLocalTime() {
        val draft = EventCreationDraft().apply {
            name = "Event"
            venue = "Venue"
            startDateIso = "2026-03-08"
            startTimeIso = "02:30"
            endDateIso = "2026-03-08"
            endTimeIso = "04:00"
            phases.add(DraftPhase("Setup", 1, 0))
        }
        assertThrows(IllegalArgumentException::class.java) {
            EventRequestMapper.createArguments("event-id", draft, ZoneId.of("America/New_York"))
        }
    }
}
