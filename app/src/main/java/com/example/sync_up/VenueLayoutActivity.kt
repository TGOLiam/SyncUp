package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.content.ContextCompat
import android.content.res.ColorStateList
import com.google.android.material.button.MaterialButton

class VenueLayoutActivity : AppCompatActivity() {
    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.transition_slide_in_left, R.anim.transition_slide_out_right)
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

        val drawing = findViewById<VenueDrawingView>(R.id.venue_drawing)
        if (savedInstanceState == null) {
            EventCreationSession.draft.layout?.let { drawing.restoreLayout(it) }
        }
        drawing.onEditModeChanged = { editing ->
            updateDrawingMode(editing)
        }
        findViewById<View>(R.id.venue_add_zone_button).setOnClickListener {
            showAddZoneDialog(drawing)
        }
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
            if (drawing.isZoneSelected()) {
                Toast.makeText(this, R.string.venue_select_for_clear, Toast.LENGTH_SHORT).show()
            } else {
                drawing.clearDrawing()
            }
        }

        findViewById<View>(R.id.venue_simplify_button).setOnClickListener {
            if (!drawing.simplifyBoundary()) {
                Toast.makeText(this, R.string.venue_simplify_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.venue_next_button).setOnClickListener {
            val nextIntent = Intent(this, EventPhasesActivity::class.java)
            // Forward the current draft without persisting or validating it yet.
            startActivity(nextIntent)
            overridePendingTransition(R.anim.transition_slide_in_right, R.anim.transition_slide_out_left)
        }
    }

    override fun onPause() {
        EventCreationSession.draft.layout =
            findViewById<VenueDrawingView>(R.id.venue_drawing).exportLayout()
        super.onPause()
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

    private fun showAddZoneDialog(drawing: VenueDrawingView) {
        val inputs = layoutInflater.inflate(R.layout.dialog_zone_input, null)
        val name = inputs.findViewById<EditText>(R.id.zone_input_name)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_PhaseDialog)
            .setTitle(R.string.venue_add_zone_title)
            .setView(inputs)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val zoneName = name.text.toString().trim()
                if (zoneName.isEmpty()) {
                    name.error = getString(R.string.event_details_required)
                } else if (drawing.addZone(zoneName)) {
                    updateDrawingMode(true)
                    dialog.dismiss()
                } else {
                    Toast.makeText(this, R.string.zone_placement_unavailable, Toast.LENGTH_SHORT).show()
                }
            }
        }
        dialog.show()
    }
}
