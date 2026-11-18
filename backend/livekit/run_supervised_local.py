"""Build and start the coupled local LiveKit and speech controller."""

import json
import os
import secrets
import socket
import subprocess
from pathlib import Path


backend = Path(__file__).resolve().parent.parent
status = subprocess.run(
    ["npx", "supabase", "status", "-o", "json"],
    cwd=backend,
    capture_output=True,
    text=True,
    check=True,
)
configuration = json.loads(status.stdout)
environment = os.environ.copy()
environment["SYNCUP_SUPABASE_PUBLISHABLE_KEY"] = configuration["PUBLISHABLE_KEY"]

# Keep a private, stable signing key so LAN clients cannot forge dev tokens.
credentials_path = backend / "livekit" / ".local-credentials.json"
if credentials_path.exists():
    credentials = json.loads(credentials_path.read_text())
else:
    credentials = {
        "key": "syncup-" + secrets.token_hex(8),
        "secret": secrets.token_hex(32),
    }
    descriptor = os.open(credentials_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(descriptor, "w") as output:
        json.dump(credentials, output)
environment["LIVEKIT_API_KEY"] = credentials["key"]
environment["LIVEKIT_API_SECRET"] = credentials["secret"]

address = environment.get("SYNCUP_LAN_ADDRESS")
if not address:
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
        probe.connect(("1.1.1.1", 80))
        address = probe.getsockname()[0]
environment["SYNCUP_LAN_ADDRESS"] = address
print("Local backend address:", address, flush=True)

# Preserve unrelated Android Studio settings while keeping debug builds pointed
# at the same LAN address advertised by the LiveKit token service.
properties_path = backend.parent / "local.properties"
settings = {
    "syncup.supabase.url": f"http://{address}:54321",
    "syncup.supabase.publishableKey": configuration["PUBLISHABLE_KEY"],
    "syncup.livekit.tokenUrl": f"http://{address}:54326",
}
lines = properties_path.read_text().splitlines() if properties_path.exists() else []
lines = [line for line in lines if line.split("=", 1)[0].strip() not in settings]
lines.extend(f"{key}={value}" for key, value in settings.items())
properties_path.write_text("\n".join(lines) + "\n")
subprocess.run(
    ["docker", "compose", "up", "-d", "--build"],
    cwd=backend / "livekit",
    env=environment,
    check=True,
)
