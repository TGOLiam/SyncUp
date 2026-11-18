package com.example.sync_up.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class VoiceRecordingStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "voice-recording-test.db"
    private val messageId = UUID.randomUUID().toString()

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
        File(context.filesDir, "voice_recordings/$messageId.m4a").delete()
    }

    @Test
    fun savesPrivateRecordingAndMetadata() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            SyncUpDatabaseHelper(context, databaseName).use { helper ->
                helper.writableDatabase.execSQL(
                    "INSERT INTO events (event_id, creation_status) VALUES ('voice-event', 'CREATED')"
                )
            }
            val temporary = File.createTempFile("voice-test-", ".m4a", context.cacheDir)
            temporary.writeText("test data")
            VoiceRecordingStore(context, databaseName).use { store ->
                val saved = store.save(messageId, "voice-event", "channel", "user", temporary, 1000)
                assertTrue(saved.isFile)
                assertFalse(temporary.exists())
            }
            SyncUpDatabaseHelper(context, databaseName).use { helper ->
                helper.readableDatabase.rawQuery(
                    "SELECT channel_id, user_id, duration_ms FROM voice_recordings WHERE message_id = ?",
                    arrayOf(messageId)
                ).use { cursor ->
                    assertTrue(cursor.moveToFirst())
                    assertEquals("channel", cursor.getString(0))
                    assertEquals("user", cursor.getString(1))
                    assertEquals(1000, cursor.getInt(2))
                }
            }
        }
    }
}
