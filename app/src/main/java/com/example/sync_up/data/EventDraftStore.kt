package com.example.sync_up.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Looper
import com.example.sync_up.DraftLayout
import com.example.sync_up.DraftPhase
import com.example.sync_up.DraftPoint
import com.example.sync_up.DraftZone
import com.example.sync_up.EventCreationDraft
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/** SQLite operations for local drafts. Call these methods on a worker thread. */
internal class EventDraftStore(
    context: Context,
    databaseName: String = SyncUpDatabaseHelper.DATABASE_NAME
) : AutoCloseable {
    private val helper = SyncUpDatabaseHelper(context, databaseName)

    data class SavedDraft(val id: String, val step: Int, val draft: EventCreationDraft)
    data class DraftSummary(val id: String, val name: String, val step: Int)

    fun save(id: String, step: Int, draft: EventCreationDraft) {
        checkWorkerThread()
        require(step in 0..5)
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.rawQuery("SELECT creation_status FROM events WHERE event_id = ?", arrayOf(id)).use { cursor ->
                if (cursor.moveToFirst()) {
                    check(cursor.getString(0) == "DRAFT") { "Only drafts can be edited" }
                }
            }
            val event = ContentValues().apply {
                put("name", draft.name)
                put("venue", draft.venue)
                put("setup_step", step)
            }
            if (db.update("events", event, "event_id = ?", arrayOf(id)) == 0) {
                event.put("event_id", id)
                event.put("creation_status", "DRAFT")
                db.insertOrThrow("events", null, event)
            }
            val inputs = ContentValues().apply {
                put("event_id", id)
                put("start_date", draft.startDate)
                put("end_date", draft.endDate)
                put("start_time", draft.startTime)
                put("end_time", draft.endTime)
                put("start_date_iso", draft.startDateIso)
                put("end_date_iso", draft.endDateIso)
                put("start_time_iso", draft.startTimeIso)
                put("end_time_iso", draft.endTimeIso)
                put("phases_initialized", if (draft.phasesInitialized) 1 else 0)
            }
            db.insertWithOnConflict("draft_inputs", null, inputs, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("venue_layouts", "event_id = ?", arrayOf(id))
            db.delete("zones", "event_id = ?", arrayOf(id))
            db.delete("phases", "event_id = ?", arrayOf(id))
            db.delete("channels", "event_id = ?", arrayOf(id))
            draft.layout?.let { layout ->
                db.insertOrThrow("venue_layouts", null, ContentValues().apply {
                    put("event_id", id)
                    put("aspect_ratio", layout.aspectRatio)
                    put("boundary_vertices_json", encodePoints(layout.boundary))
                })
                for ((index, zone) in layout.zones.withIndex()) {
                    db.insertOrThrow("zones", null, ContentValues().apply {
                        put("zone_id", childId(id, "zone", index))
                        put("event_id", id)
                        put("name", zone.name)
                        put("vertices_json", encodePoints(zone.vertices))
                    })
                }
            }
            for ((index, phase) in draft.phases.withIndex()) {
                require(phase.hours in 0..99 && phase.minutes in 0..59 && phase.durationMinutes > 0)
                db.insertOrThrow("phases", null, ContentValues().apply {
                    put("phase_id", childId(id, "phase", index))
                    put("event_id", id)
                    put("name", phase.label)
                    put("sequence_number", index + 1)
                    put("duration", String.format(Locale.US, "%02d:%02d", phase.hours, phase.minutes))
                    put("status", "UPCOMING")
                })
            }
            for ((index, name) in draft.channels.withIndex()) {
                db.insertOrThrow("channels", null, ContentValues().apply {
                    put("channel_id", childId(id, "channel", index))
                    put("event_id", id)
                    put("name", name)
                    put("channel_type", "CUSTOM")
                })
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun load(id: String): SavedDraft? {
        checkWorkerThread()
        val db = helper.readableDatabase
        val draft = EventCreationDraft()
        val step = db.rawQuery(
            "SELECT name, venue, setup_step FROM events WHERE event_id = ? AND creation_status = 'DRAFT'",
            arrayOf(id)
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            draft.name = cursor.getString(0)
            draft.venue = cursor.getString(1)
            cursor.getInt(2)
        }
        db.rawQuery("SELECT * FROM draft_inputs WHERE event_id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                draft.startDate = cursor.string("start_date")
                draft.endDate = cursor.string("end_date")
                draft.startTime = cursor.string("start_time")
                draft.endTime = cursor.string("end_time")
                draft.startDateIso = cursor.nullableString("start_date_iso")
                draft.endDateIso = cursor.nullableString("end_date_iso")
                draft.startTimeIso = cursor.nullableString("start_time_iso")
                draft.endTimeIso = cursor.nullableString("end_time_iso")
                draft.phasesInitialized = cursor.getInt(cursor.getColumnIndexOrThrow("phases_initialized")) == 1
            }
        }
        db.rawQuery("SELECT aspect_ratio, boundary_vertices_json FROM venue_layouts WHERE event_id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                val ratio = cursor.getFloat(0)
                val boundary = decodePoints(cursor.getString(1))
                val zones = mutableListOf<DraftZone>()
                db.rawQuery("SELECT name, vertices_json FROM zones WHERE event_id = ? ORDER BY rowid", arrayOf(id)).use { zoneCursor ->
                    while (zoneCursor.moveToNext()) {
                        zones.add(DraftZone(zoneCursor.getString(0), decodePoints(zoneCursor.getString(1))))
                    }
                }
                draft.layout = DraftLayout(ratio, boundary, zones)
            }
        }
        db.rawQuery("SELECT name, duration FROM phases WHERE event_id = ? ORDER BY sequence_number", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) {
                val duration = cursor.getString(1).split(':')
                draft.phases.add(DraftPhase(cursor.getString(0), duration[0].toInt(), duration[1].toInt()))
            }
        }
        db.rawQuery("SELECT name FROM channels WHERE event_id = ? AND channel_type = 'CUSTOM' ORDER BY rowid", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) draft.channels.add(cursor.getString(0))
        }
        return SavedDraft(id, step, draft)
    }

    fun list(): List<DraftSummary> {
        checkWorkerThread()
        val drafts = mutableListOf<DraftSummary>()
        helper.readableDatabase.rawQuery(
            "SELECT event_id, name, setup_step FROM events WHERE creation_status = 'DRAFT' AND is_preview = 0 ORDER BY rowid DESC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) drafts.add(DraftSummary(cursor.getString(0), cursor.getString(1), cursor.getInt(2)))
        }
        return drafts
    }

    fun delete(id: String): Boolean {
        checkWorkerThread()
        return helper.writableDatabase.delete("events", "event_id = ? AND creation_status = 'DRAFT'", arrayOf(id)) == 1
    }

    override fun close() {
        helper.close()
    }

    private fun checkWorkerThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Database access must run off the main thread" }
    }

    private fun childId(eventId: String, type: String, index: Int): String =
        UUID.nameUUIDFromBytes("$eventId:$type:$index".toByteArray(Charsets.UTF_8)).toString()

    private fun encodePoints(points: List<DraftPoint>): String {
        val array = JSONArray()
        for (point in points) array.put(JSONObject().put("x", point.x.toDouble()).put("y", point.y.toDouble()))
        return array.toString()
    }

    private fun decodePoints(json: String): List<DraftPoint> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val point = array.getJSONObject(index)
            DraftPoint(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
        }
    }

    private fun Cursor.string(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.nullableString(column: String): String? = getString(getColumnIndexOrThrow(column))
}
