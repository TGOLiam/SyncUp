package com.example.sync_up.liveaudio

import com.example.sync_up.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Requests and releases live speaking permission from the trusted controller. */
internal class LiveSpeechClient(private val client: SupabaseClient) {
    suspend fun request(channelId: String, requestId: String): Boolean {
        return post("speak", channelId, requestId).getBoolean("granted")
    }

    suspend fun stop(channelId: String, requestId: String) {
        post("stop", channelId, requestId)
    }

    private suspend fun post(action: String, channelId: String, requestId: String): JSONObject =
        withContext(Dispatchers.IO) {
            val endpoint = BuildConfig.LIVEKIT_TOKEN_URL.trim().trimEnd('/')
            require(endpoint.isNotEmpty()) { "Live audio is not configured" }
            val accessToken = client.auth.currentSessionOrNull()?.accessToken ?: error("Not signed in")
            val payload = JSONObject()
                .put("channel_id", channelId)
                .put("request_id", requestId)
                .toString()
                .toByteArray(Charsets.UTF_8)
            val connection = URL("$endpoint/livekit/$action").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setFixedLengthStreamingMode(payload.size)
                connection.outputStream.use { it.write(payload) }
                check(connection.responseCode == 200) { "Live speech request failed" }
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
}
