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

LiveKit's development credentials must stay on the server side. Do not embed
its API secret in the Android app. Membership checks and token issuance are
planned for the next phase; this server setup does not yet connect the app.
