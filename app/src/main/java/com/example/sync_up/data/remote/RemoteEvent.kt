package com.example.sync_up.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray

@Serializable
internal data class RemoteEventRow(
    @SerialName("event_id") val id: String,
    @SerialName("creator_id") val creatorId: String,
    val name: String,
    val venue: String,
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("current_phase_id") val currentPhaseId: String? = null
)

@Serializable
internal data class RemoteLayoutRow(
    @SerialName("aspect_ratio") val aspectRatio: Float,
    @SerialName("boundary_vertices") val boundary: JsonArray
)

@Serializable
internal data class RemoteZoneRow(
    @SerialName("zone_id") val id: String,
    val name: String,
    val vertices: JsonArray
)

@Serializable
internal data class RemotePhaseRow(
    @SerialName("phase_id") val id: String,
    val name: String,
    @SerialName("sequence_number") val sequence: Int,
    val duration: String,
    val status: String
)

@Serializable
internal data class RemoteChannelRow(
    @SerialName("channel_id") val id: String,
    val name: String,
    @SerialName("channel_type") val type: String,
    @SerialName("zone_id") val zoneId: String? = null
)

internal data class RemoteEvent(
    val event: RemoteEventRow,
    val layout: RemoteLayoutRow?,
    val zones: List<RemoteZoneRow>,
    val phases: List<RemotePhaseRow>,
    val channels: List<RemoteChannelRow>
)
