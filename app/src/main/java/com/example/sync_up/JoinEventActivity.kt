package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.sync_up.data.DraftSaveQueue
import com.example.sync_up.data.remote.SupabaseClientFactory
import com.example.sync_up.data.remote.SupabaseEventService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class JoinEventActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_join_event)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.join_event_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        findViewById<View>(R.id.join_back_button).setOnClickListener {
            finish()
        }
        findViewById<View>(R.id.submit_join_button).setOnClickListener {
            joinEvent()
        }
    }

    private fun joinEvent() {
        val input = findViewById<EditText>(R.id.join_code)
        val code = input.text.toString()
        if (!code.matches(Regex("[0-9]{6}"))) {
            input.error = getString(R.string.join_invalid_code)
            return
        }
        val client = SupabaseClientFactory.get(applicationContext)
        val userId = client?.auth?.currentUserOrNull()?.id
        if (client == null || userId == null) {
            Toast.makeText(this, R.string.auth_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        val button = findViewById<View>(R.id.submit_join_button)
        button.isEnabled = false
        lifecycleScope.launch {
            try {
                val service = SupabaseEventService(client)
                val response = service.joinEvent(code)
                when (response.result) {
                    "JOINED", "ALREADY_MEMBER" -> {
                        val id = requireNotNull(response.eventId)
                        val remote = service.loadEvent(id)
                        DraftSaveQueue.updateShared(applicationContext, { cache ->
                            cache.save(userId, remote)
                        }) { result ->
                            if (isFinishing || isDestroyed || client.auth.currentUserOrNull()?.id != userId) return@updateShared
                            button.isEnabled = true
                            result.onSuccess {
                                val intent = Intent(this@JoinEventActivity, WorkspaceActivity::class.java)
                                intent.putExtra(WorkspaceActivity.EXTRA_EVENT_ID, id)
                                startActivity(intent)
                                finish()
                            }.onFailure {
                                Toast.makeText(this@JoinEventActivity, R.string.join_cache_failed, Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                    "INVALID_CODE" -> {
                        input.error = getString(R.string.join_invalid_code)
                        button.isEnabled = true
                    }
                    "RATE_LIMITED" -> {
                        Toast.makeText(this@JoinEventActivity, R.string.join_rate_limited, Toast.LENGTH_LONG).show()
                        button.isEnabled = true
                    }
                    else -> throw IllegalStateException("Unknown join result")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                button.isEnabled = true
                Toast.makeText(this@JoinEventActivity, R.string.join_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
