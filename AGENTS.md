# SYNCUP contributor guide

## Project

This is a single-module Android application. Run commands from the project root. Kotlin activities are in `app/src/main/java/com/example/sync_up/`; feature layouts are under `app/src/main/res-features/`, and shared resources are in `app/src/main/res/`. `MainActivity` launches `EventListActivity` after a temporary delay. Check the current code before assuming features are implemented.

Product documents are in `/home/liamb/Documents/3rdYear/mobile/FINAL_PROJ/`. Read `docs/PRD.md` and relevant documents before making product decisions. The PRD specifies this priority when sources disagree: `PROJECT_PROPOSAL.md`, explicit decisions in `docs/wireframes/syncup-wireframe.html`, the PRD, `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT_PLAN.md`, then `docs/UI_DESIGN_PLAN.md`. Follow newer user instructions. The current wireframe references are the two HTML files directly under `docs/wireframes/`: `SyncUp — Low-Fidelity Wireframes.html` and `SyncUp — UI Prototype (reliable theme).html`. Files under `archive/` are historical. Ask the user if the current references conflict or a design decision is needed. The sibling `../SyncUp/docs/` and `../SyncUp-archive-2026-11-11/` are historical references, not necessarily current requirements.

## Collaboration

The user wants to be hands-on and direct the work. Follow their specific instructions; do not independently make product, design, or implementation decisions. Ask the user when a decision or clarification is needed before proceeding.

Always add a Codex Diff at the end of the response.

## Code style

- Write readable, expanded Kotlin, XML, and scripts. Avoid packing multiple steps, attributes, or complex expressions onto one line.
- Add concise comments for important behavior, lifecycle concerns, temporary placeholders, and non-obvious decisions. Keep comments accurate and avoid restating obvious code.
- Follow the existing Android Views and resource conventions; keep names descriptive and formatting consistent.

## Development and verification

- Follow the existing Kotlin, Android Views, XML layouts, and Gradle version catalog conventions unless the task calls for a change.
- Preserve unrelated user changes. Do not commit local SDK paths, credentials, or generated build output.
- Use the checked-in Gradle wrapper. Relevant checks include `./gradlew :app:assembleDebug`, `./gradlew :app:lintDebug`, and `./gradlew :app:testDebugUnitTest`. Run device tests with `./gradlew :app:connectedDebugAndroidTest` when appropriate and a suitable device is available. Report checks that could not run.
- Inspect `app/build.gradle.kts` and `gradle/libs.versions.toml` for current SDK and dependency configuration.

## Commits

When commits are requested, make each one a logical change and keep it under 300 lines added and removed combined. Check the staged diff before committing; split larger work into coherent commits.
