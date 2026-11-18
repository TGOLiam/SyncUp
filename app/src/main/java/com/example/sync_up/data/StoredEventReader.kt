package com.example.sync_up.data

import android.content.Context
import android.os.Looper
import com.example.sync_up.DraftLayout
import com.example.sync_up.DraftPoint
import com.example.sync_up.DraftZone
import org.json.JSONArray

/** Reads event presentation data from SQLite; callers must use a worker thread. */
internal class StoredEventReader(
    context: Context,
    databaseName: String = SyncUpDatabaseHelper.DATABASE_NAME
) : AutoCloseable {
    private val helper = SyncUpDatabaseHelper(context, databaseName)

    data class Summary(
        val id: String,
        val name: String,
        val detail: String?,
        val isLeadPreview: Boolean,
        val activePhase: String?
    )

    data class Event(
        val summary: Summary,
        val layout: DraftLayout?,
        val phases: List<String>,
        val channels: List<String>
    )

    fun list(): List<Summary> {
        checkWorkerThread()
        val events = mutableListOf<Summary>()
        val sql = """
            SELECT e.event_id, e.name, e.preview_detail, COALESCE(m.is_lead, 0), p.name
            FROM events e
            LEFT JOIN preview_metadata m ON m.event_id = e.event_id
            LEFT JOIN phases p ON p.phase_id = e.current_phase_id AND p.event_id = e.event_id
            WHERE e.creation_status = 'CREATED'
            ORDER BY e.rowid
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(
                    Summary(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        detail = cursor.getString(2),
                        isLeadPreview = cursor.getInt(3) == 1,
                        activePhase = cursor.getString(4)
                    )
                )
            }
        }
        return events
    }

    fun load(id: String): Event? {
        checkWorkerThread()
        val summary = list().firstOrNull { it.id == id } ?: return null
        val db = helper.readableDatabase
        val phases = mutableListOf<String>()
        db.rawQuery("SELECT name FROM phases WHERE event_id = ? ORDER BY sequence_number", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) {
                phases.add(cursor.getString(0))
            }
        }
        val channels = mutableListOf<String>()
        db.rawQuery("SELECT name FROM channels WHERE event_id = ? AND channel_type != 'GLOBAL' ORDER BY rowid", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) {
                channels.add(cursor.getString(0))
            }
        }
        var layout: DraftLayout? = null
        db.rawQuery("SELECT aspect_ratio, boundary_vertices_json FROM venue_layouts WHERE event_id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                val zones = mutableListOf<DraftZone>()
                db.rawQuery("SELECT name, vertices_json FROM zones WHERE event_id = ? ORDER BY rowid", arrayOf(id)).use { zoneCursor ->
                    while (zoneCursor.moveToNext()) {
                        zones.add(DraftZone(zoneCursor.getString(0), points(zoneCursor.getString(1))))
                    }
                }
                layout = DraftLayout(cursor.getFloat(0), points(cursor.getString(1)), zones)
            }
        }
        return Event(summary, layout, phases, channels)
    }

    private fun points(json: String): List<DraftPoint> {
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val point = array.getJSONObject(index)
            DraftPoint(point.getDouble("x").toFloat(), point.getDouble("y").toFloat())
        }
    }

    private fun checkWorkerThread() {
        check(Looper.myLooper() != Looper.getMainLooper()) { "Database access must run off the main thread" }
    }

    override fun close() {
        helper.close()
    }
}
