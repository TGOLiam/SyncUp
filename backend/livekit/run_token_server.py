"""Start the local token service using local development credentials."""

import json
import os
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
environment["SYNCUP_SUPABASE_INTERNAL_URL"] = "http://127.0.0.1:54321"
environment["SYNCUP_SUPABASE_PUBLISHABLE_KEY"] = configuration["PUBLISHABLE_KEY"]
# These are LiveKit's publicly documented development-only credentials.
environment["LIVEKIT_API_KEY"] = "devkey"
environment["LIVEKIT_API_SECRET"] = "secret"
environment["LIVEKIT_URL"] = "ws://127.0.0.1:7880"
os.execve(
    os.sys.executable,
    [os.sys.executable, str(backend / "livekit" / "token_server.py")],
    environment,
)
