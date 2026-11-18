# Local LiveKit test server

This setup is for local development on a trusted LAN. HTTP and WebSocket traffic
is unencrypted and is not suitable for a public network or production use.
Start the combined local stack from the project root:

```bash
python3 backend/stack.py up
```

The script starts Supabase, gets its local configuration, and builds and starts
the supervised LiveKit container. Manage the full stack with:

```bash
python3 backend/stack.py status
python3 backend/stack.py logs
python3 backend/stack.py down
```

The container runs the token service, speech controller, signaling gate and
LiveKit together. If speech revocation cannot be confirmed, the supervisor
stops the media server, disconnecting all rooms. The gate prevents clients from
reconnecting using a token with publishing permission. Direct LiveKit signaling
on port 7880 is for the local host only; do not forward it to clients. The
supervisor generates a private local signing key in the Git-ignored
`.local-credentials.json` file. Keep this file private.

The stack writes the detected LAN address to the Git-ignored Android build
settings. Rebuild after the address changes. Phones and VMs need network access
to TCP ports 54321, 54326 and 7883, plus LiveKit's advertised media ports.
Global and the selected channel use separate listening connections.

The token endpoint is `POST http://<lan-address>:54326/livekit/token`. The speech
endpoints require the supervised controller. The older standalone
`run_token_server.py` can issue listening tokens but cannot authorize speech.

Run server tests with `python3 -m unittest discover -s backend/livekit -p
'test_*.py'`. Membership is checked when a token is issued; immediate removal of
already connected participants after membership changes requires additional
server-side connection management. Production also needs private signaling,
proper secrets, TLS, and a deployment designed for media availability.
