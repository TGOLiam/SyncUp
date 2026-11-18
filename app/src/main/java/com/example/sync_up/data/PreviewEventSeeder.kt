package com.example.sync_up.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.example.sync_up.DraftPoint
import com.example.sync_up.PreviewEvents
import com.example.sync_up.R
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Seeds the existing local-only preview events when the database is created or upgraded. */
internal object PreviewEventSeeder {
    fun seed(context: Context, db: SQLiteDatabase) {
        val events = PreviewEvents.load(context)
        for ((index, event) in events.withIndex()) {
            val eventKey = event.previewId
            val eventId = previewId("event-$eventKey")
            val draft = requireNotNull(PreviewEvents.workspaceDraft(context, index))
            val eventValues = ContentValues().apply {
                put("event_id", eventId)
                put("name", event.name)
                put("creation_status", "CREATED")
                put("setup_step", 5)
                put("is_preview", 1)
                put("preview_detail", event.detail)
            }
            // Stable IDs make repeated seeding safe without replacing existing rows.
            if (db.insertWithOnConflict("events", null, eventValues, SQLiteDatabase.CONFLICT_IGNORE) == -1L) {
                continue
            }
            insertMetadata(db, eventId, event.isLead)
            draft.layout?.let { layout ->
                db.insertOrThrow("venue_layouts", null, ContentValues().apply {
                    put("event_id", eventId)
                    put("aspect_ratio", layout.aspectRatio)
                    put("boundary_vertices_json", pointsJson(layout.boundary))
                })
                for ((zoneIndex, zone) in layout.zones.withIndex()) {
                    db.insertOrThrow("zones", null, ContentValues().apply {
                        put("zone_id", previewId("event-$eventKey-zone-$zoneIndex"))
                        put("event_id", eventId)
                        put("name", zone.name)
                        put("vertices_json", pointsJson(zone.vertices))
                    })
                }
            }
            val activeIndex = event.phases.indexOf(event.workspacePhase)
            for ((phaseIndex, phase) in event.phases.withIndex()) {
                val phaseId = previewId("event-$eventKey-phase-$phaseIndex")
                db.insertOrThrow("phases", null, ContentValues().apply {
                    put("phase_id", phaseId)
                    put("event_id", eventId)
                    put("name", phase)
                    put("sequence_number", phaseIndex + 1)
                    put("status", when {
                        phaseIndex == activeIndex -> "ACTIVE"
                        activeIndex >= 0 && phaseIndex < activeIndex -> "COMPLETED"
                        else -> "UPCOMING"
                    })
                })
                if (phaseIndex == activeIndex) {
                    db.update("events", ContentValues().apply {
                        put("current_phase_id", phaseId)
                    }, "event_id = ?", arrayOf(eventId))
                }
            }
            val channelNames = listOf(context.getString(R.string.channel_global)) + event.channels
            for ((channelIndex, channel) in channelNames.withIndex()) {
                db.insertOrThrow("channels", null, ContentValues().apply {
                    put("channel_id", previewId("event-$eventKey-channel-$channelIndex"))
                    put("event_id", eventId)
                    put("name", channel)
                    put("channel_type", if (channelIndex == 0) "GLOBAL" else "CUSTOM")
                })
            }
        }
    }

    /** Populate metadata for previews created by earlier database versions. */
    fun addMissingMetadata(context: Context, db: SQLiteDatabase) {
        for (event in PreviewEvents.load(context)) {
            val eventId = previewId("event-${event.previewId}")
            db.rawQuery("SELECT 1 FROM events WHERE event_id = ? AND is_preview = 1", arrayOf(eventId)).use { cursor ->
                if (cursor.moveToFirst()) insertMetadata(db, eventId, event.isLead)
            }
        }
    }

    private fun insertMetadata(db: SQLiteDatabase, eventId: String, isLead: Boolean) {
        val values = ContentValues().apply {
            put("event_id", eventId)
            put("is_lead", if (isLead) 1 else 0)
        }
        db.insertWithOnConflict("preview_metadata", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun previewId(value: String): String {
        return UUID.nameUUIDFromBytes("syncup-preview-$value".toByteArray(Charsets.UTF_8)).toString()
    }

    private fun pointsJson(points: List<DraftPoint>): String {
        val array = JSONArray()
        for (point in points) {
            array.put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
        }
        return array.toString()
    }
}
