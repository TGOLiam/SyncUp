package com.example.sync_up.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class WorkspaceLocation(
    @SerialName("member_id") val memberId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("current_zone_id") val zoneId: String? = null,
    @SerialName("location_reported_at") val reportedAt: String? = null
)

@Serializable
internal data class WorkspaceSnapshot(
    @SerialName("current_phase_id") val currentPhaseId: String? = null,
    val phases: List<RemotePhaseRow>,
    val locations: List<WorkspaceLocation>,
    val messages: List<TextMessage>
)
