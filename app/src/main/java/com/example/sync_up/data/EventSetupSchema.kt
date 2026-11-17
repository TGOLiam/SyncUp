package com.example.sync_up.data

import android.database.sqlite.SQLiteDatabase

/** Version 2 stores incomplete local drafts as well as submitted and preview events. */
internal object EventSetupSchema {
    fun create(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE events (
                event_id TEXT PRIMARY KEY NOT NULL,
                creator_id TEXT,
                name TEXT NOT NULL DEFAULT '',
                venue TEXT NOT NULL DEFAULT '',
                start_at TEXT,
                end_at TEXT,
                creation_status TEXT NOT NULL CHECK (creation_status IN ('DRAFT', 'CREATED')),
                setup_step INTEGER NOT NULL DEFAULT 0 CHECK (setup_step BETWEEN 0 AND 5),
                is_preview INTEGER NOT NULL DEFAULT 0 CHECK (is_preview IN (0, 1)),
                preview_detail TEXT,
                current_phase_id TEXT REFERENCES phases(phase_id) DEFERRABLE INITIALLY DEFERRED
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE venue_layouts (
                event_id TEXT PRIMARY KEY NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                aspect_ratio REAL NOT NULL CHECK (aspect_ratio > 0),
                boundary_vertices_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE zones (
                zone_id TEXT PRIMARY KEY NOT NULL,
                event_id TEXT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                vertices_json TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE phases (
                phase_id TEXT PRIMARY KEY NOT NULL,
                event_id TEXT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
                duration TEXT CHECK (
                    duration IS NULL OR (
                        duration GLOB '[0-9][0-9]:[0-5][0-9]' AND duration != '00:00'
                    )
                ),
                status TEXT NOT NULL CHECK (status IN ('UPCOMING', 'ACTIVE', 'COMPLETED')),
                UNIQUE (event_id, sequence_number)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE channels (
                channel_id TEXT PRIMARY KEY NOT NULL,
                event_id TEXT NOT NULL REFERENCES events(event_id) ON DELETE CASCADE,
                name TEXT NOT NULL,
                channel_type TEXT NOT NULL CHECK (channel_type IN ('GLOBAL', 'ZONE', 'CUSTOM')),
                zone_id TEXT REFERENCES zones(zone_id) ON DELETE SET NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX zones_by_event ON zones(event_id)")
        db.execSQL("CREATE INDEX channels_by_event ON channels(event_id)")
        db.execSQL("CREATE UNIQUE INDEX one_global_channel ON channels(event_id) WHERE channel_type = 'GLOBAL'")
        db.execSQL("CREATE UNIQUE INDEX one_active_phase ON phases(event_id) WHERE status = 'ACTIVE'")
    }
}
