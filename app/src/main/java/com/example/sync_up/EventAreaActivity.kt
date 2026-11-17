package com.example.sync_up

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/** Event Lead workspace preview using the current in-memory creation draft. */
class EventAreaActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_area)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.workspace_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val previewIndex = intent.getIntExtra(PreviewEvents.EXTRA_EVENT_INDEX, -1)
        val draft = PreviewEvents.workspaceDraft(this, previewIndex) ?: EventCreationSession.draft
        val previewEvent = PreviewEvents.load(this).getOrNull(previewIndex)
        findViewById<TextView>(R.id.workspace_event_name).text = draft.name
        if (previewEvent != null) {
            findViewById<TextView>(R.id.workspace_phase).text =
                previewEvent.workspacePhase ?: getString(R.string.event_no_phase)
            findViewById<View>(R.id.workspace_edit_layout).visibility =
                if (previewEvent.isLead) View.VISIBLE else View.GONE
        }
        val map = findViewById<WorkspaceLayoutView>(R.id.workspace_layout)
        map.layout = draft.layout
        findViewById<View>(R.id.workspace_empty_layout).visibility =
            if (draft.layout == null) View.VISIBLE else View.GONE

        findViewById<View>(R.id.workspace_back).setOnClickListener { finish() }
        findViewById<View>(R.id.workspace_edit_layout).setOnClickListener {
            Toast.makeText(this, R.string.workspace_edit_pending, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.workspace_report_location).setOnClickListener {
            Toast.makeText(this, R.string.workspace_report_pending, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.workspace_nav_comms).setOnClickListener {
            val communicationsIntent = Intent(this, CommunicationsActivity::class.java)
            communicationsIntent.putExtra(PreviewEvents.EXTRA_EVENT_INDEX, previewIndex)
            startActivity(communicationsIntent)
            overridePendingTransition(R.anim.transition_fade_in, R.anim.transition_fade_out)
        }
        findViewById<View>(R.id.workspace_nav_phases).setOnClickListener {
            val phasesIntent = Intent(this, WorkspacePhasesActivity::class.java)
            phasesIntent.putExtra(PreviewEvents.EXTRA_EVENT_INDEX, previewIndex)
            startActivity(phasesIntent)
            overridePendingTransition(R.anim.transition_fade_in, R.anim.transition_fade_out)
        }
    }
}
