package com.example.sync_up.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.os.Looper
import java.io.File

internal object VoiceRecordingSchema {
    fun create(database: SQLiteDatabase) {
        database.execSQL(
            """
            CREATE TABLE voice_recordings (
                message_id TEXT PRIMARY KEY NOT NULL,
                event_id TEXT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                channel_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                file_name TEXT NOT NULL,
                duration_ms INTEGER NOT NULL CHECK (duration_ms BETWEEN 1 AND 10000)
            )
            """.trimIndent()
        )
    }
}

/** Saves an app-private recording and its account-scoped SQLite metadata. */
internal class VoiceRecordingStore(
    private val context: Context,
    databaseName: String = SyncUpDatabaseHelper.DATABASE_NAME
) : AutoCloseable {
    private val helper = SyncUpDatabaseHelper(context, databaseName)

    fun save(
        messageId: String,
        eventId: String,
        channelId: String,
        userId: String,
        temporary: File,
        durationMs: Int
    ): File {
        check(Looper.myLooper() != Looper.getMainLooper())
        require(durationMs in 1..10000)
        val directory = File(context.filesDir, "voice_recordings")
        check(directory.isDirectory || directory.mkdirs())
        val destination = File(directory, "$messageId.m4a")
        try {
            temporary.copyTo(destination, overwrite = false)
            helper.writableDatabase.insertOrThrow("voice_recordings", null, ContentValues().apply {
                put("message_id", messageId)
                put("event_id", eventId)
                put("channel_id", channelId)
                put("user_id", userId)
                put("file_name", destination.name)
                put("duration_ms", durationMs)
            })
            return destination
        } catch (error: Exception) {
            destination.delete()
            throw error
        } finally {
            temporary.delete()
        }
    }

    override fun close() {
        helper.close()
    }
}
