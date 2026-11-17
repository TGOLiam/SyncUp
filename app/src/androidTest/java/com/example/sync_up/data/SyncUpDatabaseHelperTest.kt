package com.example.sync_up.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class SyncUpDatabaseHelperTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "syncup-foundation-test.db"

    @After
    fun deleteTestDatabase() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun opensAndReopensDatabaseWithForeignKeysEnabled() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val helper = SyncUpDatabaseHelper(context, databaseName)
                try {
                    val database = helper.writableDatabase
                    assertEquals(SyncUpDatabaseHelper.DATABASE_VERSION, database.version)
                    database.rawQuery("PRAGMA foreign_keys", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(1, cursor.getInt(0))
                    }
                    assertTrue(database.isWriteAheadLoggingEnabled)
                } finally {
                    helper.close()
                }

                val reopenedHelper = SyncUpDatabaseHelper(context, databaseName)
                try {
                    assertEquals(SyncUpDatabaseHelper.DATABASE_VERSION, reopenedHelper.readableDatabase.version)
                } finally {
                    reopenedHelper.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun createsPreviewDataAndEnforcesSetupConstraints() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val helper = SyncUpDatabaseHelper(context, databaseName)
                try {
                    val database = helper.writableDatabase
                    database.rawQuery("SELECT COUNT(*) FROM events WHERE is_preview = 1", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(2, cursor.getInt(0))
                    }
                    database.execSQL("INSERT INTO events (event_id, creation_status) VALUES ('draft', 'DRAFT')")
                    database.execSQL(
                        "INSERT INTO phases (phase_id, event_id, name, sequence_number, duration, status) " +
                            "VALUES ('phase', 'draft', 'Setup', 1, '01:30', 'UPCOMING')"
                    )
                    try {
                        database.execSQL(
                            "INSERT INTO phases (phase_id, event_id, name, sequence_number, duration, status) " +
                                "VALUES ('invalid', 'draft', 'Invalid', 2, '01:60', 'UPCOMING')"
                        )
                        fail("Invalid minutes should be rejected")
                    } catch (_: SQLiteConstraintException) {
                        // The duration constraint rejects minutes above 59.
                    }
                    try {
                        database.execSQL(
                            "INSERT INTO zones (zone_id, event_id, name, vertices_json) " +
                                "VALUES ('orphan', 'missing', 'Zone', '[]')"
                        )
                        fail("An orphan zone should be rejected")
                    } catch (_: SQLiteConstraintException) {
                        // A zone must belong to an existing event.
                    }
                    PreviewEventSeeder.seed(context, database)
                    database.rawQuery("SELECT COUNT(*) FROM events WHERE is_preview = 1", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(2, cursor.getInt(0))
                    }
                } finally {
                    helper.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun upgradesTheEmptyVersionOneDatabase() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val original = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
                original.version = 1
                original.close()
                val helper = SyncUpDatabaseHelper(context, databaseName)
                try {
                    val database = helper.writableDatabase
                    assertEquals(SyncUpDatabaseHelper.DATABASE_VERSION, database.version)
                    database.rawQuery("SELECT COUNT(*) FROM events WHERE is_preview = 1", null).use { cursor ->
                        assertTrue(cursor.moveToFirst())
                        assertEquals(2, cursor.getInt(0))
                    }
                } finally {
                    helper.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }
}
