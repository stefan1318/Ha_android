#!/usr/bin/env python3
"""End-to-end smoke tests for Hey STEVORA audio dataset preparation.

Run from repository root:
    python3 -m unittest discover -s training/hey_stevora -p 'test_*.py' -v

Requires numpy and scipy (the same dependencies as prepare_dataset.py).
No real voices, cloud calls, or external data are used.
"""
import csv
import json
import math
import subprocess
import sys
import tempfile
import unittest
import wave
from pathlib import Path

SCRIPT = Path(__file__).with_name("prepare_dataset.py")


def write_test_wav(path: Path, rate: int, channels: int = 1, seconds: float = 0.45):
    path.parent.mkdir(parents=True, exist_ok=True)
    frames = bytearray()
    for i in range(int(rate * seconds)):
        sample = int(10000 * math.sin(2 * math.pi * 440 * i / rate))
        frames.extend(sample.to_bytes(2, "little", signed=True) * channels)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(channels)
        wav.setsampwidth(2)
        wav.setframerate(rate)
        wav.writeframes(frames)


class DatasetPreparationTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.raw = self.root / "raw"
        self.prepared = self.root / "prepared"

    def run_preparer(self, output=None):
        return subprocess.run(
            [sys.executable, str(SCRIPT), "--input", str(self.raw),
             "--output", str(output or self.prepared)],
            capture_output=True, text=True, check=False,
        )

    def test_speaker_disjoint_split_and_audio_format(self):
        for idx, speaker in enumerate(("speaker_a", "speaker_b", "speaker_c", "speaker_d")):
            rate = 8000 if idx % 2 == 0 else 44100
            write_test_wav(self.raw / "positive" / speaker / "sample.wav", rate, channels=2)
            write_test_wav(self.raw / "negative_speech" / speaker / "sample.wav", rate)
        write_test_wav(self.raw / "negative_ambient" / "room_a" / "noise.wav", 22050)

        result = self.run_preparer()
        self.assertEqual(result.returncode, 0, result.stderr)
        with (self.prepared / "manifest.csv").open(newline="", encoding="utf-8") as handle:
            rows = list(csv.DictReader(handle))
        self.assertEqual(len(rows), 9)
        speaker_splits = {}
        for row in rows:
            speaker = row["speaker_or_source_id"]
            speaker_splits.setdefault(speaker, set()).add(row["split"])
            file = self.prepared / row["relative_path"]
            self.assertTrue(file.is_file())
            with wave.open(str(file), "rb") as wav:
                self.assertEqual(wav.getframerate(), 16000)
                self.assertEqual(wav.getnchannels(), 1)
                self.assertEqual(wav.getsampwidth(), 2)
                self.assertGreater(wav.getnframes(), 0)
        self.assertTrue(all(len(splits) == 1 for splits in speaker_splits.values()))
        summary = json.loads((self.prepared / "summary.json").read_text())
        self.assertEqual(summary["files"], 9)
        self.assertGreater(summary["split_counts"]["test"], 0)
        self.assertGreater(summary["split_counts"]["validation"], 0)

    def test_rejects_output_nested_under_input(self):
        write_test_wav(self.raw / "positive" / "speaker_a" / "sample.wav", 16000)
        result = self.run_preparer(self.raw / "prepared")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Output must be outside", result.stderr)

    def test_rejects_audio_outside_length_range(self):
        write_test_wav(self.raw / "positive" / "speaker_a" / "short.wav", 16000, seconds=0.1)
        result = self.run_preparer()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Expected clip length", result.stderr)


if __name__ == "__main__":
    unittest.main()
