package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EventPhasesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_phases)
        CreateEventProgress.show(this, 3, R.string.event_phases)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.event_phases_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.phases_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.phases_footer_back_button).setOnClickListener {
            finish()
        }

        // Sample phases illustrate the screen until editing and persistence are implemented.
        val phaseList = findViewById<LinearLayout>(R.id.phase_list)
        val phaseNames = resources.getStringArray(R.array.preview_phases)
        for ((index, phaseName) in phaseNames.withIndex()) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_preview_phase, phaseList, false)
            row.findViewById<TextView>(R.id.phase_name).text =
                getString(R.string.phase_number_and_name, index + 1, phaseName)
            row.findViewById<View>(R.id.phase_edit_button).setOnClickListener {
                showPlaceholder()
            }
            row.findViewById<View>(R.id.phase_remove_button).setOnClickListener {
                showPlaceholder()
            }
            phaseList.addView(row)
        }

        findViewById<View>(R.id.add_phase_button).setOnClickListener {
            showPlaceholder()
        }
        findViewById<View>(R.id.phases_next_button).setOnClickListener {
            val nextIntent = Intent(this, ChannelsActivity::class.java)
            // Forward the current draft without persisting or validating it yet.
            EventDetailsDraft.copyTo(intent, nextIntent)
            startActivity(nextIntent)
        }
    }

    private fun showPlaceholder() {
        Toast.makeText(this, R.string.phases_edit_placeholder, Toast.LENGTH_SHORT).show()
    }
}
