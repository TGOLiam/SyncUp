"""Build and start the coupled local LiveKit and speech controller."""

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
environment["SYNCUP_SUPABASE_PUBLISHABLE_KEY"] = configuration["PUBLISHABLE_KEY"]
subprocess.run(
    ["docker", "compose", "up", "-d", "--build"],
    cwd=backend / "livekit",
    env=environment,
    check=True,
)
