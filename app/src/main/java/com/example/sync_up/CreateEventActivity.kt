package com.example.sync_up

import android.content.Intent
import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.text.DateFormat
import java.util.Calendar

class CreateEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)
        CreateEventProgress.show(this, 1, R.string.event_details)
        bindDatePicker(R.id.create_event_start)
        bindDatePicker(R.id.create_event_end)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.create_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.create_next_button).setOnClickListener {
            val intent = Intent(this, OrganizerDetailsActivity::class.java)
            intent.putExtra(EventDetailsDraft.NAME, fieldValue(R.id.create_event_name))
            intent.putExtra(EventDetailsDraft.VENUE, fieldValue(R.id.create_event_venue))
            intent.putExtra(EventDetailsDraft.START, fieldValue(R.id.create_event_start))
            intent.putExtra(EventDetailsDraft.END, fieldValue(R.id.create_event_end))
            startActivity(intent)
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
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun fieldValue(fieldId: Int): String {
        return findViewById<EditText>(fieldId).text.toString().trim()
    }
}
