package com.example.sync_up.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
