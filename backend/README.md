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

SyncUp tables, policies, and migrations have not been added yet.
