"""Stop the local media container if its speech monitor stops responding."""

import os
import select
import signal
import socket
import sys


heartbeat = socket.socket(fileno=int(sys.argv[1]))
media_pid = int(sys.argv[2])
while True:
    ready, _, _ = select.select([heartbeat], [], [], 1.0)
    if not ready:
        # A container's PID 1 cannot reliably be killed by its descendants.
        # Kill the media child directly; the supervisor will exit when it wakes.
        try:
            os.kill(media_pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        break
    if not heartbeat.recv(1):
        break
    heartbeat.sendall(b".")
