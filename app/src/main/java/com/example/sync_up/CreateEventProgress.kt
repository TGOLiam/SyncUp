package com.example.sync_up

import android.app.Activity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/** Binds the shared five-step indicator used by Create Event screens. */
object CreateEventProgress {
    private const val STEP_COUNT = 5

    fun show(activity: Activity, currentStep: Int, @StringRes title: Int) {
        require(currentStep in 1..STEP_COUNT)

        activity.findViewById<TextView>(R.id.create_progress_step).text =
            activity.getString(R.string.create_progress_step, currentStep, STEP_COUNT)
        activity.findViewById<TextView>(R.id.create_progress_title).setText(title)

        val segments = activity.findViewById<LinearLayout>(R.id.create_progress_segments)
        for (index in 0 until segments.childCount) {
            val color = if (index < currentStep) R.color.event_navy else R.color.event_border
            segments.getChildAt(index).setBackgroundColor(ContextCompat.getColor(activity, color))
        }
    }
}
