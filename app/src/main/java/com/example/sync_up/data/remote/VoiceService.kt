package com.example.sync_up.data.remote

import com.example.sync_up.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Requests a channel lease and sends recorded audio to the trusted media service. */
internal class VoiceService(private val client: SupabaseClient) {
    @Serializable
    data class Lease(val granted: Boolean)

    suspend fun request(channelId: String, token: String): Boolean {
        val args = buildJsonObject {
            put("p_channel_id", channelId)
            put("p_request_id", token)
        }
        return client.postgrest.rpc("request_channel_speech", args).decodeSingle<Lease>().granted
    }

    suspend fun release(channelId: String, token: String) {
        val args = buildJsonObject {
            put("p_channel_id", channelId)
            put("p_request_id", token)
        }
        client.postgrest.rpc("release_channel_speech", args)
    }

    suspend fun upload(channelId: String, token: String, messageId: String, file: File) = withContext(Dispatchers.IO) {
        val url = BuildConfig.VOICE_SERVICE_URL.trim().trimEnd('/')
        require(url.isNotEmpty()) { "Voice service is not configured" }
        val accessToken = client.auth.currentSessionOrNull()?.accessToken
            ?: error("Not signed in")
        val connection = URL("$url/voice").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 4000
            connection.readTimeout = 12000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            connection.setRequestProperty("Content-Type", "audio/mp4")
            connection.setRequestProperty("X-Channel-Id", channelId)
            connection.setRequestProperty("X-Speech-Token", token)
            connection.setRequestProperty("X-Message-Id", messageId)
            connection.setFixedLengthStreamingMode(file.length())
            connection.outputStream.use { output ->
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
            check(connection.responseCode == 201) { "Voice upload failed" }
        } finally {
            connection.disconnect()
        }
    }
}
