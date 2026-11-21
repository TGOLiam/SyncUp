package com.example.sync_up.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/** Calls the server's atomic event creation and membership operations. */
internal class SupabaseEventService(private val client: SupabaseClient) {
    @Serializable
    data class CreatedEvent(
        @SerialName("event_id") val eventId: String,
        @SerialName("join_code") val joinCode: String
    )

    @Serializable
    data class JoinResult(
        val result: String,
        @SerialName("event_id") val eventId: String? = null,
        @SerialName("member_id") val memberId: String? = null
    )

    suspend fun createEvent(arguments: JsonObject): CreatedEvent {
        return client.postgrest.rpc("create_event", arguments).decodeSingle()
    }

    suspend fun joinEvent(code: String): JoinResult {
        val arguments = buildJsonObject { put("p_code", code) }
        return client.postgrest.rpc("join_event", arguments).decodeSingle()
    }

    suspend fun sendTextMessage(messageId: String, channelId: String, text: String): TextMessage {
        val arguments = buildJsonObject {
            put("p_message_id", messageId)
            put("p_channel_id", channelId)
            put("p_text", text)
        }
        return client.postgrest.rpc("send_text_message", arguments).decodeSingle()
    }

    suspend fun pollWorkspace(eventId: String, channelIds: List<String>): WorkspaceSnapshot {
        val arguments = buildJsonObject {
            put("p_event_id", eventId)
            putJsonArray("p_channel_ids") {
                for (channelId in channelIds) add(kotlinx.serialization.json.JsonPrimitive(channelId))
            }
        }
        return client.postgrest.rpc("poll_workspace", arguments).decodeAs()
    }

    /** Creator-only join code lookup; returns null once the event has ended. */
    suspend fun getEventJoinCode(eventId: String): String? {
        val arguments = buildJsonObject { put("p_event_id", eventId) }
        val body = client.postgrest.rpc("get_event_join_code", arguments).data.trim()
        if (body.isEmpty() || body == "null") return null
        return body.removeSurrounding("\"")
    }

    suspend fun activatePhase(eventId: String, phaseId: String) {        val arguments = buildJsonObject {
            put("p_event_id", eventId)
            put("p_phase_id", phaseId)
        }
        client.postgrest.rpc("activate_event_phase", arguments)
    }

    suspend fun reportLocation(eventId: String, zoneId: String) {
        val arguments = buildJsonObject {
            put("p_event_id", eventId)
            put("p_zone_id", zoneId)
        }
        client.postgrest.rpc("report_location", arguments)
    }

    suspend fun listEvents(): List<RemoteEventRow> {
        return client.from("events").select().decodeList()
    }

    suspend fun loadEvent(eventId: String): RemoteEvent {
        val event = client.from("events").select {
            filter { eq("event_id", eventId) }
        }.decodeSingle<RemoteEventRow>()
        val layouts = client.from("venue_layouts").select {
            filter { eq("event_id", eventId) }
        }.decodeList<RemoteLayoutRow>()
        val zones = client.from("zones").select {
            filter { eq("event_id", eventId) }
        }.decodeList<RemoteZoneRow>()
        val phases = client.from("phases").select {
            filter { eq("event_id", eventId) }
        }.decodeList<RemotePhaseRow>()
        val channels = client.from("channels").select {
            filter { eq("event_id", eventId) }
        }.decodeList<RemoteChannelRow>()
        return RemoteEvent(event, layouts.singleOrNull(), zones, phases, channels)
    }
}
