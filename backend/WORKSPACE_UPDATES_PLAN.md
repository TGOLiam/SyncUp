# Workspace updates plan

## Agreed behavior

- Text messages, phase changes, and location reports use one shared polling
  interval: every three seconds, in one combined server request per cycle.
- Polling continues in the background while the user remains connected to an
  event. Messages are polled only for the user's connected channels.
- New messages appear as notifications using a reusable notification to avoid
  creating a separate notification for every message. Global and selected
  channel messages share one text notification, separate from voice status.
- Connecting to a channel loads its 20 most recent text messages.
- The server is authoritative. Offline changes are not queued for later sync.
- Only the event creator can activate a phase. Event setup remains read-only.

## Existing pieces

- Supabase has a messages table and event membership policies, a creator-only
  `activate_event_phase` function, and member zone and report-time fields.
- The workspace Send, Activate, and Report Location actions are placeholders.
- The live audio foreground service already maintains the user's global and
  selected channel connections. Its persistent notification has a disconnect
  action and represents the listening session.

## Reviewable phases

1. Define and test the server contract for one polling cycle, including
   authorization, connected-channel message selection, deterministic message
   ordering, and recovery after an interrupted request.
2. Add confirmed text sending and local message caching. Repeated requests must
   not create duplicate messages.
3. Connect creator-only phase activation and refresh the confirmed phase in the
   local event cache.
4. Connect location reporting to the current member's zone and report time.
5. Add the shared foreground/background polling lifecycle, update the workspace
   from cached results, and deliver new-message notifications without creating
   one notification per message.
6. Test two-device synchronization, channel changes, reconnects, permissions,
   duplicate prevention, and background behavior.
