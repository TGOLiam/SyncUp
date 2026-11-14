package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class OrganizerDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_organizer_details)
        CreateEventProgress.show(this, 2, R.string.organizer_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.organizer_details_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.organizer_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.organizer_footer_back_button).setOnClickListener {
            finish()
        }

        // Organizer editing is a placeholder in the UI prototype.
        findViewById<View>(R.id.add_organizer_button).setOnClickListener {
            Toast.makeText(this, R.string.organizer_add_placeholder, Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.organizer_next_button).setOnClickListener {
            val nextIntent = Intent(this, VenueLayoutActivity::class.java)
            // Forward the current draft without persisting or validating it yet.
            EventDetailsDraft.copyTo(intent, nextIntent)
            startActivity(nextIntent)
        }
    }
}
