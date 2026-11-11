package com.example.sync_up

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Presentation data for an event card, independent of its data source. */
data class EventListItem(
    val name: String,
    val detail: String,
    val isLead: Boolean,
    val phase: String? = null,
    val activePhase: Boolean = false
)

class EventListAdapter(context: Context, events: List<EventListItem>) :
    ArrayAdapter<EventListItem>(context, 0, events) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        // ListView passes an existing row when possible to avoid inflating every card again.
        val row = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_event,
            parent,
            false
        )
        val event = requireNotNull(getItem(position))

        row.findViewById<TextView>(R.id.event_name).text = event.name
        row.findViewById<TextView>(R.id.event_detail).text = event.detail

        // Reset all role and phase properties because this row may have shown another event.
        row.findViewById<TextView>(R.id.event_role).apply {
            val roleText = if (event.isLead) R.string.event_lead else R.string.event_organizer
            val roleBackground = if (event.isLead) {
                R.drawable.event_role_chip
            } else {
                R.drawable.event_organizer_chip
            }
            val roleColor = if (event.isLead) R.color.event_navy else R.color.event_muted

            setText(roleText)
            setBackgroundResource(roleBackground)
            setTextColor(ContextCompat.getColor(context, roleColor))
        }

        row.findViewById<TextView>(R.id.event_phase).apply {
            val phaseColor = if (event.activePhase) R.color.event_text else R.color.event_muted
            val phaseStyle = if (event.activePhase) Typeface.BOLD else Typeface.NORMAL

            text = event.phase
            visibility = if (event.phase == null) View.GONE else View.VISIBLE
            setTypeface(null, phaseStyle)
            setTextColor(ContextCompat.getColor(context, phaseColor))
        }

        return row
    }
}
