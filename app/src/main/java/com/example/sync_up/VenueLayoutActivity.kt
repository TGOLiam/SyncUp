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
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton

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
        // Restore the input's visibility after a configuration change.
        if (savedInstanceState?.getBoolean(ZONE_INPUT_VISIBLE) == true) {
            zoneInput.visibility = View.VISIBLE
        }

        findViewById<View>(R.id.venue_add_zone_button).setOnClickListener {
            zoneInput.visibility = View.VISIBLE
            zoneInput.requestFocus()
            WindowCompat.getInsetsController(window, zoneInput).show(WindowInsetsCompat.Type.ime())
        }

        val drawing = findViewById<VenueDrawingView>(R.id.venue_drawing)
        findViewById<View>(R.id.venue_draw_button).setOnClickListener {
            drawing.setEditMode(false)
            updateDrawingMode(false)
        }
        findViewById<View>(R.id.venue_edit_button).setOnClickListener {
            if (drawing.setEditMode(true)) {
                updateDrawingMode(true)
            } else {
                Toast.makeText(this, R.string.venue_edit_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.venue_undo_button).setOnClickListener {
            drawing.undoLastStroke()
            updateDrawingMode(drawing.isEditing())
        }
        findViewById<View>(R.id.venue_clear_button).setOnClickListener {
            drawing.clearDrawing()
        }

        // The remaining layout editor controls are visual placeholders for now.
        val editorButtonIds = listOf(
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

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        updateDrawingMode(findViewById<VenueDrawingView>(R.id.venue_drawing).isEditing())
    }

    private fun updateDrawingMode(editing: Boolean) {
        val drawColor = if (editing) R.color.white else R.color.venue_selected
        val editColor = if (editing) R.color.venue_selected else R.color.white
        findViewById<MaterialButton>(R.id.venue_draw_button).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, drawColor))
        findViewById<MaterialButton>(R.id.venue_edit_button).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, editColor))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(
            ZONE_INPUT_VISIBLE,
            findViewById<View>(R.id.venue_zone_name).visibility == View.VISIBLE
        )
        super.onSaveInstanceState(outState)
    }
}
