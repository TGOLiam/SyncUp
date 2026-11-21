package com.example.sync_up.liveaudio

import com.example.sync_up.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Gets a listen-only, channel-scoped token from the trusted backend. */
internal class LiveKitTokenClient(private val client: SupabaseClient) {
    data class Connection(val url: String, val token: String)

    suspend fun get(channelId: String): Connection = withContext(Dispatchers.IO) {
        val endpoint = BuildConfig.LIVEKIT_TOKEN_URL.trim().trimEnd('/')
        require(endpoint.isNotEmpty()) { "Live audio is not configured" }
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Not signed in")
        val payload = JSONObject().put("channel_id", channelId).toString().toByteArray(Charsets.UTF_8)
        val connection = URL("$endpoint/livekit/token").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setFixedLengthStreamingMode(payload.size)
            connection.outputStream.use { it.write(payload) }
            check(connection.responseCode == 200) {
                "Live audio authorization failed (${connection.responseCode}): " +
                    connection.errorStream?.bufferedReader()?.use { it.readText() }?.take(300)
            }
            val response = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            Connection(response.getString("server_url"), response.getString("token"))
        } finally {
            connection.disconnect()
        }
    }
}
