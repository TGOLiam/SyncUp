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
        PreviewEventSeeder.seed(appContext, db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 4) {
            EventSetupSchema.create(db)
            DraftInputSchema.create(db)
            PreviewMetadataSchema.create(db)
            PreviewEventSeeder.seed(appContext, db)
        } else if (oldVersion == 2 && newVersion == 4) {
            DraftInputSchema.create(db)
            PreviewMetadataSchema.create(db)
            PreviewEventSeeder.addMissingMetadata(appContext, db)
        } else if (oldVersion == 3 && newVersion == 4) {
            PreviewMetadataSchema.create(db)
            PreviewEventSeeder.addMissingMetadata(appContext, db)
        } else {
            throw IllegalStateException("No database migration from version $oldVersion to $newVersion")
        }
    }

    companion object {
        const val DATABASE_NAME = "syncup.db"
        const val DATABASE_VERSION = 4
    }
}
