#!/usr/bin/env python3
"""Validate/normalize consented WAV files and split by speaker (NumPy/SciPy).

This is a dataset preparation tool, NOT a wake-word trainer.
Usage: python3 training/hey_stevora/prepare_dataset.py --input /private/raw --output /private/prepared
"""
import argparse
import csv
import hashlib
import json
import random
import wave
import numpy as np
from scipy.signal import resample_poly
from math import gcd
from pathlib import Path

LABELS = ("positive", "negative_speech", "negative_ambient")
RATE = 16000


def normalize(source: Path, target: Path):
    with wave.open(str(source), "rb") as reader:
        channels, width, rate = reader.getnchannels(), reader.getsampwidth(), reader.getframerate()
        if reader.getcomptype() != "NONE" or channels not in (1, 2) or width not in (1, 2, 3, 4):
            raise ValueError("Unsupported WAV encoding")
        frames = reader.readframes(reader.getnframes())
    # Decode integer PCM to floating point before channel mixing and resampling.
    if width == 1:
        audio = (np.frombuffer(frames, dtype=np.uint8).astype(np.float32) - 128.0) / 128.0
    elif width == 2:
        audio = np.frombuffer(frames, dtype="<i2").astype(np.float32) / 32768.0
    elif width == 3:
        raw = np.frombuffer(frames, dtype=np.uint8).reshape(-1, 3).astype(np.int32)
        vals = raw[:, 0] | (raw[:, 1] << 8) | (raw[:, 2] << 16)
        vals = np.where(vals & 0x800000, vals - 0x1000000, vals)
        audio = vals.astype(np.float32) / 8388608.0
    else:
        audio = np.frombuffer(frames, dtype="<i4").astype(np.float32) / 2147483648.0
    if channels == 2:
        audio = audio.reshape(-1, 2).mean(axis=1)
    if rate != RATE:
        divisor = gcd(rate, RATE)
        audio = resample_poly(audio, RATE // divisor, rate // divisor)
    if not (RATE // 5 <= len(audio) <= RATE * 20):
        raise ValueError("Expected clip length between 0.2 and 20 seconds")
    frames = np.round(np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()
    target.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(target), "wb") as writer:
        writer.setnchannels(1)
        writer.setsampwidth(2)
        writer.setframerate(RATE)
        writer.writeframes(frames)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    source = args.input.resolve()
    dest = args.output.resolve()
    if dest == source or source in dest.parents:
        parser.error("Output must be outside the input directory")
    if dest in source.parents:
        parser.error("Input must not be inside output directory")
    speakers = sorted({p.parent.name for label in LABELS for p in (source / label).glob("*/*.wav")})
    if not speakers:
        parser.error("No WAV recordings found under <label>/<speaker_id>/")
    rng = random.Random(args.seed)
    rng.shuffle(speakers)
    n = len(speakers)
    test_count = max(1, round(n * 0.15)) if n >= 3 else 0
    val_count = max(1, round(n * 0.15)) if n >= 3 else 0
    split = {speaker: ("test" if i < test_count else "validation" if i < test_count + val_count else "train") for i, speaker in enumerate(speakers)}
    rows = []
    for label in LABELS:
        for path in sorted((source / label).glob("*/*.wav")):
            speaker = path.parent.name
            digest = hashlib.sha256(path.read_bytes()).hexdigest()
            relative = Path(split[speaker]) / label / speaker / (digest[:16] + ".wav")
            try:
                normalize(path, dest / relative)
            except (ValueError, wave.Error, EOFError) as exc:
                raise SystemExit(f"Invalid audio {path}: {exc}")
            rows.append((str(relative), split[speaker], label, speaker, digest))
    dest.mkdir(parents=True, exist_ok=True)
    with (dest / "manifest.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(("relative_path", "split", "label", "speaker_or_source_id", "sha256_original"))
        writer.writerows(rows)
    (dest / "summary.json").write_text(json.dumps({"files": len(rows), "speakers_or_sources": n, "split_counts": {key: sum(1 for row in rows if row[1] == key) for key in ("train", "validation", "test")}}, indent=2))
    print(f"Prepared {len(rows)} WAV files in {dest}")


if __name__ == "__main__":
    main()
