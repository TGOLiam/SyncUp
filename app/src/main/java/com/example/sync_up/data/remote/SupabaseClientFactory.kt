package com.example.sync_up.data.remote

import com.example.sync_up.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Creates the shared client when a public Supabase project configuration is available. */
internal object SupabaseClientFactory {
    // A single client owns session state and network resources for the app process.
    val client: SupabaseClient? by lazy {
        val url = BuildConfig.SUPABASE_URL.trim()
        val publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.trim()
        if (url.isEmpty() || publishableKey.isEmpty()) {
            null
        } else {
            createSupabaseClient(url, publishableKey) {
                install(Auth)
                install(Postgrest)
            }
        }
    }
}
