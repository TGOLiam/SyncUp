import unittest
from unittest.mock import patch

from speech_controller import SpeechController


class FakeTimer:
    def __init__(self, delay, callback, args):
        self.delay = delay
        self.callback = callback
        self.args = args
        self.daemon = False
        self.cancelled = False

    def start(self):
        pass

    def cancel(self):
        self.cancelled = True

    def fire(self):
        if not self.cancelled:
            self.callback(*self.args)


class SpeechControllerTest(unittest.TestCase):
    def setUp(self):
        self.failures = []
        self.controller = SpeechController(
            "http://supabase", "public", "ws://livekit", "key", "secret",
            fail_closed=lambda: self.failures.append("shutdown"),
        )
        self.controller._supabase_rpc = lambda name, payload, authorization: (
            [{"granted": True}] if name == "request_channel_speech" else None
        )
        self.changes = []
        self.controller._set_publish = lambda channel, actor, enabled: self.changes.append(
            (channel, actor, enabled)
        )

    def test_only_one_speaker_and_release_revokes_before_next_grant(self):
        with patch("speech_controller.threading.Timer", FakeTimer):
            self.assertTrue(self.controller.start("channel", "alice", "one", "Bearer alice"))
            self.assertFalse(self.controller.start("channel", "bob", "two", "Bearer bob"))
            self.assertFalse(self.controller.stop("channel", "bob", "one"))
            self.assertTrue(self.controller.stop("channel", "alice", "one"))
            self.assertTrue(self.controller.start("channel", "bob", "two", "Bearer bob"))
        self.assertEqual([
            ("channel", "alice", True),
            ("channel", "alice", False),
            ("channel", "bob", True),
        ], self.changes)

    def test_server_deadline_revokes_and_stale_timer_cannot_revoke_new_speaker(self):
        with patch("speech_controller.threading.Timer", FakeTimer):
            self.controller.start("channel", "alice", "one", "Bearer alice")
            timer = self.controller.sessions["channel"]["timer"]
            self.assertEqual(9.5, timer.delay)
            timer.fire()
            self.assertNotIn("channel", self.controller.sessions)
            self.assertTrue(self.controller.start("channel", "bob", "two", "Bearer bob"))
            self.controller._expire("channel", "one")
            self.assertEqual("two", self.controller.sessions["channel"]["token"])
        self.assertEqual(("channel", "alice", False), self.changes[1])

    def test_monitor_stops_if_an_expired_session_was_not_revoked(self):
        self.controller.sessions["channel"] = {"deadline": 0}
        class UnusedHeartbeat:
            def settimeout(self, value):
                pass

        self.controller.monitor(UnusedHeartbeat())
        self.assertEqual(["shutdown"], self.failures)

    def test_revoke_failure_stops_the_controller(self):
        with patch("speech_controller.threading.Timer", FakeTimer):
            self.controller.start("channel", "alice", "one", "Bearer alice")
            self.controller._set_publish = lambda *args: (_ for _ in ()).throw(OSError("unavailable"))
            with self.assertRaises(OSError):
                self.controller.stop("channel", "alice", "one")
        self.assertEqual(["shutdown"], self.failures)
        self.assertIn("channel", self.controller.sessions)


if __name__ == "__main__":
    unittest.main()
