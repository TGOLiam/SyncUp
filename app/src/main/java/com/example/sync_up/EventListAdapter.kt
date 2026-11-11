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
        val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_event, parent, false)
        val event = requireNotNull(getItem(position))
        row.findViewById<TextView>(R.id.event_name).text = event.name
        row.findViewById<TextView>(R.id.event_detail).text = event.detail
        row.findViewById<TextView>(R.id.event_role).apply {
            setText(if (event.isLead) R.string.event_lead else R.string.event_organizer)
            setBackgroundResource(if (event.isLead) R.drawable.event_role_chip else R.drawable.event_organizer_chip)
            setTextColor(ContextCompat.getColor(context, if (event.isLead) R.color.event_navy else R.color.event_muted))
        }
        row.findViewById<TextView>(R.id.event_phase).apply {
            text = event.phase
            visibility = if (event.phase == null) View.GONE else View.VISIBLE
            setTypeface(null, if (event.activePhase) Typeface.BOLD else Typeface.NORMAL)
            setTextColor(ContextCompat.getColor(context, if (event.activePhase) R.color.event_text else R.color.event_muted))
        }
        return row
    }
}
