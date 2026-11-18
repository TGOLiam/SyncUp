package com.example.sync_up.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class StoredEventReaderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "syncup-event-reader-test.db"

    @After
    fun cleanUp() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun previewDataCanBeReadAfterReopening() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val first = StoredEventReader(context, databaseName)
                val events = try {
                    first.list()
                } finally {
                    first.close()
                }
                assertEquals(2, events.size)
                val techConf = events.first { it.name.contains("TechConf") }
                assertTrue(techConf.isLeadPreview)
                assertEquals("Registration", techConf.activePhase)
                val reopened = StoredEventReader(context, databaseName)
                try {
                    val event = reopened.load(techConf.id)
                    assertNotNull(event)
                    assertEquals(4, event!!.layout!!.boundary.size)
                    assertEquals(listOf("Stage", "Registration", "Food"), event.layout!!.zones.map { it.name })
                    assertEquals(5, event.phases.size)
                    assertEquals(listOf("Registration", "Stage Crew", "Food and Bev"), event.channels)
                    assertNull(reopened.load("missing-event"))
                } finally {
                    reopened.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun upgradesVersionThreeWithoutReplacingExistingDrafts() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val oldDatabase = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
                try {
                    oldDatabase.setForeignKeyConstraintsEnabled(true)
                    EventSetupSchema.create(oldDatabase)
                    DraftInputSchema.create(oldDatabase)
                    PreviewMetadataSchema.create(oldDatabase)
                    PreviewEventSeeder.seed(context, oldDatabase)
                    oldDatabase.execSQL("DROP TABLE preview_metadata")
                    oldDatabase.execSQL("INSERT INTO events (event_id, name, creation_status) VALUES ('saved', 'My draft', 'DRAFT')")
                    oldDatabase.version = 3
                } finally {
                    oldDatabase.close()
                }
                val reader = StoredEventReader(context, databaseName)
                try {
                    val previews = reader.list()
                    assertEquals(2, previews.size)
                    assertTrue(previews.first { it.name.contains("TechConf") }.isLeadPreview)
                } finally {
                    reader.close()
                }
                val store = EventDraftStore(context, databaseName)
                try {
                    assertEquals("My draft", store.load("saved")?.draft?.name)
                } finally {
                    store.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }
}
