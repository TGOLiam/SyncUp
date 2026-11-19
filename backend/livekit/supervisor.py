"""Run the local media server and speech controller in one fail-closed container."""

import asyncio
import os
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer

import signaling_gate
import token_server
from speech_controller import SpeechController


if os.getpid() != 1:
    raise RuntimeError("The fail-closed supervisor must run as container PID 1")

media_environment = os.environ.copy()
media_environment["LIVEKIT_KEYS"] = token_server.LIVEKIT_API_KEY + ": " + token_server.LIVEKIT_API_SECRET
media_command = ["/livekit-server", "--bind", "127.0.0.1"]
if os.environ.get("SYNCUP_DEPLOYED") == "1":
    # The public address is advertised to WebRTC clients, while signaling and
    # administration stay on loopback behind the protected signaling gate.
    media_command.extend(["--node-ip", os.environ["LIVEKIT_PUBLIC_IP"], "--udp-port", "7882"])
else:
    media_command.append("--dev")
media = subprocess.Popen(media_command, env=media_environment)


def watch_media():
    media.wait()
    # Exiting PID 1 also terminates every remaining process in this container.
    os._exit(1)


threading.Thread(target=watch_media, daemon=True).start()
for attempt in range(100):
    try:
        with urllib.request.urlopen("http://127.0.0.1:7880/", timeout=1):
            break
    except (OSError, urllib.error.URLError):
        time.sleep(0.1)
else:
    os._exit(1)

def run_gate():
    try:
        asyncio.run(signaling_gate.serve(token_server.LIVEKIT_API_KEY, token_server.LIVEKIT_API_SECRET))
    finally:
        # If the gate fails, no signaling endpoint may remain available.
        os._exit(1)


threading.Thread(target=run_gate, daemon=True).start()

monitor_socket, watchdog_socket = socket.socketpair()
subprocess.Popen(
    [sys.executable, "/opt/syncup/watchdog.py", str(watchdog_socket.fileno()), str(media.pid)],
    pass_fds=(watchdog_socket.fileno(),),
)
watchdog_socket.close()

token_server.controller = SpeechController(
    token_server.SUPABASE_URL,
    token_server.PUBLISHABLE_KEY,
    "http://127.0.0.1:7880",
    token_server.LIVEKIT_API_KEY,
    token_server.LIVEKIT_API_SECRET,
)
threading.Thread(target=token_server.controller.monitor, args=(monitor_socket,), daemon=True).start()
ThreadingHTTPServer(
    (os.environ["SYNCUP_LAN_ADDRESS"], 54326), token_server.TokenHandler
).serve_forever()
