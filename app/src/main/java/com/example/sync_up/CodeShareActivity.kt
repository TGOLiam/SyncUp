package com.example.sync_up

import android.os.Bundle
import android.content.Intent
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class CodeShareActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_share)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.code_share_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val eventName = EventCreationSession.draft.name
        findViewById<TextView>(R.id.code_share_event_name).text =
            getString(R.string.code_share_event_ready, eventName)
        // Open the workspace UI with the current draft while event creation is unavailable.
        findViewById<View>(R.id.code_share_open_workspace).setOnClickListener {
            startActivity(Intent(this, EventAreaActivity::class.java))
        }
    }
}
