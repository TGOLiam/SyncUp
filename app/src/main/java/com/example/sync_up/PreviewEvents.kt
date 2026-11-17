package com.example.sync_up

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/** Temporary sample data for the frontend; replace with an event data source later. */
object PreviewEvents {
    const val EXTRA_EVENT_INDEX = "preview_event_index"

    fun load(context: Context): List<EventListItem> {
        val events = mutableListOf<EventListItem>()
        context.resources.getXml(R.xml.preview_events).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "event") {
                    continue
                }

                // Preview attributes can contain either string resources or literal text.
                fun stringAttribute(name: String): String? {
                    val resource = parser.getAttributeResourceValue(null, name, 0)
                    return if (resource != 0) {
                        context.getString(resource)
                    } else {
                        parser.getAttributeValue(null, name)
                    }
                }

                events.add(
                    EventListItem(
                        name = requireNotNull(stringAttribute("name")),
                        detail = requireNotNull(stringAttribute("detail")),
                        isLead = stringAttribute("role") == "lead",
                        phase = stringAttribute("phase"),
                        activePhase = parser.getAttributeBooleanValue(null, "activePhase", false),
                        workspacePhase = stringAttribute("workspacePhase"),
                        phases = stringAttribute("phases")
                            ?.split("|")
                            ?.filter { it.isNotBlank() }
                            ?: emptyList(),
                        channels = stringAttribute("channels")
                            ?.split("|")
                            ?.filter { it.isNotBlank() }
                            ?: emptyList()
                    )
                )
            }
        }
        return events
    }

    /** Builds a separate workspace preview without changing the event creation draft. */
    fun workspaceDraft(context: Context, index: Int): EventCreationDraft? {
        val event = load(context).getOrNull(index) ?: return null
        return EventCreationDraft().apply {
            name = event.name
            channels.addAll(event.channels)
        }
    }
}
