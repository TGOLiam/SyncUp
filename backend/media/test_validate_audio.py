import subprocess
import tempfile
import unittest
from pathlib import Path

from validate_audio import InvalidAudio, normalize_audio, validate_audio


class ValidateAudioTest(unittest.TestCase):
    def make_audio(self, directory: str, seconds: float) -> Path:
        path = Path(directory) / f"{seconds}.m4a"
        subprocess.run(
            [
                "ffmpeg", "-v", "error", "-f", "lavfi", "-i",
                f"sine=frequency=440:duration={seconds}", "-c:a", "aac", str(path),
            ],
            check=True,
        )
        return path

    def test_accepts_short_audio_and_rejects_long_audio(self):
        with tempfile.TemporaryDirectory() as directory:
            self.assertLessEqual(validate_audio(self.make_audio(directory, 1)), 1050)
            self.assertLessEqual(validate_audio(self.make_audio(directory, 9.9)), 10000)
            exact = self.make_audio(directory, 10.0)
            self.assertEqual(10000, validate_audio(exact))
            normalized = Path(directory) / "normalized.m4a"
            normalize_audio(exact, normalized)
            self.assertEqual(10000, validate_audio(normalized))
            with self.assertRaises(InvalidAudio):
                validate_audio(self.make_audio(directory, 10.5))

    def test_rejects_invalid_file(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "invalid.m4a"
            path.write_bytes(b"not audio")
            with self.assertRaises(InvalidAudio):
                validate_audio(path)


if __name__ == "__main__":
    unittest.main()
