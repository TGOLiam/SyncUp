package com.example.sync_up.data

import android.database.sqlite.SQLiteDatabase

/** Keeps unfinished form values independently, including partially entered dates and times. */
internal object DraftInputSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE draft_inputs (
                event_id TEXT PRIMARY KEY NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                start_date TEXT NOT NULL DEFAULT '',
                end_date TEXT NOT NULL DEFAULT '',
                start_time TEXT NOT NULL DEFAULT '',
                end_time TEXT NOT NULL DEFAULT '',
                start_date_iso TEXT,
                end_date_iso TEXT,
                start_time_iso TEXT,
                end_time_iso TEXT,
                phases_initialized INTEGER NOT NULL DEFAULT 0 CHECK (phases_initialized IN (0, 1))
            )
            """.trimIndent()
        )
    }
}
