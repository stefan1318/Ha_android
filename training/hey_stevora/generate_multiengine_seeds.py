#!/usr/bin/env python3
"""Reproducible dual-engine Hey STEVORA TTS seed generation.

Engines: eSpeak (Bulgarian and English), FFmpeg libflite (English).
This creates *synthetic* training candidates, not human age/gender samples.
The expanded private dataset also includes deterministic noise/reverb/EQ augmentation;
see SYNTHETIC_EXPANSION_PROTOCOL.md. Never split augmented source groups across
train and test, and never upload real human voice recordings to public GitHub.
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

POSITIVE = {"bg": ["Хей Стевора", "Хей, Стевора!"],
            "en": ["Hey Stevora", "Hey, Stevora!"]}
NEGATIVE = {"bg": ["Хей Сири", "Окей Гугъл", "Окей Набу", "Алекса",
                   "Хей Джарвис", "Стевора", "Окей Стевора", "Хей Стефан",
                   "Хей Стивън", "Хей Стела", "Хей Стефора", "Здравей Стевора",
                   "Хей Стева", "Хей Стора"],
            "en": ["Hey Siri", "Okay Google", "Okay Nabu", "Alexa",
                   "Hey Jarvis", "Stevora", "Okay Stevora", "Hey Steven",
                   "Hey Stella", "Hey Stefora", "Hey Steve", "Hey store",
                   "Hey Trevor", "Hello Stevora"]}
ESPEAK = {"bg": ["bg", "bg+f1", "bg+f3", "bg+f5", "bg+m1", "bg+m3",
                 "bg+m5", "bg+klatt", "bg+whisper"],
          "en": ["en-us", "en-gb", "en-sc", "en-uk-north", "en-uk-rp",
                 "en-uk-wmids", "en-wi", "en-us+f2", "en-gb+f4",
                 "en-us+m3", "en-us+klatt", "en-gb+whisper"]}
FLITE = ["awb", "kal", "kal16", "rms", "slt"]
RATE = 16000


def pcm_from_wav(payload):
    with wave.open(io.BytesIO(payload), "rb") as wav:
        rate = wav.getframerate()
        channels = wav.getnchannels()
        if wav.getsampwidth() != 2:
            raise ValueError("Expected PCM16")
        signal = np.frombuffer(wav.readframes(wav.getnframes()), dtype="<i2").astype(np.float32)
    if channels != 1:
        signal = signal.reshape(-1, channels).mean(axis=1)
    if rate != RATE:
        factor = gcd(rate, RATE)
        signal = resample_poly(signal, RATE // factor, rate // factor)
    return signal


def espeak(voice, text, speed, pitch):
    result = subprocess.run(["espeak", "-v", voice, "-s", str(speed), "-p",
                             str(pitch), "--stdout", text],
                            capture_output=True, check=True)
    return pcm_from_wav(result.stdout)


def flite(voice, text, tempo):
    escaped = text.replace("\\", "\\\\").replace("'", "\\'")
    expression = f"flite=text='{escaped}':voice={voice},atempo={tempo}"
    result = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error",
                             "-f", "lavfi", "-i", expression, "-ar", str(RATE),
                             "-ac", "1", "-f", "wav", "-c:a", "pcm_s16le", "-"],
                            capture_output=True, check=True)
    return pcm_from_wav(result.stdout)


def write_wav(path, signal):
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as wav:
        wav.setnchannels(1)
        wav.setsampwidth(2)
        wav.setframerate(RATE)
        wav.writeframes(np.clip(np.rint(signal), -32768, 32767).astype("<i2").tobytes())


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    rows = []
    for lang, voices in ESPEAK.items():
        for voice in voices:
            for profile, (speed, pitch) in enumerate([(123, 28), (152, 68), (177, 83), (111, 37)]):
                for rep in range(3):
                    for label, phrases in (("positive", POSITIVE[lang]),
                                           ("negative_speech", NEGATIVE[lang])):
                        # Keep all negative phrases for the reproducible seed corpus.
                        for text in phrases:
                            audio = espeak(voice, text, speed + (rep-1)*11,
                                           pitch + (rep-1)*6)
                            group = f"espeak_{lang}_{voice.replace('+','_')}_style{profile}"
                            rel = Path(label) / group / f"{len(rows):06d}.wav"
                            write_wav(args.output / rel, audio)
                            rows.append([str(rel), label, lang, text, "espeak",
                                         voice, group, "synthetic"])
    for voice in FLITE:
        for rep, tempo in enumerate([0.90, 1.0, 1.11]):
            for label, phrases in (("positive", POSITIVE["en"]),
                                   ("negative_speech", NEGATIVE["en"])):
                for text in phrases:
                    audio = flite(voice, text, tempo)
                    group = f"flite_en_{voice}"
                    rel = Path(label) / group / f"{len(rows):06d}.wav"
                    write_wav(args.output / rel, audio)
                    rows.append([str(rel), label, "en", text, "flite",
                                 voice, group, "synthetic"])
    args.output.mkdir(parents=True, exist_ok=True)
    with (args.output / "manifest.csv").open("w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        writer.writerow(["relative_path", "label", "language", "text",
                         "engine", "voice_preset", "source_group", "origin"])
        writer.writerows(rows)
    print(f"Generated {len(rows)} synthetic seed clips; verify pronunciation before use")


if __name__ == "__main__":
    main()
