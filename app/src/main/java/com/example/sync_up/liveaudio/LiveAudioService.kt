package com.example.sync_up.liveaudio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.sync_up.R
import com.example.sync_up.data.remote.SupabaseClientFactory
import com.example.sync_up.data.remote.SupabaseEventService
import com.example.sync_up.data.SharedEventCache
import com.example.sync_up.data.TextMessageCache
import androidx.core.app.NotificationCompat
import io.github.jan.supabase.auth.auth
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

/** Keeps listen-only room connections alive while the user participates in an event. */
internal class LiveAudioService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val jobs = mutableMapOf<String, Job>()
    private var userId: String? = null
    private var eventId: String? = null
    private var globalId: String? = null
    private var pollingJob: Job? = null
    private var selection = LiveAudioPreferences.Selection(false, null)
    private val preferences by lazy { LiveAudioPreferences(applicationContext) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.live_audio_notification_channel), NotificationManager.IMPORTANCE_LOW)
            )
        }
        startForeground(NOTIFICATION_ID, notification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_DISCONNECT) {
            disconnectAll()
            return START_NOT_STICKY
        }
        if (intent == null) {
            val previous = preferences.active()
            if (previous == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            scope.launch {
                val client = SupabaseClientFactory.get(applicationContext)
                try {
                    client?.auth?.awaitInitialization()
                    if (preferences.active() == previous && client?.auth?.currentUserOrNull()?.id == previous.userId) {
                        onStartCommand(command(applicationContext, ACTION_START, previous.userId, previous.eventId, previous.globalId), 0, startId)
                    } else {
                        preferences.setActive(null)
                        stopSelf()
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.w(TAG, "restore failed", error)
                    stopSelf()
                }
            }
            return START_STICKY
        }
        val newUser = intent.getStringExtra(EXTRA_USER)
        val newEvent = intent.getStringExtra(EXTRA_EVENT)
        val newGlobal = intent.getStringExtra(EXTRA_GLOBAL)
        if (newUser == null || newEvent == null || newGlobal == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (newUser != userId || newEvent != eventId) {
            cancelConnections()
            userId = newUser
            eventId = newEvent
            globalId = newGlobal
            selection = preferences.load(newUser, newEvent)
        }
        when (intent.action) {
            ACTION_GLOBAL -> selection = selection.copy(globalEnabled = intent.getBooleanExtra(EXTRA_ENABLED, false))
            ACTION_SELECT -> selection = selection.copy(selectedId = intent.getStringExtra(EXTRA_SELECTED))
        }
        preferences.save(newUser, newEvent, selection)
        preferences.setActive(LiveAudioPreferences.ActiveSession(newUser, newEvent, newGlobal))
        synchronizeRooms()
        return START_STICKY
    }

    private fun synchronizeRooms() {
        val global = globalId ?: return
        val desired = buildSet {
            if (selection.globalEnabled) add(global)
            selection.selectedId?.let { add(it) }
        }
        for (id in jobs.keys.toList()) {
            if (id !in desired) jobs.remove(id)?.cancel()
        }
        LiveAudioSession.set(
            LiveAudioSession.State(
                eventId = eventId,
                globalId = global,
                selectedId = selection.selectedId,
                global = statusFor(global, desired),
                selected = selection.selectedId?.let { statusFor(it, desired) } ?: LiveAudioSession.Status.DISCONNECTED
            )
        )
        if (desired.isEmpty()) {
            preferences.setActive(null)
            stopSelf()
            return
        }
        for (id in desired) {
            if (jobs[id] == null) jobs[id] = scope.launch { maintainRoom(id) }
        }
        if (pollingJob == null) pollingJob = scope.launch { pollWorkspace() }
    }

    private suspend fun pollWorkspace() {
        val seen = mutableMapOf<String, MutableSet<String>>()
        while (scope.isActive) {
            val currentEvent = eventId ?: break
            val currentUser = userId ?: break
            val client = SupabaseClientFactory.get(applicationContext) ?: break
            val state = LiveAudioSession.state.value
            val channels = buildList {
                if (state.global == LiveAudioSession.Status.CONNECTED) state.globalId?.let { add(it) }
                if (state.selected == LiveAudioSession.Status.CONNECTED) state.selectedId?.let { add(it) }
            }.distinct()
            seen.keys.retainAll(channels.toSet())
            try {
                val snapshot = SupabaseEventService(client).pollWorkspace(currentEvent, channels)
                if (eventId != currentEvent || userId != currentUser) break
                withContext(Dispatchers.IO) {
                    TextMessageCache(applicationContext).use { it.save(currentUser, snapshot.messages) }
                    SharedEventCache(applicationContext).use { it.updatePhases(currentEvent, snapshot) }
                }
                val ownMember = snapshot.locations.firstOrNull { it.userId == currentUser }?.memberId
                for (channel in channels) {
                    val messages = snapshot.messages.filter { it.channelId == channel }
                    val previous = seen[channel]
                    if (previous != null) {
                        val newest = messages.lastOrNull { it.id !in previous && it.senderMemberId != ownMember }
                        if (newest != null) notifyText(newest.text)
                    }
                    seen[channel] = messages.mapTo(mutableSetOf()) { it.id }
                }
                WorkspaceUpdates.publish(currentEvent, snapshot)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Keep the last confirmed state and retry on the next cycle.
                Log.w(TAG, "workspace poll failed", error)
            }
            delay(3_000)
        }
    }

    private fun notifyText(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel("workspace_messages", getString(R.string.text_notification_channel), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val notification = NotificationCompat.Builder(this, "workspace_messages")
            .setSmallIcon(R.drawable.ic_workspace_microphone)
            .setContentTitle(getString(R.string.text_notification_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        manager.notify(103, notification)
    }

    private fun statusFor(id: String, desired: Set<String>): LiveAudioSession.Status {
        if (id !in desired) return LiveAudioSession.Status.DISCONNECTED
        val previous = LiveAudioSession.state.value
        return when (id) {
            previous.globalId -> previous.global
            previous.selectedId -> previous.selected
            else -> LiveAudioSession.Status.CONNECTING
        }.let { if (it == LiveAudioSession.Status.DISCONNECTED) LiveAudioSession.Status.CONNECTING else it }
    }

    private suspend fun maintainRoom(channelId: String) {
        var retrySeconds = 2L
        while (scope.isActive) {
            val client = SupabaseClientFactory.get(applicationContext)
            if (client == null || client.auth.currentUserOrNull()?.id != userId) {
                preferences.setActive(null)
                cancelConnections()
                stopSelf()
                return
            }
            val room = LiveKit.create(applicationContext)
            val connected = CompletableDeferred<Unit>()
            val disconnected = CompletableDeferred<Unit>()
            val listener = scope.launch {
                room.events.events.collect { event ->
                    when (event) {
                        is RoomEvent.Connected -> {
                            LiveAudioSession.attach(channelId, room)
                            connected.complete(Unit)
                            setStatus(channelId, LiveAudioSession.Status.CONNECTED)
                        }
                        is RoomEvent.Reconnecting -> setStatus(channelId, LiveAudioSession.Status.RECONNECTING)
                        is RoomEvent.Reconnected -> setStatus(channelId, LiveAudioSession.Status.CONNECTED)
                        is RoomEvent.Disconnected -> {
                            connected.completeExceptionally(IllegalStateException("Room disconnected"))
                            disconnected.complete(Unit)
                        }
                        else -> Unit
                    }
                }
            }
            try {
                val credentials = LiveKitTokenClient(client).get(channelId)
                room.connect(credentials.url, credentials.token)
                withTimeout(15000) { connected.await() }
                retrySeconds = 2L
                disconnected.await()
            } catch (error: TimeoutCancellationException) {
                // An incomplete media connection is retried with a bounded delay.
                Log.w(TAG, "connect timeout for room $channelId")
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Network and authorization failures are retried with a bounded delay.
                Log.w(TAG, "connect failed for room $channelId", error)
            } finally {
                LiveAudioSession.detach(channelId, room)
                listener.cancel()
                room.disconnect()
                room.release()
            }
            setStatus(channelId, LiveAudioSession.Status.RECONNECTING)
            delay(retrySeconds * 1000)
            retrySeconds = (retrySeconds * 2).coerceAtMost(30)
        }
    }

    private fun setStatus(channelId: String, status: LiveAudioSession.Status) {
        val state = LiveAudioSession.state.value
        if (state.eventId != eventId) return
        LiveAudioSession.set(
            when (channelId) {
                state.globalId -> state.copy(global = status)
                state.selectedId -> state.copy(selected = status)
                else -> state
            }
        )
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
    }

    private fun disconnectAll() {
        val persisted = preferences.active()
        val user = userId ?: persisted?.userId
        val event = eventId ?: persisted?.eventId
        if (user != null && event != null) {
            preferences.save(user, event, LiveAudioPreferences.Selection(false, null))
        }
        preferences.setActive(null)
        cancelConnections()
        stopSelf()
    }

    private fun cancelConnections() {
        pollingJob?.cancel()
        pollingJob = null
        WorkspaceUpdates.clear()
        jobs.values.forEach { it.cancel() }
        jobs.clear()
        LiveAudioSession.set(LiveAudioSession.State())
    }

    private fun notification() = LiveAudioNotification.create(this, CHANNEL_ID, ACTION_DISCONNECT)

    override fun onDestroy() {
        cancelConnections()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LiveAudioService"
        private const val CHANNEL_ID = "live_audio"
        private const val NOTIFICATION_ID = 102
        private const val ACTION_DISCONNECT = "com.example.sync_up.liveaudio.DISCONNECT"
        private const val ACTION_START = "com.example.sync_up.liveaudio.START"
        private const val ACTION_GLOBAL = "com.example.sync_up.liveaudio.GLOBAL"
        private const val ACTION_SELECT = "com.example.sync_up.liveaudio.SELECT"
        private const val EXTRA_USER = "user"
        private const val EXTRA_EVENT = "event"
        private const val EXTRA_GLOBAL = "global"
        private const val EXTRA_ENABLED = "enabled"
        private const val EXTRA_SELECTED = "selected"

        fun disconnect(context: Context) {
            LiveAudioPreferences(context).setActive(null)
            if (LiveAudioSession.state.value.eventId != null) {
                context.startService(Intent(context, LiveAudioService::class.java).setAction(ACTION_DISCONNECT))
            }
        }

        fun start(context: Context, userId: String, eventId: String, globalId: String) {
            val intent = command(context, ACTION_START, userId, eventId, globalId)
            ContextCompat.startForegroundService(context, intent)
        }

        fun setGlobal(context: Context, userId: String, eventId: String, globalId: String, enabled: Boolean) {
            val intent = command(context, ACTION_GLOBAL, userId, eventId, globalId)
                .putExtra(EXTRA_ENABLED, enabled)
            ContextCompat.startForegroundService(context, intent)
        }

        fun select(context: Context, userId: String, eventId: String, globalId: String, selectedId: String?) {
            val intent = command(context, ACTION_SELECT, userId, eventId, globalId)
                .putExtra(EXTRA_SELECTED, selectedId)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun command(context: Context, action: String, user: String, event: String, global: String): Intent {
            return Intent(context, LiveAudioService::class.java).setAction(action)
                .putExtra(EXTRA_USER, user)
                .putExtra(EXTRA_EVENT, event)
                .putExtra(EXTRA_GLOBAL, global)
        }
    }
}
