package com.example.sync_up.data.remote

import android.content.Context
import com.example.sync_up.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Creates the shared client when a public Supabase project configuration is available. */
internal object SupabaseClientFactory {
    // A single client owns session state and network resources for the app process.
    private var sharedClient: SupabaseClient? = null

    @Synchronized
    fun get(context: Context): SupabaseClient? {
        sharedClient?.let { return it }
        val url = BuildConfig.SUPABASE_URL.trim()
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        if (url.isEmpty() || publishableKey.isEmpty()) {
            return null
        }
        return createSupabaseClient(url, publishableKey) {
            install(Auth) {
                sessionManager = SecureSessionManager(context.applicationContext, url)
            }
            install(Postgrest)
        }.also { sharedClient = it }
    }
}
