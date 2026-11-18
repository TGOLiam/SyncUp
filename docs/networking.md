# Supabase client configuration

The Android app reads these values at build time:

- `SYNCUP_SUPABASE_URL`: the Supabase project URL.
- `SYNCUP_SUPABASE_PUBLISHABLE_KEY`: the project's publishable key.

Set them as environment variables when building, or use Gradle properties
`syncup.supabase.url` and `syncup.supabase.publishableKey` in your user-level
Gradle configuration. Do not add project-specific values to the repository.

These values are included in the APK. Use only the publishable key; never put a
secret or service-role key in the application. Backend access still depends on
authentication and row-level security.

If either value is absent, the client factory returns `null`. No network calls
are made until a feature explicitly uses the client. Event creation remains a
local draft until the backend submission flow is implemented.
