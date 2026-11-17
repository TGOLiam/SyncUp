package com.example.sync_up.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** Owns the local SQLite connection and schema lifecycle. */
internal class SyncUpDatabaseHelper(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {

    init {
        setWriteAheadLoggingEnabled(true)
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        // Tables will be introduced with the reviewed event setup schema.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No migrations exist yet; never silently discard existing data.
        throw IllegalStateException("No database migration from version $oldVersion to $newVersion")
    }

    companion object {
        const val DATABASE_NAME = "syncup.db"
        const val DATABASE_VERSION = 1
    }
}
