package com.example.sync_up

import android.content.Context
import org.xmlpull.v1.XmlPullParser

/** Temporary sample data for the frontend; replace with an event data source later. */
object PreviewEvents {
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
                        previewId = requireNotNull(stringAttribute("id")),
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
            layout = loadLayout(context, index)
        }
    }

    /** Geometry uses normalized coordinates so it scales with the workspace view. */
    private fun loadLayout(context: Context, index: Int): DraftLayout? {
        context.resources.getXml(R.xml.preview_events).use { parser ->
            var eventIndex = -1
            var ratio = 1f
            val boundary = mutableListOf<DraftPoint>()
            val zones = mutableListOf<DraftZone>()
            var points: MutableList<DraftPoint>? = null
            var zoneName: String? = null
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG && parser.name == "event") {
                    eventIndex++
                }
                if (eventIndex != index) continue
                if (parser.eventType == XmlPullParser.END_TAG && parser.name == "event") {
                    return if (boundary.isEmpty()) null else DraftLayout(ratio, boundary, zones)
                }
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "layout" -> ratio = parser.getAttributeValue(null, "aspectRatio").toFloat()
                        "boundary" -> points = boundary
                        "zone" -> {
                            zoneName = requireNotNull(parser.getAttributeValue(null, "name"))
                            points = mutableListOf()
                        }
                        "point" -> points?.add(
                            DraftPoint(
                                parser.getAttributeValue(null, "x").toFloat(),
                                parser.getAttributeValue(null, "y").toFloat()
                            )
                        )
                    }
                } else if (parser.eventType == XmlPullParser.END_TAG && parser.name == "zone") {
                    zones.add(DraftZone(requireNotNull(zoneName), requireNotNull(points)))
                    points = null
                    zoneName = null
                }
            }
        }
        return null
    }
}
