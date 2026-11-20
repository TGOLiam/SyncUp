package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.sync_up.data.remote.SupabaseClientFactory
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class AuthActivity : AppCompatActivity() {
    private var signingUp = false
    private var submitting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_auth)
        // Screen-reader reads the full brand once, not the two color halves.
        findViewById<View>(R.id.auth_wordmark).contentDescription = getString(R.string.wordmark_syncup)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.auth_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        signingUp = savedInstanceState?.getBoolean("signing_up") ?: false
        renderMode()
        findViewById<View>(R.id.auth_toggle).setOnClickListener {
            signingUp = !signingUp
            renderMode()
        }
        findViewById<View>(R.id.auth_submit).setOnClickListener { submit() }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("signing_up", signingUp)
        super.onSaveInstanceState(outState)
    }

    private fun renderMode() {
        findViewById<View>(R.id.auth_name).visibility = if (signingUp) View.VISIBLE else View.GONE
        findViewById<View>(R.id.auth_name_label).visibility = if (signingUp) View.VISIBLE else View.GONE
        // Revision examples 02/03: headline and supporting copy follow the mode.
        findViewById<TextView>(R.id.auth_title).setText(
            if (signingUp) R.string.auth_headline_sign_up else R.string.auth_headline_sign_in
        )
        findViewById<TextView>(R.id.auth_support).setText(
            if (signingUp) R.string.auth_support_sign_up else R.string.auth_support_sign_in
        )
        findViewById<TextView>(R.id.auth_submit).setText(
            if (signingUp) R.string.auth_sign_up else R.string.auth_sign_in
        )
        findViewById<TextView>(R.id.auth_toggle).setText(
            if (signingUp) R.string.auth_switch_sign_in else R.string.auth_switch_sign_up
        )
        findViewById<View>(R.id.auth_error).visibility = View.GONE
    }

    private fun submit() {
        if (submitting) return
        val name = findViewById<EditText>(R.id.auth_name)
        val email = findViewById<EditText>(R.id.auth_email)
        val password = findViewById<EditText>(R.id.auth_password)
        val displayName = name.text.toString().trim()
        val emailValue = email.text.toString().trim()
        val passwordValue = password.text.toString()
        var valid = true
        if (signingUp && displayName.isBlank()) {
            name.error = getString(R.string.event_details_required)
            valid = false
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(emailValue).matches()) {
            email.error = getString(R.string.auth_invalid_email)
            valid = false
        }
        if (passwordValue.length < 6) {
            password.error = getString(R.string.auth_invalid_password)
            valid = false
        }
        if (!valid) return
        val client = SupabaseClientFactory.get(applicationContext)
        if (client == null) {
            showError(R.string.auth_unavailable)
            return
        }
        submitting = true
        setButtonsEnabled(false)
        lifecycleScope.launch {
            try {
                if (signingUp) {
                    client.auth.signUpWith(Email) {
                        this.email = emailValue
                        this.password = passwordValue
                        data = buildJsonObject { put("name", displayName) }
                    }
                } else {
                    client.auth.signInWith(Email) {
                        this.email = emailValue
                        this.password = passwordValue
                    }
                }
                if (client.auth.currentSessionOrNull() == null) {
                    showError(R.string.auth_check_email)
                } else {
                    startActivity(Intent(this@AuthActivity, EventListActivity::class.java))
                    finish()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                // Log only the exception type; authentication details may contain credentials.
                Log.w("SyncUpAuth", "Authentication failed: ${error.javaClass.name}")
                showError(R.string.auth_failed)
            } finally {
                submitting = false
                setButtonsEnabled(true)
            }
        }
    }

    private fun showError(message: Int) {
        findViewById<TextView>(R.id.auth_error).apply {
            setText(message)
            visibility = View.VISIBLE
        }
    }

    private fun setButtonsEnabled(enabled: Boolean) {
        findViewById<View>(R.id.auth_submit).isEnabled = enabled
        findViewById<View>(R.id.auth_toggle).isEnabled = enabled
    }
}
