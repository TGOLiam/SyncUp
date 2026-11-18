"""Local-only voice upload service. Run behind a TLS proxy outside development."""

import json
import os
import tempfile
import urllib.error
import urllib.request
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path

from validate_audio import InvalidAudio, MAX_FILE_BYTES, normalize_audio, validate_audio


API_URL = os.environ["SYNCUP_SUPABASE_INTERNAL_URL"].rstrip("/")
PUBLIC_KEY = os.environ["SYNCUP_SUPABASE_PUBLISHABLE_KEY"]
SERVICE_KEY = os.environ["SYNCUP_SUPABASE_SERVICE_ROLE_KEY"]


def request(path, *, method="GET", data=None, headers=None):
    req = urllib.request.Request(API_URL + path, data=data, method=method, headers=headers or {})
    with urllib.request.urlopen(req, timeout=5) as response:
        return response.read()


def service_request(path, data, *, method="POST", content_type="application/json"):
    return request(path, method=method, data=data, headers={
        "apikey": SERVICE_KEY,
        "Authorization": "Bearer " + SERVICE_KEY,
        "Content-Type": content_type,
    })


def rpc(name, arguments):
    data = json.dumps(arguments).encode("utf-8")
    return service_request("/rest/v1/rpc/" + name, data)


def canonical_uuid(value):
    return str(uuid.UUID(value))


class VoiceHandler(BaseHTTPRequestHandler):
    def do_POST(self):
        if self.path != "/voice":
            self.respond(404, "Not found")
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
            channel_id = canonical_uuid(self.headers["X-Channel-Id"])
            token = canonical_uuid(self.headers["X-Speech-Token"])
            message_id = canonical_uuid(self.headers["X-Message-Id"])
            authorization = self.headers.get("Authorization", "")
            if not 0 < length <= MAX_FILE_BYTES or not authorization.startswith("Bearer "):
                raise ValueError("Invalid request")
        except (KeyError, ValueError, TypeError):
            self.respond(400, "Invalid request")
            return

        try:
            user = json.loads(request("/auth/v1/user", headers={
                "apikey": PUBLIC_KEY,
                "Authorization": authorization,
            }))
            actor = canonical_uuid(user["id"])
            lease = {"p_actor": actor, "p_channel_id": channel_id, "p_token": token}
            if json.loads(rpc("check_speech_lease", lease)) is not True:
                self.respond(403, "Speech permission expired or unavailable")
                return
            self.connection.settimeout(5)
            audio = self.rfile.read(length)
            if len(audio) != length:
                raise ValueError("Incomplete upload")
            with tempfile.TemporaryDirectory(prefix="syncup-voice-") as directory:
                path = Path(directory) / "audio.m4a"
                path.write_bytes(audio)
                duration = validate_audio(path)
                normalized = Path(directory) / "normalized.m4a"
                normalize_audio(path, normalized)
                audio = normalized.read_bytes()
            object_name = channel_id + "/" + message_id + ".m4a"
            storage_path = "/storage/v1/object/voice-messages/" + object_name
            service_request(storage_path, audio, content_type="audio/mp4")
            try:
                rpc("finish_validated_voice", {
                    **lease,
                    "p_message_id": message_id,
                    "p_audio_reference": object_name,
                    "p_duration_ms": duration,
                })
            except Exception:
                # Delete through the Storage API, not directly from its metadata table.
                try:
                    service_request(storage_path, None, method="DELETE")
                except Exception:
                    pass
                raise
            self.respond(201, "Voice message saved")
        except InvalidAudio:
            self.respond(422, "Invalid audio or recording exceeds 10 seconds")
        except urllib.error.HTTPError as error:
            self.respond(401 if error.code == 401 else 503, "Voice upload failed")
        except (OSError, ValueError, KeyError, json.JSONDecodeError):
            self.respond(503, "Voice upload failed")

    def respond(self, status, message):
        body = json.dumps({"message": message}).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, format, *args):
        # Never include credentials or request bodies in the service log.
        return


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 54325), VoiceHandler).serve_forever()
