package com.example.sync_up

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import androidx.core.content.ContextCompat

/** Read-only rendering of the editor's normalized venue and zone geometry. */
class WorkspaceLayoutView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var layout: DraftLayout? = null
        set(value) {
            field = value
            requestLayout()
            invalidate()
        }

    private val boundaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_text)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
    }
    private val zoneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x332F6DA8
        style = Paint.Style.FILL
    }
    private val zoneOutline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_navy)
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        strokeJoin = Paint.Join.ROUND
        pathEffect = DashPathEffect(
            floatArrayOf(5f * resources.displayMetrics.density, 4f * resources.displayMetrics.density),
            0f
        )
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.event_text)
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            11f,
            resources.displayMetrics
        )
        textAlign = Paint.Align.LEFT
        isFakeBoldText = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val availableWidth = MeasureSpec.getSize(widthMeasureSpec)
        val ratio = layout?.aspectRatio?.takeIf { it > 0f } ?: 1f
        val desiredHeight = (availableWidth / ratio).toInt().coerceAtLeast(1)
        setMeasuredDimension(availableWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val current = layout ?: return
        if (current.boundary.size >= 3) {
            canvas.drawPath(pathFor(current.boundary), boundaryPaint)
        }
        for (zone in current.zones) {
            if (zone.vertices.size < 3) continue
            val path = pathFor(zone.vertices)
            canvas.drawPath(path, zoneFill)
            canvas.drawPath(path, zoneOutline)
            val x = zone.vertices.minOf { it.x } * width + 8f * resources.displayMetrics.density
            val y = zone.vertices.minOf { it.y } * height + 16f * resources.displayMetrics.density
            canvas.drawText(zone.name, x, y, labelPaint)
        }
    }

    private fun pathFor(points: List<DraftPoint>): Path {
        val path = Path()
        path.moveTo(points.first().x * width, points.first().y * height)
        for (point in points.drop(1)) {
            path.lineTo(point.x * width, point.y * height)
        }
        path.close()
        return path
    }
}
