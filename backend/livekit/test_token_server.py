import json
import os
import threading
import unittest
import urllib.error
import urllib.request
from http.server import ThreadingHTTPServer
from unittest.mock import patch

import jwt


os.environ.setdefault("SYNCUP_SUPABASE_INTERNAL_URL", "http://127.0.0.1:54321")
os.environ.setdefault("SYNCUP_SUPABASE_PUBLISHABLE_KEY", "test-public")
os.environ.setdefault("LIVEKIT_API_KEY", "test-key")
os.environ.setdefault("LIVEKIT_API_SECRET", "test-secret-with-at-least-32-bytes")
os.environ.setdefault("LIVEKIT_URL", "ws://127.0.0.1:7880")

import token_server


USER_ID = "10000000-0000-0000-0000-000000000001"
CHANNEL_ID = "20000000-0000-0000-0000-000000000001"


class TokenServerTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), token_server.TokenHandler)
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()

    def request(self, payload, authorization="Bearer user-token"):
        headers = {"Content-Type": "application/json"}
        if authorization is not None:
            headers["Authorization"] = authorization
        req = urllib.request.Request(
            f"http://127.0.0.1:{self.server.server_port}/livekit/token",
            data=json.dumps(payload).encode(),
            headers=headers,
        )
        try:
            with urllib.request.urlopen(req, timeout=5) as response:
                return response.status, json.load(response), response.headers
        except urllib.error.HTTPError as error:
            with error:
                return error.code, json.load(error), error.headers

    def test_member_gets_channel_scoped_listen_only_token(self):
        def authorized(path, authorization):
            self.assertEqual("Bearer user-token", authorization)
            if path == "/auth/v1/user":
                return {"id": USER_ID}
            self.assertIn(CHANNEL_ID, path)
            return [{"channel_id": CHANNEL_ID}]

        with patch.object(token_server, "supabase_get", side_effect=authorized):
            status, body, headers = self.request({"channel_id": CHANNEL_ID})
        self.assertEqual(200, status)
        self.assertEqual("no-store", headers["Cache-Control"])
        claims = jwt.decode(
            body["token"], token_server.LIVEKIT_API_SECRET,
            algorithms=["HS256"], issuer=token_server.LIVEKIT_API_KEY,
        )
        self.assertEqual(USER_ID, claims["sub"])
        self.assertEqual("channel-" + CHANNEL_ID, claims["video"]["room"])
        self.assertTrue(claims["video"]["roomJoin"])
        self.assertTrue(claims["video"]["canSubscribe"])
        self.assertFalse(claims["video"]["canPublish"])
        self.assertFalse(claims["video"]["canPublishData"])
        self.assertEqual(300, claims["exp"] - claims["nbf"])

    def test_nonmember_is_denied(self):
        with patch.object(token_server, "supabase_get", side_effect=[{"id": USER_ID}, []]):
            status, body, _ = self.request({"channel_id": CHANNEL_ID})
        self.assertEqual(403, status)
        self.assertNotIn("token", body)

    def test_invalid_request_and_missing_auth_are_denied(self):
        with patch.object(token_server, "supabase_get") as get:
            self.assertEqual(401, self.request({"channel_id": CHANNEL_ID}, None)[0])
            self.assertEqual(400, self.request({"channel_id": "not-a-uuid"})[0])
            get.assert_not_called()


if __name__ == "__main__":
    unittest.main()
