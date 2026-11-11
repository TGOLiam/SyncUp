package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EventListActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)
        // Preview data is temporary; the adapter can later receive events from a repository.
        val events = PreviewEvents.load(this)
        findViewById<ListView>(R.id.event_list).adapter = EventListAdapter(this, events)

        findViewById<View>(R.id.join_event_button).setOnClickListener {
            startActivity(Intent(this, JoinEventActivity::class.java))
        }
        findViewById<View>(R.id.create_event_button).setOnClickListener {
            startActivity(Intent(this, CreateEventActivity::class.java))
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }
}
