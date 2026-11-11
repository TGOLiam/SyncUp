package com.example.sync_up

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class VenueLayoutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_venue_layout)
        CreateEventProgress.show(this, 3, R.string.venue_layout)

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

        // The layout editor and zone setup are visual placeholders for now.
        val editorButtonIds = listOf(
            R.id.venue_draw_button,
            R.id.venue_edit_button,
            R.id.venue_undo_button,
            R.id.venue_straighten_button,
            R.id.venue_add_zone_button
        )
        for (buttonId in editorButtonIds) {
            findViewById<View>(buttonId).setOnClickListener {
                Toast.makeText(this, R.string.venue_editor_placeholder, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.venue_next_button).setOnClickListener {
            Toast.makeText(this, R.string.venue_next_placeholder, Toast.LENGTH_SHORT).show()
        }
    }
}
