package com.example.sync_up.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.example.sync_up.data.remote.TextMessage
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TextMessageCacheTest {
    @Test
    fun storesConfirmedMessagesByAccountAndLimitsHistory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "text-message-test.db"
        context.deleteDatabase(name)
        val executor = Executors.newSingleThreadExecutor()
        try {
            executor.submit {
                TextMessageCache(context, name).use { cache ->
                    val messages = (1..25).map { number ->
                        TextMessage("$number", "channel", "sender", "Message $number", "%02d".format(number))
                    }
                    cache.save("first", messages)
                    cache.save("first", listOf(messages.last()))
                    assertEquals(20, cache.recent("first", "channel").size)
                    assertEquals("Message 6", cache.recent("first", "channel").first().text)
                    assertEquals(emptyList<TextMessage>(), cache.recent("second", "channel"))
                }
            }.get()
        } finally {
            executor.shutdown()
            context.deleteDatabase(name)
        }
    }
}
