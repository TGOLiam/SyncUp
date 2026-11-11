package com.example.sync_up

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/** Temporary sample data for the frontend; replace with an event data source later. */
object PreviewEvents {
    fun load(context: Context): List<EventListItem> {
        val events = mutableListOf<EventListItem>()
        context.resources.getXml(R.xml.preview_events).use { parser ->
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "event") continue
                fun stringAttribute(name: String): String? {
                    val resource = parser.getAttributeResourceValue(null, name, 0)
                    return if (resource != 0) context.getString(resource) else parser.getAttributeValue(null, name)
                }
                events.add(
                    EventListItem(
                        name = requireNotNull(stringAttribute("name")),
                        detail = requireNotNull(stringAttribute("detail")),
                        isLead = stringAttribute("role") == "lead",
                        phase = stringAttribute("phase"),
                        activePhase = parser.getAttributeBooleanValue(null, "activePhase", false)
                    )
                )
            }
        }
        return events
    }
}
