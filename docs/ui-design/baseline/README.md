# UI revision baseline capture

Date: 2026-11-20. Device: physical phone, 1080 × 2400 @ 440dpi. Build: `:app:assembleDebug` BUILD SUCCESSFUL (all tasks up-to-date), APK installed and launched.

## Screen inventory captured

| Ref | File | Notes |
|---|---|---|
| 04 | `04_event_list.png` | Old "My Events" toolbar, no greeting, no drafts visible; navy Create Event; 4 event cards (incl. two Preview, one Event Lead badge) |
| 05 | `05_settings_overlay.png` | Sign out actionsheet over dimmed list |
| 06 | `06_join_event.png` | Single large code input, navy Join |
| 07 | `07_create_details.png` | Auto-resumed "Test Event" draft (draft resume confirmed); "Step 1 of 6" — current app has SIX segments, revision expects five steps |
| 08 | `08_venue_editor.png` | Empty blueprint; Draw/Edit/Undo/Simplify + Clear drawing + Add Zone; Back/Next |
| 16 | `16_event_area.png` | Barangay Fun Run (Preview) workspace; zone map Start/Finish; Report log empty; no organizer count row yet (approved additions not implemented) |
| 19 | `19_comms_global.png` | OLD comms layout: expanded channel list (Global/Registration/Course Team) with green/red phone icons, "Not connected" segment state, composer + PTT |
| 21 | `21_phases.png` | Four upcoming phase cards, no Activate (non-creator view? verify); "Only Event Leads can change the active phase." |

## Not captured (with reason)

- **01 loading / 02 sign in / 03 create account** — requires sign-out of the signed-in session; skipped to avoid logging out without a password at hand.
- **09 add zone / 11 add-edit phase / 13 add channel dialogs** — step 2 progress is validated: Next is blocked with an empty blueprint; capturing steps 3–5 would require drawing a boundary (user elected to skip).
- **10 phase setup / 12 channels / 14 review / 15 created-code** — same validation blocker as above.
- **17 joined organizers / 18 report location** — organizer screen does not exist yet in the app; report dialog requires tapping Report my location (state-revealing but skipped; trivial to capture later).
- **20 comms/custom / 22 phases-organizer variants, active phase row** — would require connecting LiveKit / activating phases, which mutates event state.

## Observations vs. approved direction

- Old navy/blue theme is pervasive (buttons, progress).
- Comms still uses the expanded channel list + colored connection icons — the exact pattern the revision removes.
- Creation flow shows "Step 1 of 6" / six progress segments; revision says five steps.
- Phase rows currently plain cards; no numeric prefixes visible (good).

## Frozen baseline (uncommitted work preserved)

`WorkspaceActivity.kt`, `section_workspace_comms.xml`, `colors.xml`, `strings.xml` modified; `comms_message_incoming.xml` / `comms_message_outgoing.xml` untracked — existing message-bubble work to be preserved, not reverted.
