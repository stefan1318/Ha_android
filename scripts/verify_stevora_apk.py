#!/usr/bin/env python3
"""Verify signed STEVORA APK brand evidence without assuming AAPT resource filenames."""
from pathlib import Path
from zipfile import ZipFile
from PIL import Image
import hashlib
import io
import sys

root = Path(__file__).resolve().parents[1]
approved = (root / "app/src/main/res/drawable-nodpi/stevora_logo.png").read_bytes()
expected_sha = "58e54712a07d909695a4bbdef5ef0d80dccae05e2485730011b8e588951349e0"
if hashlib.sha256(approved).hexdigest() != expected_sha:
    sys.exit("Approved STEVORA logo checksum mismatch")
apks = sorted((root / "app/build/outputs/apk/full/release").glob("*.apk"))
if len(apks) != 1:
    sys.exit(f"Expected one Full Release APK, found {len(apks)}")
for apk in apks:
    with ZipFile(apk) as archive:
        bad = archive.testzip()
        if bad:
            sys.exit(f"Corrupt APK ZIP member: {bad}")
        entries = set(archive.namelist())
        if "AndroidManifest.xml" not in entries or "resources.arsc" not in entries:
            sys.exit("Missing Android manifest or resource table")
        # AAPT/R8 may rename resource ZIP entries; assets retain stable paths.
        brand_asset = "assets/stevora/approved_logo.png"
        if brand_asset not in entries:
            sys.exit(f"Missing packaged brand evidence: {brand_asset}")
        image_bytes = archive.read(brand_asset)
        if hashlib.sha256(image_bytes).hexdigest() != expected_sha:
            sys.exit("Packaged STEVORA logo does not match approved original")
        with Image.open(io.BytesIO(image_bytes)) as image:
            image.verify()
        with Image.open(io.BytesIO(image_bytes)) as image:
            if image.format != "PNG" or image.size != (512, 512):
                sys.exit("Packaged STEVORA logo is not the approved 512x512 PNG")
        print(f"STEVORA approved logo verified in {apk.name} ({apk.stat().st_size} bytes)")
