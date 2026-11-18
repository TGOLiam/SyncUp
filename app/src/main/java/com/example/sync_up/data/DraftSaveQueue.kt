package com.example.sync_up.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.example.sync_up.EventCreationDraft
import java.util.concurrent.Executors

/** Serializes draft writes across Activity recreation and never performs SQLite work on the UI thread. */
internal object DraftSaveQueue {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun <T> read(context: Context, operation: (EventDraftStore) -> T, callback: (Result<T>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { EventDraftStore(appContext).use(operation) }
            mainHandler.post { callback(result) }
        }
    }

    fun <T> readEvents(context: Context, operation: (StoredEventReader) -> T, callback: (Result<T>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { StoredEventReader(appContext).use(operation) }
            mainHandler.post { callback(result) }
        }
    }

    fun <T> updateShared(context: Context, operation: (SharedEventCache) -> T, callback: (Result<T>) -> Unit) {
        val appContext = context.applicationContext
        executor.execute {
            val result = runCatching { SharedEventCache(appContext).use(operation) }
            mainHandler.post { callback(result) }
        }
    }

    fun save(
        context: Context,
        id: String,
        step: Int,
        snapshot: EventCreationDraft,
        userId: String? = null,
        onFailure: (Exception) -> Unit
    ) {
        val appContext = context.applicationContext
        executor.execute {
            try {
                EventDraftStore(appContext).use { store ->
                    store.save(id, step, snapshot, userId)
                }
            } catch (error: Exception) {
                mainHandler.post { onFailure(error) }
            }
        }
    }
}
