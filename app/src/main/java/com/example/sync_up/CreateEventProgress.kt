package com.example.sync_up

import android.app.Activity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat

/** Binds the five-step creation indicator from the approved prototype. */
object CreateEventProgress {
    private const val STEP_COUNT = 5

    fun show(activity: Activity, root: View, currentStep: Int, @StringRes title: Int) {
        require(currentStep in 1..STEP_COUNT)

        root.findViewById<TextView>(R.id.create_progress_step).text =
            activity.getString(R.string.create_progress_step, currentStep, STEP_COUNT)
        root.findViewById<TextView>(R.id.create_progress_title).setText(title)

        val segments = root.findViewById<LinearLayout>(R.id.create_progress_segments)
        require(segments.childCount == STEP_COUNT)
        for (index in 0 until segments.childCount) {
            val color = if (index < currentStep) R.color.ui_orange else R.color.ui_divider
            segments.getChildAt(index).setBackgroundColor(ContextCompat.getColor(activity, color))
        }
    }
}
