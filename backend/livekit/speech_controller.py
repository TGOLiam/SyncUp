"""Fail-closed local LiveKit speech coordination.

The coordinator and LiveKit must run in the same container, with the
coordinator as PID 1. Exiting the coordinator stops the media server too.
"""

import json
import os
import threading
import time
import urllib.error
import urllib.request

import jwt


class SpeechController:
    def __init__(self, supabase_url, public_key, livekit_url, api_key, api_secret, fail_closed=None):
        self.supabase_url = supabase_url.rstrip("/")
        self.public_key = public_key
        self.livekit_url = livekit_url.replace("ws://", "http://").replace("wss://", "https://").rstrip("/")
        self.api_key = api_key
        self.api_secret = api_secret
        self.fail_closed = fail_closed or (lambda: os._exit(1))
        self.guard = threading.Lock()
        self.channel_guards = {}
        self.sessions = {}

    def monitor(self, heartbeat):
        # The monitor and watchdog both require a response from one another.
        heartbeat.settimeout(0.5)
        while True:
            now = time.monotonic()
            for session in list(self.sessions.values()):
                if now >= session["deadline"]:
                    self.fail_closed()
                    return
            try:
                heartbeat.sendall(b".")
                if heartbeat.recv(1) != b".":
                    self.fail_closed()
                    return
            except OSError:
                self.fail_closed()
                return
            time.sleep(0.1)

    def _channel_guard(self, channel_id):
        with self.guard:
            return self.channel_guards.setdefault(channel_id, threading.RLock())

    def _post(self, url, payload, headers, timeout=4):
        request = urllib.request.Request(
            url,
            data=json.dumps(payload).encode(),
            headers={"Content-Type": "application/json", **headers},
        )
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return response.read()

    def _supabase_rpc(self, name, payload, authorization):
        data = self._post(
            self.supabase_url + "/rest/v1/rpc/" + name,
            payload,
            {"apikey": self.public_key, "Authorization": authorization},
        )
        return json.loads(data) if data else None

    def _set_publish(self, channel_id, actor, enabled):
        room = "channel-" + channel_id
        now = int(time.time())
        admin = jwt.encode({
            "iss": self.api_key,
            "sub": "syncup-speech-controller",
            "nbf": now,
            "exp": now + 60,
            "video": {"roomAdmin": True, "room": room},
        }, self.api_secret, algorithm="HS256")
        payload = {
            "room": room,
            "identity": actor,
            "permission": {
                "can_subscribe": True,
                "can_publish": enabled,
                "can_publish_data": False,
                "can_publish_sources": ["MICROPHONE"] if enabled else [],
            },
        }
        deadline = time.monotonic() + 0.5
        try:
            self._post(
                self.livekit_url + "/twirp/livekit.RoomService/UpdateParticipant",
                payload,
                {"Authorization": "Bearer " + admin},
                timeout=4 if enabled else max(0.001, deadline - time.monotonic()),
            )
            if not enabled:
                # Permission updates and track removal can finish asynchronously.
                # Keep the channel locked until the media server confirms both.
                while True:
                    remaining = deadline - time.monotonic()
                    if remaining <= 0:
                        raise TimeoutError("LiveKit did not confirm track removal")
                    data = self._post(
                        self.livekit_url + "/twirp/livekit.RoomService/GetParticipant",
                        {"room": room, "identity": actor},
                        {"Authorization": "Bearer " + admin},
                        timeout=remaining,
                    )
                    participant = json.loads(data)
                    permission = participant.get("permission")
                    if isinstance(permission, dict) and not permission.get(
                        "canPublish", permission.get("can_publish", False)
                    ) and not participant.get("tracks"):
                        return
                    time.sleep(min(0.01, max(0, deadline - time.monotonic())))
        except urllib.error.HTTPError as error:
            if not enabled and error.code == 404:
                with error:
                    try:
                        details = json.load(error)
                    except (ValueError, OSError):
                        raise error
                if details.get("code") == "not_found" and details.get("msg") in (
                    "participant does not exist", "room not found"
                ):
                    return
            raise

    def _release_lease(self, channel_id, token, authorization):
        try:
            self._supabase_rpc("release_channel_speech", {
                "p_channel_id": channel_id,
                "p_request_id": token,
            }, authorization)
        except Exception:
            # The existing database lease expires at 15 seconds even if release fails.
            pass

    def _revoke(self, channel_id, session):
        try:
            self._set_publish(channel_id, session["actor"], False)
        except Exception:
            # A live stream must not outlive the component enforcing its deadline.
            self.fail_closed()
            raise
        session["timer"].cancel()
        self.sessions.pop(channel_id, None)
        self._release_lease(channel_id, session["token"], session["authorization"])

    def _expire(self, channel_id, token):
        with self._channel_guard(channel_id):
            session = self.sessions.get(channel_id)
            if session is not None and session["token"] == token:
                self._revoke(channel_id, session)

    def start(self, channel_id, actor, token, authorization):
        with self._channel_guard(channel_id):
            if channel_id in self.sessions:
                return False
            result = self._supabase_rpc("request_channel_speech", {
                "p_channel_id": channel_id,
                "p_request_id": token,
            }, authorization)
            if not result or not result[0]["granted"]:
                return False
            # Begin revocation slightly before ten seconds; an unresponsive
            # media API causes the coupled server to stop after 0.5 seconds.
            timer = threading.Timer(9.5, self._expire, args=(channel_id, token))
            timer.daemon = True
            session = {
                "actor": actor,
                "token": token,
                "authorization": authorization,
                "timer": timer,
                "deadline": time.monotonic() + 10,
            }
            self.sessions[channel_id] = session
            # Start the server deadline before enabling the media track.
            timer.start()
            try:
                self._set_publish(channel_id, actor, True)
            except Exception:
                # The grant response may have been lost; revoke before releasing.
                self._revoke(channel_id, session)
                raise
            return True

    def stop(self, channel_id, actor, token):
        with self._channel_guard(channel_id):
            session = self.sessions.get(channel_id)
            if session is None or session["actor"] != actor or session["token"] != token:
                return False
            self._revoke(channel_id, session)
            return True
