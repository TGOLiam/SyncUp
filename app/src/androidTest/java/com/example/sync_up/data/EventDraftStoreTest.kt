package com.example.sync_up.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sync_up.DraftLayout
import com.example.sync_up.DraftPhase
import com.example.sync_up.DraftPoint
import com.example.sync_up.DraftZone
import com.example.sync_up.EventCreationDraft
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.Executors

@RunWith(AndroidJUnit4::class)
class EventDraftStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databaseName = "syncup-draft-test.db"

    @After
    fun cleanup() {
        context.deleteDatabase(databaseName)
    }

    @Test
    fun savesRestoresAndDeletesDraftWithoutLosingGeometry() {
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                context.deleteDatabase(databaseName)
                val store = EventDraftStore(context, databaseName)
                try {
                    val id = UUID.randomUUID().toString()
                    val boundary = listOf(DraftPoint(0.1f, 0.1f), DraftPoint(0.9f, 0.1f), DraftPoint(0.9f, 0.9f))
                    val draft = EventCreationDraft().apply {
                        name = "Draft event"
                        venue = "Venue"
                        startDate = "Nov 20, 2026"
                        startDateIso = "2026-11-20"
                        phasesInitialized = true
                        phases.add(DraftPhase("Setup", 1, 30))
                        channels.add("Crew")
                        layout = DraftLayout(1.5f, boundary, listOf(DraftZone("Stage", boundary)))
                    }
                    store.save(id, 3, draft)
                    val restored = store.load(id)
                    assertNotNull(restored)
                    assertEquals(3, restored!!.step)
                    assertEquals(draft.name, restored.draft.name)
                    assertEquals(draft.startDateIso, restored.draft.startDateIso)
                    assertEquals(draft.layout, restored.draft.layout)
                    assertEquals(draft.phases, restored.draft.phases)
                    assertEquals(draft.channels, restored.draft.channels)
                    assertEquals(id, store.list().single().id)
                    draft.phases.add(DraftPhase("Invalid", 100, 0))
                    try {
                        store.save(id, 4, draft)
                        throw AssertionError("Invalid duration should be rejected")
                    } catch (_: IllegalArgumentException) {
                        // The failed save must roll back changes to every table.
                    }
                    assertEquals(3, store.load(id)!!.step)
                    assertEquals(1, store.load(id)!!.draft.phases.size)
                    assertTrue(store.delete(id))
                    assertFalse(store.delete(id))
                    assertNull(store.load(id))
                } finally {
                    store.close()
                }
            }.get()
        } finally {
            executor.shutdown()
        }
    }
}
