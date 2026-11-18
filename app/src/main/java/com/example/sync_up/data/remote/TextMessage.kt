package com.example.sync_up.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A text message accepted by the server. */
@Serializable
internal data class TextMessage(
    @SerialName("message_id") val id: String,
    @SerialName("channel_id") val channelId: String,
    @SerialName("sender_member_id") val senderMemberId: String,
    @SerialName("text_content") val text: String,
    @SerialName("sent_at") val sentAt: String
)
