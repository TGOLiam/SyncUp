package com.example.sync_up

/** Compares canonical local dates and times produced by the event setup form. */
internal object EventScheduleValidator {
    fun isEndAfterStart(
        startDate: String,
        startTime: String,
        endDate: String,
        endTime: String
    ): Boolean {
        return "$endDate $endTime" > "$startDate $startTime"
    }
}
