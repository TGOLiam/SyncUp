package com.example.sync_up.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.sync_up.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs only with an isolated local fixture supplied by the integration harness. */
@RunWith(AndroidJUnit4::class)
class WorkspaceServiceIntegrationTest {
    @Test
    fun sendsAndPollsThroughTheAndroidClient() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString("fixture_email")
        val password = arguments.getString("fixture_password")
        val eventId = arguments.getString("fixture_event")
        val channelId = arguments.getString("fixture_channel")
        assumeTrue(email != null && password != null && eventId != null && channelId != null)
        val client = createSupabaseClient(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY) {
            install(Auth) {
                autoLoadFromStorage = false
                autoSaveToStorage = false
            }
            install(Postgrest)
        }
        try {
            client.auth.signInWith(Email) {
                this.email = requireNotNull(email)
                this.password = requireNotNull(password)
            }
            val service = SupabaseEventService(client)
            val messageId = UUID.randomUUID().toString()
            val message = service.sendTextMessage(messageId, requireNotNull(channelId), "Android integration")
            assertEquals(messageId, message.id)
            val snapshot = service.pollWorkspace(requireNotNull(eventId), listOf(channelId))
            assertTrue(snapshot.messages.any { it.id == messageId })
            assertTrue(snapshot.locations.any { it.userId == client.auth.currentUserOrNull()?.id })
        } finally {
            client.close()
        }
    }
}
