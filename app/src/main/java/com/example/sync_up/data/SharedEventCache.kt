package com.example.sync_up.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import com.example.sync_up.data.remote.RemoteEvent
import com.example.sync_up.data.remote.WorkspaceSnapshot

/** Stores server-confirmed event data and records which account fetched it. */
internal class SharedEventCache(context: Context, databaseName: String = SyncUpDatabaseHelper.DATABASE_NAME) : AutoCloseable {
    private val helper = SyncUpDatabaseHelper(context, databaseName)

    fun save(userId: String, remote: RemoteEvent) {
        checkWorkerThread()
        val database = helper.writableDatabase
        val event = remote.event
        database.beginTransaction()
        try {
            database.rawQuery("SELECT is_preview FROM events WHERE event_id = ?", arrayOf(event.id)).use { cursor ->
                check(!cursor.moveToFirst() || cursor.getInt(0) == 0) { "A preview cannot be replaced" }
            }
            val values = ContentValues().apply {
                put("creator_id", event.creatorId)
                put("name", event.name)
                put("venue", event.venue)
                put("start_at", event.startAt)
                put("end_at", event.endAt)
                put("creation_status", "CREATED")
                putNull("current_phase_id")
            }
            if (database.update("events", values, "event_id = ?", arrayOf(event.id)) == 0) {
                values.put("event_id", event.id)
                database.insertOrThrow("events", null, values)
            }
            database.delete("channels", "event_id = ?", arrayOf(event.id))
            database.delete("phases", "event_id = ?", arrayOf(event.id))
            database.delete("zones", "event_id = ?", arrayOf(event.id))
            database.delete("venue_layouts", "event_id = ?", arrayOf(event.id))
            remote.layout?.let { layout ->
                database.insertOrThrow("venue_layouts", null, ContentValues().apply {
                    put("event_id", event.id)
                    put("aspect_ratio", layout.aspectRatio)
                    put("boundary_vertices_json", layout.boundary.toString())
                })
            }
            for (zone in remote.zones) {
                database.insertOrThrow("zones", null, ContentValues().apply {
                    put("zone_id", zone.id)
                    put("event_id", event.id)
                    put("name", zone.name)
                    put("vertices_json", zone.vertices.toString())
                })
            }
            for (phase in remote.phases) {
                database.insertOrThrow("phases", null, ContentValues().apply {
                    put("phase_id", phase.id)
                    put("event_id", event.id)
                    put("name", phase.name)
                    put("sequence_number", phase.sequence)
                    put("duration", phase.duration)
                    put("status", phase.status)
                })
            }
            for (channel in remote.channels) {
                database.insertOrThrow("channels", null, ContentValues().apply {
                    put("channel_id", channel.id)
                    put("event_id", event.id)
                    put("name", channel.name)
                    put("channel_type", channel.type)
                    put("zone_id", channel.zoneId)
                })
            }
            database.update("events", ContentValues().apply {
                put("current_phase_id", event.currentPhaseId)
            }, "event_id = ?", arrayOf(event.id))
            database.delete("draft_inputs", "event_id = ?", arrayOf(event.id))
            database.insertWithOnConflict("shared_event_access", null, ContentValues().apply {
                put("event_id", event.id)
                put("user_id", userId)
            }, SQLiteDatabase.CONFLICT_IGNORE)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun retainAccess(userId: String, eventIds: List<String>) {
        checkWorkerThread()
        val database = helper.writableDatabase
        if (eventIds.isEmpty()) {
            database.delete("shared_event_access", "user_id = ? AND event_id IN (SELECT event_id FROM events WHERE creation_status = 'CREATED')", arrayOf(userId))
        } else {
            val placeholders = eventIds.joinToString(",") { "?" }
            database.delete(
                "shared_event_access",
                "user_id = ? AND event_id IN (SELECT event_id FROM events WHERE creation_status = 'CREATED') AND event_id NOT IN ($placeholders)",
                (listOf(userId) + eventIds).toTypedArray()
            )
        }
    }

    fun updatePhases(eventId: String, snapshot: WorkspaceSnapshot) {
        checkWorkerThread()
        val database = helper.writableDatabase
        database.beginTransaction()
        try {
            database.update("phases", ContentValues().apply { put("status", "UPCOMING") }, "event_id = ?", arrayOf(eventId))
            for (phase in snapshot.phases) {
                database.update("phases", ContentValues().apply { put("status", phase.status) },
                    "event_id = ? AND phase_id = ?", arrayOf(eventId, phase.id))
            }
            database.update("events", ContentValues().apply { put("current_phase_id", snapshot.currentPhaseId) },
                "event_id = ?", arrayOf(eventId))
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    private fun checkWorkerThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Database access must run off the main thread" }
    }

    override fun close() {
        helper.close()
    }
}
