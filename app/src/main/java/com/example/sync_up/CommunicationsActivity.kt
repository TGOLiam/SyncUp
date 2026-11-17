package com.example.sync_up

import android.content.res.ColorStateList
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** Displays a local connection-state preview until communications are implemented. */
class CommunicationsActivity : AppCompatActivity() {
    private var selectedChannel = ""
    private var connectedChannel: String? = null
    private val globalChannel by lazy { getString(R.string.channel_global) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_communications)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.comms_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        selectedChannel = savedInstanceState?.getString("selected_channel") ?: globalChannel
        connectedChannel = savedInstanceState?.getString("connected_channel")
        renderChannels()
        renderConversation()

        findViewById<View>(R.id.workspace_nav_area).setOnClickListener {
            val areaIntent = Intent(this, EventAreaActivity::class.java)
            areaIntent.putExtra(
                PreviewEvents.EXTRA_EVENT_INDEX,
                intent.getIntExtra(PreviewEvents.EXTRA_EVENT_INDEX, -1)
            )
            areaIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(areaIntent)
        }
        findViewById<View>(R.id.workspace_nav_phases).setOnClickListener {
            val phasesIntent = Intent(this, WorkspacePhasesActivity::class.java)
            phasesIntent.putExtra(
                PreviewEvents.EXTRA_EVENT_INDEX,
                intent.getIntExtra(PreviewEvents.EXTRA_EVENT_INDEX, -1)
            )
            startActivity(phasesIntent)
        }
        findViewById<View>(R.id.comms_send).setOnClickListener { showUnavailable() }
        findViewById<View>(R.id.comms_push_to_talk).setOnClickListener { showUnavailable() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("selected_channel", selectedChannel)
        outState.putString("connected_channel", connectedChannel)
        super.onSaveInstanceState(outState)
    }

    private fun renderChannels() {
        val list = findViewById<LinearLayout>(R.id.comms_channel_list)
        list.removeAllViews()
        val previewIndex = intent.getIntExtra(PreviewEvents.EXTRA_EVENT_INDEX, -1)
        val draft = PreviewEvents.workspaceDraft(this, previewIndex) ?: EventCreationSession.draft
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
                    ContextCompat.getColor(this@CommunicationsActivity,
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
