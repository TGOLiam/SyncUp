package com.example.sync_up.data

import android.database.sqlite.SQLiteDatabase

/** Associates cached shared events with the accounts that fetched them. */
internal object SharedEventCacheSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE shared_event_access (
                event_id TEXT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                user_id TEXT NOT NULL,
                PRIMARY KEY (event_id, user_id)
            )
            """.trimIndent()
        )
    }
}
