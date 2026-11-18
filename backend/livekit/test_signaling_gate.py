import time
import unittest

import jwt

from signaling_gate import allowed_token, extract_token


class SignalingGateTest(unittest.TestCase):
    def test_rejects_ambiguous_or_conflicting_tokens(self):
        self.assertEqual("token", extract_token("/rtc?access_token=token", []))
        self.assertEqual("token", extract_token("/rtc", ["Authorization: Bearer token"]))
        with self.assertRaises(ValueError):
            extract_token("/rtc?access_token=one&access_token=two", [])
        with self.assertRaises(ValueError):
            extract_token("/rtc?access_token=one", ["Authorization: Bearer two"])
        with self.assertRaises(ValueError):
            extract_token("/rtc", ["Authorization: Bearer one", "Authorization: Bearer two"])

    def test_requires_explicit_listen_only_grants(self):
        key = "test-key"
        secret = "test-secret-with-at-least-32-bytes"
        now = int(time.time())
        claims = {
            "iss": key,
            "sub": "user",
            "nbf": now,
            "exp": now + 60,
            "video": {
                "roomJoin": True,
                "room": "channel-id",
                "canSubscribe": True,
                "canPublish": False,
                "canPublishData": False,
            },
        }
        self.assertTrue(allowed_token(jwt.encode(claims, secret, algorithm="HS256"), key, secret))
        claims["video"]["canPublish"] = True
        self.assertFalse(allowed_token(jwt.encode(claims, secret, algorithm="HS256"), key, secret))
        del claims["video"]["canPublish"]
        self.assertFalse(allowed_token(jwt.encode(claims, secret, algorithm="HS256"), key, secret))
        self.assertFalse(allowed_token("invalid", key, secret))


if __name__ == "__main__":
    unittest.main()
