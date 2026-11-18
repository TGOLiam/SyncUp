package com.example.sync_up

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.sync_up.data.DraftSaveQueue
import com.example.sync_up.data.remote.SupabaseClientFactory
import com.example.sync_up.data.remote.SupabaseEventService
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class EventListActivity : AppCompatActivity() {
    private lateinit var events: List<EventListItem>
    private var loadingDraft = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_list)
        events = emptyList()
        findViewById<ListView>(R.id.event_list).apply {
            adapter = EventListAdapter(this@EventListActivity, events)
            setOnItemClickListener { _, _, position, _ ->
                val event = events[position]
                if (event.draftId != null) {
                    openDraft(event.draftId)
                } else {
                    val intent = Intent(this@EventListActivity, WorkspaceActivity::class.java)
                    intent.putExtra(WorkspaceActivity.EXTRA_EVENT_ID, event.previewId)
                    startActivity(intent)
                }
            }
        }

        findViewById<View>(R.id.join_event_button).setOnClickListener {
            startActivity(Intent(this, JoinEventActivity::class.java))
        }
        findViewById<View>(R.id.create_event_button).setOnClickListener {
            EventCreationSession.start()
            startActivity(Intent(this, CreateEventActivity::class.java))
        }
        findViewById<View>(R.id.settings_button).setOnClickListener {
            AlertDialog.Builder(this)
                .setItems(arrayOf(getString(R.string.auth_sign_out))) { _, _ -> signOut() }
                .show()
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

    override fun onResume() {
        super.onResume()
        val client = SupabaseClientFactory.get(applicationContext)
        val userId = client?.auth?.currentUserOrNull()?.id
        loadLocalEvents(userId)
        if (client != null && userId != null) {
            lifecycleScope.launch {
                try {
                    val service = SupabaseEventService(client)
                    val remoteEvents = service.listEvents().map { service.loadEvent(it.id) }
                    DraftSaveQueue.updateShared(applicationContext, { cache ->
                        for (event in remoteEvents) cache.save(userId, event)
                        cache.retainAccess(userId, remoteEvents.map { it.event.id })
                    }) { result ->
                        if (isFinishing || isDestroyed || client.auth.currentUserOrNull()?.id != userId) return@updateShared
                        result.onSuccess { loadLocalEvents(userId) }
                            .onFailure { Toast.makeText(this@EventListActivity, R.string.event_refresh_failed, Toast.LENGTH_LONG).show() }
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Toast.makeText(this@EventListActivity, R.string.event_refresh_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun loadLocalEvents(userId: String?) {
        DraftSaveQueue.read(applicationContext, { store ->
            if (userId != null) store.claimUnownedDrafts(userId)
            val drafts = store.list(userId)
            val storedEvents = com.example.sync_up.data.StoredEventReader(applicationContext).use { it.list(userId) }
            Pair(drafts, storedEvents)
        }) { result ->
            if (isFinishing || isDestroyed) return@read
            result.onSuccess { (drafts, storedEvents) ->
                val draftCards = drafts.map { draft ->
                    EventListItem(
                        previewId = "",
                        draftId = draft.id,
                        name = draft.name.ifBlank { getString(R.string.draft_untitled) },
                        detail = getString(R.string.draft_resume),
                        isLead = false
                    )
                }
                val eventCards = storedEvents.map { event ->
                    EventListItem(
                        previewId = event.id,
                        isPreview = event.isPreview,
                        name = event.name,
                        detail = event.detail.orEmpty(),
                        isLead = if (event.isPreview) event.isLeadPreview else event.isCreator,
                        phase = event.activePhase?.let { getString(R.string.event_active_phase_format, it) }
                            ?: getString(R.string.event_no_phase),
                        activePhase = event.activePhase != null
                    )
                }
                events = draftCards + eventCards
                findViewById<ListView>(R.id.event_list).adapter = EventListAdapter(this, events)
            }.onFailure {
                Toast.makeText(this, R.string.draft_load_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openDraft(id: String) {
        if (loadingDraft) return
        loadingDraft = true
        DraftSaveQueue.read(applicationContext, { it.load(id) }) { result ->
            loadingDraft = false
            if (isFinishing || isDestroyed) return@read
            val saved = result.getOrNull()
            if (saved == null) {
                Toast.makeText(this, R.string.draft_load_failed, Toast.LENGTH_LONG).show()
                return@read
            }
            EventCreationSession.resume(saved.draft)
            val intent = Intent(this, CreateEventActivity::class.java)
            intent.putExtra(CreateEventActivity.EXTRA_DRAFT_ID, saved.id)
            intent.putExtra(CreateEventActivity.EXTRA_DRAFT_STEP, saved.step)
            startActivity(intent)
        }
    }

    private fun signOut() {
        val client = SupabaseClientFactory.get(applicationContext) ?: return
        lifecycleScope.launch {
            try {
                client.auth.signOut()
                startActivity(Intent(this@EventListActivity, AuthActivity::class.java))
                finish()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                Toast.makeText(this@EventListActivity, R.string.auth_sign_out_failed, Toast.LENGTH_LONG).show()
            }
        }
    }
}
