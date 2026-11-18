package com.example.sync_up.data

import android.database.sqlite.SQLiteDatabase

/** Stores confirmed messages separately for each signed-in account. */
internal object TextMessageSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE text_messages (
                user_id TEXT NOT NULL,
                message_id TEXT NOT NULL,
                channel_id TEXT NOT NULL,
                sender_member_id TEXT NOT NULL,
                text_content TEXT NOT NULL,
                sent_at TEXT NOT NULL,
                PRIMARY KEY (user_id, message_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX text_messages_by_channel ON text_messages(user_id, channel_id, sent_at)")
    }
}
