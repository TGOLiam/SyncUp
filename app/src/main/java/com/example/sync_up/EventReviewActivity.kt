package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EventReviewActivity : AppCompatActivity() {
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.transition_slide_in_left, R.anim.transition_slide_out_right)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_review)
        CreateEventProgress.show(this, 5, R.string.event_review)
        showEventDetails()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.event_review_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.review_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.review_footer_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.review_edit_details).setOnClickListener {
            openSetupStep(CreateEventActivity::class.java)
        }

        bindSection(
            R.id.review_venue_section,
            R.string.review_venue_title,
            R.string.review_venue_detail,
            VenueLayoutActivity::class.java
        )
        bindSection(
            R.id.review_phases_section,
            R.string.review_phases_title,
            R.string.review_phases_detail,
            EventPhasesActivity::class.java
        )
        val draft = EventCreationSession.draft
        val layout = draft.layout
        findViewById<View>(R.id.review_venue_section)
            .findViewById<TextView>(R.id.review_section_detail).text =
            if (layout == null) getString(R.string.review_venue_detail)
            else getString(R.string.review_venue_draft, layout.boundary.size, layout.zones.size) +
                if (layout.zones.isEmpty()) "" else "\n" + layout.zones.joinToString(", ") { it.name }
        val phaseCount = draft.phases.size
        findViewById<View>(R.id.review_phases_section)
            .findViewById<TextView>(R.id.review_section_detail).text =
            getString(R.string.review_phases_detail, phaseCount) +
                if (draft.phases.isEmpty()) "" else "\n" + draft.phases.joinToString(", ") { it.label }
        bindSection(
            R.id.review_channels_section,
            R.string.review_channels_title,
            R.string.review_channels_detail,
            ChannelsActivity::class.java
        )
        val channelCount = draft.channels.size
        findViewById<View>(R.id.review_channels_section)
            .findViewById<TextView>(R.id.review_section_detail).text =
            getString(R.string.review_channels_detail, channelCount) +
                if (draft.channels.isEmpty()) "" else "\n" + draft.channels.joinToString(", ")

        // The code screen is a preview until event creation is implemented.
        findViewById<View>(R.id.review_create_button).setOnClickListener {
            val nextIntent = Intent(this, CodeShareActivity::class.java)
            startActivity(nextIntent)
            overridePendingTransition(R.anim.transition_slide_in_right, R.anim.transition_slide_out_left)
        }
    }

    private fun showEventDetails() {
        val draft = EventCreationSession.draft
        val fallback = getString(R.string.review_not_provided)
        fun value(text: String): String = text.takeIf { it.isNotBlank() } ?: fallback
        fun dateAndTime(date: String, time: String): String {
            return listOf(date, time).filter { it.isNotBlank() }.joinToString(" · ")
                .ifBlank { fallback }
        }

        findViewById<TextView>(R.id.review_event_name).text = value(draft.name)
        findViewById<TextView>(R.id.review_event_venue).text = value(draft.venue)
        findViewById<TextView>(R.id.review_event_schedule).text = getString(
            R.string.review_schedule,
            dateAndTime(draft.startDate, draft.startTime),
            dateAndTime(draft.endDate, draft.endTime)
        )
    }

    private fun bindSection(
        sectionId: Int,
        @StringRes title: Int,
        @StringRes detail: Int,
        destination: Class<out AppCompatActivity>
    ) {
        val section = findViewById<View>(sectionId)
        section.findViewById<TextView>(R.id.review_section_title).setText(title)
        section.findViewById<TextView>(R.id.review_section_detail).setText(detail)
        section.findViewById<View>(R.id.review_section_edit).apply {
            contentDescription = getString(R.string.review_edit_section, getString(title))
            setOnClickListener {
                openSetupStep(destination)
            }
        }
    }

    private fun openSetupStep(destination: Class<out AppCompatActivity>) {
        // Reuse the earlier activity so entered fields remain in its existing instance.
        val intent = Intent(this, destination)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        overridePendingTransition(R.anim.transition_slide_in_right, R.anim.transition_slide_out_left)
    }
}
