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
        logo_candidates = sorted(p for p in entries if p.startswith("res/") and p.endswith("/stevora_logo.png"))
        if not logo_candidates:
            diagnostic = sorted(p for p in entries if "stevora" in p.lower() or "launcher" in p.lower())
            sys.exit(f"Missing STEVORA logo in {apk.name}; related entries: {diagnostic[:35]}")
        logo = logo_candidates[0]
        required = []
        for density in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
            for name in ("ic_launcher.png", "ic_launcher_round.png"):
                matches = sorted(p for p in entries if p.startswith(f"res/mipmap-{density}") and p.endswith("/" + name))
                if not matches:
                    sys.exit(f"Missing STEVORA launcher asset: {density}/{name}")
                required.append(matches[0])
        banner = sorted(p for p in entries if p.startswith("res/mipmap-xhdpi") and p.endswith("/ic_banner.png"))
        if not banner:
            sys.exit("Missing STEVORA banner asset: xhdpi/ic_banner.png")
        required.append(banner[0])
        for entry in [logo, *required]:
            if entry not in entries:
                sys.exit(f"Missing brand asset: {entry}")
            with Image.open(io.BytesIO(archive.read(entry))) as im:
                im.verify()
            if entry == logo:
                with Image.open(io.BytesIO(archive.read(entry))) as im:
                    if im.size != (512, 512) or im.format != "PNG":
                        sys.exit("Compiled STEVORA logo must be the approved 512x512 PNG")
        print(f"STEVORA APK brand resources validated: {apk.name} ({apk.stat().st_size} bytes)")
