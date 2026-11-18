package com.example.sync_up.liveaudio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LiveAudioPreferencesTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "live_audio_preferences_test"

    @After
    fun cleanup() {
        context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun manualDisconnectAndSelectedChannelRemainScopedToAccountAndEvent() {
        val store = LiveAudioPreferences(context, name)
        assertTrue(store.load("user-a", "event-a").globalEnabled)
        store.save("user-a", "event-a", LiveAudioPreferences.Selection(false, "crew"))
        store.setActive(LiveAudioPreferences.ActiveSession("user-a", "event-a", "global"))

        val reopened = LiveAudioPreferences(context, name)
        assertEquals(LiveAudioPreferences.Selection(false, "crew"), reopened.load("user-a", "event-a"))
        assertEquals(LiveAudioPreferences.Selection(true, null), reopened.load("user-b", "event-a"))
        assertEquals(LiveAudioPreferences.Selection(true, null), reopened.load("user-a", "event-b"))
        assertEquals(LiveAudioPreferences.ActiveSession("user-a", "event-a", "global"), reopened.active())

        reopened.save("user-a", "event-a", LiveAudioPreferences.Selection(false, null))
        reopened.setActive(null)
        assertNull(LiveAudioPreferences(context, name).active())
        assertEquals(LiveAudioPreferences.Selection(false, null), reopened.load("user-a", "event-a"))
    }
}
