"""Validate voice audio by decoding it to a bounded PCM stream.

This module is not an HTTP endpoint. It must run in a trusted media service
before that service accepts an upload or creates a voice message.
"""

import subprocess
from pathlib import Path


SAMPLE_RATE = 16_000
BYTES_PER_SAMPLE = 2
MAX_DURATION_MS = 10_000
MAX_FILE_BYTES = 2 * 1024 * 1024


class InvalidAudio(ValueError):
    """The audio is invalid or exceeds the recording limits."""


def validate_audio(path: Path) -> int:
    """Return the decoded duration in milliseconds, rejecting invalid audio."""
    if not path.is_file() or not 0 < path.stat().st_size <= MAX_FILE_BYTES:
        raise InvalidAudio("Audio file is empty or exceeds the size limit")

    # Decode slightly past the limit. Counting actual samples rejects recordings
    # whose container reports a falsely short duration and bounds process output.
    command = [
        "ffmpeg", "-hide_banner", "-v", "error", "-nostdin",
        "-protocol_whitelist", "file,pipe", "-f", "mov", "-i", str(path),
        "-map", "0:a:0", "-vn", "-sn", "-dn", "-t", "10.05",
        "-ac", "1", "-ar", str(SAMPLE_RATE), "-f", "s16le", "pipe:1",
    ]
    try:
        result = subprocess.run(command, capture_output=True, timeout=15, check=False)
    except (OSError, subprocess.TimeoutExpired) as error:
        raise InvalidAudio("Audio could not be decoded") from error

    sample_bytes = len(result.stdout)
    max_bytes = SAMPLE_RATE * BYTES_PER_SAMPLE * MAX_DURATION_MS // 1000
    # AAC encoder delay can add one silent frame to an exact ten-second clip.
    # The service trims and re-encodes accepted input before storing it.
    padding_bytes = SAMPLE_RATE * BYTES_PER_SAMPLE * 25 // 1000
    if result.returncode != 0 or sample_bytes == 0 or sample_bytes > max_bytes + padding_bytes:
        raise InvalidAudio("Audio is invalid or longer than 10 seconds")
    duration = (sample_bytes * 1000 + SAMPLE_RATE * BYTES_PER_SAMPLE - 1) // (
        SAMPLE_RATE * BYTES_PER_SAMPLE
    )
    return min(duration, MAX_DURATION_MS)


def normalize_audio(source: Path, destination: Path) -> None:
    """Re-encode with a hard ten-second output limit, removing codec padding."""
    command = [
        "ffmpeg", "-hide_banner", "-v", "error", "-nostdin",
        "-protocol_whitelist", "file,pipe", "-f", "mov", "-i", str(source),
        "-map", "0:a:0", "-vn", "-sn", "-dn", "-t", "10",
        "-c:a", "aac", "-movflags", "+faststart", str(destination),
    ]
    try:
        subprocess.run(command, capture_output=True, timeout=15, check=True)
    except (OSError, subprocess.SubprocessError) as error:
        raise InvalidAudio("Audio could not be normalized") from error
