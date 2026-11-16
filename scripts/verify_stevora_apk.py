#!/usr/bin/env python3
"""Static verification of STEVORA brand assets in the compiled APK."""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import io
import sys

root = Path(__file__).resolve().parents[1]
apks = sorted((root / "app/build/outputs/apk").rglob("*.apk"))
if not apks:
    sys.exit("No APK was generated")
for apk in apks:
    with ZipFile(apk) as archive:
        bad = archive.testzip()
        if bad:
            sys.exit(f"Corrupt APK ZIP member: {bad}")
        entries = set(archive.namelist())
        logo = "res/drawable-nodpi-v4/stevora_logo.jpg"
        if logo not in entries:
            sys.exit(f"Missing STEVORA logo in {apk.name}")
        required = [f"res/mipmap-{d}-v4/ic_launcher.png" for d in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")]
        required += [f"res/mipmap-{d}-v4/ic_launcher_round.png" for d in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")]
        for entry in [logo, *required]:
            if entry not in entries:
                sys.exit(f"Missing brand asset: {entry}")
            with Image.open(io.BytesIO(archive.read(entry))) as im:
                im.verify()
        print(f"STEVORA APK brand resources validated: {apk.name} ({apk.stat().st_size} bytes)")
