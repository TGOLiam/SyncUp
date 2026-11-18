package com.example.sync_up.liveaudio

import android.content.Context
import io.livekit.android.room.Room
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Process-visible connection state; it never contains credentials. */
internal object LiveAudioSession {
    enum class Status { DISCONNECTED, CONNECTING, CONNECTED, RECONNECTING }

    data class State(
        val eventId: String? = null,
        val globalId: String? = null,
        val selectedId: String? = null,
        val global: Status = Status.DISCONNECTED,
        val selected: Status = Status.DISCONNECTED
    )

    private val rooms = mutableMapOf<String, Room>()
    private val mutableState = MutableStateFlow(State())
    val state: StateFlow<State> = mutableState

    fun set(value: State) {
        mutableState.value = value
    }

    fun attach(channelId: String, room: Room) {
        rooms[channelId] = room
    }

    fun detach(channelId: String, room: Room) {
        if (rooms[channelId] === room) rooms.remove(channelId)
    }

    fun room(channelId: String): Room? = rooms[channelId]
}

/** Remembers explicit disconnections and the selected channel per account and event. */
internal class LiveAudioPreferences(
    context: Context,
    preferencesName: String = "live_audio_sessions"
) {
    private val preferences = context.applicationContext.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)

    data class Selection(val globalEnabled: Boolean, val selectedId: String?)
    data class ActiveSession(val userId: String, val eventId: String, val globalId: String)

    fun active(): ActiveSession? {
        val user = preferences.getString("active_user", null) ?: return null
        val event = preferences.getString("active_event", null) ?: return null
        val global = preferences.getString("active_global", null) ?: return null
        return ActiveSession(user, event, global)
    }

    fun setActive(session: ActiveSession?) {
        preferences.edit()
            .putString("active_user", session?.userId)
            .putString("active_event", session?.eventId)
            .putString("active_global", session?.globalId)
            .apply()
    }

    fun load(userId: String, eventId: String): Selection {
        val key = "$userId:$eventId"
        return Selection(
            globalEnabled = preferences.getBoolean("$key:global", true),
            selectedId = preferences.getString("$key:selected", null)
        )
    }

    fun save(userId: String, eventId: String, selection: Selection) {
        val key = "$userId:$eventId"
        preferences.edit()
            .putBoolean("$key:global", selection.globalEnabled)
            .putString("$key:selected", selection.selectedId)
            .apply()
    }
}
