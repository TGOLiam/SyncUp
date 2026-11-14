package com.example.sync_up

import android.os.Bundle
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

        val eventName = intent.getStringExtra(EventDetailsDraft.NAME).orEmpty()
        findViewById<TextView>(R.id.code_share_event_name).text =
            getString(R.string.code_share_event_ready, eventName)
        // Actions remain disabled until event creation supplies a real join code and workspace.
    }
}
