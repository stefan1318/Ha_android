#!/usr/bin/env python3
"""Verify STEVORA logo and generate consistent Android legacy launcher icons."""
from pathlib import Path
from PIL import Image, ImageOps
import io
import sys

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
SOURCE = RES / "drawable-nodpi/stevora_logo.jpg"

try:
    data = SOURCE.read_bytes()
    with Image.open(io.BytesIO(data)) as logo:
        logo.verify()
    with Image.open(io.BytesIO(data)) as logo:
        logo.load()
        if logo.format != "JPEG" or logo.width < 64 or logo.height < 64:
            raise ValueError("Expected a valid STEVORA JPEG logo at least 64x64")
        logo = logo.convert("RGB")
        for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
            canvas = Image.new("RGBA", (size, size), (8, 19, 31, 255))
            inset = round(size * 0.08)
            scaled = ImageOps.contain(logo, (size - 2 * inset, size - 2 * inset), Image.Resampling.LANCZOS)
            canvas.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2))
            for name in ("ic_launcher.png", "ic_launcher_round.png"):
                dest = RES / f"mipmap-{density}/{name}"
                dest.parent.mkdir(parents=True, exist_ok=True)
                canvas.save(dest, format="PNG", optimize=True)
                with Image.open(dest) as check:
                    check.verify()
            print(f"STEVORA launcher icons OK: {density} {size}x{size}")
        print(f"STEVORA logo OK: {logo.width}x{logo.height}, {len(data)} bytes")
except Exception as exc:
    print(f"STEVORA branding validation FAILED: {exc}", file=sys.stderr)
    sys.exit(1)
