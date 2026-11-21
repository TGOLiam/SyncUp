package com.example.sync_up

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.example.sync_up.data.DraftSaveQueue
import com.example.sync_up.data.remote.SupabaseClientFactory
import com.example.sync_up.data.remote.SupabaseEventService
import com.example.sync_up.data.remote.EventRequestMapper
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.time.ZoneId
import io.github.jan.supabase.auth.auth
import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import java.lang.ref.WeakReference

/** Hosts the event creation steps and keeps their views alive while navigating. */
class CreateEventActivity : AppCompatActivity() {
    companion object {
        const val EXTRA_DRAFT_ID = "draft_id"
        const val EXTRA_DRAFT_STEP = "draft_step"
    }
    private data class Phase(val label: String, val hours: Int, val minutes: Int)
    private val phases = mutableListOf<Phase>()
    private val channels = mutableListOf<String>()
    private var step = 0
    private lateinit var draftId: String
    private var submitting = false
    private var createdEventId: String? = null
    private var joinCode: String? = null
    private val sections = intArrayOf(
        R.id.creation_details, R.id.creation_venue, R.id.creation_phases,
        R.id.creation_channels, R.id.creation_review, R.id.creation_code
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        draftId = savedInstanceState?.getString("draft_id")
            ?: intent.getStringExtra(EXTRA_DRAFT_ID)
            ?: UUID.randomUUID().toString()
        createdEventId = savedInstanceState?.getString("created_event_id")
        joinCode = savedInstanceState?.getString("join_code")
        setContentView(R.layout.activity_creation_workspace)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.creation_workspace_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        bindDatePicker(R.id.create_event_start)
        bindDatePicker(R.id.create_event_end)
        bindTimePicker(R.id.phases_start_time, R.id.phases_start_time_error)
        bindTimePicker(R.id.phases_end_time, R.id.phases_end_time_error)
        val draft = EventCreationSession.draft
        if (savedInstanceState == null && draft.name.isNotEmpty()) {
            findViewById<EditText>(R.id.create_event_name).setText(draft.name)
            findViewById<EditText>(R.id.create_event_venue).setText(draft.venue)
            findViewById<EditText>(R.id.create_event_start).setText(draft.startDate)
            findViewById<EditText>(R.id.create_event_end).setText(draft.endDate)
        } else if (savedInstanceState == null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val today = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Calendar.getInstance().time)
            findViewById<EditText>(R.id.create_event_name).setText("Test Event")
            findViewById<EditText>(R.id.create_event_venue).setText("Test Venue")
            findViewById<EditText>(R.id.create_event_start).setText(today)
            findViewById<EditText>(R.id.create_event_end).setText(today)
        }
        val drawing = findViewById<VenueDrawingView>(R.id.venue_drawing)
        if (savedInstanceState == null) draft.layout?.let { drawing.restoreLayout(it) }
        drawing.onEditModeChanged = { editing -> updateDrawingMode(editing) }
        val labels = savedInstanceState?.getStringArrayList("phase_labels")
        val hours = savedInstanceState?.getIntegerArrayList("phase_hours")
        val minutes = savedInstanceState?.getIntegerArrayList("phase_minutes")
        if (labels != null && hours != null && minutes != null) {
            for (index in labels.indices) phases.add(Phase(labels[index], hours[index], minutes[index]))
        } else if (draft.phasesInitialized) {
            findViewById<EditText>(R.id.phases_start_time).setText(draft.startTime)
            findViewById<EditText>(R.id.phases_end_time).setText(draft.endTime)
            for (phase in draft.phases) phases.add(Phase(phase.label, phase.hours, phase.minutes))
        } else if (savedInstanceState == null && applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.HOUR_OF_DAY, 9)
            calendar.set(Calendar.MINUTE, 0)
            val format = android.text.format.DateFormat.getTimeFormat(this)
            findViewById<EditText>(R.id.phases_start_time).setText(format.format(calendar.time))
            calendar.set(Calendar.HOUR_OF_DAY, 17)
            findViewById<EditText>(R.id.phases_end_time).setText(format.format(calendar.time))
            phases.add(Phase("Test Phase", 1, 0))
        }
        channels.addAll(savedInstanceState?.getStringArrayList("channels") ?: draft.channels)
        renderPhases()
        renderChannels()
        for (index in 0..4) {
            val titles = intArrayOf(R.string.event_details, R.string.venue_layout, R.string.event_phases, R.string.channels_title, R.string.event_review)
            CreateEventProgress.show(this, findViewById(sections[index]), index + 1, titles[index])
        }
        bindActions(drawing)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBack()
            }
        })
        showStep(
            savedInstanceState?.getInt("creation_step") ?: intent.getIntExtra(EXTRA_DRAFT_STEP, 0),
            save = false
        )
    }

    private fun bindActions(drawing: VenueDrawingView) {
        findViewById<View>(R.id.creation_back).setOnClickListener { goBack() }
        for (id in listOf(R.id.venue_footer_back_button, R.id.phases_footer_back_button, R.id.channels_footer_back_button, R.id.review_footer_back_button)) {
            findViewById<View>(id).setOnClickListener { goBack() }
        }
        findViewById<View>(R.id.create_next_button).setOnClickListener { if (validateDetails()) showStep(1) }
        findViewById<View>(R.id.venue_next_button).setOnClickListener { showStep(2) }
        findViewById<View>(R.id.phases_next_button).setOnClickListener { if (validatePhases()) showStep(3) }
        findViewById<View>(R.id.channels_review_button).setOnClickListener { showStep(4) }
        findViewById<View>(R.id.review_create_button).setOnClickListener {
            submitEvent()
        }
        findViewById<View>(R.id.review_edit_details).setOnClickListener { showStep(0) }
        bindReviewSection(R.id.review_venue_section, R.string.review_venue_title, 1)
        bindReviewSection(R.id.review_phases_section, R.string.review_phases_title, 2)
        bindReviewSection(R.id.review_channels_section, R.string.review_channels_title, 3)
        findViewById<View>(R.id.code_share_open_workspace).setOnClickListener {
            val eventId = createdEventId ?: return@setOnClickListener
            startActivity(Intent(this, WorkspaceActivity::class.java).putExtra(WorkspaceActivity.EXTRA_EVENT_ID, eventId))
            finish()
        }
        findViewById<View>(R.id.code_share_copy).setOnClickListener {
            val code = joinCode ?: return@setOnClickListener
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.code_share_title), code))
            Toast.makeText(this, R.string.code_copied, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.add_phase_button).setOnClickListener { showPhaseDialog(null) }
        findViewById<View>(R.id.add_channel_button).setOnClickListener { showChannelDialog() }
        findViewById<View>(R.id.venue_add_zone_button).setOnClickListener { showAddZoneDialog(drawing) }
        findViewById<View>(R.id.venue_draw_button).setOnClickListener {
            drawing.setEditMode(false)
            updateDrawingMode(false)
        }
        findViewById<View>(R.id.venue_edit_button).setOnClickListener {
            if (drawing.setEditMode(true)) updateDrawingMode(true)
            else Toast.makeText(this, R.string.venue_edit_unavailable, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.venue_undo_button).setOnClickListener {
            drawing.undoLastStroke()
            updateDrawingMode(drawing.isEditing())
        }
        findViewById<View>(R.id.venue_clear_button).setOnClickListener {
            if (drawing.isZoneSelected()) Toast.makeText(this, R.string.venue_select_for_clear, Toast.LENGTH_SHORT).show()
            else drawing.clearDrawing()
        }
        findViewById<View>(R.id.venue_simplify_button).setOnClickListener {
            if (!drawing.simplifyBoundary()) Toast.makeText(this, R.string.venue_simplify_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun showStep(next: Int, save: Boolean = true) {
        if (save) saveDraft()
        val input = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let {
            input.hideSoftInputFromWindow(it.windowToken, 0)
            it.clearFocus()
        }
        step = next
        if (save) persistDraft()
        for (index in sections.indices) findViewById<View>(sections[index]).visibility = if (index == step) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.creation_title).setText(if (step == 5) R.string.code_share_title else R.string.create_event)
        findViewById<TextView>(R.id.phases_event_date).text = EventCreationSession.draft.startDate
        if (step == 4) updateReview()
        if (step == 5) {
            findViewById<TextView>(R.id.code_share_event_name).text = getString(R.string.code_share_event_ready, EventCreationSession.draft.name)
            findViewById<TextView>(R.id.code_share_value).text = joinCode.orEmpty()
        }
    }

    private fun goBack() {
        if (submitting) return
        if (step == 0 || createdEventId != null) finish() else showStep(step - 1)
    }

    override fun onPause() {
        if (!submitting && createdEventId == null) {
            saveDraft()
            persistDraft()
        }
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("creation_step", step)
        outState.putString("draft_id", draftId)
        outState.putString("created_event_id", createdEventId)
        outState.putString("join_code", joinCode)
        outState.putStringArrayList("phase_labels", ArrayList(phases.map { it.label }))
        outState.putIntegerArrayList("phase_hours", ArrayList(phases.map { it.hours }))
        outState.putIntegerArrayList("phase_minutes", ArrayList(phases.map { it.minutes }))
        outState.putStringArrayList("channels", ArrayList(channels))
        super.onSaveInstanceState(outState)
    }

    private fun saveDraft() {
        val draft = EventCreationSession.draft
        draft.name = fieldValue(R.id.create_event_name)
        draft.venue = fieldValue(R.id.create_event_venue)
        draft.startDate = fieldValue(R.id.create_event_start)
        draft.endDate = fieldValue(R.id.create_event_end)
        draft.startDateIso = toIsoDate(draft.startDate)
        draft.endDateIso = toIsoDate(draft.endDate)
        val drawing = findViewById<VenueDrawingView>(R.id.venue_drawing)
        // Before the venue view is measured, its existing draft must not be discarded.
        if (drawing.width > 0 && drawing.height > 0) {
            draft.layout = drawing.exportLayout()
        }
        draft.startTime = timeValue(R.id.phases_start_time)
        draft.endTime = timeValue(R.id.phases_end_time)
        draft.startTimeIso = toIsoTime(draft.startTime)
        draft.endTimeIso = toIsoTime(draft.endTime)
        draft.phasesInitialized = true
        draft.phases.clear()
        phases.forEach { draft.phases.add(DraftPhase(it.label, it.hours, it.minutes)) }
        draft.channels.clear()
        draft.channels.addAll(channels)
    }

    private fun draftSnapshot(): EventCreationDraft {
        val draft = EventCreationSession.draft
        return EventCreationDraft().apply {
            name = draft.name
            venue = draft.venue
            startDate = draft.startDate
            endDate = draft.endDate
            startTime = draft.startTime
            endTime = draft.endTime
            startDateIso = draft.startDateIso
            endDateIso = draft.endDateIso
            startTimeIso = draft.startTimeIso
            endTimeIso = draft.endTimeIso
            layout = draft.layout?.let { savedLayout ->
                DraftLayout(
                    savedLayout.aspectRatio,
                    savedLayout.boundary.toList(),
                    savedLayout.zones.map { DraftZone(it.name, it.vertices.toList()) }
                )
            }
            phasesInitialized = draft.phasesInitialized
            phases.addAll(draft.phases)
            channels.addAll(draft.channels)
        }
    }

    private fun persistDraft() {
        val snapshot = draftSnapshot()
        val activity = WeakReference(this)
        val userId = SupabaseClientFactory.get(applicationContext)?.auth?.currentUserOrNull()?.id
        DraftSaveQueue.save(applicationContext, draftId, step, snapshot, userId) {
            activity.get()?.let { current ->
                if (!current.isFinishing && !current.isDestroyed) {
                    Toast.makeText(current, R.string.draft_save_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun submitEvent() {
        if (submitting || createdEventId != null) return
        if (!validateDetails() || !validatePhases()) {
            Toast.makeText(this, R.string.creation_invalid_schedule, Toast.LENGTH_LONG).show()
            return
        }
        val client = SupabaseClientFactory.get(applicationContext)
        val userId = client?.auth?.currentUserOrNull()?.id
        if (client == null || userId == null) {
            Toast.makeText(this, R.string.creation_submit_failed, Toast.LENGTH_LONG).show()
            return
        }
        saveDraft()
        val snapshot = draftSnapshot()
        val arguments = try {
            EventRequestMapper.createArguments(draftId, snapshot, ZoneId.systemDefault())
        } catch (_: Exception) {
            Toast.makeText(this, R.string.creation_invalid_schedule, Toast.LENGTH_LONG).show()
            return
        }
        submitting = true
        findViewById<View>(R.id.review_create_button).isEnabled = false
        // Save the exact request before sending it, so a lost response can be retried.
        DraftSaveQueue.read(applicationContext, { store ->
            store.save(draftId, step, snapshot, userId)
        }) { saved ->
            if (isFinishing || isDestroyed) return@read
            if (saved.isFailure) {
                submitting = false
                findViewById<View>(R.id.review_create_button).isEnabled = true
                Toast.makeText(this, R.string.draft_save_failed, Toast.LENGTH_LONG).show()
                return@read
            }
            lifecycleScope.launch {
                val service = SupabaseEventService(client)
                val created = try {
                    service.createEvent(arguments)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    submitting = false
                    findViewById<View>(R.id.review_create_button).isEnabled = true
                    Toast.makeText(this@CreateEventActivity, R.string.creation_submit_failed, Toast.LENGTH_LONG).show()
                    return@launch
                }
                try {
                    val event = service.loadEvent(created.eventId)
                    DraftSaveQueue.updateShared(applicationContext, { cache -> cache.save(userId, event) }) { result ->
                        if (isFinishing || isDestroyed) return@updateShared
                        submitting = false
                        if (result.isSuccess) {
                            createdEventId = created.eventId
                            joinCode = created.joinCode
                            showStep(5, save = false)
                        } else {
                            Toast.makeText(this@CreateEventActivity, R.string.creation_cache_failed, Toast.LENGTH_LONG).show()
                            finish()
                        }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    submitting = false
                    Toast.makeText(this@CreateEventActivity, R.string.creation_cache_failed, Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }
    }

    private fun toIsoTime(value: String): String? {
        if (value.isBlank()) return null
        return try {
            android.text.format.DateFormat.getTimeFormat(this).parse(value)?.let { SimpleDateFormat("HH:mm", Locale.US).format(it) }
        } catch (_: ParseException) { null }
    }

    private fun bindReviewSection(id: Int, title: Int, destination: Int) {
        val section = findViewById<View>(id)
        section.findViewById<TextView>(R.id.review_section_title).setText(title)
        section.findViewById<View>(R.id.review_section_edit).apply {
            contentDescription = getString(R.string.review_edit_section, getString(title))
            setOnClickListener { showStep(destination) }
        }
    }
    private fun updateReview() {
        showEventDetails()
        val draft = EventCreationSession.draft
        val layout = draft.layout
        findViewById<View>(R.id.review_venue_section).findViewById<TextView>(R.id.review_section_detail).text =
            if (layout == null) getString(R.string.review_venue_detail) else getString(R.string.review_venue_draft, layout.boundary.size, layout.zones.size) +
                if (layout.zones.isEmpty()) "" else "\n" + layout.zones.joinToString(", ") { it.name }
        findViewById<View>(R.id.review_phases_section).findViewById<TextView>(R.id.review_section_detail).text =
            getString(R.string.review_phases_detail, draft.phases.size) + if (draft.phases.isEmpty()) "" else "\n" + draft.phases.joinToString(", ") { it.label }
        findViewById<View>(R.id.review_channels_section).findViewById<TextView>(R.id.review_section_detail).text =
            getString(R.string.review_channels_detail, draft.channels.size) + if (draft.channels.isEmpty()) "" else "\n" + draft.channels.joinToString(", ")
    }

    private fun toIsoDate(displayValue: String): String? {
        if (displayValue.isBlank()) return null
        return try {
            val parsed = DateFormat.getDateInstance(DateFormat.MEDIUM).parse(displayValue)
            parsed?.let { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(it) }
        } catch (_: ParseException) {
            null
        }
    }

    private fun bindDatePicker(fieldId: Int) {
        val field = findViewById<EditText>(fieldId)
        field.setOnClickListener {
            val dateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM)
            val calendar = Calendar.getInstance()
            // Reopen the picker at the previously selected date when available.
            try {
                val selectedDate = dateFormat.parse(field.text.toString())
                if (selectedDate != null) {
                    calendar.time = selectedDate
                }
            } catch (_: java.text.ParseException) {
                // An empty field starts at today's date.
            }
            DatePickerDialog(
                this,
                { _, year, month, day ->
                    calendar.set(year, month, day)
                    field.setText(dateFormat.format(calendar.time))
                    field.error = null
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun validateDetails(): Boolean {
        val fieldIds = listOf(
            R.id.create_event_name,
            R.id.create_event_venue,
            R.id.create_event_start,
            R.id.create_event_end
        )
        var allFieldsFilled = true
        for (fieldId in fieldIds) {
            val field = findViewById<EditText>(fieldId)
            if (field.text.toString().isBlank()) {
                field.error = getString(R.string.event_details_required)
                allFieldsFilled = false
            } else {
                field.error = null
            }
        }
        return allFieldsFilled
    }

    private fun fieldValue(fieldId: Int): String {
        return findViewById<EditText>(fieldId).text.toString().trim()
    }
    private fun updateDrawingMode(editing: Boolean) {
        val drawColor = if (editing) R.color.white else R.color.venue_selected
        val editColor = if (editing) R.color.venue_selected else R.color.white
        // The venue tool buttons are AppCompatButtons; background tints apply
        // through the platform attribute, no MaterialButton cast needed.
        findViewById<AppCompatButton>(R.id.venue_draw_button).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, drawColor))
        findViewById<AppCompatButton>(R.id.venue_edit_button).backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, editColor))
    }

    private fun showAddZoneDialog(drawing: VenueDrawingView) {
        val inputs = layoutInflater.inflate(R.layout.dialog_zone_input, null)
        val name = inputs.findViewById<EditText>(R.id.zone_input_name)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_UiDialog)
            .setCustomTitle(uiDialogTitle(this, R.string.venue_add_zone_title))
            .setView(inputs)
            .create()
        // Ref 09: Cancel/Save live in the dialog view, not the platform frame.
        inputs.findViewById<View>(R.id.dialog_cancel).setOnClickListener { dialog.dismiss() }
        inputs.findViewById<View>(R.id.dialog_save).setOnClickListener {
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
        dialog.show()
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
                    field.setText(android.text.format.DateFormat.getTimeFormat(this).format(calendar.time))
                    findViewById<View>(errorId).visibility = View.GONE
                },
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                android.text.format.DateFormat.is24HourFormat(this)
            ).show()
        }
    }

    private fun timeValue(fieldId: Int): String {
        return findViewById<EditText>(fieldId).text.toString()
    }

    private fun validatePhases(): Boolean {
        val startMissing = timeValue(R.id.phases_start_time).isBlank()
        val endMissing = timeValue(R.id.phases_end_time).isBlank()
        val phasesMissing = phases.isEmpty()

        findViewById<View>(R.id.phases_start_time_error).visibility =
            if (startMissing) View.VISIBLE else View.GONE
        findViewById<View>(R.id.phases_end_time_error).visibility =
            if (endMissing) View.VISIBLE else View.GONE
        findViewById<View>(R.id.phases_list_error).visibility =
            if (phasesMissing) View.VISIBLE else View.GONE

        if (startMissing || endMissing || phasesMissing) return false

        val startDate = toIsoDate(fieldValue(R.id.create_event_start))
        val endDate = toIsoDate(fieldValue(R.id.create_event_end))
        val startTime = toIsoTime(timeValue(R.id.phases_start_time))
        val endTime = toIsoTime(timeValue(R.id.phases_end_time))
        if (startDate == null || endDate == null || startTime == null || endTime == null ||
            !EventScheduleValidator.isEndAfterStart(startDate, startTime, endDate, endTime)
        ) {
            findViewById<EditText>(R.id.phases_end_time).error = getString(R.string.event_end_after_start)
            return false
        }
        findViewById<EditText>(R.id.phases_end_time).error = null
        return true
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
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_UiDialog)
            .setCustomTitle(uiDialogTitle(this, if (index == null) R.string.phase_add_title else R.string.phase_edit_title))
            .setView(inputs)
            .create()
        // Ref 11: Cancel/Save live in the dialog view, not the platform frame.
        inputs.findViewById<View>(R.id.dialog_cancel).setOnClickListener { dialog.dismiss() }
        inputs.findViewById<View>(R.id.dialog_save).setOnClickListener {
            val phaseLabel = label.text.toString().trim()
            val phaseHours = hours.text.toString().toIntOrNull() ?: 0
            val phaseMinutes = minutes.text.toString().toIntOrNull() ?: 0
            if (phaseLabel.isEmpty()) {
                label.error = getString(R.string.event_details_required)
            } else if (phaseHours > 99 || phaseMinutes >= 60 || (phaseHours == 0 && phaseMinutes == 0)) {
                minutes.error = getString(R.string.phase_duration_invalid)
            } else {
                val phase = Phase(phaseLabel, phaseHours, phaseMinutes)
                if (index == null) phases.add(phase) else phases[index] = phase
                findViewById<View>(R.id.phases_list_error).visibility = View.GONE
                renderPhases()
                dialog.dismiss()
            }
        }
        dialog.show()
    }
    private fun renderChannels() {
        val list = findViewById<LinearLayout>(R.id.channel_list)
        list.removeAllViews()
        for ((index, name) in channels.withIndex()) {
            val row = layoutInflater.inflate(R.layout.item_channel, list, false)
            row.findViewById<TextView>(R.id.channel_name).text = name
            row.findViewById<View>(R.id.channel_remove_button).apply {
                contentDescription = getString(R.string.channel_remove, name)
                setOnClickListener {
                    channels.removeAt(index)
                    renderChannels()
                }
            }
            list.addView(row)
        }
    }

    private fun showChannelDialog() {
        val inputs = layoutInflater.inflate(R.layout.dialog_channel_input, null)
        val name = inputs.findViewById<EditText>(R.id.channel_input_name)
        val dialog = AlertDialog.Builder(this, R.style.Theme_SYNCUP_UiDialog)
            .setCustomTitle(uiDialogTitle(this, R.string.channel_add_title))
            .setView(inputs)
            .create()
        // Ref 13: Cancel/Save live in the dialog view, not the platform frame.
        inputs.findViewById<View>(R.id.dialog_cancel).setOnClickListener { dialog.dismiss() }
        inputs.findViewById<View>(R.id.dialog_save).setOnClickListener {
            val channelName = name.text.toString().trim()
            if (channelName.isEmpty()) {
                name.error = getString(R.string.event_details_required)
            } else {
                channels.add(channelName)
                renderChannels()
                dialog.dismiss()
            }
        }
        dialog.show()
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


}

