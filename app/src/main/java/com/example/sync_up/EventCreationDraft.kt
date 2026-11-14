package com.example.sync_up

/** In-memory event setup data; discarded when a new creation flow starts. */
data class DraftPoint(val x: Float, val y: Float)
data class DraftZone(val name: String, val vertices: List<DraftPoint>)
data class DraftLayout(
    val aspectRatio: Float,
    val boundary: List<DraftPoint>,
    val zones: List<DraftZone>
)
data class DraftPhase(val label: String, val hours: Int, val minutes: Int) {
    val durationMinutes: Long
        get() = hours.toLong() * 60 + minutes
}

class EventCreationDraft {
    var name = ""
    var venue = ""
    var startDate = ""
    var endDate = ""
    var startTime = ""
    var endTime = ""
    // Canonical local date and time values are kept separately from display text.
    var startDateIso: String? = null
    var endDateIso: String? = null
    var startTimeIso: String? = null
    var endTimeIso: String? = null
    var layout: DraftLayout? = null
    var phasesInitialized = false
    val phases = mutableListOf<DraftPhase>()
    val channels = mutableListOf<String>()
}

/** The creation flow shares one draft while the process remains alive. */
object EventCreationSession {
    var draft = EventCreationDraft()
        private set

    fun start() {
        draft = EventCreationDraft()
    }
}
