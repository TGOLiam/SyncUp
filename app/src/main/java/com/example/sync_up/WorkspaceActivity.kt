package com.example.sync_up

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
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.example.sync_up.data.DraftSaveQueue

/** Hosts the three workspace sections while keeping their views and navigation in place. */
class WorkspaceActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_EVENT_ID = "event_id"
    }

    private var selectedChannel = ""
    private var connectedChannel: String? = null
    private val globalChannel by lazy { getString(R.string.channel_global) }
    private var selectedSection = 0
    private var event: EventListItem? = null
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
        findViewById<View>(R.id.workspace_back).setOnClickListener { finish() }
        findViewById<View>(R.id.workspace_nav_area).setOnClickListener { showSection(0) }
        findViewById<View>(R.id.workspace_nav_comms).setOnClickListener { showSection(1) }
        findViewById<View>(R.id.workspace_nav_phases).setOnClickListener { showSection(2) }
        findViewById<View>(R.id.workspace_report_location).setOnClickListener {
            Toast.makeText(this, R.string.workspace_report_pending, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.comms_send).setOnClickListener { showUnavailable() }
        findViewById<View>(R.id.comms_push_to_talk).setOnClickListener { showUnavailable() }
        val eventId = intent.getStringExtra(EXTRA_EVENT_ID)
        if (eventId == null) {
            renderEvent()
        } else {
            findViewById<View>(R.id.workspace_root).visibility = View.INVISIBLE
            DraftSaveQueue.readEvents(applicationContext, { it.load(eventId) }) { result ->
                if (isFinishing || isDestroyed) return@readEvents
                val stored = result.getOrNull()
                if (stored == null) {
                    Toast.makeText(this, R.string.workspace_load_failed, Toast.LENGTH_LONG).show()
                    finish()
                    return@readEvents
                }
                event = EventListItem(
                    previewId = stored.summary.id,
                    name = stored.summary.name,
                    detail = stored.summary.detail.orEmpty(),
                    isLead = stored.summary.isLeadPreview,
                    workspacePhase = stored.summary.activePhase,
                    phases = stored.phases,
                    channels = stored.channels
                )
                draft = EventCreationDraft().apply {
                    name = stored.summary.name
                    layout = stored.layout
                    channels.addAll(stored.channels)
                }
                renderEvent()
                findViewById<View>(R.id.workspace_root).visibility = View.VISIBLE
            }
        }
    }

    private fun renderEvent() {
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
        val sections = listOf(R.id.workspace_section_area, R.id.workspace_section_comms, R.id.workspace_section_phases)
        val navigation = listOf(R.id.workspace_nav_area, R.id.workspace_nav_comms, R.id.workspace_nav_phases)
        val indicators = listOf(R.id.workspace_indicator_area, R.id.workspace_indicator_comms, R.id.workspace_indicator_phases)
        val icons = listOf(R.id.workspace_icon_area, R.id.workspace_icon_comms, R.id.workspace_icon_phases)
        val labels = listOf(R.id.workspace_label_area, R.id.workspace_label_comms, R.id.workspace_label_phases)
        for (index in sections.indices) {
            val selected = index == section
            findViewById<View>(sections[index]).visibility = if (selected) View.VISIBLE else View.GONE
            findViewById<View>(navigation[index]).isSelected = selected
            val color = ContextCompat.getColor(this, if (selected) R.color.event_navy else R.color.event_muted)
            findViewById<View>(indicators[index]).setBackgroundColor(if (selected) color else android.graphics.Color.TRANSPARENT)
            findViewById<ImageView>(icons[index]).setColorFilter(color)
            findViewById<TextView>(labels[index]).apply {
                setTextColor(color)
                setTypeface(null, if (selected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
            }
        }
        findViewById<TextView>(R.id.workspace_title).text = when (section) {
            1 -> getString(R.string.workspace_communications)
            2 -> getString(R.string.event_phases)
            else -> draft.name
        }
        findViewById<MaterialButton>(R.id.workspace_edit).apply {
            visibility = if (section != 1 && (event?.isLead ?: true)) View.VISIBLE else View.GONE
            setText(if (section == 2) R.string.venue_edit else R.string.workspace_edit_layout)
            setOnClickListener {
                val message = if (section == 2) R.string.phases_edit_placeholder else R.string.workspace_edit_pending
                Toast.makeText(this@WorkspaceActivity, message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderPhases() {
        val phases = event?.phases ?: draft.phases.map { it.label }
        val activeIndex = event?.workspacePhase?.let { phases.indexOf(it) } ?: -1
        val isLead = event?.isLead ?: true
        findViewById<View>(R.id.workspace_phase_empty).visibility =
            if (phases.isEmpty()) View.VISIBLE else View.GONE
        val list = findViewById<LinearLayout>(R.id.workspace_phase_list)
        for ((index, phase) in phases.withIndex()) {
            val active = index == activeIndex
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundResource(if (active) R.drawable.workspace_active_phase_card else R.drawable.workspace_card)
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(this).apply {
                text = phase
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, if (active) R.color.white else R.color.event_text))
            }
            val status = TextView(this).apply {
                setText(when {
                    active -> R.string.workspace_phase_active
                    activeIndex >= 0 && index < activeIndex -> R.string.workspace_phase_completed
                    else -> R.string.workspace_phase_upcoming
                })
                setTextColor(ContextCompat.getColor(this@WorkspaceActivity, if (active) R.color.white else R.color.event_muted))
            }
            content.addView(title)
            content.addView(status)
            row.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (isLead && !active && (activeIndex < 0 || index > activeIndex)) {
                val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                button.setText(R.string.workspace_phase_activate)
                button.isAllCaps = false
                button.cornerRadius = dp(10)
                button.setOnClickListener {
                    Toast.makeText(this, R.string.phases_edit_placeholder, Toast.LENGTH_SHORT).show()
                }
                row.addView(button)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(8)
            list.addView(row, params)
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
            val connected = index == 0 || name == connectedChannel
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
                    if (index != 0) {
                        connectedChannel = if (connectedChannel == name) null else name
                        selectedChannel = connectedChannel ?: globalChannel
                        renderChannels()
                        renderConversation()
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
        findViewById<MaterialButton>(R.id.comms_push_to_talk).text =
            getString(R.string.comms_hold_to_talk, selectedChannel)
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
    }

    private fun showUnavailable() {
        Toast.makeText(this, R.string.comms_feature_pending, Toast.LENGTH_SHORT).show()
    }
}
