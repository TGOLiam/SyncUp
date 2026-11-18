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
        val activePhase: String?,
        val isPreview: Boolean,
        val isCreator: Boolean,
        val activePhaseId: String?
    )

    data class Event(
        val summary: Summary,
        val layout: DraftLayout?,
        val phases: List<String>,
        val phaseIds: List<String>,
        val zoneIds: List<Pair<String, String>>,
        val channels: List<String>,
        val channelIds: Map<String, String>
    )

    fun list(userId: String? = null): List<Summary> {
        checkWorkerThread()
        val events = mutableListOf<Summary>()
        val sql = """
            SELECT e.event_id, e.name, COALESCE(e.preview_detail, e.venue),
                COALESCE(m.is_lead, 0), p.name, e.is_preview, e.creator_id, e.current_phase_id
            FROM events e
            LEFT JOIN preview_metadata m ON m.event_id = e.event_id
            LEFT JOIN phases p ON p.phase_id = e.current_phase_id AND p.event_id = e.event_id
            WHERE e.creation_status = 'CREATED'
              AND (e.is_preview = 1 OR EXISTS (
                  SELECT 1 FROM shared_event_access a
                  WHERE a.event_id = e.event_id AND a.user_id = ?
              ))
            ORDER BY e.rowid
        """.trimIndent()
        helper.readableDatabase.rawQuery(sql, arrayOf(userId.orEmpty())).use { cursor ->
            while (cursor.moveToNext()) {
                events.add(
                    Summary(
                        id = cursor.getString(0),
                        name = cursor.getString(1),
                        detail = cursor.getString(2),
                        isLeadPreview = cursor.getInt(3) == 1,
                        activePhase = cursor.getString(4),
                        isPreview = cursor.getInt(5) == 1,
                        isCreator = userId != null && cursor.getString(6) == userId,
                        activePhaseId = cursor.getString(7)
                    )
                )
            }
        }
        return events
    }

    fun load(id: String, userId: String? = null): Event? {
        checkWorkerThread()
        val summary = list(userId).firstOrNull { it.id == id } ?: return null
        val db = helper.readableDatabase
        val phases = mutableListOf<String>()
        val phaseIds = mutableListOf<String>()
        db.rawQuery("SELECT name, phase_id FROM phases WHERE event_id = ? ORDER BY sequence_number", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) {
                phases.add(cursor.getString(0))
                phaseIds.add(cursor.getString(1))
            }
        }
        val channels = mutableListOf<String>()
        val channelIds = mutableMapOf<String, String>()
        val ambiguousNames = mutableSetOf<String>()
        db.rawQuery("SELECT channel_id, name, channel_type FROM channels WHERE event_id = ? ORDER BY rowid", arrayOf(id)).use { cursor ->
            while (cursor.moveToNext()) {
                val channelId = cursor.getString(0)
                val name = cursor.getString(1)
                val type = cursor.getString(2)
                if (type == "GLOBAL") {
                    channelIds[""] = channelId
                } else {
                    channels.add(name)
                    if (channelIds.containsKey(name)) ambiguousNames.add(name)
                    channelIds[name] = channelId
                }
            }
        }
        for (name in ambiguousNames) channelIds.remove(name)
        var layout: DraftLayout? = null
        val zoneIds = mutableListOf<Pair<String, String>>()
        db.rawQuery("SELECT aspect_ratio, boundary_vertices_json FROM venue_layouts WHERE event_id = ?", arrayOf(id)).use { cursor ->
            if (cursor.moveToFirst()) {
                val zones = mutableListOf<DraftZone>()
                db.rawQuery("SELECT name, vertices_json, zone_id FROM zones WHERE event_id = ? ORDER BY rowid", arrayOf(id)).use { zoneCursor ->
                    while (zoneCursor.moveToNext()) {
                        zones.add(DraftZone(zoneCursor.getString(0), points(zoneCursor.getString(1))))
                        zoneIds.add(zoneCursor.getString(2) to zoneCursor.getString(0))
                    }
                }
                layout = DraftLayout(cursor.getFloat(0), points(cursor.getString(1)), zones)
            }
        }
        return Event(summary, layout, phases, phaseIds, zoneIds, channels, channelIds)
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
