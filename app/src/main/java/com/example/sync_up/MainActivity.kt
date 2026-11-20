package com.example.sync_up

import android.content.Intent
import android.view.View
import android.widget.TextView
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.sync_up.data.remote.SupabaseClientFactory
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        // Loading screen (revision example 01): 40sp wordmark variant.
        findViewById<View>(R.id.loading_wordmark).apply {
            contentDescription = getString(R.string.wordmark_syncup)
        }
        for (id in listOf(R.id.ui_wordmark_sync, R.id.ui_wordmark_up)) {
            val half = findViewById<TextView>(id)
            half.textSize = 40f
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        lifecycleScope.launch {
            val client = SupabaseClientFactory.get(applicationContext)
            val authenticated = if (client == null) {
                false
            } else {
                try {
                    client.auth.awaitInitialization()
                    client.auth.currentSessionOrNull() != null
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
            }
            val destination = if (authenticated) EventListActivity::class.java else AuthActivity::class.java
            startActivity(Intent(this@MainActivity, destination))
            finish()
        }
    }
}
