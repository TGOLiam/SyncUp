package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreateEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)
        CreateEventProgress.show(this, 1, R.string.event_details)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.create_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.create_next_button).setOnClickListener {
            startActivity(Intent(this, OrganizerDetailsActivity::class.java))
        }
    }
}
