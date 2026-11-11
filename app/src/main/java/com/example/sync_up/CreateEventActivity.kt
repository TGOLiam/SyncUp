package com.example.sync_up

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CreateEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_event)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.create_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.create_back_button).setOnClickListener {
            finish()
        }
        // The next setup step is not connected during the UI prototype.
        findViewById<View>(R.id.create_next_button).setOnClickListener {
            Toast.makeText(this, R.string.create_event_placeholder, Toast.LENGTH_SHORT).show()
        }
    }
}
