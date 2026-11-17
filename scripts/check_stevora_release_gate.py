#!/usr/bin/env python3
"""Release gate: STEVORA startup branding and authentication compatibility."""
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
res = root / "app/src/main/res"
checks = {
    "drawable/launch_screen_background.xml": "stevora_brand_mark",
    "drawable-v26/launch_screen_background.xml": "stevora_brand_mark",
    "drawable-v31/app_icon_launch_screen.xml": "stevora_brand_mark",
    "values-v31/styles.xml": "stevora_brand_mark",
    "debug/res/mipmap-anydpi-v26/ic_launcher.xml": "stevora_brand_mark",
    "debug/res/mipmap-anydpi-v26/ic_launcher_round.xml": "stevora_brand_mark",
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
