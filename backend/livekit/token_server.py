"""Local LiveKit token service; Supabase remains the authorization authority."""

import json
import os
import time
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

import jwt


SUPABASE_URL = os.environ["SYNCUP_SUPABASE_INTERNAL_URL"].rstrip("/")
PUBLISHABLE_KEY = os.environ["SYNCUP_SUPABASE_PUBLISHABLE_KEY"]
LIVEKIT_API_KEY = os.environ["LIVEKIT_API_KEY"]
LIVEKIT_API_SECRET = os.environ["LIVEKIT_API_SECRET"]
LIVEKIT_URL = os.environ["LIVEKIT_URL"]
TOKEN_TTL_SECONDS = 300


def supabase_get(path, authorization):
    request = urllib.request.Request(
        SUPABASE_URL + path,
        headers={"apikey": PUBLISHABLE_KEY, "Authorization": authorization},
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        return json.load(response)


def listen_token(user_id, channel_id):
    now = int(time.time())
    claims = {
        "iss": LIVEKIT_API_KEY,
        "sub": user_id,
        "nbf": now,
        "exp": now + TOKEN_TTL_SECONDS,
        "video": {
            "roomJoin": True,
            "room": "channel-" + channel_id,
            "canSubscribe": True,
            "canPublish": False,
            "canPublishData": False,
        },
    }
    return jwt.encode(claims, LIVEKIT_API_SECRET, algorithm="HS256")


class TokenHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/livekit/token":
            self.respond(404, {"error": "Not found"})
            return
        try:
            authorization = self.headers.get("Authorization", "")
            if not authorization.startswith("Bearer "):
                self.respond(401, {"error": "Authentication required"})
                return
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= 4096:
                raise ValueError("Invalid request")
            self.connection.settimeout(5)
            payload = json.loads(self.rfile.read(length))
            channel_id = str(uuid.UUID(payload["channel_id"]))
        except (ValueError, TypeError, KeyError, OSError):
            self.respond(400, {"error": "Invalid request"})
            return

        try:
            user = supabase_get("/auth/v1/user", authorization)
            user_id = str(uuid.UUID(user["id"]))
            # Query under the caller's JWT so the existing channel RLS policy
            # determines membership; never use a service-role key here.
            channels = supabase_get(
                "/rest/v1/channels?select=channel_id&channel_id=eq." + channel_id,
                authorization,
            )
            if len(channels) != 1:
                self.respond(403, {"error": "Channel unavailable"})
                return
            self.respond(200, {
                "server_url": LIVEKIT_URL,
                "token": listen_token(user_id, channel_id),
                "expires_in": TOKEN_TTL_SECONDS,
            })
        except urllib.error.HTTPError as error:
            status = 401 if error.code in (401, 403) else 503
            self.respond(status, {"error": "Authorization failed"})
        except (OSError, ValueError, KeyError, TypeError):
            self.respond(503, {"error": "Authorization unavailable"})

    def respond(self, status, payload):
        body = json.dumps(payload).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Authorization headers and issued tokens must never appear in logs.
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 54326), TokenHandler).serve_forever()
