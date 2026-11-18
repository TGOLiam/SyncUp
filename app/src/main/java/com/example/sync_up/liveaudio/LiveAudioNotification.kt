package com.example.sync_up.liveaudio

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.sync_up.EventListActivity
import com.example.sync_up.R

/** Builds the persistent notification from the current live listening state. */
internal object LiveAudioNotification {
    fun create(context: Context, channelId: String, disconnectAction: String) =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_workspace_microphone)
            .setContentTitle(context.getString(R.string.live_audio_notification_title))
            .setContentText(context.getString(statusText()))
            .setOngoing(true)
            .setContentIntent(PendingIntent.getActivity(
                context, 0, Intent(context, EventListActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .addAction(0, context.getString(R.string.live_audio_disconnect), PendingIntent.getService(
                context, 1, Intent(context, LiveAudioService::class.java).setAction(disconnectAction),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

    private fun statusText(): Int {
        val state = LiveAudioSession.state.value
        val statuses = listOf(state.global, state.selected)
        return when {
            LiveAudioSession.Status.RECONNECTING in statuses -> R.string.live_audio_notification_retrying
            LiveAudioSession.Status.CONNECTING in statuses -> R.string.live_audio_notification_connecting
            LiveAudioSession.Status.CONNECTED in statuses -> R.string.live_audio_notification_text
            else -> R.string.live_audio_notification_connecting
        }
    }
}
