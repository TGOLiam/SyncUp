#!/usr/bin/env python3
"""Deploy the current public main branch only after its backend CI passes."""

import json
import ipaddress
import hashlib
import os
import re
import socket
import subprocess
import sys
import time
import urllib.parse
import urllib.request
from pathlib import Path


REPOSITORY = "TGOLiam/SyncUp"
REMOTE = "https://github.com/" + REPOSITORY + ".git"
CHECKOUT = Path("/opt/syncup/repository")
CONFIG = Path("/etc/syncup/backend.env")
STATE = Path("/var/lib/syncup/deployed-sha")


def command(*arguments, cwd=None):
    return subprocess.run(arguments, cwd=cwd, check=True, text=True, capture_output=True).stdout.strip()


def ci_passed(commit, payload):
    runs = payload.get("workflow_runs", [])
    if not runs:
        return False
    # GitHub returns newest runs first. A later failure must not be masked by
    # an earlier successful run for the same commit.
    run = runs[0]
    return (
        run.get("head_sha") == commit
        and run.get("head_branch") == "main"
        and run.get("event") == "push"
        and run.get("status") == "completed"
        and run.get("conclusion") == "success"
        and (run.get("head_repository") or {}).get("full_name") == REPOSITORY
    )


def checked_config():
    if not CONFIG.is_file():
        return False
    mode = CONFIG.stat().st_mode & 0o777
    if CONFIG.stat().st_uid != 0 or mode & 0o077:
        raise RuntimeError("Deployment configuration must be root-owned and private")
    required = {
        "SUPABASE_URL", "SUPABASE_PUBLISHABLE_KEY", "SYNCUP_DOMAIN",
        "LIVEKIT_PUBLIC_IP", "LIVEKIT_API_KEY", "LIVEKIT_API_SECRET",
    }
    values = {}
    for line in CONFIG.read_text().splitlines():
        name, separator, value = line.partition("=")
        if separator and name in required:
            values[name] = value.strip()
    if any(not values.get(name) or values[name].startswith("replace-") for name in required):
        raise RuntimeError("Deployment configuration is incomplete")
    if not values["SUPABASE_URL"].startswith("https://"):
        raise RuntimeError("Hosted Supabase must use HTTPS")
    if values["SUPABASE_URL"] == "https://project.supabase.co" or values["SYNCUP_DOMAIN"] == "livekit.example.org":
        raise RuntimeError("Replace the example deployment addresses")
    if not ipaddress.ip_address(values["LIVEKIT_PUBLIC_IP"]).is_global:
        raise RuntimeError("LiveKit needs a public IP address")
    if len(values["LIVEKIT_API_SECRET"]) < 32:
        raise RuntimeError("LiveKit API secret must contain at least 32 characters")
    return True


def main():
    if not checked_config():
        print("Deployment is disabled until /etc/syncup/backend.env is configured.")
        return

    CHECKOUT.parent.mkdir(parents=True, exist_ok=True)
    if not (CHECKOUT / ".git").exists():
        command("git", "clone", "--no-checkout", REMOTE, str(CHECKOUT))
    if command("git", "remote", "get-url", "origin", cwd=CHECKOUT) != REMOTE:
        raise RuntimeError("Unexpected deployment repository")
    command("git", "fetch", "--no-tags", "--depth=1", "origin", "refs/heads/main", cwd=CHECKOUT)
    commit = command("git", "rev-parse", "FETCH_HEAD", cwd=CHECKOUT)
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise RuntimeError("Invalid commit ID")
    previous_state = STATE.read_text().splitlines() if STATE.exists() else []
    previous = previous_state[0] if previous_state else None
    config_hash = hashlib.sha256(CONFIG.read_bytes()).hexdigest()
    if previous == commit and len(previous_state) > 1 and previous_state[1] == config_hash:
        print("Already deployed:", commit[:12])
        return

    query = urllib.parse.urlencode({
        "branch": "main", "event": "push",
        "head_sha": commit, "per_page": 20,
    })
    request = urllib.request.Request(
        "https://api.github.com/repos/" + REPOSITORY + "/actions/workflows/backend-ci.yml/runs?" + query,
        headers={"Accept": "application/vnd.github+json", "User-Agent": "syncup-ec2-deployer"},
    )
    with urllib.request.urlopen(request, timeout=15) as response:
        runs = json.load(response)
    if not ci_passed(commit, runs):
        print("Waiting for successful backend CI:", commit[:12])
        return

    command("git", "checkout", "--force", "--detach", commit, cwd=CHECKOUT)
    compose = [
        "docker", "compose", "--project-name", "syncup-backend",
        "--env-file", str(CONFIG), "-f", str(CHECKOUT / "backend/deploy/compose.yaml"),
    ]
    try:
        # Build before replacing running services, so an image build failure
        # cannot interrupt the previously deployed instance.
        subprocess.run([*compose, "build"], check=True)
        subprocess.run([*compose, "up", "-d", "--remove-orphans"], check=True)
        for _ in range(30):
            try:
                running = set(command(*compose, "ps", "--status", "running", "--services").splitlines())
                if {"livekit", "proxy"} <= running:
                    with urllib.request.urlopen("http://127.0.0.1:7880/", timeout=1) as response:
                        if response.status == 200:
                            with socket.create_connection(("127.0.0.1", 54326), timeout=1):
                                with socket.create_connection(("127.0.0.1", 7883), timeout=1):
                                    break
            except OSError:
                pass
            time.sleep(1)
        else:
            raise RuntimeError("LiveKit failed its local startup check")
    except (subprocess.CalledProcessError, RuntimeError):
        if previous and re.fullmatch(r"[0-9a-f]{40}", previous):
            print("Deployment failed; attempting to restore", previous[:12], file=sys.stderr)
            command("git", "fetch", "--no-tags", "origin", previous, cwd=CHECKOUT)
            command("git", "checkout", "--force", "--detach", previous, cwd=CHECKOUT)
            subprocess.run([*compose, "up", "-d", "--build", "--remove-orphans"], check=True)
        else:
            subprocess.run([*compose, "down", "--remove-orphans"], check=False)
        raise
    STATE.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
    os.chmod(STATE.parent, 0o700)
    temporary = STATE.with_suffix(".tmp")
    temporary.write_text(commit + "\n" + config_hash + "\n")
    os.replace(temporary, STATE)
    print("Deployed", commit[:12])


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        print("Deployment failed:", type(error).__name__, file=sys.stderr)
        sys.exit(1)
