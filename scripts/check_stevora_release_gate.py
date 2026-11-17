#!/usr/bin/env python3
"""Release gate: STEVORA startup branding and authentication compatibility."""
from pathlib import Path
import sys
import hashlib
import xml.etree.ElementTree as ET
import struct

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
logo_path = res / "drawable-nodpi/stevora_logo.png"
if not logo_path.is_file() or (res / "drawable-nodpi/stevora_logo.jpg").exists():
    sys.exit("Release gate failed: the official PNG must be the only stevora_logo drawable")
png_bytes = logo_path.read_bytes()
if (len(png_bytes) < 24 or png_bytes[:8] != bytes.fromhex("89504e470d0a1a0a") or png_bytes[12:16] != b"IHDR"
        or struct.unpack(">II", png_bytes[16:24]) != (512, 512)):
    sys.exit("Release gate failed: official logo is not a 512x512 PNG")
if hashlib.sha256(png_bytes).hexdigest() != "58e54712a07d909695a4bbdef5ef0d80dccae05e2485730011b8e588951349e0":
    sys.exit("Release gate failed: logo SHA-256 does not match approved original")
apk_verifier = (root / "scripts/verify_stevora_apk.py").read_text()
if "res/drawable-nodpi-v4/stevora_logo.png" not in apk_verifier:
    sys.exit("Release gate failed: compiled APK verification must validate the approved PNG")
welcome = (root / "app/src/main/kotlin/io/homeassistant/companion/android/onboarding/welcome/WelcomeTemplate.kt").read_text()
if "painterResource(R.drawable.stevora_logo)" not in welcome:
    sys.exit("Release gate failed: onboarding does not use original logo")

checks = {
    "drawable/stevora_brand_mark.xml": "stevora_logo",
    "drawable/launch_screen_background.xml": "stevora_brand_mark",
    "drawable-v26/launch_screen_background.xml": "stevora_brand_mark",
    "drawable-v31/app_icon_launch_screen.xml": "stevora_brand_mark",
    "values-v31/styles.xml": "stevora_adaptive_foreground",
    "mipmap-anydpi-v26/ic_launcher.xml": "stevora_adaptive_foreground",
    "mipmap-anydpi-v26/ic_launcher_round.xml": "stevora_adaptive_foreground",
    "debug/res/mipmap-anydpi-v26/ic_launcher.xml": "stevora_adaptive_foreground",
    "debug/res/mipmap-anydpi-v26/ic_launcher_round.xml": "stevora_adaptive_foreground",
}
for relative, expected in checks.items():
    path = (root / "app/src" / relative) if relative.startswith("debug/") else (res / relative)
    data = path.read_text()
    ET.fromstring(data)
    if expected not in data:
        sys.exit(f"Release gate failed: {path} does not reference {expected}")
    if "app_icon_launch" in data or "ic_launcher_foreground" in data or "ic_home_assistant_branding" in data:
        sys.exit(f"Release gate failed: legacy HA splash reference in {path}")

auth = (root / "common/src/main/kotlin/io/homeassistant/companion/android/common/data/authentication/impl/AuthenticationService.kt").read_text()
connection = (root / "app/src/main/kotlin/io/homeassistant/companion/android/onboarding/connection/ConnectionViewModel.kt").read_text()
if "CLIENT_ID" not in auth or "auth/authorize" not in connection or "redirect_uri" not in connection:
    sys.exit("Release gate failed: OAuth compatibility requires manual review")
print("STEVORA startup brand references validated; existing OAuth flow preserved.")
print("Manual release tests still required: 2FA, login, refresh, logout, session expiry, reconnect, offline.")
