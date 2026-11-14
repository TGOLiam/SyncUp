package com.example.sync_up

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.os.Bundle
import android.os.Parcelable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat

/** Displays freehand strokes using points relative to the canvas size. */
class VenueDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val strokes = mutableListOf<MutableList<Float>>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_text)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            if (stroke.size < 4) continue
            val path = Path()
            path.moveTo(stroke[0] * width, stroke[1] * height)
            for (index in 2 until stroke.size step 2) {
                path.lineTo(stroke[index] * width, stroke[index + 1] * height)
            }
            canvas.drawPath(path, paint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                strokes.add(mutableListOf())
                addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                for (index in 0 until event.historySize) {
                    addPoint(event.getHistoricalX(index), event.getHistoricalY(index))
                }
                addPoint(event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP -> {
                addPoint(event.x, event.y)
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex)
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun addPoint(x: Float, y: Float) {
        val stroke = strokes.lastOrNull() ?: return
        stroke.add((x / width).coerceIn(0f, 1f))
        stroke.add((y / height).coerceIn(0f, 1f))
        invalidate()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("super_state", super.onSaveInstanceState())
        state.putInt("stroke_count", strokes.size)
        for ((index, stroke) in strokes.withIndex()) {
            state.putFloatArray("stroke_$index", stroke.toFloatArray())
        }
        return state
    }

    @Suppress("DEPRECATION")
    override fun onRestoreInstanceState(state: Parcelable?) {
        if (state !is Bundle) {
            super.onRestoreInstanceState(state)
            return
        }
        super.onRestoreInstanceState(state.getParcelable("super_state"))
        strokes.clear()
        for (index in 0 until state.getInt("stroke_count")) {
            state.getFloatArray("stroke_$index")?.let { strokes.add(it.toMutableList()) }
        }
        invalidate()
    }
}
