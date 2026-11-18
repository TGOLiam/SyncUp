package com.example.sync_up.liveaudio

import com.example.sync_up.data.remote.WorkspaceSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Latest server-confirmed workspace snapshot for the current app process. */
internal object WorkspaceUpdates {
    data class State(val eventId: String, val snapshot: WorkspaceSnapshot, val revision: Long)

    private val mutable = MutableStateFlow<State?>(null)
    val state = mutable.asStateFlow()

    fun publish(eventId: String, snapshot: WorkspaceSnapshot) {
        mutable.value = State(eventId, snapshot, (mutable.value?.revision ?: 0) + 1)
    }

    fun clear() {
        mutable.value = null
    }
}
