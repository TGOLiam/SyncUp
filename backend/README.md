# Local Supabase for SyncUp

This project runs the Supabase development stack locally. It is for testing,
not a production deployment.

From this directory, install the pinned CLI and start the services:

```bash
npm ci
npx supabase start
```

The local API is available on `http://127.0.0.1:54321`, and Supabase Studio is
available on `http://127.0.0.1:54323` on the computer running Docker. Use
`npx supabase status` to inspect local project connection details. Its output
includes secret development credentials; do not commit or share them.

Stop the local stack with `npx supabase stop`. Check the CLI's current help
before using any reset or no-backup options, which can delete local data.

The Android app accepts `SYNCUP_SUPABASE_URL` and
`SYNCUP_SUPABASE_PUBLISHABLE_KEY` as build environment variables (or the
Gradle properties `syncup.supabase.url` and `syncup.supabase.publishableKey`).
The same property names can be placed in the Git-ignored `local.properties`
for Android Studio builds.
Use only the publishable key, never the service-role or secret key. For a
connected phone or emulator, route its loopback port to the local API:

```bash
adb reverse tcp:54321 tcp:54321
```

Build the debug app with the URL `http://127.0.0.1:54321` and the local
publishable key from `npx supabase status`. The debug build permits HTTP to
loopback only. Do not expose the local development stack to public traffic.
The app requires a configured backend and an account to open the event list.

For local live-audio server setup, see [livekit/README.md](livekit/README.md).

For local voice validation, install FFmpeg and run the separate local service:

```bash
python3 media/run_local.py
```

Keep it running while testing voice. It listens only on `127.0.0.1:54325`;
connect a phone with `adb reverse tcp:54325 tcp:54325` and set
`syncup.voice.url=http://127.0.0.1:54325` in the ignored `local.properties`.
The service uses the local service-role key only on the computer, validates
actual decoded audio, and uploads to a private bucket. It is a development
service and requires a secure deployment and TLS before production use.

The migrations define the shared SyncUp tables, automatic account profiles, and
creator-only phase activation. Signup metadata must include a nonblank `name`.
Row-level security restricts event data to event members and shared profiles to
the user's event teammates. Event setup cannot be modified through direct
client writes. Direct voice-message writes are disabled; the trusted media
service validates and saves voice messages in protected storage. Event creation
and joining functions are available locally.

To apply new migrations to the local stack, run `npx supabase migration up`
from this directory. The schema test uses temporary rows and rolls them back:

```bash
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/shared_entities.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/auth_profiles.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/access_policies.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/event_creation_joining.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/channel_speech_leases.sql
```

`create_event` accepts client-generated IDs and the complete setup, and returns
the event ID and six-digit code. Repeating the same request with the same event
ID returns the original code; a different payload with that ID is rejected.
Start and end timestamps should include a UTC offset. The creator can retrieve a
valid code with `get_event_join_code`. `join_event` returns `JOINED`,
`ALREADY_MEMBER`, `INVALID_CODE`, or `RATE_LIMITED`. Expired and unknown codes
both return `INVALID_CODE`; ten failed attempts in a one-minute window block
further attempts until the window resets. Local codes are retained after expiry
and cannot currently be reused.
