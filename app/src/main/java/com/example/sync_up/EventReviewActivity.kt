package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EventReviewActivity : AppCompatActivity() {
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
        bindSection(
            R.id.review_channels_section,
            R.string.review_channels_title,
            R.string.review_channels_detail,
            ChannelsActivity::class.java
        )

        // Event creation and validation will be connected after the UI prototype.
        findViewById<View>(R.id.review_create_button).setOnClickListener {
            Toast.makeText(this, R.string.review_create_placeholder, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showEventDetails() {
        val fallback = getString(R.string.review_not_provided)
        fun draftValue(key: String): String {
            return intent.getStringExtra(key)?.takeIf { it.isNotBlank() } ?: fallback
        }

        findViewById<TextView>(R.id.review_event_name).text = draftValue(EventDetailsDraft.NAME)
        findViewById<TextView>(R.id.review_event_venue).text = draftValue(EventDetailsDraft.VENUE)
        findViewById<TextView>(R.id.review_event_schedule).text = getString(
            R.string.review_schedule,
            draftValue(EventDetailsDraft.START),
            draftValue(EventDetailsDraft.END)
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
    }
}
