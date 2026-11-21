package com.example.sync_up

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat

/** Presentation data for an event card, independent of its data source. */
data class EventListItem(
    val eventId: String,
    val draftId: String? = null,
    val name: String,
    val detail: String,
    val isLead: Boolean,
    val phase: String? = null,
    val activePhase: Boolean = false,
    val workspacePhase: String? = null,
    val phases: List<String> = emptyList(),
    val channels: List<String> = emptyList()
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
            visibility = if (event.draftId != null) View.VISIBLE else View.GONE
            val roleText = when {
                event.draftId != null -> R.string.event_draft
                event.isLead -> R.string.event_lead
                else -> R.string.event_organizer
            }
            val roleColor = if (event.isLead) R.color.ui_orange else R.color.ui_chip_text

            setText(roleText)
            setBackgroundResource(R.drawable.bg_ui_chip)
            setTextColor(ContextCompat.getColor(context, roleColor))
        }

        // Draft rows use the pale wash surface like the reference example 04.
        row.findViewById<com.google.android.material.card.MaterialCardView>(R.id.event_card)
            .setCardBackgroundColor(
                ContextCompat.getColor(
                    context,
                    if (event.draftId != null) R.color.ui_tabs_track else R.color.ui_surface
                )
            )

        row.findViewById<TextView>(R.id.event_phase).apply {
            val phaseColor = if (event.activePhase) R.color.ui_ink else R.color.ui_secondary_text

            text = event.phase
            visibility = if (event.phase == null) View.GONE else View.VISIBLE
            setTextColor(ContextCompat.getColor(context, phaseColor))
        }

        return row
    }
}
