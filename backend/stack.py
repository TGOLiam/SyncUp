"""Manage the local Supabase and LiveKit development services together."""

import argparse
import subprocess
import sys
from pathlib import Path


BACKEND = Path(__file__).resolve().parent
LIVEKIT = BACKEND / "livekit"
SUPABASE = BACKEND / "node_modules" / ".bin" / "supabase"


def supabase(*arguments, show_output=True):
    # Startup and status output contains development keys; keep it private.
    result = subprocess.run(
        [str(SUPABASE), *arguments],
        cwd=BACKEND,
        capture_output=not show_output,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(
            "Supabase command failed. Run 'npx supabase "
            + " ".join(arguments)
            + "' in backend for details."
        )
    return result


def compose(*arguments):
    subprocess.run(["docker", "compose", *arguments], cwd=LIVEKIT, check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("up", "down", "status", "logs"))
    action = parser.parse_args().action

    if action == "up":
        if not SUPABASE.exists():
            raise RuntimeError("Install the pinned Supabase CLI first: cd backend && npm ci")
        print("Starting local Supabase...", flush=True)
        supabase("start", show_output=False)
        print("Starting LiveKit and speech services...", flush=True)
        subprocess.run([sys.executable, str(LIVEKIT / "run_supervised_local.py")], check=True)
    elif action == "down":
        print("Stopping LiveKit and speech services...", flush=True)
        compose("down")
        print("Stopping Supabase; local data volumes are retained...", flush=True)
        supabase("stop")
    elif action == "status":
        try:
            supabase("status", "-o", "json", show_output=False)
            print("Supabase: running", flush=True)
        except RuntimeError:
            print("Supabase: stopped or unavailable", flush=True)
        compose("ps", "--all")
    else:
        compose("logs", "--tail", "100", "livekit")


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, subprocess.CalledProcessError, OSError) as error:
        print(f"Local stack: {error}", file=sys.stderr)
        sys.exit(1)
