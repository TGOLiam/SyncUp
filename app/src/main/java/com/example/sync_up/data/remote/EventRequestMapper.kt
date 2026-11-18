package com.example.sync_up.data.remote

import com.example.sync_up.DraftPoint
import com.example.sync_up.EventCreationDraft
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID

/** Converts a completed local draft into the backend's create_event arguments. */
internal object EventRequestMapper {
    fun createArguments(eventId: String, draft: EventCreationDraft, zoneId: ZoneId): JsonObject {
        val start = timestamp(draft.startDateIso, draft.startTimeIso, zoneId)
        val end = timestamp(draft.endDateIso, draft.endTimeIso, zoneId)
        require(end.toInstant().isAfter(start.toInstant())) { "End must be after start" }
        require(draft.name.isNotBlank() && draft.venue.isNotBlank()) { "Event details are required" }
        require(draft.phases.isNotEmpty()) { "At least one phase is required" }

        val layout = draft.layout
        val layoutJson = if (layout == null) {
            JsonNull
        } else {
            buildJsonObject {
                put("aspect_ratio", layout.aspectRatio)
                put("boundary_vertices", points(layout.boundary))
            }
        }
        val zones = JsonArray(layout?.zones.orEmpty().mapIndexed { index, zone ->
            buildJsonObject {
                put("zone_id", childId(eventId, "zone", index))
                put("name", zone.name)
                put("vertices", points(zone.vertices))
            }
        })
        val phases = JsonArray(draft.phases.mapIndexed { index, phase ->
            require(phase.hours in 0..99 && phase.minutes in 0..59 && phase.durationMinutes > 0)
            buildJsonObject {
                put("phase_id", childId(eventId, "phase", index))
                put("name", phase.label)
                put("sequence_number", index + 1)
                put("duration", String.format(Locale.US, "%02d:%02d", phase.hours, phase.minutes))
            }
        })
        val channels = JsonArray(draft.channels.mapIndexed { index, name ->
            buildJsonObject {
                put("channel_id", childId(eventId, "channel", index))
                put("name", name)
                put("channel_type", "CUSTOM")
            }
        })
        return buildJsonObject {
            put("p_event_id", eventId)
            put("p_name", draft.name)
            put("p_venue", draft.venue)
            put("p_start_at", start.toString())
            put("p_end_at", end.toString())
            put("p_layout", layoutJson)
            put("p_zones", zones)
            put("p_phases", phases)
            put("p_channels", channels)
        }
    }

    private fun timestamp(date: String?, time: String?, zone: ZoneId): java.time.OffsetDateTime {
        require(date != null && time != null) { "Event dates and times are required" }
        val local = LocalDateTime.parse("${date}T$time")
        val offsets = zone.rules.getValidOffsets(local)
        require(offsets.size == 1) { "Event time is ambiguous or does not exist in this time zone" }
        return local.atOffset(offsets.single())
    }

    private fun childId(eventId: String, type: String, index: Int): String =
        UUID.nameUUIDFromBytes("$eventId:$type:$index".toByteArray(Charsets.UTF_8)).toString()

    private fun points(vertices: List<DraftPoint>): JsonArray = JsonArray(vertices.map { point ->
        buildJsonObject {
            put("x", point.x)
            put("y", point.y)
        }
    })
}
