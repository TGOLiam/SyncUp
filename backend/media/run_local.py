"""Start the media service using the running local Supabase stack."""

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
environment["SYNCUP_SUPABASE_SERVICE_ROLE_KEY"] = configuration["SERVICE_ROLE_KEY"]
os.execve(
    os.sys.executable,
    [os.sys.executable, str(backend / "media" / "server.py")],
    environment,
)
