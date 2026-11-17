#!/usr/bin/env python3
"""Generate clearly labeled synthetic Hey STEVORA augmentation WAVs using eSpeak.

Requires espeak, numpy, scipy. No voices are cloned and no human recordings are used.
Voice profiles are *perceived styles*, NOT evidence of actual age or gender.
Do not use synthetic voices as speaker-independent evaluation samples.
Usage: python3 training/hey_stevora/generate_synthetic.py --output /private/synthetic
"""
import argparse
import csv
import io
import subprocess
import wave
from pathlib import Path
from math import gcd
import numpy as np
from scipy.signal import resample_poly

RATE = 16000
PROFILES = (
    ("masculine", "m3", 105, 42),
    ("feminine", "f3", 130, 65),
    ("youthful_synthetic", "f5", 150, 80),
)
LANGUAGES = (("bg", "bg", "Хей Стевора"), ("en", "en-us", "Hey Stevora"))
REPETITIONS = 4


def synthesize(lang, modifier, speed, pitch, phrase):
    cmd = ["espeak", "-v", f"{lang}+{modifier}", "-s", str(speed),
           "-p", str(pitch), "--stdout", phrase]
    result = subprocess.run(cmd, capture_output=True, check=True)
    with wave.open(io.BytesIO(result.stdout), "rb") as wav:
        sr = wav.getframerate()
        channels = wav.getnchannels()
        assert wav.getsampwidth() == 2
        signal = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32)
        if channels > 1:
            signal = signal.reshape(-1, channels).mean(axis=1)
    if sr != RATE:
        g = gcd(sr, RATE)
        signal = resample_poly(signal, RATE // g, sr // g)
    return signal


def write_wav(path, audio):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(np.clip(np.rint(audio), -32768, 32767).astype("<i2").tobytes())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    dest = args.output
    dest.mkdir(parents=True, exist_ok=True)
    rows = []
    for language, voice, phrase in LANGUAGES:
        for profile, modifier, speed, pitch in PROFILES:
            for repetition in range(REPETITIONS):
                rate = speed + (repetition - 1) * 9
                tone = pitch + (repetition - 1) * 4
                audio = synthesize(voice, modifier, rate, tone, phrase)
                path = Path("positive") / f"synthetic_{language}_{profile}" / f"{repetition+1:02}.wav"
                write_wav(dest / path, audio)
                rows.append((str(path), language, profile, phrase, "espeak", rate, tone, "synthetic", "not_real_child_voice"))
    with (dest / "synthetic_manifest.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(("relative_path", "language", "perceived_style", "text", "engine",
                         "speed", "pitch", "origin", "age_note"))
        writer.writerows(rows)
    print(f"Generated {len(rows)} synthetic clips at 16kHz mono into {dest}")


if __name__ == "__main__":
    main()
