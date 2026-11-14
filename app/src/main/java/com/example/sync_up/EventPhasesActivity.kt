package com.example.sync_up

import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Calendar
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class EventPhasesActivity : AppCompatActivity() {
    private data class Phase(val label: String, val hours: Int, val minutes: Int)

    private val phases = mutableListOf<Phase>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_phases)
        CreateEventProgress.show(this, 3, R.string.event_phases)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.event_phases_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<TextView>(R.id.phases_event_date).text =
            EventCreationSession.draft.startDate
        bindTimePicker(R.id.phases_start_time, R.id.phases_start_time_error)
        bindTimePicker(R.id.phases_end_time, R.id.phases_end_time_error)

        // Keep entered phases when Android recreates this screen.
        val labels = savedInstanceState?.getStringArrayList("phase_labels")
        val hours = savedInstanceState?.getIntegerArrayList("phase_hours")
        val minutes = savedInstanceState?.getIntegerArrayList("phase_minutes")
        if (labels != null && hours != null && minutes != null) {
            for (index in labels.indices) {
                phases.add(Phase(labels[index], hours[index], minutes[index]))
            }
        }
        val draft = EventCreationSession.draft
        if (savedInstanceState == null && draft.phasesInitialized) {
            findViewById<EditText>(R.id.phases_start_time).setText(draft.startTime)
            findViewById<EditText>(R.id.phases_end_time).setText(draft.endTime)
            for (phase in draft.phases) {
                phases.add(Phase(phase.label, phase.hours, phase.minutes))
            }
        } else if (savedInstanceState == null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            val timeFormat = DateFormat.getTimeFormat(this)
            findViewById<EditText>(R.id.phases_start_time).setText(timeFormat.format(calendar.time))
            calendar.set(Calendar.HOUR_OF_DAY, 17)
            findViewById<EditText>(R.id.phases_end_time).setText(timeFormat.format(calendar.time))
            phases.add(Phase("Test Phase", 1, 0))
        }
        renderPhases()

        findViewById<View>(R.id.phases_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.phases_footer_back_button).setOnClickListener { finish() }
        findViewById<View>(R.id.add_phase_button).setOnClickListener { showPhaseDialog(null) }
        findViewById<View>(R.id.phases_next_button).setOnClickListener {
            if (!validateRequiredFields()) {
                return@setOnClickListener
            }
            val nextIntent = Intent(this, ChannelsActivity::class.java)
            startActivity(nextIntent)
        }
    }

    override fun onPause() {
        val draft = EventCreationSession.draft
        draft.startTime = timeValue(R.id.phases_start_time)
        draft.endTime = timeValue(R.id.phases_end_time)
        draft.startTimeIso = toIsoTime(draft.startTime)
        draft.endTimeIso = toIsoTime(draft.endTime)
        draft.phasesInitialized = true
        draft.phases.clear()
        for (phase in phases) {
            draft.phases.add(DraftPhase(phase.label, phase.hours, phase.minutes))
        }
        super.onPause()
    }

    private fun toIsoTime(displayValue: String): String? {
        if (displayValue.isBlank()) return null
        return try {
            val parsed = DateFormat.getTimeFormat(this).parse(displayValue)
            parsed?.let { SimpleDateFormat("HH:mm", Locale.US).format(it) }
        } catch (_: ParseException) {
            null
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList("phase_labels", ArrayList(phases.map { it.label }))
        outState.putIntegerArrayList("phase_hours", ArrayList(phases.map { it.hours }))
        outState.putIntegerArrayList("phase_minutes", ArrayList(phases.map { it.minutes }))
        super.onSaveInstanceState(outState)
    }

    private fun bindTimePicker(fieldId: Int, errorId: Int) {
        val field = findViewById<EditText>(fieldId)
        field.setOnClickListener {
            val calendar = Calendar.getInstance()
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    calendar.set(Calendar.HOUR_OF_DAY, hour)
                    calendar.set(Calendar.MINUTE, minute)
                    field.setText(DateFormat.getTimeFormat(this).format(calendar.time))
                    findViewById<View>(errorId).visibility = View.GONE
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    private fun timeValue(fieldId: Int): String {
        return findViewById<EditText>(fieldId).text.toString()
    }

    private fun validateRequiredFields(): Boolean {
        val startMissing = timeValue(R.id.phases_start_time).isBlank()
        val endMissing = timeValue(R.id.phases_end_time).isBlank()
        val phasesMissing = phases.isEmpty()

        findViewById<View>(R.id.phases_start_time_error).visibility =
            if (startMissing) View.VISIBLE else View.GONE
        findViewById<View>(R.id.phases_end_time_error).visibility =
            if (endMissing) View.VISIBLE else View.GONE
        findViewById<View>(R.id.phases_list_error).visibility =
            if (phasesMissing) View.VISIBLE else View.GONE

        return !startMissing && !endMissing && !phasesMissing
    }

    private fun renderPhases() {
        val list = findViewById<LinearLayout>(R.id.phase_list)
        list.removeAllViews()
        for ((index, phase) in phases.withIndex()) {
            val row = LayoutInflater.from(this).inflate(R.layout.item_preview_phase, list, false)
            row.findViewById<TextView>(R.id.phase_name).text = phase.label
            row.findViewById<TextView>(R.id.phase_duration).text =
                getString(R.string.phase_duration_format, phase.hours, phase.minutes)
            row.findViewById<View>(R.id.phase_edit_button).setOnClickListener { showPhaseDialog(index) }
            row.findViewById<View>(R.id.phase_remove_button).setOnClickListener {
                phases.removeAt(index)
                renderPhases()
            }
            list.addView(row)
        }
    }

    private fun showPhaseDialog(index: Int?) {
        val inputs = layoutInflater.inflate(R.layout.dialog_phase_input, null)
        val label = inputs.findViewById<EditText>(R.id.phase_input_label)
        val hours = inputs.findViewById<EditText>(R.id.phase_input_hours)
        val minutes = inputs.findViewById<EditText>(R.id.phase_input_minutes)
        if (index != null) {
            val phase = phases[index]
            label.setText(phase.label)
            hours.setText(phase.hours.toString())
            minutes.setText(phase.minutes.toString())
        }
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_PhaseDialog)
            .setTitle(if (index == null) R.string.phase_add_title else R.string.phase_edit_title)
            .setView(inputs)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.save, null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phaseLabel = label.text.toString().trim()
                val phaseHours = hours.text.toString().toIntOrNull() ?: 0
                val phaseMinutes = minutes.text.toString().toIntOrNull() ?: 0
                if (phaseLabel.isEmpty()) {
                    label.error = getString(R.string.event_details_required)
                } else if (phaseMinutes >= 60 || (phaseHours == 0 && phaseMinutes == 0)) {
                    minutes.error = getString(R.string.phase_duration_invalid)
                } else {
                    val phase = Phase(phaseLabel, phaseHours, phaseMinutes)
                    if (index == null) phases.add(phase) else phases[index] = phase
                    findViewById<View>(R.id.phases_list_error).visibility = View.GONE
                    renderPhases()
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
}
