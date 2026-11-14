package com.example.sync_up

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.text.ParseException
import java.util.Calendar
import java.util.Locale

class CreateEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)
        CreateEventProgress.show(this, 1, R.string.event_details)
        bindDatePicker(R.id.create_event_start)
        bindDatePicker(R.id.create_event_end)

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

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.create_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.create_next_button).setOnClickListener {
            if (!validateRequiredFields()) {
                return@setOnClickListener
            }
            val intent = Intent(this, VenueLayoutActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onPause() {
        val draft = EventCreationSession.draft
        draft.name = fieldValue(R.id.create_event_name)
        draft.venue = fieldValue(R.id.create_event_venue)
        draft.startDate = fieldValue(R.id.create_event_start)
        draft.endDate = fieldValue(R.id.create_event_end)
        draft.startDateIso = toIsoDate(draft.startDate)
        draft.endDateIso = toIsoDate(draft.endDate)
        super.onPause()
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

    private fun validateRequiredFields(): Boolean {
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
}
