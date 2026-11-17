#!/usr/bin/env python3
"""Verify STEVORA logo and generate consistent Android legacy launcher icons."""
from pathlib import Path
from PIL import Image, ImageOps, ImageDraw, ImageFont
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
        # Adaptive launcher icons need a safe-zone foreground, not the inherited
        # Home Assistant launcher artwork. Generated from the packaged STEVORA logo.
        adaptive = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
        artwork = ImageOps.contain(logo, (288, 288), Image.Resampling.LANCZOS)
        adaptive.paste(artwork, ((432 - artwork.width) // 2, (432 - artwork.height) // 2))
        adaptive.save(RES / "drawable-nodpi/stevora_adaptive_foreground.png", format="PNG", optimize=True)
        print("STEVORA adaptive launcher foreground OK")

        for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
            canvas = Image.new("RGBA", (size, size), (8, 19, 31, 255))
            inset = round(size * 0.08)
            scaled = ImageOps.contain(logo, (size - 2 * inset, size - 2 * inset), Image.Resampling.LANCZOS)
            canvas.paste(scaled, ((size - scaled.width) // 2, (size - scaled.height) // 2))
            for name in ("ic_launcher.png", "ic_launcher_round.png"):
                for variant in ("main", "debug"):
                    dest = ROOT / f"app/src/{variant}/res/mipmap-{density}/{name}"
                    dest.parent.mkdir(parents=True, exist_ok=True)
                    canvas.save(dest, format="PNG", optimize=True)
                    with Image.open(dest) as check:
                        check.verify()
            print(f"STEVORA launcher icons OK: {density} {size}x{size}")
        # Android TV banner, replacing the inherited Home Assistant branding.
        banner = Image.new("RGB", (320, 180), (8, 19, 31))
        art = ImageOps.contain(logo, (128, 128), Image.Resampling.LANCZOS)
        banner.paste(art, (12, (180 - art.height) // 2))
        draw = ImageDraw.Draw(banner)
        try:
            font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 25)
        except OSError:
            font = ImageFont.load_default()
        draw.text((148, 74), "STEVORA", fill=(39, 185, 245), font=font)
        banner_path = RES / "mipmap-xhdpi/ic_banner.png"
        banner.save(banner_path, format="PNG", optimize=True)
        with Image.open(banner_path) as check:
            check.verify()
        print("STEVORA Android TV banner OK")
        print(f"STEVORA logo OK: {logo.width}x{logo.height}, {len(data)} bytes")
except Exception as exc:
    print(f"STEVORA branding validation FAILED: {exc}", file=sys.stderr)
    sys.exit(1)
