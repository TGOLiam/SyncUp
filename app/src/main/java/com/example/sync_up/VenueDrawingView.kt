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
import kotlin.math.hypot

/** Displays freehand strokes using points relative to the canvas size. */
class VenueDrawingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    private val strokes = mutableListOf<MutableList<Float>>()
    private var editMode = false
    private var draggedPoint = -1
    private var dragOriginal: MutableList<Float>? = null
    private var previousStroke: MutableList<Float>? = null
    private var replacementUndo: MutableList<Float>? = null
    private val editHistory = mutableListOf<MutableList<Float>>()
    fun isEditing(): Boolean = editMode
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_navy)
        style = Paint.Style.FILL
    }
    private val editHandleOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.white)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }
    private val closedStrokes = mutableListOf<Boolean>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_text)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for ((strokeIndex, stroke) in strokes.withIndex()) {
            if (stroke.size < 4) continue
            val path = Path()
            path.moveTo(stroke[0] * width, stroke[1] * height)
            for (index in 2 until stroke.size step 2) {
                path.lineTo(stroke[index] * width, stroke[index + 1] * height)
            }
            if (closedStrokes[strokeIndex]) {
                path.close()
            }
            canvas.drawPath(path, paint)
            if (closedStrokes[strokeIndex] && strokeIndex == strokes.lastIndex) {
                val radius = (if (editMode) 8f else 4f) * resources.displayMetrics.density
                for (index in 0 until stroke.size step 2) {
                    val x = stroke[index] * width
                    val y = stroke[index + 1] * height
                    canvas.drawCircle(x, y, radius, handlePaint)
                    if (editMode) {
                        canvas.drawCircle(x, y, radius, editHandleOutline)
                    }
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (width == 0 || height == 0) return false
        if (editMode) {
            return handleEditTouch(event)
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                parent.requestDisallowInterceptTouchEvent(true)
                previousStroke = strokes.lastOrNull()?.toMutableList()
                editHistory.clear()
                strokes.clear()
                closedStrokes.clear()
                strokes.add(mutableListOf())
                closedStrokes.add(false)
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
                val stroke = strokes.last()
                val startX = stroke[0] * width
                val startY = stroke[1] * height
                val moved = (2 until stroke.size step 2).any { index ->
                    hypot(stroke[index] * width - startX, stroke[index + 1] * height - startY) >=
                        8f * resources.displayMetrics.density
                }
                if (moved) {
                    val simplified = simplifyStroke(stroke, true)
                    if (simplified.size >= 6) {
                        strokes[strokes.lastIndex] = simplified
                        closedStrokes[closedStrokes.lastIndex] = true
                        replacementUndo = previousStroke
                    } else {
                        restorePreviousStroke()
                    }
                } else {
                    restorePreviousStroke()
                }
                previousStroke = null
                invalidate()
                parent.requestDisallowInterceptTouchEvent(false)
                performClick()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                restorePreviousStroke()
                previousStroke = null
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun restorePreviousStroke() {
        strokes.clear()
        closedStrokes.clear()
        previousStroke?.let {
            strokes.add(it)
            closedStrokes.add(true)
        }
    }

    private fun addPoint(x: Float, y: Float) {
        val stroke = strokes.lastOrNull() ?: return
        stroke.add((x / width).coerceIn(0f, 1f))
        stroke.add((y / height).coerceIn(0f, 1f))
        invalidate()
    }

    fun undoLastStroke() {
        if (editHistory.isNotEmpty() && strokes.isNotEmpty()) {
            strokes[strokes.lastIndex] = editHistory.removeAt(editHistory.lastIndex)
            invalidate()
            return
        }
        if (strokes.isNotEmpty()) {
            strokes.clear()
            closedStrokes.clear()
            replacementUndo?.let {
                strokes.add(it)
                closedStrokes.add(true)
            }
            replacementUndo = null
            invalidate()
        }
    }

    fun clearDrawing() {
        strokes.clear()
        closedStrokes.clear()
        editHistory.clear()
        replacementUndo = null
        invalidate()
    }

    /** Enters editing when a completed boundary has enough vertices. */
    fun setEditMode(enabled: Boolean): Boolean {
        if (enabled && !editMode) {
            val stroke = strokes.lastOrNull() ?: return false
            if (stroke.size < 6 || width == 0 || height == 0) {
                return false
            }
        }
        editMode = enabled
        invalidate()
        return true
    }

    private fun simplifyStroke(stroke: List<Float>, closed: Boolean): MutableList<Float> {
        val count = stroke.size / 2
        val tolerance = 8f * resources.displayMetrics.density
        fun distance(a: Int, b: Int): Float = hypot(
            (stroke[a * 2] - stroke[b * 2]) * width,
            (stroke[a * 2 + 1] - stroke[b * 2 + 1]) * height
        )
        // For closed strokes, split at a distant point so the seam is simplified too.
        var split = count - 1
        if (closed) {
            split = (1 until count).maxByOrNull { distance(0, it) } ?: return stroke.toMutableList()
        }
        val points = (0 until count).toMutableList()
        if (closed) points.add(0)
        val keep = BooleanArray(points.size)
        val ranges = ArrayDeque<Pair<Int, Int>>()
        fun addRange(start: Int, end: Int) {
            keep[start] = true
            keep[end] = true
            ranges.add(start to end)
        }
        if (closed) {
            addRange(0, split)
            addRange(split, points.lastIndex)
        } else {
            addRange(0, points.lastIndex)
        }
        while (ranges.isNotEmpty()) {
            val (start, end) = ranges.removeLast()
            val first = points[start] * 2
            val last = points[end] * 2
            val ax = stroke[first] * width
            val ay = stroke[first + 1] * height
            val dx = stroke[last] * width - ax
            val dy = stroke[last + 1] * height - ay
            val lengthSquared = dx * dx + dy * dy
            var furthest = -1
            var greatest = tolerance
            for (position in start + 1 until end) {
                val point = points[position] * 2
                val px = stroke[point] * width - ax
                val py = stroke[point + 1] * height - ay
                val fraction = if (lengthSquared == 0f) 0f else
                    ((px * dx + py * dy) / lengthSquared).coerceIn(0f, 1f)
                val distance = hypot(px - fraction * dx, py - fraction * dy)
                if (distance > greatest) {
                    greatest = distance
                    furthest = position
                }
            }
            if (furthest >= 0) {
                addRange(start, furthest)
                addRange(furthest, end)
            }
        }
        val result = mutableListOf<Float>()
        val end = if (closed) points.lastIndex else points.size
        for (position in 0 until end) {
            if (keep[position]) {
                val index = points[position] * 2
                result.add(stroke[index])
                result.add(stroke[index + 1])
            }
        }
        return result
    }

    private fun handleEditTouch(event: MotionEvent): Boolean {
        val stroke = strokes.lastOrNull() ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val radius = 24f * resources.displayMetrics.density
                var nearest = radius
                draggedPoint = -1
                for (index in 0 until stroke.size step 2) {
                    val distance = hypot(event.x - stroke[index] * width,
                        event.y - stroke[index + 1] * height)
                    if (distance <= nearest) {
                        nearest = distance
                        draggedPoint = index
                    }
                }
                if (draggedPoint < 0) return false
                dragOriginal = stroke.toMutableList()
                parent.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                if (draggedPoint < 0) return false
                stroke[draggedPoint] = (event.x / width).coerceIn(0f, 1f)
                stroke[draggedPoint + 1] = (event.y / height).coerceIn(0f, 1f)
                invalidate()
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    dragOriginal?.let { if (it != stroke) editHistory.add(it) }
                    dragOriginal = null
                    draggedPoint = -1
                    parent.requestDisallowInterceptTouchEvent(false)
                    performClick()
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                dragOriginal?.let { strokes[strokes.lastIndex] = it }
                dragOriginal = null
                draggedPoint = -1
                parent.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return false
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun onSaveInstanceState(): Parcelable {
        val state = Bundle()
        state.putParcelable("super_state", super.onSaveInstanceState())
        state.putBoolean("edit_mode", editMode)
        state.putInt("stroke_count", strokes.size)
        for ((index, stroke) in strokes.withIndex()) {
            state.putFloatArray("stroke_$index", stroke.toFloatArray())
            state.putBoolean("closed_$index", closedStrokes[index])
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
        editMode = state.getBoolean("edit_mode")
        strokes.clear()
        closedStrokes.clear()
        editHistory.clear()
        for (index in 0 until state.getInt("stroke_count")) {
            state.getFloatArray("stroke_$index")?.let {
                strokes.add(it.toMutableList())
                closedStrokes.add(state.getBoolean("closed_$index"))
            }
        }
        invalidate()
    }
}
