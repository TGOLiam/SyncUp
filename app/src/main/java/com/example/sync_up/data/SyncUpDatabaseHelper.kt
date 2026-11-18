package com.example.sync_up.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Owns the local SQLite connection and schema lifecycle. */
internal class SyncUpDatabaseHelper(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {
    private val appContext = context.applicationContext

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        EventSetupSchema.create(db)
        DraftInputSchema.create(db)
        PreviewMetadataSchema.create(db)
        SharedEventCacheSchema.create(db)
        VoiceRecordingSchema.create(db)
        PreviewEventSeeder.seed(appContext, db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 6) {
            EventSetupSchema.create(db)
            DraftInputSchema.create(db)
            PreviewMetadataSchema.create(db)
            SharedEventCacheSchema.create(db)
            PreviewEventSeeder.seed(appContext, db)
        } else if (oldVersion == 2 && newVersion == 6) {
            DraftInputSchema.create(db)
            PreviewMetadataSchema.create(db)
            SharedEventCacheSchema.create(db)
            PreviewEventSeeder.addMissingMetadata(appContext, db)
        } else if (oldVersion == 3 && newVersion == 6) {
            PreviewMetadataSchema.create(db)
            SharedEventCacheSchema.create(db)
            PreviewEventSeeder.addMissingMetadata(appContext, db)
        } else if (oldVersion == 4 && newVersion == 6) {
            SharedEventCacheSchema.create(db)
        } else if (oldVersion == 5 && newVersion == 6) {
            // The version 5 schema already has shared-event access.
        } else {
            throw IllegalStateException("No database migration from version $oldVersion to $newVersion")
        }
        VoiceRecordingSchema.create(db)
    }

    companion object {
        const val DATABASE_NAME = "syncup.db"
        const val DATABASE_VERSION = 6
    }
}
