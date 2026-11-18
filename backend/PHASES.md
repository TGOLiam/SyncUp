# Backend integration phases

1. **User profiles — implemented locally:** Create a shared profile when a
   Supabase Auth account is created, requiring a display name and email.
2. **Access policies — implemented locally:** Allow users to access their
   profile and event members to access the event's data. Protect creator-only
   phase activation and preserve read-only setup after creation. Voice-message
   writes remain disabled until protected audio storage is configured.
3. **Event creation and joining — implemented locally:** Create an event and
   its setup atomically; allow an authenticated user to join using a code.
   Codes expire at event end. An account is limited after ten incorrect
   attempts in a one-minute window.
4. **Android authentication:** Add signup, sign-in, session restoration,
   validation, and error states.
5. **Event submission:** Send a completed local draft to Supabase and update
   the SQLite cache after the server confirms creation.

The local Supabase stack is for testing. Drafts remain local, every event member
can access every channel, and only the event creator may activate phases.
