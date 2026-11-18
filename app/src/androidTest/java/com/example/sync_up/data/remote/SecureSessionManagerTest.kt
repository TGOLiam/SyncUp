package com.example.sync_up.data.remote

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SecureSessionManagerTest {
    @Test
    fun sessionIsEncryptedAndIsolatedByProject() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val first = SecureSessionManager(context, "https://first.example.test")
        val second = SecureSessionManager(context, "https://second.example.test")
        first.deleteSession()
        second.deleteSession()
        try {
            val session = UserSession(
                accessToken = "example-access-token",
                refreshToken = "example-refresh-token",
                expiresIn = 3600,
                tokenType = "bearer"
            )
            first.saveSession(session)
            assertEquals(session, SecureSessionManager(context, "https://first.example.test").loadSession())
            assertNull(second.loadSessionOrNull())
            val stored = context.getSharedPreferences("syncup_auth_session", Context.MODE_PRIVATE).all.values.toString()
            assertFalse(stored.contains("example-access-token"))
            assertFalse(stored.contains("example-refresh-token"))
            first.deleteSession()
            assertNull(first.loadSessionOrNull())
        } finally {
            first.deleteSession()
            second.deleteSession()
        }
    }
}
