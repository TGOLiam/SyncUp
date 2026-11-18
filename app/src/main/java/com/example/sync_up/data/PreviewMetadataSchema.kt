package com.example.sync_up.data

import android.database.sqlite.SQLiteDatabase

/** Retains the existing preview presentation until real user identity is available. */
internal object PreviewMetadataSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE preview_metadata (
                event_id TEXT PRIMARY KEY NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                is_lead INTEGER NOT NULL CHECK (is_lead IN (0, 1))
            )
            """.trimIndent()
        )
    }
}
