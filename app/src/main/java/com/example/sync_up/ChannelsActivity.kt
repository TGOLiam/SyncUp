package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class ChannelsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_channels)
        CreateEventProgress.show(this, 4, R.string.channels_title)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.channels_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<View>(R.id.channels_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.channels_footer_back_button).setOnClickListener {
            finish()
        }

        // Channel editing is a placeholder in the frontend prototype.
        val editButtons = listOf(
            R.id.remove_stage_channel,
            R.id.remove_registration_channel,
            R.id.remove_food_channel,
            R.id.add_channel_button
        )
        for (buttonId in editButtons) {
            findViewById<View>(buttonId).setOnClickListener {
                Toast.makeText(this, R.string.channels_edit_placeholder, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<View>(R.id.channels_review_button).setOnClickListener {
            val nextIntent = Intent(this, EventReviewActivity::class.java)
            // Forward the current draft without persisting or validating it yet.
            EventDetailsDraft.copyTo(intent, nextIntent)
            startActivity(nextIntent)
        }
    }
}
