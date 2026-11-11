package com.example.sync_up

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class JoinEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_event)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.join_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<android.view.View>(R.id.join_back_button).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.submit_join_button).setOnClickListener {
            Toast.makeText(this, R.string.join_event_placeholder, Toast.LENGTH_SHORT).show()
        }
    }
}
