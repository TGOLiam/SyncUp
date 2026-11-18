# Local LiveKit test server

This setup is for local development. Development credentials are public and the
HTTP endpoints are not suitable for a public network or production use.
Start local Supabase first, then from the project root run:

```bash
python3 backend/livekit/run_supervised_local.py
```

The script gets the local Supabase configuration and builds and starts the
supervised LiveKit container. Manage it from `backend/livekit` with:

```bash
docker compose ps
docker compose logs livekit
docker compose down
```

The container runs the token service, speech controller, signaling gate and
LiveKit together. If speech revocation cannot be confirmed, the supervisor
stops the media server, disconnecting all rooms. The gate prevents clients from
reconnecting using a token with publishing permission. Direct LiveKit signaling
on port 7880 is for the local host only; do not forward it to clients.

For Android debug builds, set `syncup.livekit.tokenUrl=http://127.0.0.1:54326`
in the Git-ignored `local.properties`, then forward the local ports:

```bash
adb reverse tcp:54321 tcp:54321
adb reverse tcp:54326 tcp:54326
adb reverse tcp:7883 tcp:7883
```

The WebRTC media path also needs the phone to reach the server's advertised LAN
address and media ports; ADB reverse only forwards TCP. Use a trusted local
network. Global and the selected channel use separate listening connections.

The token endpoint is `POST http://127.0.0.1:54326/livekit/token`. The speech
endpoints require the supervised controller. The older standalone
`run_token_server.py` can issue listening tokens but cannot authorize speech.

Run server tests with `python3 -m unittest discover -s backend/livekit -p
'test_*.py'`. Membership is checked when a token is issued; immediate removal of
already connected participants after membership changes requires additional
server-side connection management. Production also needs private signaling,
proper secrets, TLS, and a deployment designed for media availability.
