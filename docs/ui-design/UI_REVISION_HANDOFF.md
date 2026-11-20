# SyncUp UI revision handoff

Status: approved visual direction; implementation plan. Prepared 2026-11-20.

## 1. Reference and scope

- Primary reference: [approved Field Operations prototype](syncup-field-operations.html). Use its final rendered appearance: later style blocks override earlier experiments.
- Comparison only: [original sketch](syncup-current-sketch.html) and [first redesign](syncup-redesign.html).
- Apply the visual revision to existing Android Views/XML. Preserve working authentication, SQLite drafts/cache, event submission/joining, polling, location reporting, and LiveKit behavior.
- The approved direction is warm, restrained, and operational: paper-colored surfaces, deep green, signal orange, condensed headings, compact metadata, and low corner radii.
- This document does not authorize replacing Android Views with Compose or changing backend contracts.
- Joined-organizer UI is an approved prototype addition that still needs Android implementation. Treat it separately from purely visual edits.
- Sample names, event dates, join codes, initials, messages, durations, channel lists, and membership counts are illustrative. Bind actual app data.

## 2. Final decisions to preserve

1. Text-only lowercase **syncup** wordmark. `sync` is deep green; `up` is signal orange. No slash, standalone logo symbol, or gap between words.
2. Wordmark replaces “My Events” in the event-list toolbar. Settings sits at the far right.
3. Event-list section label is exactly **Your Events**. The larger greeting remains **Let’s get to work.**
4. Event setup remains five steps: Event Details → Venue Layout → Event Phases → Channels → Review, followed by creation success/code.
5. Phase entries have no numeric prefixes. Names and durations remain visible.
6. Workspace navigation remains Event Area / Comms / Phases within the existing workspace activity.
7. Venue geometry preserves its stored aspect ratio and receives substantial screen space. The sample ratio is 1.3:1, not a universal event ratio.
8. Event Area exposes the joined-organizer count and list. Membership does not imply online or currently listening.
9. Comms uses two rows: Global plus one selected custom channel. There is no long expanded channel list.
10. Both channel-name surfaces have matching dimensions and pale fills. Only the custom channel has a dropdown chevron.
11. No connection circles. Explicit **Connect / Disconnect** buttons remain, with matching outlined styling for both states.
12. Channel picker has a distinct shaded trailing chevron area, a searchable bottom sheet, and a selected-row marker.
13. Composer tabs select the conversation. They are separate from channel connection controls.
14. No fade transitions between workspace/creation sections. Preserve the shared toolbar and background.

## 3. Typography and font assets

Bundle fonts in `app/src/main/res/font/` so typography works offline and never depends on Google Fonts fetching at runtime. Include distribution licenses. Use genuine weights rather than synthesized bold.

| Family | Usage | Weights | Implementation notes |
|---|---|---|---|
| DM Sans | Body, labels, buttons, event names, toolbar titles, message content, wordmark | 400, 500, 600, 700 | Primary app typeface. Prototype loads Google Fonts; Android must bundle assets. |
| Barlow Condensed | Prominent headings, dialog titles, success title, avatar initials | 600; 500 available | Preserve condensed character rather than substituting the body family. |
| Adwaita Mono / monospace | Small metadata, progress labels, phase chips, report headings, join code | 400; join code 600 | Prototype uses a Linux-installed family with generic monospace fallback. Exact redistributable Android asset and license must be resolved before typography sign-off. Do not silently assume every browser used Adwaita Mono. |
| Cantarell / generic sans-serif | Browser fallback only | — | Not a deliberate fourth Android font family. |

### Type scale from the prototype

Sizes below are reference CSS px. Start with equivalent Android sp, then validate density, font metrics, and accessibility scaling. Do not scale the whole HTML screenshot as an image.

| Element | Family / weight | Reference size / treatment |
|---|---|---|
| Toolbar wordmark | DM Sans 700 | 27; letter spacing -0.065em |
| Loading wordmark | DM Sans 700 | 40; same tracking and two-color split |
| Auth headline | Barlow Condensed 600 | 36; line height 1.1; maximum text width about 230 |
| Generic screen headline | Barlow Condensed 600 | 28; line height 1.1; tracking -0.02em |
| Event-list greeting | Barlow Condensed 600 | 32; tracking -0.02em |
| Toolbar title | DM Sans 700 | 16 |
| Event title on list | DM Sans 600 | 16; wrap as necessary |
| Body / message content | DM Sans 400 | 14; body line height about 1.45 |
| Input values | DM Sans 400 | 13 |
| Form labels / ordinary buttons | DM Sans 600 | 12 |
| Supporting text | DM Sans 400 | 12 |
| Auth supporting copy | DM Sans 400 | 13 |
| Connection actions | DM Sans 600 | 11 |
| Incoming/outgoing message metadata | DM Sans 400 | 11 |
| Role chips / creation progress / report heading | Monospace | 10; heading tracking about 0.1em |
| Workspace event context | Monospace | 9; uppercase; tracking 0.1em |
| Happening-now caption | Monospace | 9; tracking 0.07em |
| Bottom navigation labels | DM Sans | 10; selected 700 |
| Dialog titles | Barlow Condensed 600 | 24 |
| Channel-sheet title | Barlow Condensed 600 | 26 |
| Join code | Monospace 600 | 40; tracking 5 reference px |
| Organizer initials | Barlow Condensed | 18 |

Small metadata in the specimen needs accessibility review on actual devices. Preserve hierarchy, but do not force essential text into unreadable 9–10sp labels. Resolve any size increase during device review and record the change. Use sp for text and dp for geometry. Android `letterSpacing` is em-based; 5px join-code tracking at 40px is approximately 0.125em.

## 4. Color tokens

Define semantic Android color resources rather than embedding literal colors throughout XML/Kotlin. The old navy/blue theme must not leak into revised surfaces.

| Suggested token | Hex | Usage |
|---|---|---|
| `ui_ink` | `#222A28` | Primary text |
| `ui_surface` | `#FFFEFA` | App backgrounds, toolbars, dialogs, footer, wordmark surroundings |
| `ui_green` | `#213B35` | Primary green buttons, active phase, outgoing messages, `sync` wordmark |
| `ui_orange` | `#B63E20` | `up` wordmark, primary creation/auth/PTT actions, selected nav rule, progress |
| `ui_muted` | `#717974` | General muted color token |
| `ui_secondary_text` | `#65756A` | Supporting text |
| `ui_divider` | `#D5D8CE` | Separators, basic card borders |
| `ui_paper` | `#F2F0E9` | Desktop design-board background; not the default phone surface |
| `ui_wash` | `#E8EBE3` | Neutral supporting surface token |
| `ui_input` | `#FCFDF9` | Form field fill |
| `ui_input_border` | `#CBD3C7` | Form field outline |
| `ui_input_text` | `#68786D` | Specimen input values; check entered-value contrast |
| `ui_action_outline` | `#B7C5B5` | Outlined action border |
| `ui_chip` | `#E7ECE1` | Role chip fill |
| `ui_chip_text` | `#345241` | Role chip text |
| `ui_channel_surface` | `#F3F6EE` | Both channel-name surfaces |
| `ui_channel_border` | `#DCE2D5` | Both channel-name borders |
| `ui_chevron_surface` | `#E8EEE0` | Trailing dropdown affordance and selected channel-picker row |
| `ui_chevron_divider` | `#D4DDCC` | Chevron area left border |
| `ui_chevron` | `#36523B` | Dropdown stroke |
| `ui_tabs_track` | `#E9EEE2` | Composer segment track |
| `ui_incoming` | `#EDF1E6` | Incoming message fill |
| `ui_incoming_meta` | `#637361` | Incoming metadata |
| `ui_outgoing_meta` | `#C3D2BD` | Outgoing metadata |
| `ui_outgoing_text` | `#F6F7EF` | Outgoing message text |
| `ui_selected_nav` | `#F0F3EB` | Selected bottom-nav fill |
| `ui_nav_text` | `#758176` | Unselected navigation labels |
| `ui_phase_caption` | `#C4D2BD` | Happening-now caption on green |
| `ui_phase_secondary` | `#C2D4BD` | Active phase secondary text |
| `ui_avatar` | `#DCE6D1` | Organizer initial tile |
| `ui_success` | `#DFE9D4` | Creation success mark background |
| `ui_success_text` | `#294D32` | Creation success check |
| `ui_scrim` | `#22362A` at 50% | Modal/bottom-sheet dim layer; Android ARGB approximately `#8022362A` |

### Venue palette

| Element | Hex / treatment |
|---|---|
| Canvas | `#F1F3E9` |
| Canvas outline | `#C8D2BF` |
| Boundary fill | `#FCFDF8` |
| Boundary stroke | `#364C3B` |
| Zone fill | `#DCE7D2` |
| Zone stroke | `#72876A`; dashed in sample |
| Zone text | `#3E5C3E` |

The HTML contains obsolete green/red connection-dot CSS. Those dots were explicitly removed and must not be implemented. Error, disabled, pressed, focus, and reconnect colors require accessible mappings; preserve existing functional error states and review their new styling rather than using brand orange indiscriminately as an error color.

## 5. Geometry, shared components, and Android adaptation

- Specimen phone: 350 × 748 CSS px. This is a presentation frame, not a fixed Android viewport.
- Android must fill the available window, respect status/navigation bar insets, and adapt to smaller widths and larger text.
- Phone-frame border, rounded device corners, fake clock/status symbols, shadows, chapter numbers, desktop hero, screen labels, collection filters, and brand comparison cards are design-board chrome. Do not reproduce them inside the app.
- Reference toolbar content height: 60dp, body horizontal inset: 16dp, body vertical inset: 16dp, general gap: 12dp.
- Common forms: label/field gap 8dp; field minimum visible height 48dp; field padding 12dp; border 1dp; radius 4dp.
- Buttons: reference visible height 44dp; radius 4dp; outlined border 1dp; horizontal padding 12dp. Bottom paired actions have equal weights and a 10dp gap.
- On Android provide at least 48 × 48dp effective touch targets, including visually smaller controls. Do not shrink tap targets to copy browser pixels.
- Footer: 12dp vertical / 16dp horizontal padding; top divider; remains reachable above system navigation and the IME as appropriate.
- Most content uses flat rows with bottom dividers, not elevated cards. Event-list cards are the deliberate outlined exception: 16dp padding and 4dp corners.
- Role badge: 3dp corners, 5dp vertical / 7dp horizontal padding; low emphasis.
- Dialog: surface color, 8dp corners, 20dp padding, 16dp content gaps; dim scrim; scroll if needed.
- Organizer initial tile: 36 × 36dp, 4dp corners; 12dp gap to text.
- Workspace navigation: three equal destinations, reference 64dp content height plus bottom inset; 20dp icons; selected green text, pale fill, 3dp orange top indicator.
- Replace HTML glyphs (`◇`, `▱`, `☷`, `⚙`, `‹`, `×`, `♩`) with consistent Android vector icons. Retain meaning and stroke consistency. Do not use platform emoji rendering.
- Keep reference light surfaces when the device uses dark mode until a dark palette is separately designed. Avoid accidental DayNight field/dialog recoloring.
- Allow wrapping for names, headings, role badges, dates, and translated strings. Ellipsize channel labels within the selector, leaving the chevron and connection action visible.
- No horizontal scrolling for normal app forms. Only genuinely large lists/content regions scroll.

## 6. Complete screen and state inventory

The following numbers identify prototype examples only; they are not UI labels.

| Ref | Screen / state | Required presentation and behavior |
|---|---|---|
| 01 | Loading | Centered selected wordmark, “Loading…” beneath. Native system bars. Preserve existing auth/session routing. |
| 02 | Sign in | Toolbar wordmark; “Welcome back.”; supporting copy; Email, Password; orange Sign in; text Create an account. Existing validation, submit progress, and errors remain. |
| 03 | Create account | Same shell; “Your team starts here.”; supporting copy; Name, Email, Password; orange Create account; text sign-in link. No email-confirmation step for the configured demo. |
| 04 | Your Events | Wordmark left/settings right; Your Events eyebrow; Let’s get to work.; draft and event cards; Join Event outlined/Create Event orange fixed bottom pair. |
| 05 | Settings/sign out | Same event-list shell under dim overlay; Sign out menu action. The mockup's simplified background must use the same actual list styling as 04. No new settings screen implied. |
| 06 | Join event | Back/title; Enter event code; code-help copy; six-digit numeric entry; Join. Preserve invalid code, rate-limit, network, and join-progress handling. |
| 07 | Details | Shared Create Event toolbar; Step 1 of 5 and five-segment progress; required name, venue, start/end date fields; Back/Next footer. |
| 08 | Venue editor | Step 2; large proportional canvas; Draw/Edit/Undo/Simplify; Clear drawing; Add Zone; Back/Next. Preserve geometry interactions and selected edit state. |
| 09 | Add zone | Editor dimmed beneath Add zone dialog; zone name; Cancel/Save; required-name validation. |
| 10 | Phase setup | Step 3; date, start/end time; phase rows with name and duration, no numeric prefix; Add phase; Back/Next. Preserve editing/removal affordances supported by the app. |
| 11 | Add/edit phase | Phase label and Hours/Minutes fields; Cancel/Save; duration validation. Reuse dialog for editing with actual values. Toolbar remains Create Event; the event-context line accidentally present in this specimen is a mockup inconsistency. |
| 12 | Channels | Step 4; immutable Global row; custom channel names and remove actions; Add custom channel; Back/Review. No member/channel assignment form. |
| 13 | Add channel | Channel name dialog; Cancel/Save; duplicate/required-name handling as supported. Underlying step-4 footer stays Back/Review even though the simplified dialog specimen says Next. |
| 14 | Review | Step 5; heading/supporting copy; event detail/date/time summary; Venue & zones, Event phases, Channels sections with Edit controls; join-code note; Back/Create Event. |
| 15 | Created/code | Centered success check; actual event-name success heading; sharing guidance; large join code; Copy code; Open event workspace. Do not invent a Share button absent from this revision. |
| 16 | Event Area | Event context/section toolbar; green Happening now banner; Organizers/count entry; large ratio-preserving venue; Report log; bottom Report my location; persistent navigation. |
| 17 | Joined organizers | Organizer title and member count; rows with initials, names, You/Event Lead markers, last reported zone/time or no-location state; manual-location caveat; back action. |
| 18 | Report location | Event Area beneath dim layer; zone choices and Cancel. Preserve actual confirmation/submission behavior and error feedback. |
| 19 | Comms/Global | Two balanced connection rows; Global conversation selected; incoming/outgoing messages; composer segment tabs; input/Send; orange PTT; persistent nav. |
| 20 | Comms/custom | Same controls; chosen custom channel selected and connected; empty history example; composer and PTT use its actual name. |
| 21 | Phases/creator | Completed rows with check; active green row/orange accent and start time; upcoming rows with Activate; creator-only help; persistent nav. |
| 22 | Phases/organizer | Same phase appearance; no Activate controls for noncreator; explanatory help. Always render the full actual phase list, not only the abbreviated sample. |

### Event list details

- Draft: event name or Untitled event, Draft badge, Tap to continue setup. Tapping resumes the saved step.
- Event card: name, creator/organizer badge, venue/date, current phase or No phase active yet. Keep `(Preview)` in preview event names.
- Orange leading rule highlights the example event. The reference does not define a universal rule for which real event receives it. Resolve that mapping before applying it to arbitrary cards; do not assume “online” or “currently selected.”
- Settings target is 44px in HTML; Android touch target at least 48dp. Position at toolbar end without moving the wordmark off its left inset.

### Venue details

- Workspace height follows `availableWidth / storedAspectRatio`. Preview XML currently uses 1.3. Do not hardcode a universal 200dp map height.
- Preserve normalized vertices, boundary/zone shapes, labels, and proportion through creation, review, and workspace rendering.
- The sample editor reserves at least 340 reference px, then consumes remaining height. On Android distribute available space without forcing a minimum that hides actions on small screens.
- Report log can scroll below the canvas; the location action and workspace navigation stay reachable.
- No GPS, fabricated organizer map pins, or live position tracking is introduced.

## 7. Communications specification: final version

### Connection panel

- One vertical panel, 4dp top / 16dp bottom padding, 8dp row gap, one bottom divider. No divider between the two rows.
- Each row: flexible channel surface, 12dp column gap, 90dp reference action column. Both surfaces/actions are 44dp visible height with expanded Android touch targets.
- Channel surface: pale `#F3F6EE`, border `#DCE2D5`, radius 5dp, 12dp left padding, DM Sans 600 at 13sp.
- Global is a static label with the same geometry as the custom selector; it must not open a picker.
- Custom label is ellipsized as needed. The dropdown’s rightmost 36dp region is `#E8EEE0`, with a 1dp left separator `#D4DDCC`; dark downward chevron has a 2dp stroke and approximately 7dp legs.
- Connect and Disconnect both use transparent fill, `#B7C5B5` outline, green 11sp semibold text, and 5dp radius. No dark-filled Connect action and no status circles.
- Button text reflects confirmed connection state; preserve connecting/failure/retry handling. A mock toggle is not a substitute for LiveKit state.
- Global and one custom channel are independently connectable. Switching custom channel while connected transfers that connection using existing service rules. Avoid transiently showing a successful connection before it exists.

### Custom channel picker

- Opens from the custom-channel surface only. Modal bottom sheet, dim scrim, surface fill, 16dp top corners, 16dp horizontal / 20dp bottom padding.
- Maximum height around 86% of available window; respect IME and system insets. Sheet contents can scroll.
- Handle: 32 × 4dp; heading Choose a channel; 44px reference close action expanded to Android touch minimum.
- Guidance: Choose one custom channel to listen to alongside Global.
- Search field with visible hint Search channels, accessible label, clear/search keyboard behavior. Filter only this event’s actual custom channels.
- Channel rows: reference minimum 48dp; 14dp vertical / 8dp horizontal padding; name left; Selected/check marker right; selected pale surface. No Global entry in this custom list.
- No matches: No channels match your search. Handle zero custom channels without inventing channels.
- Select, close, outside dismissal, and system Back work. Restore focus to selector after closing. Membership/channel access remains server-controlled.

### History and composer

- History label follows viewed channel. Incoming messages left, up to 88% width, pale fill; outgoing right, green fill, content width.
- Bubble padding 12dp. Incoming corners TL/TR/BR/BL: 4/10/10/10dp; outgoing: 10/4/10/10dp.
- Metadata is sender name and local time; message body 14sp. Bind You for the current sender. Long messages wrap.
- Empty: No messages yet. Initial recent history remains limited to 20 messages per connected channel under the existing contract.
- Composer footer: 12dp vertical / 16dp horizontal padding, 8dp gaps, top divider.
- Tabs: equal widths, 4dp track inset/gap, pale track, selected surface fill. Global versus actual selected custom-channel name. Existing disconnected/unavailable guards remain in effect.
- Input and Send share a row; input expands, Send stays visible when keyboard opens. Preserve pending-send and failure feedback.
- Orange PTT action: reference height 56dp; Hold to talk · [viewed channel]. Maintain server permission-before-transmit, 10-second maximum, busy cooldown, release/cancel and lifecycle cleanup.
- Listening remains a background service until disconnected. Preserve notification channels, reusable text-message notification, and runtime permission handling. This visual revision does not change audio transport or add recorded-voice history.

## 8. Organizers: implementation handoff

- Read event members from the existing `poll_workspace` response’s `locations` array: member ID, user ID, name, current zone ID, report time. Include members without a location.
- Reuse existing three-second polling; no extra per-row requests or new polling endpoint.
- Derive You from authenticated user ID and Event Lead from event creator ID. Do not add roles/responsibilities/permission fields removed earlier.
- Count all joined members, including creator. Resolve zone IDs to actual zone names.
- The screen must render its current data immediately and update while open; provide loading, empty, unavailable, and stale-cache handling.
- A person being joined says nothing about online status, voice connection, or location freshness. Do not show online badges from this data.
- Back returns to Event Area without resetting the event or voice session. Keep current workspace navigation/lifecycle ownership.

## 9. State, accessibility, and motion requirements

- Retain existing auth validation, expired session, failed join/submit, missing layout/phases, speech denied, disconnected, busy, reconnecting, polling failure, draft persistence failure, and notification/microphone permission states.
- Form errors must identify the field and remain readable. Loading actions prevent duplicate submissions without losing entered values.
- Preserve local drafts through navigation/restarts. “Saved draft” must reflect an actual successful save, not decorative UI text.
- Keyboard must not obscure focused fields or Send. Test multiline messages and large text. Dialogs scroll instead of clipping.
- Android TalkBack: label settings, back, remove, edit, dropdown, close, zone actions and PTT. Expose selected navigation/tab/channel states and correct reading order.
- Meaning must not depend on color alone; active/completed/upcoming and Connect/Disconnect are textual.
- Check text contrast at least 4.5:1 for ordinary text and 3:1 for large text; check control boundaries and focus indication. Increase weak tiny metadata or adjust tokens with review rather than silently changing branding.
- Use concise state announcements for send results, connection changes, and location reports. Do not announce every polling cycle.
- Respect reduced motion/system animation preferences. Preserve common toolbar during section switching. Subtle translation is acceptable; no fades, looping decoration, flashing status, or surprise audio.
- Prototype board reveal (8px/350ms), hover styling, desktop filters, and wordmark comparison interactions are not native app requirements.

## 10. Android change map

| Area | Primary existing files |
|---|---|
| Theme/tokens/fonts | `app/src/main/res/values/{colors,styles,themes,strings}.xml`, new `res/font/` and shared vector/drawable resources |
| Loading/auth | `MainActivity.kt`, `AuthActivity.kt`, `res-features/loading/layout/activity_main.xml`, `res-features/auth/layout/activity_auth.xml` |
| Events/settings | `EventListActivity.kt`, `EventListAdapter.kt`, `res-features/event-list/layout/{activity_event_list,item_event}.xml` |
| Join | `JoinEventActivity.kt`, `res-features/join-event/layout/activity_join_event.xml` |
| Creation | `CreateEventActivity.kt`, `CreateEventProgress.kt`, `res-features/create-event/layout/` sections, row layouts, dialogs |
| Venue drawing | `VenueDrawingView.kt`, `WorkspaceLayoutView.kt`; change appearance without altering geometry algorithms |
| Workspace shell | `WorkspaceActivity.kt`, `res-features/workspace/layout/activity_workspace.xml` |
| Area/phases/comms | `section_workspace_area.xml`, `section_workspace_phases.xml`, `section_workspace_comms.xml`, `item_comms_channel.xml`, related Kotlin view binding/rendering |
| Organizer view | Add a section/sheet within the workspace architecture after agreeing the navigation presentation; reuse `WorkspaceSnapshot` member data |
| Polling/audio | Existing `LiveAudioService` and remote clients: preserve behavior; adjust view-state plumbing only when necessary |

Inspect the repository before edits; these paths describe the current structure, not a requirement to create unused styles or classes. Preserve unrelated work and existing uncommitted message-bubble changes.

## 11. Reviewable implementation phases

### Phase A — Handoff and assets

- Confirm font files/licensing, monospace rendering, Android icon set, event-card accent mapping, and organizer navigation presentation.
- Capture current phone/VM screenshots and baseline build status. Keep the approved HTML available during review.
- Exit: assets and unresolved adaptations recorded; no functional redesign mixed into foundational styling.

### Phase B — Shared visual foundation

- Add semantic colors, bundled font families, type styles, fields, buttons, chips, dialogs, vector icons, and the two-color wordmark.
- Validate light appearance under both system themes, system insets, contrast, and font scaling.
- Exit: representative components match reference and build/lint checks pass where available.

### Phase C — Loading, authentication, event list, joining

- Implement examples 01–06, including header branding, Your Events label, settings alignment, draft/preview cards and footers.
- Exercise session restoration, signup/sign-in, draft resume, settings/sign-out and code joining.
- Exit: screenshots approved at default and large text; behavior unchanged.

### Phase D — Creation and venue

- Implement examples 07–15, five-step progress, proportional editor, unnumbered phases, channel rows/dialogs, review and code success.
- Verify date/time inputs, validation, Back, draft resume, shape editing, and server-confirmed creation.
- Exit: no clipped controls on phone/VM, venue geometry remains proportional, all creation steps match.

### Phase E — Workspace area, organizers, phases

- Implement examples 16–18 and 21–22, navigation styling, proportional map, report log/action, organizer screen and phase states.
- Verify organizer membership updates and absent-location state; creator-only activation; location reporting and return navigation.
- Exit: two-device data behavior and screenshots approved; voice survives section navigation.

### Phase F — Communications

- Implement examples 19–20, final balanced selectors/chevron, searchable picker, connected state actions, messages, tabs/composer/PTT.
- Exercise Global only, custom only, both, neither, custom switching, empty search/history, long labels, IME, reconnect and busy PTT.
- Exit: selectors visibly aligned; no circles; no regression to channel lists or hidden connection actions.

### Phase G — Final UI QA and handoff

- Compare every inventory row against phone and VM screenshots. Check 320/350/390/430dp-equivalent widths, landscape, large text, IME, TalkBack, and system themes.
- Run appropriate `:app:assembleDebug`, `:app:lintDebug`, and relevant unit/device checks. Report unavailable checks rather than implying coverage.
- Complete two-device smoke flow: sign in → create/join → report zone → send text → live PTT → activate phase → background/reconnect.
- Record approved deviations, remaining issues, and screenshots. Remove obsolete experimental drawables/styles only after references are checked.
- When commits are requested, keep each logical commit below 300 added/deleted lines combined; stage and inspect only intended work.

## 12. Definition of done

- [ ] Selected two-color wordmark is consistent on loading, auth and event list.
- [ ] Both event-list states use the same header/card system; settings is right-aligned; Your Events uses exact case.
- [ ] Five creation steps, dialogs, review, success and all existing error states are accounted for.
- [ ] No phase numbers or stale organizer-assignment fields return.
- [ ] Stored venue ratio is preserved at every display size.
- [ ] Joined organizers are visible using actual member data; no false presence claims.
- [ ] Final connection selector has matched rows, outlined action buttons, no circles, and the distinct chevron area.
- [ ] Real connection state, history tabs, composer, PTT and background notifications still work.
- [ ] Fonts work offline; licenses are included; vector icons replace text glyphs.
- [ ] Accessibility, insets, keyboard and large-text behavior pass device review.
- [ ] App-only design is implemented; board presentation and alternate brand experiments are excluded.
- [ ] Remaining adaptations are explicitly approved rather than inferred from CSS accidents.
