package com.example.sync_up

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat

/**
 * Condensed 24sp dialog heading for the revised dialog surfaces (refs 09,
 * 11, 13, 18). The typeface is set directly because the bundled font
 * family does not resolve through setTextAppearance on the dialog-title
 * path on every OS level.
 */
internal fun uiDialogTitle(context: Context, resId: Int): View {
    val density = context.resources.displayMetrics.density
    val title = TextView(context)
    title.setText(resId)
    title.typeface = ResourcesCompat.getFont(context, R.font.barlow_condensed_600)
    title.textSize = 24f
    title.letterSpacing = -0.02f
    title.setTextColor(context.getColor(R.color.ui_ink))
    // Custom titles sit outside the dialog body padding; mirror the 20dp
    // used by the dialog content so the heading lines up with the text below.
    title.setPadding(Math.round(20 * density), Math.round(20 * density), Math.round(20 * density), Math.round(4 * density))
    return title
}
