# UI revision — phase decision log

Companion to `UI_REVISION_HANDOFF.md`. Records the decisions required by Phase A before implementation starts.

## Phase A — assets and open decisions (2026-11-20)

### Fonts bundled (`app/src/main/res/font/`)

| File | Family | Weight | Source |
|---|---|---|---|
| `dm_sans_400/500/600/700.ttf` | DM Sans | 400/500/600/700 static instances (opsz pinned at 40) | google/fonts OFL, instanced with fontTools |
| `barlow_condensed_500/600.ttf` | Barlow Condensed | 500/600 upstream statics | google/fonts OFL |
| `noto_sans_mono_400/600.ttf` | Noto Sans Mono | 400/600 static instances (wdth pinned at 100) | google/fonts OFL |
| `public_sans.ttf` | (pre-existing; left untouched pending review) | — | — |

Licenses: verbatim `OFL.txt` copies kept in `docs/ui-design/licenses/`. minSdk 24 requires static instances — variable-font weight axes only resolve on API 26+.

### Decision 1 — monospace family

The approved prototype *never loads* `'Adwaita Mono'`: every monospace rule is `'Adwaita Mono',monospace` with no `@font-face`/import, so rendered appearance is the viewer's system default monospace. The reference therefore demands a neutral OFL mono, not Adwaita glyphs.

**Decision:** bundle **Noto Sans Mono 400/600** as the app's monospace family (including join code at 600 weight). Matches the license model of the other families; genuine 600 available.

### Decision 2 — event-card accent (orange leading rule)

The prototype's rule (`.events .card:nth-of-type(3)`) selects by presentation order only, and the handoff forbids mapping it to "online" or "selected".

**Decision:** **drop the orange leading rule entirely.** No real-data mapping is defensible; cards use the standard outlined card style from the reference.

### Decision 3 — joined-organizer presentation (ref 17)

**Decision:** a **screen within the workspace shell** (launched from the Event Area organizer row), reusing workspace toolbar/background and leaving the voice session untouched.

### Decision 4 — icon set

**Decision:** **Material Symbols vector XML** as the inline `res/drawable/` set for replacing HTML glyphs (◇ ▱ ☷ ⚙ ‹ × ♩). Consistent 24dp grid with the reference's stroke feel; no emoji rendering.

## Carry-in observations from Phase 0

- Current creation flow shows "Step 1 of **6**" / six progress segments; revision expects five steps. Reconcile during Phase D.
- Venue editor validation blocks `Next` on an empty blueprint (confirmed on device).
- Draft resume works (Test Event draft reopened at step 1).
- Build baseline clean; uncommitted message-bubble work frozen as of Phase 0.
