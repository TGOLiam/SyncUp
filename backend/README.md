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

The Android app is not yet connected to the local stack. An emulator or phone
needs a connection to the computer's API, and the app's debug configuration
must permit local HTTP before that connection can be tested. The CLI development
stack should not be exposed to public traffic.

The migrations define the shared SyncUp tables, automatic account profiles, and
creator-only phase activation. Signup metadata must include a nonblank `name`.
Row-level security restricts event data to event members and shared profiles to
the user's event teammates. Event setup cannot be modified through direct
client writes. Voice-message writes are disabled until protected audio storage
is configured. Event creation and joining functions have not been added yet.

To apply new migrations to the local stack, run `npx supabase migration up`
from this directory. The schema test uses temporary rows and rolls them back:

```bash
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/shared_entities.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/auth_profiles.sql
docker exec -i supabase_db_backend psql -U postgres -d postgres -v ON_ERROR_STOP=1 < supabase/tests/access_policies.sql
```
