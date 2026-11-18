package com.example.sync_up.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sync_up.EventCreationDraft
import com.example.sync_up.data.remote.RemoteChannelRow
import com.example.sync_up.data.remote.RemoteEvent
import com.example.sync_up.data.remote.RemoteEventRow
import com.example.sync_up.data.remote.RemoteLayoutRow
import com.example.sync_up.data.remote.RemotePhaseRow
import com.example.sync_up.data.remote.RemoteZoneRow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedEventCacheTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "syncup-shared-cache-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun versionFourMigrationPreservesDraftForFirstAccount() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            val database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(databaseName), null)
            try {
                EventSetupSchema.create(database)
                DraftInputSchema.create(database)
                PreviewMetadataSchema.create(database)
                database.execSQL("INSERT INTO events (event_id, name, creation_status) VALUES ('old', 'Existing draft', 'DRAFT')")
                database.version = 4
            } finally {
                database.close()
            }
            EventDraftStore(context, databaseName).use { store ->
                store.claimUnownedDrafts("first-user")
                assertEquals("Existing draft", store.list("first-user").single().name)
                assertEquals(emptyList<String>(), store.list("second-user").map { it.id })
            }
        }
    }

    @Test
    fun unownedDraftsAreClaimedOnlyByFirstAccountAndSurviveRefresh() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            EventDraftStore(context, databaseName).use { store ->
                store.save("old-draft", 0, EventCreationDraft().apply { name = "Old" })
                store.claimUnownedDrafts("user-a")
                store.claimUnownedDrafts("user-b")
                assertEquals(listOf("old-draft"), store.list("user-a").map { it.id })
                assertEquals(emptyList<String>(), store.list("user-b").map { it.id })
                store.save("new-draft", 0, EventCreationDraft().apply { name = "New" }, "user-b")
            }
            SharedEventCache(context, databaseName).use { it.retainAccess("user-a", emptyList()) }
            EventDraftStore(context, databaseName).use { store ->
                assertEquals(listOf("old-draft"), store.list("user-a").map { it.id })
                assertEquals(listOf("new-draft"), store.list("user-b").map { it.id })
            }
        }
    }

    @Test
    fun eventCacheIsIsolatedByAccountAndRestoresGeometry() = runBlocking {
        withContext(Dispatchers.IO) {
            context.deleteDatabase(databaseName)
            val points = Json.parseToJsonElement("[{\"x\":0.1,\"y\":0.1},{\"x\":0.9,\"y\":0.1},{\"x\":0.9,\"y\":0.9}]").jsonArray
            val event = RemoteEvent(
                RemoteEventRow("event", "creator", "Test", "Venue", "2026-11-20T09:00:00Z", "2026-11-20T17:00:00Z", "phase"),
                RemoteLayoutRow(1.5f, points),
                listOf(RemoteZoneRow("zone", "Stage", points)),
                listOf(RemotePhaseRow("phase", "Setup", 1, "01:30", "ACTIVE")),
                listOf(RemoteChannelRow("global", "Global", "GLOBAL"), RemoteChannelRow("crew", "Crew", "CUSTOM"))
            )
            SharedEventCache(context, databaseName).use { cache ->
                cache.save("user-a", event)
                StoredEventReader(context, databaseName).use { reader ->
                    val loaded = reader.load("event", "user-a")
                    assertNotNull(loaded)
                    assertEquals("Setup", loaded!!.summary.activePhase)
                    assertEquals("Stage", loaded.layout!!.zones.single().name)
                    assertEquals(listOf("Crew"), loaded.channels)
                    assertNull(reader.load("event", "user-b"))
                }
                cache.save("user-b", event)
                cache.retainAccess("user-a", emptyList())
            }
            StoredEventReader(context, databaseName).use { reader ->
                assertNull(reader.load("event", "user-a"))
                assertNotNull(reader.load("event", "user-b"))
            }
        }
    }
}
