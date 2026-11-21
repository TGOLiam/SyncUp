package com.example.sync_up

import android.Manifest
import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.SystemClock
import android.os.Build
import android.view.MotionEvent
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
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
    // User-initiated connection changes; cleared once the session reports the
    // requested state, so the UI never assumes a connection before it exists.
    private val pendingConnections = mutableMapOf<String, Boolean>()
    private val pendingConnectionJobs = mutableMapOf<String, Job>()
    private var previewGlobalConnected = true
    private var pttNote: String? = null
    private var pttNoteJob: Job? = null
    private var pttPulse: ObjectAnimator? = null
    private var messageRetry: Triple<String, String, String>? = null
    private var ownMemberId: String? = null
    private var messageLoad: Job? = null
    private var venueBodyOpen = false
    private var showAllActivity = false
    private var latestLocations: List<com.example.sync_up.data.remote.WorkspaceLocation> = emptyList()
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
        updatePttButton()
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
            // Organizers is an in-shell screen; back first returns to Overview.
            if (selectedSection == SECTION_ORGANIZERS) showSection(0) else finish()
        }
        findViewById<View>(R.id.workspace_organizers_back).setOnClickListener { showSection(0) }
        findViewById<View>(R.id.workspace_organizers_entry).setOnClickListener {
            // Ref 16: the organizer entry opens the in-shell organizers screen.
            showSection(SECTION_ORGANIZERS)
        }
        findViewById<View>(R.id.workspace_nav_area).setOnClickListener { showSection(0) }
        findViewById<View>(R.id.workspace_nav_comms).setOnClickListener { showSection(1) }
        // Overview: the current-phase card is the way into event phases.
        findViewById<View>(R.id.workspace_banner).setOnClickListener { showSection(2) }
        findViewById<View>(R.id.workspace_phase_open).setOnClickListener { showSection(2) }
        // Clicking the venue card expands the layout in place.
        findViewById<View>(R.id.workspace_venue_card).setOnClickListener { toggleVenueBody() }
        findViewById<View>(R.id.workspace_last_report_update).setOnClickListener { reportLocation() }
        findViewById<View>(R.id.workspace_activity_toggle).setOnClickListener { toggleRecentActivity() }
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
                        reconcilePendingConnections()
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
                        renderPhaseCard()
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
        // Show the permission state before the controller answers.
        updatePttButton()
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
                updatePttButton()
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
                updatePttButton()
            }
        }
    }

    private fun finishPushToTalk() {
        val wasTalking = speakingLive
        val wasWaiting = pressHeld && requestPending && !wasTalking
        pressHeld = false
        val active = lease
        lease = null
        speechTimeout?.cancel()
        speechTimeout = null
        speakingLive = false
        when {
            wasTalking -> showPttNote(getString(R.string.voice_transmission_ended))
            wasWaiting -> showPttNote(getString(R.string.voice_released_before_permission))
            else -> updatePttButton()
        }
        if (active == null) return
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
        val snapshot = WorkspaceUpdates.state.value?.takeIf { it.eventId == event?.previewId }
        renderOrganizationSummary(snapshot?.snapshot?.locations.orEmpty())
        findViewById<WorkspaceLayoutView>(R.id.workspace_layout).layout = draft.layout
        findViewById<TextView>(R.id.workspace_venue_name).text = venueDisplayName()
        findViewById<TextView>(R.id.workspace_venue_hint).text =
            getString(if (draft.layout == null) R.string.workspace_no_layout_hint else R.string.workspace_view_layout_hint)
        findViewById<View>(R.id.workspace_empty_layout).visibility =
            if (draft.layout == null) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.workspace_phase).text =
            event?.workspacePhase ?: getString(R.string.event_no_phase)
        renderPhaseCard()
        renderChannels()
        renderConversation()
        renderPhases()
        showSection(selectedSection)
    }

    /** The venue card label: draft venue first, then the stored detail text. */
    private fun venueDisplayName(): String {
        draft.venue.takeIf { it.isNotBlank() }?.let { return it }
        val detail = event?.detail.orEmpty()
        // Preview details read "venue, city · date"; keep the venue part only.
        return detail.substringBefore(" · ").substringBefore(",").trim()
    }

    private fun toggleVenueBody() {
        venueBodyOpen = !venueBodyOpen
        findViewById<View>(R.id.workspace_venue_body).visibility =
            if (venueBodyOpen) View.VISIBLE else View.GONE
    }

    private fun toggleRecentActivity() {
        showAllActivity = !showAllActivity
        findViewById<TextView>(R.id.workspace_activity_toggle).text =
            getString(if (showAllActivity) R.string.workspace_show_less else R.string.workspace_view_all)
        renderLocations(latestLocations)
    }

    /**
     * Phase card helper: mono caption (Current phase · x of y), segment
     * progress, and the next phase line feed the compact current-phase card
     * that opens the phases section.
     */
    private fun renderPhaseCard() {
        val phases = event?.phases ?: draft.phases.map { it.label }
        val activeIndex = if (activePhaseId != null) phaseIds.indexOf(activePhaseId) else -1
        val caption = findViewById<TextView>(R.id.workspace_phase_caption)
        caption.visibility = if (phases.isEmpty()) View.GONE else View.VISIBLE
        if (activeIndex >= 0) {
            caption.text = getString(R.string.workspace_current_phase, activeIndex + 1, phases.size)
        } else {
            // Nothing running yet: the card still shows the phase plan.
            caption.text = getString(R.string.workspace_phases_planned, phases.size)
        }
        val next = findViewById<TextView>(R.id.workspace_phase_next)
        val nextPhase = if (activeIndex >= 0 && activeIndex + 1 < phases.size) {
            phases[activeIndex + 1]
        } else if (activeIndex < 0) {
            phases.firstOrNull()
        } else {
            null
        }
        // Empty text (never gone) keeps View phases pinned right on every device.
        next.text = nextPhase?.let { getString(R.string.workspace_phase_next, it) }.orEmpty()
        val progress = findViewById<LinearLayout>(R.id.workspace_phase_progress)
        progress.removeAllViews()
        val density = resources.displayMetrics.density
        for (index in phases.indices) {
            val segment = View(this)
            segment.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (activeIndex >= 0 && index <= activeIndex) R.color.ui_orange else R.color.ui_phase_track
                )
            )
            progress.addView(segment, LinearLayout.LayoutParams(
                0,
                dp(4),
                1f
            ).apply {
                if (index < phases.size - 1) marginEnd = dp(4)
            })
        }
        progress.visibility = if (phases.isEmpty()) View.GONE else View.VISIBLE
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
        // Bottom navigation keeps Overview and Comms; phases and organizers
        // open at their entry points and leave Overview highlighted.
        val navigation = listOf(R.id.workspace_nav_area, R.id.workspace_nav_comms)
        val indicators = listOf(R.id.workspace_indicator_area, R.id.workspace_indicator_comms)
        val icons = listOf(R.id.workspace_icon_area, R.id.workspace_icon_comms)
        val labels = listOf(R.id.workspace_label_area, R.id.workspace_label_comms)
        for (index in sections.indices) {
            findViewById<View>(sections[index]).visibility = if (index == section) View.VISIBLE else View.GONE
        }
        for (index in navigation.indices) {
            val selected = index == section || (section == 2 || section == SECTION_ORGANIZERS) && index == 0
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
                renderPhaseCard()
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

    /** Rows rendered in the connection panel: Global plus the chosen custom channel. */
    private fun channelRows(): List<String> {
        val connected = connectedChannel?.takeIf { it != globalChannel }
        // A channel keeps its row while its connection change is in flight.
        val pending = pendingConnections.keys.firstOrNull { it != globalChannel }
        val chosen = connected
            ?: pending
            ?: selectedChannel.takeIf { it != globalChannel && it.isNotEmpty() }
            ?: draft.channels.firstOrNull()
        return if (chosen == null) listOf(globalChannel) else listOf(globalChannel, chosen)
    }

    private fun channelStatus(name: String): LiveAudioSession.Status {
        val state = LiveAudioSession.state.value
        val shared = event?.isPreview == false
        return when {
            shared && state.eventId != event?.previewId -> LiveAudioSession.Status.DISCONNECTED
            name == globalChannel -> if (shared) state.global else previewGlobalStatus()
            shared -> if (state.selectedId != null && name == connectedChannel) state.selected
                      else LiveAudioSession.Status.DISCONNECTED
            name == connectedChannel -> LiveAudioSession.Status.CONNECTED
            else -> LiveAudioSession.Status.DISCONNECTED
        }
    }

    private fun previewGlobalStatus(): LiveAudioSession.Status =
        if (previewGlobalConnected) LiveAudioSession.Status.CONNECTED else LiveAudioSession.Status.DISCONNECTED

    /** Clears a pending change once the session reports the requested state. */
    private fun reconcilePendingConnections() {
        if (pendingConnections.isEmpty()) return
        val iterator = pendingConnections.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val connected = channelStatus(entry.key) == LiveAudioSession.Status.CONNECTED
            if (connected == entry.value) {
                iterator.remove()
                pendingConnectionJobs.remove(entry.key)?.cancel()
            }
        }
    }

    private fun markConnectionPending(name: String, target: Boolean) {
        pendingConnections[name] = target
        pendingConnectionJobs.remove(name)?.cancel()
        // The session retries in the background; stop showing the transition
        // once a request has clearly not completed.
        pendingConnectionJobs[name] = lifecycleScope.launch {
            delay(20000)
            val connected = channelStatus(name) == LiveAudioSession.Status.CONNECTED
            if (pendingConnections[name] == target && connected != target) {
                pendingConnections.remove(name)
                renderChannels()
                Toast.makeText(
                    this@WorkspaceActivity,
                    getString(R.string.comms_connection_failed, name),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun renderChannels() {
        val list = findViewById<LinearLayout>(R.id.comms_channel_list)
        list.removeAllViews()
        for ((index, name) in channelRows().withIndex()) {
            val row = LayoutInflater.from(this).inflate(
                if (index == 0) R.layout.item_comms_global else R.layout.item_comms_picker,
                list,
                false
            )
            row.findViewById<TextView>(R.id.comms_channel_name).text = name
            // Selection stays locked while a custom channel change is in flight.
            val channelTransitioning = pendingConnections.keys.any { it != globalChannel }
            row.findViewById<View>(R.id.comms_picker_trigger)?.apply {
                contentDescription = getString(R.string.comms_picker_open)
                isEnabled = !channelTransitioning
                if (!channelTransitioning) setOnClickListener { openChannelSheet(name) }
            }
            val status = channelStatus(name)
            val target = pendingConnections[name]
            val pending = target != null && (status == LiveAudioSession.Status.CONNECTED) != target
            val connected = status == LiveAudioSession.Status.CONNECTED
            val connecting = status == LiveAudioSession.Status.CONNECTING ||
                status == LiveAudioSession.Status.RECONNECTING
            val dot = row.findViewById<View>(R.id.comms_status_dot)
            val statusText = row.findViewById<TextView>(R.id.comms_status_text)
            val toggle = row.findViewById<androidx.appcompat.widget.AppCompatButton>(R.id.comms_channel_toggle)
            val progress = row.findViewById<View>(R.id.comms_channel_progress)
            when {
                pending -> {
                    // The requested state is not confirmed yet.
                    tintStatusDot(dot, R.color.ui_status_pending)
                    statusText.setText(if (target == true) R.string.comms_connecting else R.string.comms_disconnecting)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.ui_disconnect_text))
                    startDotPulse(dot)
                }
                connected -> {
                    tintStatusDot(dot, R.color.ui_status_on)
                    statusText.setText(R.string.comms_connected)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.ui_status_on_text))
                    dot.clearAnimation()
                }
                connecting -> {
                    tintStatusDot(dot, R.color.ui_status_pending)
                    statusText.setText(R.string.comms_connecting)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.ui_disconnect_text))
                    startDotPulse(dot)
                }
                else -> {
                    tintStatusDot(dot, R.color.ui_status_off)
                    statusText.setText(R.string.comms_not_connected)
                    statusText.setTextColor(ContextCompat.getColor(this, R.color.ui_secondary_text))
                    dot.clearAnimation()
                }
            }
            val showSpinner = pending || connecting
            progress.visibility = if (showSpinner) View.VISIBLE else View.GONE
            // Repeated actions are blocked until the transition settles.
            toggle.isEnabled = !pending
            toggle.setText(
                when {
                    pending -> if (target == true) R.string.comms_connecting else R.string.comms_disconnecting
                    connected || connecting -> R.string.comms_disconnect
                    else -> R.string.comms_connect
                }
            )
            val quiet = connected || connecting || pending
            toggle.setTextColor(
                ContextCompat.getColor(this, if (quiet) R.color.ui_disconnect_text else R.color.ui_outgoing_text)
            )
            toggle.setBackgroundResource(
                if (quiet) R.drawable.bg_ui_connection_disconnect else R.drawable.bg_ui_connection_connect
            )
            // Keep the label clear of the spinner while it is visible.
            if (showSpinner) toggle.setPadding(dp(20), 0, dp(2), 0) else toggle.setPadding(dp(2), 0, dp(2), 0)
            toggle.contentDescription = getString(
                if (connected || connecting) R.string.comms_connected_description else R.string.comms_disconnected_description,
                name
            )
            toggle.setOnClickListener { onConnectionToggle(name, connected || connecting, index == 0) }
            list.addView(row)
        }
    }

    private fun tintStatusDot(dot: View, colorRes: Int) {
        dot.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun startDotPulse(dot: View) {
        if (dot.animation != null) return
        dot.startAnimation(AlphaAnimation(0.35f, 1f).apply {
            duration = 700
            repeatMode = Animation.REVERSE
            repeatCount = Animation.INFINITE
        })
    }

    private fun onConnectionToggle(name: String, connected: Boolean, isGlobal: Boolean) {
        if (pendingConnections[name] != null) return
        val user = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
        val eventId = event?.previewId
        val globalId = channelIds[""]
        val channelId = if (isGlobal) globalId else channelIds[name]
        if (event?.isPreview != false) {
            // Previews have no backend session; the toggle is local and immediate.
            if (isGlobal) {
                previewGlobalConnected = !connected
            } else {
                connectedChannel = if (connected) null else name
                selectedChannel = connectedChannel ?: globalChannel
            }
            renderChannels()
            renderConversation()
            return
        }
        if (user == null || eventId == null || globalId == null || channelId == null) {
            Toast.makeText(this, R.string.voice_channel_unavailable, Toast.LENGTH_SHORT).show()
            return
        }
        withNotificationPermission {
            markConnectionPending(name, !connected)
            if (isGlobal) {
                LiveAudioService.setGlobal(applicationContext, user, eventId, globalId, !connected)
            } else {
                val selected = if (connected) null else channelId
                LiveAudioService.select(applicationContext, user, eventId, globalId, selected)
                selectedChannel = if (selected == null) globalChannel else name
            }
            renderChannels()
            renderConversation()
        }
    }

    private fun renderConversation() {
        findViewById<TextView>(R.id.comms_history_title).text = selectedChannel
        findViewById<EditText>(R.id.comms_message).hint = getString(R.string.comms_message_hint, selectedChannel)
        // Refs 19-20: segment tabs are Global and the row's custom channel
        // (shows Not connected until that channel connects).
        val tabs = findViewById<LinearLayout>(R.id.comms_history_tabs)
        tabs.removeAllViews()
        val customTab = connectedChannel?.takeIf { it != globalChannel }
            ?: selectedChannel.takeIf { it != globalChannel && it.isNotEmpty() }
            ?: draft.channels.firstOrNull()
        val visibleTabs = listOf(
            globalChannel,
            customTab ?: ""
        )
        for ((index, name) in visibleTabs.withIndex()) {
            val isCustomRow = index == 1
            val tab = TextView(this)
            tab.text = if (isCustomRow && name.isEmpty()) {
                getString(R.string.comms_not_connected)
            } else {
                name
            }
            tab.gravity = android.view.Gravity.CENTER
            val selected = name.isNotEmpty() && name == selectedChannel
            tab.setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            tab.setTextColor(
                ContextCompat.getColor(this, if (selected) R.color.ui_ink else R.color.ui_nav_text)
            )
            if (selected) tab.setBackgroundResource(R.drawable.bg_ui_tab_selected)
            tab.isSelected = selected
            val connectable = name.isNotEmpty() &&
                channelStatus(name) == LiveAudioSession.Status.CONNECTED
            if (connectable) {
                tab.setOnClickListener {
                    selectedChannel = name
                    renderChannels()
                    renderConversation()
                }
            }
            val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            if (index > 0) params.marginStart = dp(4)
            // Not-connected tabs stay inert without consuming space.
            tab.visibility = if (index == 1 && name.isEmpty()) View.GONE else if (index == 0) View.VISIBLE else View.VISIBLE
            tabs.addView(tab, params)
        }
        updatePttButton()
        loadMessages()
    }

    /** Reflects the real live-audio state on the push-to-talk action. */
    private fun updatePttButton() {
        val button = findViewById<MaterialButton>(R.id.comms_push_to_talk) ?: return
        val note = findViewById<TextView>(R.id.comms_ptt_note) ?: return
        val available = channelStatus(selectedChannel) == LiveAudioSession.Status.CONNECTED
        val waiting = pressHeld && requestPending
        val talking = speakingLive
        button.isEnabled = available
        button.text = when {
            talking -> getString(R.string.voice_transmitting, selectedChannel)
            waiting -> getString(R.string.voice_requesting_permission)
            !available -> getString(R.string.comms_ptt_unavailable, selectedChannel)
            else -> getString(R.string.comms_hold_to_talk, selectedChannel)
        }
        val tint = when {
            talking -> R.color.ui_ptt_talking
            waiting -> R.color.ui_ptt_waiting
            !available -> R.color.ui_ptt_disabled
            else -> R.color.ui_orange
        }
        button.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, tint))
        note.text = pttNote ?: when {
            waiting -> getString(R.string.voice_requesting_note)
            talking -> getString(R.string.voice_transmitting_note)
            else -> ""
        }
        if (waiting) startPttPulse(button) else stopPttPulse(button)
    }

    private fun showPttNote(text: String) {
        pttNote = text
        pttNoteJob?.cancel()
        pttNoteJob = lifecycleScope.launch {
            delay(1700)
            pttNote = null
            updatePttButton()
        }
        updatePttButton()
    }

    private fun startPttPulse(button: View) {
        if (pttPulse?.isStarted == true) return
        pttPulse = ObjectAnimator.ofFloat(button, View.ALPHA, 1f, 0.72f).apply {
            duration = 650
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            start()
        }
    }

    private fun stopPttPulse(button: View) {
        pttPulse?.cancel()
        pttPulse = null
        button.alpha = 1f
    }

    /** Ref §7: bottom sheet picker bound to one event custom channel selection. */
    private fun openChannelSheet(current: String) {
        val channels = draft.channels
        if (channels.isEmpty()) {
            Toast.makeText(this, R.string.comms_feature_pending, Toast.LENGTH_SHORT).show()
            return
        }
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.Theme_SYNCUP_BottomSheet)
        val content = layoutInflater.inflate(R.layout.sheet_channel_picker, null)
        sheet.setContentView(content)
        content.findViewById<View>(R.id.picker_close).setOnClickListener { sheet.dismiss() }
        val rows = content.findViewById<LinearLayout>(R.id.picker_rows)
        val empty = content.findViewById<TextView>(R.id.picker_empty)
        val search = content.findViewById<EditText>(R.id.picker_search)
        fun render(query: String) {
            rows.removeAllViews()
            val matches = channels.filter { it.lowercase().contains(query.trim().lowercase()) }
            empty.visibility = if (matches.isEmpty()) View.VISIBLE else View.GONE
            for (name in matches) {
                val selectedHere = name == selectedChannel
                val row = LinearLayout(this).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    orientation = LinearLayout.HORIZONTAL
                    if (selectedHere) {
                        setBackgroundResource(R.drawable.bg_ui_wash)
                    } else {
                        setBackgroundResource(R.drawable.bg_ui_row_divider)
                    }
                    setOnClickListener {
                        sheet.dismiss()
                        chooseChannel(name)
                    }
                }
                row.addView(TextView(this).apply {
                    text = name
                    textSize = 14f
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(14), dp(14), dp(8), dp(14))
                })
                if (selectedHere) {
                    row.addView(TextView(this).apply {
                        text = getString(R.string.comms_picker_selected)
                        textSize = 12f
                        setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_chip_text))
                        setPadding(dp(8), dp(14), dp(14), dp(14))
                    })
                }
                rows.addView(row)
            }
        }
        render("")
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                render(s?.toString().orEmpty())
            }
        })
        sheet.show()
    }

    /** Handles picker selection with the existing transfer rules. */
    private fun chooseChannel(name: String) {
        val selectedChannelId = channelIds[name]
        val user = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
        val eventId = event?.previewId
        val globalId = channelIds[""]
        if (event?.isPreview == true || user == null || eventId == null || globalId == null) {
            selectedChannel = name
            renderChannels()
            renderConversation()
            return
        }
        val state = LiveAudioSession.state.value
        val wasSelected = state.selectedId != null
        val sameEvent = state.eventId == eventId
        if (selectedChannelId == null || !sameEvent) {
            selectedChannel = name
            renderChannels()
            renderConversation()
            return
        }
        selectedChannel = name
        if (wasSelected) {
            // Connected custom channel: the service transfers the selection.
            withNotificationPermission {
                LiveAudioService.select(applicationContext, user, eventId, globalId, selectedChannelId)
            }
        } else {
            renderChannels()
            renderConversation()
        }
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
        // Ref 18: revised dialog surface over the dimmed Event Area with a Cancel row.
        val content = layoutInflater.inflate(R.layout.dialog_report_zone, null)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_UiDialog)
            .setCustomTitle(uiDialogTitle(this, R.string.location_choose_zone))
            .setView(content)
            .create()
        val list = content.findViewById<LinearLayout>(R.id.report_zone_list)
        for (zone in zoneIds) {
            val row = TextView(this).apply {
                text = zone.second
                textSize = 14f
                setPadding(0, dp(10), 0, dp(10))
                setBackgroundResource(R.drawable.bg_ui_row_divider)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
                setOnClickListener {
                    lifecycleScope.launch {
                        try {
                            SupabaseEventService(client).reportLocation(current.previewId, zone.first)
                            Toast.makeText(this@WorkspaceActivity, R.string.location_report_sent, Toast.LENGTH_SHORT).show()
                        } catch (error: CancellationException) {
                            throw error
                        } catch (_: Exception) {
                            Toast.makeText(this@WorkspaceActivity, R.string.location_report_failed, Toast.LENGTH_LONG).show()
                        }
                        dialog.dismiss()
                    }
                }
            }
            list.addView(row)
        }
        content.findViewById<View>(R.id.report_zone_cancel).setOnClickListener { dialog.dismiss() }
        dialog.show()
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
        latestLocations = locations
        val list = findViewById<LinearLayout>(R.id.workspace_reports)
        list.removeAllViews()
        // Ref 16: report rows are borderless entries with a bottom hairline;
        // the feed collapses to the latest three until expanded.
        val visible = if (showAllActivity) locations else locations.take(3)
        for (location in visible) {
            val detail = reportTimestampDetail(location) ?: continue
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setBackgroundResource(R.drawable.bg_ui_row_divider)
                setPadding(0, dp(10), 0, dp(10))
            }
            row.addView(initialBadge(location.name, 28))
            val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            column.addView(TextView(this).apply {
                text = getString(
                    R.string.workspace_activity_reported,
                    location.name ?: getString(R.string.message_member)
                )
                textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
            })
            column.addView(TextView(this).apply {
                text = detail
                textSize = 13f
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_action_gray))
            }, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(2) })
            row.addView(column, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            })
            list.addView(row)
        }
        findViewById<TextView>(R.id.workspace_reports_empty).visibility =
            if (list.childCount == 0) View.VISIBLE else View.GONE
        renderLastReport(locations)
    }

    /** Initial chip used by the organizer stack and the activity rows. */
    private fun initialBadge(name: String?, sizeDp: Int): TextView {
        return TextView(this).apply {
            text = (name ?: getString(R.string.message_member)).trim().take(1).uppercase()
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            setBackgroundResource(R.drawable.bg_ui_avatar_overlap)
            setTextColor(ContextCompat.getColor(this@WorkspaceActivity, R.color.ui_ink))
            minHeight = dp(sizeDp)
            minWidth = dp(sizeDp)
        }
    }

    /** Own report recap under Your last report, with the Update entry point. */
    private fun renderLastReport(locations: List<com.example.sync_up.data.remote.WorkspaceLocation>) {
        val userId = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
        val own = locations.firstOrNull { it.userId == userId }
        val zoneText = findViewById<TextView>(R.id.workspace_last_report_zone)
        val timeText = findViewById<TextView>(R.id.workspace_last_report_time)
        // A member row can exist before any report; without a zone there is
        // nothing to recap yet.
        if (own == null || own.zoneId == null) {
            zoneText.setTextColor(ContextCompat.getColor(this, R.color.ui_muted))
            zoneText.text = getString(R.string.workspace_last_report_none)
            timeText.text = getString(R.string.workspace_not_reported)
            return
        }
        val zone = zoneIds.firstOrNull { it.first == own.zoneId }?.second
        val time = own.reportedAt?.let { reportedAt ->
            try {
                val instant = OffsetDateTime.parse(reportedAt).toInstant()
                android.text.format.DateFormat.getTimeFormat(this).format(Date.from(instant))
            } catch (_: Exception) {
                null
            }
        }
        zoneText.setTextColor(ContextCompat.getColor(this, R.color.ui_ink))
        zoneText.text = zone ?: getString(R.string.workspace_not_reported)
        timeText.text = if (time == null) {
            getString(R.string.workspace_not_reported)
        } else {
            getString(R.string.workspace_reported_time, time)
        }
    }

    /** Entry-row subtitle plus the overlapping avatar stack. */
    private fun renderOrganizationSummary(locations: List<com.example.sync_up.data.remote.WorkspaceLocation>) {
        findViewById<TextView>(R.id.workspace_organizers_subtitle).text =
            getString(R.string.workspace_organizers_subtitle, locations.size)
        val stack = findViewById<LinearLayout>(R.id.workspace_organizer_avatars)
        stack.removeAllViews()
        for (location in locations.take(3)) {
            stack.addView(initialBadge(location.name, 25), LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { if (stack.childCount > 0) marginStart = dp(-7) })
        }
        stack.visibility = if (locations.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderOrganizers(locations: List<com.example.sync_up.data.remote.WorkspaceLocation>) {
        renderOrganizationSummary(locations)
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
            val screenPadding = dp(32)
            val maxBubble = (resources.displayMetrics.widthPixels - screenPadding) * 88 / 100
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
                // Handoff §7: pale incoming bubbles hug the left edge; green
                // outgoing bubbles size to their content on the right.
                val bubble = LinearLayout(this@WorkspaceActivity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(12), dp(12), dp(12), dp(12))
                    setBackgroundResource(if (isOwn) R.drawable.bg_ui_bubble_outgoing else R.drawable.bg_ui_bubble_incoming)
                }
                bubble.addView(TextView(this@WorkspaceActivity).apply {
                    text = if (time.isEmpty()) sender else getString(R.string.message_metadata, sender, time)
                    textSize = 12f
                    setTextColor(
                        ContextCompat.getColor(
                            this@WorkspaceActivity,
                            if (isOwn) R.color.ui_outgoing_meta else R.color.ui_incoming_meta
                        )
                    )
                })
                bubble.addView(TextView(this@WorkspaceActivity).apply {
                    text = message.text
                    textSize = 14f
                    setTextColor(
                        ContextCompat.getColor(
                            this@WorkspaceActivity,
                            if (isOwn) R.color.ui_outgoing_text else R.color.ui_ink
                        )
                    )
                })
                row.addView(bubble, LinearLayout.LayoutParams(
                    if (isOwn) LinearLayout.LayoutParams.WRAP_CONTENT else maxBubble,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ))
                list.addView(row, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) })
            }
        }
    }

    private fun showUnavailable() {
        Toast.makeText(this, R.string.comms_feature_pending, Toast.LENGTH_SHORT).show()
    }
}
