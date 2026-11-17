package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton

/** Displays the selected event's phase timeline as a non-persistent preview. */
class WorkspacePhasesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_workspace_phases)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.workspace_phases_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val previewIndex = intent.getIntExtra(PreviewEvents.EXTRA_EVENT_INDEX, -1)
        val event = PreviewEvents.load(this).getOrNull(previewIndex)
        val phases = event?.phases ?: EventCreationSession.draft.phases.map { it.label }
        val activeIndex = event?.workspacePhase?.let { phases.indexOf(it) } ?: -1
        val isLead = event?.isLead ?: true
        findViewById<View>(R.id.workspace_phases_edit).apply {
            visibility = if (isLead) View.VISIBLE else View.GONE
            setOnClickListener { showUnavailable() }
        }
        findViewById<View>(R.id.workspace_phase_empty).visibility =
            if (phases.isEmpty()) View.VISIBLE else View.GONE

        val list = findViewById<LinearLayout>(R.id.workspace_phase_list)
        for ((index, phase) in phases.withIndex()) {
            val active = index == activeIndex
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(dp(14), dp(8), dp(14), dp(8))
                setBackgroundResource(if (active) R.drawable.workspace_active_phase_card else R.drawable.workspace_card)
            }
            val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val title = TextView(this).apply {
                text = phase
                textSize = 16f
                setTypeface(null, android.graphics.Typeface.BOLD)
                setTextColor(ContextCompat.getColor(this@WorkspacePhasesActivity, if (active) R.color.white else R.color.event_text))
            }
            val status = TextView(this).apply {
                setText(when {
                    active -> R.string.workspace_phase_active
                    activeIndex >= 0 && index < activeIndex -> R.string.workspace_phase_completed
                    else -> R.string.workspace_phase_upcoming
                })
                setTextColor(ContextCompat.getColor(this@WorkspacePhasesActivity, if (active) R.color.white else R.color.event_muted))
            }
            content.addView(title)
            content.addView(status)
            row.addView(content, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            if (isLead && !active && (activeIndex < 0 || index > activeIndex)) {
                val button = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
                button.setText(R.string.workspace_phase_activate)
                button.isAllCaps = false
                button.cornerRadius = dp(10)
                button.setOnClickListener { showUnavailable() }
                row.addView(button)
            }
            val params = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            params.bottomMargin = dp(8)
            list.addView(row, params)
        }
        findViewById<View>(R.id.workspace_nav_area).setOnClickListener {
            val area = Intent(this, EventAreaActivity::class.java)
            area.putExtra(PreviewEvents.EXTRA_EVENT_INDEX, previewIndex)
            area.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(area)
        }
        findViewById<View>(R.id.workspace_nav_comms).setOnClickListener {
            val communications = Intent(this, CommunicationsActivity::class.java)
            communications.putExtra(PreviewEvents.EXTRA_EVENT_INDEX, previewIndex)
            communications.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            startActivity(communications)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun showUnavailable() {
        Toast.makeText(this, R.string.phases_edit_placeholder, Toast.LENGTH_SHORT).show()
    }
}
