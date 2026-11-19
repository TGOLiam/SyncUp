# The controller is PID 1, so if it exits the media server in the same
# container is stopped too, rather than continuing to transmit.
FROM livekit/livekit-server:v1.13.11

RUN apk add --no-cache python3 py3-pip \
    && python3 -m venv /opt/syncup-venv \
    && /opt/syncup-venv/bin/pip install --no-cache-dir PyJWT==2.13.0

COPY token_server.py speech_controller.py signaling_gate.py supervisor.py watchdog.py /opt/syncup/
ENTRYPOINT ["/opt/syncup-venv/bin/python", "/opt/syncup/supervisor.py"]
