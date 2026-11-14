package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

class VenueLayoutActivity : AppCompatActivity() {
    companion object {
        private const val ZONE_INPUT_VISIBLE = "zone_input_visible"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_venue_layout)
        CreateEventProgress.show(this, 2, R.string.venue_layout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.venue_layout_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.venue_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.venue_footer_back_button).setOnClickListener {
            finish()
        }

        val zoneInput = findViewById<EditText>(R.id.venue_zone_name)
        val boundaryLabel = findViewById<View>(R.id.venue_boundary_label)

        // Restore the input's visibility after a configuration change.
        if (savedInstanceState?.getBoolean(ZONE_INPUT_VISIBLE) == true) {
            zoneInput.visibility = View.VISIBLE
            boundaryLabel.visibility = View.GONE
        }

        findViewById<View>(R.id.venue_add_zone_button).setOnClickListener {
            boundaryLabel.visibility = View.GONE
            zoneInput.visibility = View.VISIBLE
            zoneInput.requestFocus()
            WindowCompat.getInsetsController(window, zoneInput).show(WindowInsetsCompat.Type.ime())
        }

        // The remaining layout editor controls are visual placeholders for now.
        val editorButtonIds = listOf(
            R.id.venue_draw_button,
            R.id.venue_edit_button,
            R.id.venue_undo_button,
            R.id.venue_straighten_button
        )
        for (buttonId in editorButtonIds) {
            findViewById<View>(buttonId).setOnClickListener {
                Toast.makeText(this, R.string.venue_editor_placeholder, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.venue_next_button).setOnClickListener {
            val nextIntent = Intent(this, EventPhasesActivity::class.java)
            // Forward the current draft without persisting or validating it yet.
            EventDetailsDraft.copyTo(intent, nextIntent)
            startActivity(nextIntent)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            ZONE_INPUT_VISIBLE,
            findViewById<View>(R.id.venue_zone_name).visibility == View.VISIBLE
        )
        super.onSaveInstanceState(outState)
    }
}
