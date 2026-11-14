package com.example.sync_up

import android.content.Intent

/** Keys for carrying the current event details through the setup screens. */
object EventDetailsDraft {
    const val NAME = "event_details_name"
    const val VENUE = "event_details_venue"
    const val START = "event_details_start"
    const val END = "event_details_end"
    const val START_TIME = "event_start_time"
    const val END_TIME = "event_end_time"
    const val PHASE_COUNT = "event_phase_count"

    private val keys = listOf(NAME, VENUE, START, END, START_TIME, END_TIME, PHASE_COUNT)

    fun copyTo(source: Intent, destination: Intent) {
        for (key in keys) {
            destination.putExtra(key, source.getStringExtra(key))
        }
    }
}
