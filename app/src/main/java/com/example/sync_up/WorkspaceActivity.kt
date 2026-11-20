package com.example.sync_up

import android.Manifest
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.Build
import android.view.MotionEvent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.example.sync_up.data.DraftSaveQueue
import com.example.sync_up.liveaudio.LiveAudioPreferences
import com.example.sync_up.liveaudio.LiveAudioService
import com.example.sync_up.liveaudio.LiveAudioSession
import com.example.sync_up.liveaudio.LiveSpeechClient
import com.example.sync_up.liveaudio.WorkspaceUpdates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.time.OffsetDateTime
import java.util.Date
import com.example.sync_up.data.remote.SupabaseClientFactory
import com.example.sync_up.data.remote.SupabaseEventService
import com.example.sync_up.data.TextMessageCache
import io.github.jan.supabase.auth.auth

/** Hosts the three workspace sections while keeping their views and navigation in place. */
class WorkspaceActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"

        // Organizers (ref 17) lives in the shell but has no navigation row.
        const val SECTION_ORGANIZERS = 3
    }

    private var channelIds: Map<String, String> = emptyMap()
    private var phaseIds: List<String> = emptyList()
    private var activePhaseId: String? = null
    private var zoneIds: List<Pair<String, String>> = emptyList()
    private var pressHeld = false
    private var requestPending = false
    private var lease: Pair<String, String>? = null
    private var speechTimeout: Job? = null
    private var speakingLive = false
    private var messageRetry: Triple<String, String, String>? = null
    private var ownMemberId: String? = null
    private var messageLoad: Job? = null
    private var retryAfter = 0L
    private var pendingAudioAction: (() -> Unit)? = null
    private var automaticNotificationRequestMade = false
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pendingAudioAction
        pendingAudioAction = null
        if (granted && lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            action?.invoke()
        } else if (!granted) {
            Toast.makeText(this, R.string.live_audio_notification_required, Toast.LENGTH_LONG).show()
        }
    }
    private val microphonePermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        Toast.makeText(this, if (granted) R.string.voice_hold_again else R.string.voice_permission_denied, Toast.LENGTH_LONG).show()
    }
    private var selectedChannel = ""
    private var connectedChannel: String? = null
    private val globalChannel by lazy { getString(R.string.channel_global) }
    private var selectedSection = 0
    private var event: EventListItem? = null
    private var creatorId: String? = null
    private var draft: EventCreationDraft = EventCreationSession.draft

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.workspace_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        selectedChannel = savedInstanceState?.getString("selected_channel") ?: globalChannel
        connectedChannel = savedInstanceState?.getString("connected_channel")
        selectedSection = savedInstanceState?.getInt("selected_section") ?: 0
        findViewById<View>(R.id.workspace_back).setOnClickListener {
            // Organizers is an in-shell screen; back first returns to the Event Area.
            if (selectedSection == SECTION_ORGANIZERS) showSection(0) else finish()
        }
        findViewById<View>(R.id.workspace_organizers_back).setOnClickListener { showSection(0) }
        findViewById<View>(R.id.workspace_organizers_entry).setOnClickListener {
            // Ref 16: the organizer entry opens the in-shell organizers screen.
            showSection(SECTION_ORGANIZERS)
        }
        findViewById<View>(R.id.workspace_nav_area).setOnClickListener { showSection(0) }
        findViewById<View>(R.id.workspace_nav_comms).setOnClickListener { showSection(1) }
        findViewById<View>(R.id.workspace_nav_phases).setOnClickListener { showSection(2) }
        findViewById<View>(R.id.workspace_report_location).setOnClickListener { reportLocation() }
        findViewById<View>(R.id.comms_send).setOnClickListener { sendTextMessage() }
        findViewById<View>(R.id.comms_push_to_talk).setOnTouchListener { _, motion ->
            when (motion.actionMasked) {
                MotionEvent.ACTION_DOWN -> startPushToTalk()
                MotionEvent.ACTION_UP -> finishPushToTalk()
                MotionEvent.ACTION_CANCEL -> cancelPushToTalk()
            }
            true
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                LiveAudioSession.state.collect { state ->
                    if (event?.isPreview == false) {
                        connectedChannel = if (state.eventId == event?.previewId) {
                            state.selectedId?.let { id ->
                                channelIds.entries.firstOrNull { it.key.isNotEmpty() && it.value == id }?.key
                            }
                        } else {
                            null
                        }
                        if (selectedChannel != globalChannel && selectedChannel != connectedChannel) {
                            selectedChannel = globalChannel
                        }
                        val active = lease
                        if (active != null) {
                            val channelStatus = when (active.first) {
                                state.globalId -> state.global
                                state.selectedId -> state.selected
                                else -> LiveAudioSession.Status.DISCONNECTED
                            }
                            if (state.eventId != event?.previewId || channelStatus != LiveAudioSession.Status.CONNECTED) {
                                finishPushToTalk()
                            }
                        }
                        renderChannels()
                        renderConversation()
                    }
                }
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                WorkspaceUpdates.state.collect { update ->
                    val current = event
                    if (update != null && current != null && current.previewId == update.eventId) {
                        val active = update.snapshot.phases.firstOrNull { it.id == update.snapshot.currentPhaseId }
                        event = current.copy(
                            workspacePhase = active?.name,
                            phases = update.snapshot.phases.map { it.name }
                        )
                        phaseIds = update.snapshot.phases.map { it.id }
                        activePhaseId = update.snapshot.currentPhaseId
                        findViewById<TextView>(R.id.workspace_phase).text = active?.name ?: getString(R.string.event_no_phase)
                        renderPhases()
                        renderLocations(update.snapshot.locations)
                        renderOrganizers(update.snapshot.locations)
                        loadMessages()
                    }
                }
            }
        }
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId == null) {
            renderEvent()
        } else {
            findViewById<View>(R.id.workspace_root).visibility = View.INVISIBLE
            val userId = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
            DraftSaveQueue.readEvents(applicationContext, { it.load(eventId, userId) }) { result ->
                if (isFinishing || isDestroyed) return@readEvents
                val stored = result.getOrNull()
                if (stored == null) {
                    Toast.makeText(this, R.string.workspace_load_failed, Toast.LENGTH_LONG).show()
                    finish()
                    return@readEvents
                }
                channelIds = stored.channelIds
                phaseIds = stored.phaseIds
                activePhaseId = stored.summary.activePhaseId
                zoneIds = stored.zoneIds
                event = EventListItem(
                    previewId = stored.summary.id,
                    isPreview = stored.summary.isPreview,
                    name = stored.summary.name,
                    detail = stored.summary.detail.orEmpty(),
                    isLead = if (stored.summary.isPreview) stored.summary.isLeadPreview else stored.summary.isCreator,
                    workspacePhase = stored.summary.activePhase,
                    phases = stored.phases,
                    channels = stored.channels
                )
                creatorId = stored.summary.creatorId
                draft = EventCreationDraft().apply {
                    name = stored.summary.name
                    layout = stored.layout
                    channels.addAll(stored.channels)
                }
                renderEvent()
                findViewById<View>(R.id.workspace_root).visibility = View.VISIBLE
                ensureLiveListening()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ensureLiveListening()
    }

    private fun ensureLiveListening() {
        val currentEvent = event ?: return
        if (currentEvent.isPreview || !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        if (LiveAudioSession.state.value.eventId == currentEvent.previewId) return
        val user = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id ?: return
        val global = channelIds[""] ?: return
        val selection = LiveAudioPreferences(applicationContext).load(user, currentEvent.previewId)
        if (!selection.globalEnabled && selection.selectedId == null) return
        val permissionMissing = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (permissionMissing && (automaticNotificationRequestMade || pendingAudioAction != null)) return
        if (permissionMissing) automaticNotificationRequestMade = true
        withNotificationPermission {
            LiveAudioService.start(applicationContext, user, currentEvent.previewId, global)
        }
    }

    private fun withNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            action()
        } else {
            pendingAudioAction = action
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startPushToTalk() {
        if (requestPending || lease != null) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        if (SystemClock.elapsedRealtime() < retryAfter) {
            Toast.makeText(this, R.string.voice_wait_before_retry, Toast.LENGTH_SHORT).show()
            return
        }
        val client = SupabaseClientFactory.get(applicationContext)
        val currentEvent = event
        val channelId = channelIds[if (selectedChannel == globalChannel) "" else selectedChannel]
        val state = LiveAudioSession.state.value
        val status = if (channelId == state.globalId) state.global else state.selected
        if (client == null || currentEvent == null || currentEvent.isPreview || channelId == null ||
            state.eventId != currentEvent.previewId || status != LiveAudioSession.Status.CONNECTED ||
            LiveAudioSession.room(channelId) == null
        ) {
            Toast.makeText(this, R.string.voice_channel_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val requestId = UUID.randomUUID().toString()
        val speech = LiveSpeechClient(client)
        pressHeld = true
        requestPending = true
        lifecycleScope.launch {
            try {
                if (!speech.request(channelId, requestId)) {
                    retryAfter = SystemClock.elapsedRealtime() + 3000
                    if (pressHeld) {
                        Toast.makeText(this@WorkspaceActivity, R.string.voice_channel_busy, Toast.LENGTH_SHORT).show()
                    }
                    pressHeld = false
                    return@launch
                }
                if (!pressHeld || isFinishing || isDestroyed) {
                    speech.stop(channelId, requestId)
                    return@launch
                }
                val active = channelId to requestId
                lease = active
                val room = LiveAudioSession.room(channelId)
                if (room == null) {
                    finishPushToTalk()
                    return@launch
                }
                val ready = withTimeoutOrNull(3000) {
                    while (pressHeld && lease == active && room.localParticipant.permissions?.canPublish != true) {
                        delay(50)
                    }
                    room.localParticipant.permissions?.canPublish == true && pressHeld && lease == active
                } == true
                if (!ready) {
                    if (lease == active) finishPushToTalk()
                    return@launch
                }
                if (!room.localParticipant.setMicrophoneEnabled(true)) {
                    if (lease == active) finishPushToTalk()
                    Toast.makeText(this@WorkspaceActivity, R.string.voice_recording_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                if (!pressHeld || lease != active) {
                    room.localParticipant.setMicrophoneEnabled(false)
                    return@launch
                }
                speakingLive = true
                findViewById<MaterialButton>(R.id.comms_push_to_talk).setText(R.string.voice_live_speaking)
                speechTimeout = lifecycleScope.launch {
                    delay(10000)
                    if (lease == active) finishPushToTalk()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                pressHeld = false
                if (lease == (channelId to requestId)) finishPushToTalk()
                Toast.makeText(this@WorkspaceActivity, R.string.voice_connection_failed, Toast.LENGTH_LONG).show()
            } finally {
                requestPending = false
            }
        }
    }

    private fun finishPushToTalk() {
        pressHeld = false
        val active = lease ?: return
        lease = null
        speechTimeout?.cancel()
        speechTimeout = null
        speakingLive = false
        renderConversation()
        val client = SupabaseClientFactory.get(applicationContext) ?: return
        val room = LiveAudioSession.room(active.first)
        lifecycleScope.launch {
            try {
                room?.localParticipant?.setMicrophoneEnabled(false)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // The server also revokes publishing at the speech deadline.
            } finally {
                try {
                    LiveSpeechClient(client).stop(active.first, active.second)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // The controller revokes permission even if this request fails.
                }
            }
        }
    }

    private fun cancelPushToTalk() {
        finishPushToTalk()
    }

    override fun onPause() {
        cancelPushToTalk()
        super.onPause()
    }

    private fun renderEvent() {
        // Ref 16: the organizers entry needs a real joined event; previews have no member data.
        findViewById<View>(R.id.workspace_organizers_entry).visibility =
            if (event?.isPreview == false) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.workspace_organizer_count).text =
            WorkspaceUpdates.state.value?.takeIf { it.eventId == event?.previewId }
                ?.snapshot?.locations?.size?.toString().orEmpty()
        findViewById<WorkspaceLayoutView>(R.id.workspace_layout).layout = draft.layout
        findViewById<View>(R.id.workspace_empty_layout).visibility =
            if (draft.layout == null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.workspace_phase).text =
            event?.workspacePhase ?: getString(R.string.event_no_phase)
        renderChannels()
        renderConversation()
        renderPhases()
        showSection(selectedSection)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("selected_section", selectedSection)
        outState.putString("selected_channel", selectedChannel)
        outState.putString("connected_channel", connectedChannel)
        super.onSaveInstanceState(outState)
    }

    private fun showSection(section: Int) {
        if (section != 1) {
            val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            input.hideSoftInputFromWindow(findViewById<View>(R.id.comms_message).windowToken, 0)
            findViewById<View>(R.id.comms_message).clearFocus()
        }
        selectedSection = section
        val sections = listOf(
            R.id.workspace_section_area,
            R.id.workspace_section_comms,
            R.id.workspace_section_phases,
            R.id.workspace_section_organizers
        )
        // Navigation rows exist only for the first three sections.
        val navigation = listOf(R.id.workspace_nav_area, R.id.workspace_nav_comms, R.id.workspace_nav_phases)
        val indicators = listOf(R.id.workspace_indicator_area, R.id.workspace_indicator_comms, R.id.workspace_indicator_phases)
        val icons = listOf(R.id.workspace_icon_area, R.id.workspace_icon_comms, R.id.workspace_icon_phases)
        val labels = listOf(R.id.workspace_label_area, R.id.workspace_label_comms, R.id.workspace_label_phases)
        for (index in sections.indices) {
            findViewById<View>(sections[index]).visibility = if (index == section) View.VISIBLE else View.GONE
        }
        for (index in navigation.indices) {
            // The organizers screen keeps the Event Area entry highlighted (ref 17).
            val selected = index == section || (section == SECTION_ORGANIZERS && index == 0)
            findViewById<View>(navigation[index]).isSelected = selected
            val nav = findViewById<View>(navigation[index])
            nav.setBackgroundColor(
                ContextCompat.getColor(this, if (selected) R.color.ui_selected_nav else android.R.color.transparent)
            )
            val color = ContextCompat.getColor(this, if (selected) R.color.ui_green else R.color.ui_nav_text)
            findViewById<View>(indicators[index]).setBackgroundColor(
                ContextCompat.getColor(this, if (selected) R.color.ui_orange else android.R.color.transparent)
            )
            findViewById<ImageView>(icons[index]).setColorFilter(color)
            findViewById<TextView>(labels[index]).apply {
                setTextColor(color)
                setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        findViewById<TextView>(R.id.workspace_context).text =
            (event?.name ?: draft.name).uppercase()
        findViewById<TextView>(R.id.workspace_title).text = when (section) {
            1 -> getString(R.string.workspace_communications)
            2 -> getString(R.string.event_phases)
            SECTION_ORGANIZERS -> getString(R.string.workspace_organizers)
            else -> getString(R.string.workspace_area)
        }
        findViewById<android.widget.Button>(R.id.workspace_edit).apply {
            visibility = if (section != 1 && section != SECTION_ORGANIZERS && (event?.isPreview != false) &&
                (event?.isLead ?: true)
            ) {
                View.VISIBLE
            } else {
                View.GONE
            }
            setText(if (section == 2) R.string.venue_edit else R.string.workspace_edit_layout)
            setOnClickListener {
                val message = if (section == 2) R.string.phases_edit_placeholder else R.string.workspace_edit_pending
                Toast.makeText(this@WorkspaceActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPhases() {
        val phases = event?.phases ?: draft.phases.map { it.label }
        val activeIndex = if (activePhaseId != null) {
            phaseIds.indexOf(activePhaseId)
        } else {
            -1
        }
        val isLead = event?.isLead ?: true
        findViewById<View>(R.id.workspace_phase_empty).visibility =
            if (phases.isEmpty()) View.VISIBLE else View.GONE
        val list = findViewById<LinearLayout>(R.id.workspace_phase_list)
        list.removeAllViews()
        for ((index, phase) in phases.withIndex()) {
            val active = index == activeIndex
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                // Ref 21: plain rows with a hairline; the active phase is the dark green card.
                setBackgroundResource(if (active) R.drawable.bg_ui_button_green else R.drawable.bg_ui_row_divider)
                setPadding(0, dp(14), 0, dp(14))
            }
            if (active) row.setPadding(dp(14), dp(14), dp(14), dp(14))
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val statusRes = when {
                active -> R.string.workspace_phase_active
                activeIndex >= 0 && index < activeIndex -> R.string.workspace_phase_completed
                else -> R.string.workspace_phase_upcoming
            }
            val completed = statusRes == R.string.workspace_phase_completed
            val title = TextView(this).apply {
                // Completed phases carry a leading check marker.
                text = if (completed) "✓  $phase" else phase
                textSize = if (active) 16f else 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(
                    ContextCompat.getColor(
                        this@WorkspaceActivity,
                        if (active) R.color.ui_outgoing_text else R.color.ui_ink
                    )
                )
            }
            content.addView(title)
            if (!completed) {
                content.addView(TextView(this).apply {
                    text = getString(statusRes)
                    textSize = 13f
                    setTextColor(
                        ContextCompat.getColor(
                            this@WorkspaceActivity,
                            if (active) R.color.ui_outgoing_meta else R.color.ui_secondary_text
                        )
                    )
                })
            } else {
                // Ref 21: "✓ Name · Completed" on a single line.
                title.text = "✓  $phase · ${getString(statusRes)}"
            }
            row.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (isLead && !active && (activeIndex < 0 || index > activeIndex)) {
                val button = android.widget.Button(this).apply {
                    setText(R.string.workspace_phase_activate)
                    isAllCaps = false
                    setTextAppearance(R.style.TextAppearance_Ui_Label)
                    setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
                    setBackgroundResource(R.drawable.bg_ui_button_outline)
                    minHeight = (44f * resources.displayMetrics.density).toInt()
                    minWidth = dp(88)
                }
                button.setOnClickListener {
                    activatePhase(index, button)
                }
                row.addView(button)
            }
            list.addView(row, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    private fun activatePhase(index: Int, button: View) {
        val current = event ?: return
        val phaseId = phaseIds.getOrNull(index) ?: return
        if (current.isPreview || !current.isLead) return
        val client = SupabaseClientFactory.get(applicationContext) ?: return
        val userId = client.auth.currentUserOrNull()?.id ?: return
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val service = SupabaseEventService(client)
                service.activatePhase(current.previewId, phaseId)
                val remote = service.loadEvent(current.previewId)
                withContext(Dispatchers.IO) {
                    com.example.sync_up.data.SharedEventCache(applicationContext).use { it.save(userId, remote) }
                }
                val active = remote.phases.firstOrNull { it.id == remote.event.currentPhaseId }
                event = current.copy(workspacePhase = active?.name, phases = remote.phases.sortedBy { it.sequence }.map { it.name })
                phaseIds = remote.phases.sortedBy { it.sequence }.map { it.id }
                activePhaseId = remote.event.currentPhaseId
                findViewById<TextView>(R.id.workspace_phase).text = active?.name ?: getString(R.string.event_no_phase)
                renderPhases()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@WorkspaceActivity, R.string.phase_activate_failed, Toast.LENGTH_LONG).show()
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun renderChannels() {
        val list = findViewById<LinearLayout>(R.id.comms_channel_list)
        list.removeAllViews()
        val channels = listOf(globalChannel) + draft.channels
        for ((index, name) in channels.withIndex()) {
            if (index > 0) {
                val divider = View(this)
                divider.layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    resources.displayMetrics.density.toInt().coerceAtLeast(1)
                )
                divider.setBackgroundColor(ContextCompat.getColor(this, R.color.event_border))
                list.addView(divider)
            }
            val row = LayoutInflater.from(this).inflate(R.layout.item_comms_channel, list, false)
            row.findViewById<TextView>(R.id.comms_channel_name).apply {
                text = name
                setTypeface(null, if (name == selectedChannel) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
            val state = LiveAudioSession.state.value
            val isShared = event?.isPreview == false
            val id = channelIds[if (index == 0) "" else name]
            val sameEvent = state.eventId == event?.previewId
            val status = if (!isShared) {
                if (index == 0 || name == connectedChannel) LiveAudioSession.Status.CONNECTED
                else LiveAudioSession.Status.DISCONNECTED
            } else if (!sameEvent) {
                LiveAudioSession.Status.DISCONNECTED
            } else if (index == 0) {
                state.global
            } else if (id == state.selectedId) {
                state.selected
            } else {
                LiveAudioSession.Status.DISCONNECTED
            }
            val connected = status == LiveAudioSession.Status.CONNECTED
            row.findViewById<ImageButton>(R.id.comms_channel_call).apply {
                imageTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(this@WorkspaceActivity,
                        if (connected) R.color.comms_connected else R.color.comms_disconnected)
                )
                contentDescription = getString(
                    if (connected) R.string.comms_connected_description else R.string.comms_disconnected_description,
                    name
                )
                setOnClickListener {
                    if (!isShared) {
                        if (index != 0) {
                            connectedChannel = if (connectedChannel == name) null else name
                            selectedChannel = connectedChannel ?: globalChannel
                            renderChannels()
                            renderConversation()
                        }
                        return@setOnClickListener
                    }
                    val user = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
                    val eventId = event?.previewId
                    val globalId = channelIds[""]
                    if (user == null || eventId == null || globalId == null || id == null) {
                        Toast.makeText(this@WorkspaceActivity, R.string.voice_channel_unavailable, Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    if (index == 0) {
                        val enabled = if (sameEvent) state.global != LiveAudioSession.Status.DISCONNECTED else false
                        withNotificationPermission {
                            LiveAudioService.setGlobal(applicationContext, user, eventId, globalId, !enabled)
                        }
                    } else {
                        val selected = if (sameEvent && state.selectedId == id) null else id
                        withNotificationPermission {
                            LiveAudioService.select(applicationContext, user, eventId, globalId, selected)
                            selectedChannel = if (selected == null) globalChannel else name
                        }
                    }
                }
            }
            row.setOnClickListener {
                if (name == globalChannel || name == connectedChannel) {
                    selectedChannel = name
                    renderChannels()
                    renderConversation()
                }
            }
            list.addView(row)
        }
    }

    private fun renderConversation() {
        findViewById<TextView>(R.id.comms_history_title).text = selectedChannel
        findViewById<EditText>(R.id.comms_message).hint = getString(R.string.comms_message_hint, selectedChannel)
        findViewById<MaterialButton>(R.id.comms_push_to_talk).text = if (speakingLive) {
            getString(R.string.voice_live_speaking)
        } else {
            getString(R.string.comms_hold_to_talk, selectedChannel)
        }
        val tabs = findViewById<LinearLayout>(R.id.comms_history_tabs)
        tabs.removeAllViews()
        val visibleTabs = listOf(globalChannel, connectedChannel)
        for ((index, name) in visibleTabs.withIndex()) {
            val tab = TextView(this)
            tab.text = name ?: getString(R.string.comms_not_connected)
            tab.gravity = android.view.Gravity.CENTER
            tab.setTypeface(null, android.graphics.Typeface.BOLD)
            tab.setTextColor(
                ContextCompat.getColor(this, if (name == selectedChannel) R.color.white else R.color.event_navy)
            )
            val background = if (index == 0) {
                R.drawable.comms_segment_left
            } else {
                R.drawable.comms_segment_right
            }
            tab.setBackgroundResource(background)
            tab.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, if (name == selectedChannel) R.color.event_navy else R.color.white)
            )
            tab.isSelected = name == selectedChannel
            if (name != null) {
                tab.setOnClickListener {
                    selectedChannel = name
                    renderChannels()
                    renderConversation()
                }
            }
            tabs.addView(tab, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
        }
        loadMessages()
    }

    private fun sendTextMessage() {
        val input = findViewById<EditText>(R.id.comms_message)
        val text = input.text.toString()
        if (text.isBlank()) return
        val client = SupabaseClientFactory.get(applicationContext) ?: return
        val userId = client.auth.currentUserOrNull()?.id ?: return
        val currentEvent = event ?: return
        val channelId = channelIds[if (selectedChannel == globalChannel) "" else selectedChannel]
        val state = LiveAudioSession.state.value
        val status = if (channelId == state.globalId) state.global else state.selected
        if (currentEvent.isPreview || channelId == null || state.eventId != currentEvent.previewId ||
            status != LiveAudioSession.Status.CONNECTED
        ) {
            Toast.makeText(this, R.string.text_channel_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        val retry = messageRetry
        val messageId = if (retry != null && retry.first == channelId && retry.second == text) {
            retry.third
        } else {
            UUID.randomUUID().toString()
        }
        messageRetry = Triple(channelId, text, messageId)
        val button = findViewById<View>(R.id.comms_send)
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val message = SupabaseEventService(client).sendTextMessage(messageId, channelId, text)
                ownMemberId = message.senderMemberId
                withContext(Dispatchers.IO) {
                    TextMessageCache(applicationContext).use { it.save(userId, listOf(message)) }
                }
                messageRetry = null
                if (input.text.toString() == text) input.text.clear()
                loadMessages()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@WorkspaceActivity, R.string.text_send_failed, Toast.LENGTH_LONG).show()
            } finally {
                button.isEnabled = true
            }
        }
    }

    private fun reportLocation() {
        val current = event ?: return
        if (current.isPreview) return
        if (zoneIds.isEmpty()) {
            Toast.makeText(this, R.string.location_no_zones, Toast.LENGTH_SHORT).show()
            return
        }
        val client = SupabaseClientFactory.get(applicationContext) ?: return
        // Ref 18: revised dialog surface over the dimmed Event Area.
        AlertDialog.Builder(this, R.style.Theme_SYNCUP_UiDialog)
            .setCustomTitle(uiDialogTitle(this, R.string.location_choose_zone))
            .setItems(zoneIds.map { it.second }.toTypedArray()) { _, index ->
                lifecycleScope.launch {
                    try {
                        SupabaseEventService(client).reportLocation(current.previewId, zoneIds[index].first)
                        Toast.makeText(this@WorkspaceActivity, R.string.location_report_sent, Toast.LENGTH_SHORT).show()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        Toast.makeText(this@WorkspaceActivity, R.string.location_report_failed, Toast.LENGTH_LONG).show()
                    }
                }
            }
            .show()
    }

    private fun reportTimestampDetail(location: com.example.sync_up.data.remote.WorkspaceLocation): String? {
        val zone = zoneIds.firstOrNull { it.first == location.zoneId }?.second ?: return null
        val time = location.reportedAt?.let { reportedAt ->
            try {
                val instant = OffsetDateTime.parse(reportedAt).toInstant()
                android.text.format.DateFormat.getTimeFormat(this).format(Date.from(instant))
            } catch (_: Exception) {
                null
            }
        }
        return if (time == null) zone else getString(R.string.location_report_entry, zone, time)
    }

    private fun renderLocations(locations: List<com.example.sync_up.data.remote.WorkspaceLocation>) {
        val list = findViewById<LinearLayout>(R.id.workspace_reports)
        list.removeAllViews()
        // Ref 16: report rows are borderless entries with a bottom hairline.
        for (location in locations) {
            val detail = reportTimestampDetail(location) ?: continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_ui_row_divider)
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(TextView(this).apply {
                text = location.name ?: getString(R.string.message_member)
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
            })
            row.addView(TextView(this).apply {
                text = detail
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_secondary_text))
            })
            list.addView(row)
        }
        findViewById<TextView>(R.id.workspace_reports_empty).visibility =
            if (list.childCount == 0) View.VISIBLE else View.GONE
    }

    private fun renderOrganizers(locations: List<com.example.sync_up.data.remote.WorkspaceLocation>) {
        if (selectedSection != SECTION_ORGANIZERS) {
            // Row rebuild can happen while the organizer list is still visible later;
            // the count on the Event Area entry stays fresh.
            findViewById<TextView>(R.id.workspace_organizer_count).text = locations.size.toString()
        }
        findViewById<TextView>(R.id.workspace_organizer_subtitle).text =
            getString(R.string.workspace_organizer_members, locations.size)
        val list = findViewById<LinearLayout>(R.id.workspace_organizer_list)
        list.removeAllViews()
        val userId = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
        for (location in locations) {
            val detail = reportTimestampDetail(location)
            val isYou = location.userId == userId
            val isLead = location.userId != null && location.userId == creatorId
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ui_row_divider)
                setPadding(0, dp(12), 0, dp(12))
            }
            row.addView(TextView(this).apply {
                text = (location.name ?: getString(R.string.message_member)).trim().take(1).uppercase()
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                setBackgroundResource(R.drawable.bg_ui_avatar)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_green))
                minimumHeight = dp(36)
                minimumWidth = dp(36)
            })
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val nameLine = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            nameLine.addView(TextView(this).apply {
                text = if (isYou) getString(R.string.message_you) else location.name ?: getString(R.string.message_member)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
            })
            if (isLead) {
                nameLine.addView(TextView(this).apply {
                    text = getString(if (isYou) R.string.workspace_organizer_badge_you else R.string.workspace_organizer_badge_lead)
                    textSize = 11f
                    setPadding(dp(7), dp(5), dp(7), dp(5))
                    setBackgroundResource(R.drawable.bg_ui_chip)
                    setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_chip_text))
                }, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(8) })
            }
            column.addView(nameLine)
            column.addView(TextView(this).apply {
                text = detail ?: getString(R.string.workspace_organizer_no_location)
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_secondary_text))
            })
            row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
            list.addView(row)
        }
    }

    private fun loadMessages() {
        messageLoad?.cancel()
        val userId = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id ?: return
        val channelId = channelIds[if (selectedChannel == globalChannel) "" else selectedChannel] ?: return
        messageLoad = lifecycleScope.launch {
            val messages = withContext(Dispatchers.IO) {
                TextMessageCache(applicationContext).use { it.recent(userId, channelId) }
            }
            val list = findViewById<LinearLayout>(R.id.comms_messages)
            list.removeAllViews()
            findViewById<View>(R.id.comms_empty_history).visibility = if (messages.isEmpty()) View.VISIBLE else View.GONE
            val update = WorkspaceUpdates.state.value?.takeIf { it.eventId == event?.previewId }
            val members = update?.snapshot?.locations.orEmpty()
            val ownId = members.firstOrNull { it.userId == userId }?.memberId ?: ownMemberId
            for (message in messages) {
                val isOwn = message.senderMemberId == ownId
                val sender = if (isOwn) {
                    getString(R.string.message_you)
                } else {
                    members.firstOrNull { it.memberId == message.senderMemberId }?.name
                        ?: getString(R.string.message_member)
                }
                val time = try {
                    val instant = OffsetDateTime.parse(message.sentAt).toInstant()
                    android.text.format.DateFormat.getTimeFormat(this@WorkspaceActivity).format(Date.from(instant))
                } catch (_: Exception) {
                    ""
                }
                val row = LinearLayout(this@WorkspaceActivity).apply {
                    gravity = if (isOwn) android.view.Gravity.END else android.view.Gravity.START
                }
                val bubble = LinearLayout(this@WorkspaceActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(8), dp(12), dp(8))
                    setBackgroundResource(if (isOwn) R.drawable.comms_message_outgoing else R.drawable.comms_message_incoming)
                }
                bubble.addView(TextView(this@WorkspaceActivity).apply {
                    text = if (time.isEmpty()) sender else getString(R.string.message_metadata, sender, time)
                    textSize = 12f
                    maxWidth = dp(256)
                    setTextColor(ContextCompat.getColor(this@WorkspaceActivity, if (isOwn) R.color.comms_outgoing_metadata else R.color.event_muted))
                })
                bubble.addView(TextView(this@WorkspaceActivity).apply {
                    text = message.text
                    textSize = 14f
                    maxWidth = dp(256)
                    setTextColor(ContextCompat.getColor(this@WorkspaceActivity, if (isOwn) R.color.white else R.color.event_text))
                })
                val bubbleWidth = if (isOwn) {
                    LinearLayout.LayoutParams.WRAP_CONTENT
                } else {
                    minOf(dp(280), resources.displayMetrics.widthPixels - dp(32))
                }
                row.addView(bubble, LinearLayout.LayoutParams(bubbleWidth, LinearLayout.LayoutParams.WRAP_CONTENT))
                list.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(8)
                })
            }
        }
    }

    private fun showUnavailable() {
        Toast.makeText(this, R.string.comms_feature_pending, Toast.LENGTH_SHORT).show()
    }
}
