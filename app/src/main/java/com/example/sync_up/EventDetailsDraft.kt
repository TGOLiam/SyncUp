package com.example.sync_up

import android.content.Intent

/** Keys for carrying the current event details through the setup screens. */
object EventDetailsDraft {
    const val NAME = "event_details_name"
    const val VENUE = "event_details_venue"
    const val START = "event_details_start"
    const val END = "event_details_end"

    private val keys = listOf(NAME, VENUE, START, END)

    fun copyTo(source: Intent, destination: Intent) {
        for (key in keys) {
            destination.putExtra(key, source.getStringExtra(key))
        }
    }
}
