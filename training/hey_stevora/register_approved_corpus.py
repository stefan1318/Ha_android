#!/usr/bin/env python3
"""Register owner-approved Hey STEVORA corpus without publishing voice audio.

Usage:
  python training/hey_stevora/register_approved_corpus.py \
    --archive /private/Hey_Stevora_Expanded_v03.zip \
    --output /private/hey_stevora_approved_v03

Checks manifest, unique paths, all audio members, labels and WAV format.
Writes a manifest for training; does not train or publish any audio.
"""
import argparse
import csv
import hashlib
import io
import json
import wave
import zipfile
from collections import Counter
from pathlib import Path

LABELS = {"positive", "negative_speech", "negative_ambient"}
REQUIRED = {"relative_path", "label", "language", "origin", "source_group"}


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--archive", type=Path, required=True)
    p.add_argument("--output", type=Path, required=True)
    args = p.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.archive) as archive:
        names = set(archive.namelist())
        with archive.open("manifest.csv") as source:
            reader = csv.DictReader(io.TextIOWrapper(source, encoding="utf-8"))
            if not REQUIRED.issubset(reader.fieldnames or []):
                raise ValueError(f"Missing manifest columns: {REQUIRED - set(reader.fieldnames or [])}")
            rows = list(reader)
        seen = set()
        counts = Counter()
        for row in rows:
            name = row["relative_path"]
            path = Path(name)
            if path.is_absolute() or ".." in path.parts or name in seen or name not in names:
                raise ValueError(f"Invalid/duplicate/missing audio path: {name}")
            seen.add(name)
            if row["label"] not in LABELS or row["language"] not in {"bg", "en"}:
                raise ValueError(f"Unexpected label/language for {name}")
            with archive.open(name) as audio:
                with wave.open(io.BytesIO(audio.read()), "rb") as wav:
                    if (wav.getframerate(), wav.getnchannels(), wav.getsampwidth()) != (16000, 1, 2):
                        raise ValueError(f"Not 16kHz mono PCM16: {name}")
            counts[row["label"]] += 1
        extra = {name for name in names if name.endswith(".wav")} - seen
        if extra:
            raise ValueError(f"{len(extra)} unindexed WAV files")
    if not rows:
        raise ValueError("Empty dataset")
    fields = list(rows[0]) + ["owner_approval", "training_eligible", "evaluation_policy"]
    with (args.output / "approved_training_manifest.csv").open("w", newline="", encoding="utf-8") as target:
        writer = csv.DictWriter(target, fieldnames=fields)
        writer.writeheader()
        for row in rows:
            writer.writerow({**row, "owner_approval": "approved_2026-11-17",
                             "training_eligible": "yes",
                             "evaluation_policy": "group_by_source_group_no_augmented_leakage"})
    digest = hashlib.sha256(args.archive.read_bytes()).hexdigest()
    summary = {"archive": args.archive.name, "sha256": digest, "owner_approval": "2026-11-17",
               "clips": len(rows), "labels": dict(counts),
               "training_status": "approved_for_training_not_yet_trained",
               "speaker_generalization_validated": False}
    (args.output / "approved_registry.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
