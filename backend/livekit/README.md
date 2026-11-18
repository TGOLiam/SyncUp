# Local LiveKit test server

This configuration is for local development only. LiveKit development mode
uses publicly known credentials and is not suitable for a public network or
production deployment. It runs separately from the local Supabase stack.

From this directory:

```bash
docker compose up -d
```

Check and stop the server with:

```bash
docker compose ps
docker compose logs livekit
docker compose down
```

The signaling endpoint is `ws://127.0.0.1:7880` on the host. Host networking
is used so WebRTC media traffic can reach the server without a large Docker
port mapping. The development server defaults to loopback signaling; it is not
configured for access from other devices. Phone testing needs a separately
reviewed network configuration: forwarding the signaling port alone does not
establish the WebRTC media path.

To test the host media path with the [LiveKit CLI](https://docs.livekit.io/reference/developer-tools/livekit-cli/):

```bash
lk --dev perf load-test --room syncup-test --audio-publishers 1 --subscribers 1 --duration 10s
```

This sends simulated silent packets between two clients on the computer; it
does not exercise a microphone or prove phone connectivity. Do not run load
tests against a shared or production service without authorization.

The local token service checks the caller's Supabase session and channel
membership before issuing a five-minute, listen-only token. Start it in a
separate terminal after starting local Supabase and LiveKit:

```bash
python3 -m venv .venv
.venv/bin/pip install -r requirements.txt
.venv/bin/python run_token_server.py
```

The endpoint is `POST http://127.0.0.1:54326/livekit/token`. Supply the user's
Supabase access token as a bearer token and JSON `{"channel_id":"<uuid>"}`.
The response contains a LiveKit server URL, room token, and `expires_in`.
The endpoint and LiveKit signaling URL are host-only during this phase; the
Android app is not connected yet.

Test the service with `.venv/bin/python -m unittest discover -p 'test_*.py'`.
LiveKit's development credentials stay on the server side. Do not embed the
API secret in the Android app. This local service uses HTTP, fixed development
credentials, and a loopback listener; it is not a production token service.

Membership is checked when a token is issued. A previously issued token can
remain usable until it expires, and an already connected participant is not
automatically disconnected if membership changes. Immediate revocation needs
additional server-side connection management before production use.
