package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ChannelsActivity : AppCompatActivity() {
    private val channels = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels)
        CreateEventProgress.show(this, 4, R.string.channels_title)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.channels_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        // Restore entered channels when Android recreates this screen.
        val savedChannels = savedInstanceState?.getStringArrayList("channels")
        channels.addAll(savedChannels ?: EventCreationSession.draft.channels)
        renderChannels()

        findViewById<View>(R.id.channels_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.channels_footer_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.add_channel_button).setOnClickListener { showChannelDialog() }
        findViewById<View>(R.id.channels_review_button).setOnClickListener {
            val nextIntent = Intent(this, EventReviewActivity::class.java)
            startActivity(nextIntent)
        }
    }

    override fun onPause() {
        val draft = EventCreationSession.draft
        draft.channels.clear()
        draft.channels.addAll(channels)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("channels", ArrayList(channels))
        super.onSaveInstanceState(outState)
    }

    private fun renderChannels() {
        val list = findViewById<LinearLayout>(R.id.channel_list)
        list.removeAllViews()
        for ((index, name) in channels.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_channel, list, false)
            row.findViewById<TextView>(R.id.channel_name).text = name
            row.findViewById<View>(R.id.channel_remove_button).apply {
                contentDescription = getString(R.string.channel_remove, name)
                setOnClickListener {
                    channels.removeAt(index)
                    renderChannels()
                }
            }
            list.addView(row)
        }
    }

    private fun showChannelDialog() {
        val inputs = layoutInflater.inflate(R.layout.dialog_channel_input, null)
        val name = inputs.findViewById<EditText>(R.id.channel_input_name)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_PhaseDialog)
            .setTitle(R.string.channel_add_title)
            .setView(inputs)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val channelName = name.text.toString().trim()
                if (channelName.isEmpty()) {
                    name.error = getString(R.string.event_details_required)
                } else {
                    channels.add(channelName)
                    renderChannels()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
