#!/usr/bin/env python3
"""Static STEVORA Ecosystem release contract checks; not a device/security audit."""
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
read = lambda path: (ROOT / path).read_text(encoding="utf-8")
app = read("app/build.gradle.kts")
workflow = read(".github/workflows/stevora-full-release.yml")
roadmap = read("STEVORA_ANDROID_ROADMAP.md")
contract = read("STEVORA_ECOSYSTEM_CONTRACT.md")
gate = read("scripts/check_stevora_update_contract.py")
stevora_dir = ROOT / "app/src/main/kotlin/io/homeassistant/companion/android/stevora"
sources = "\n".join(p.read_text(encoding="utf-8") for p in stevora_dir.rglob("*.kt"))
checks = {
    "Ecosystem contract exists": "STEVORA Ecosystem" in contract,
    "Ecosystem contains OS + two native apps only": "STEVORA Ecosystem = STEVORA OS + STEVORA App for Android + STEVORA App for iOS" in contract,
    "Android is native STEVORA OS client": "native client of STEVORA OS" in contract and "source of truth" in contract,
    "System modules bundled into OS": all(term in contract for term in ("Stevora Core", "Stevora Universal Integration", "Stevora Home Panel 7.1.1 Premium", "Stevora Home Hub", "Stevora Frontend", "Stevora People & Presence", "Stevora Manifest")),
    "Hey Stevora uses installed providers and customer package": all(term in contract for term in ("Hey Stevora is a built-in", "purchased", "configured")),
    "Memory is private owner-only OS extension": all(term in contract for term in ("owner's homes only", "NOT bundled in Android", "NOT enabled by default")),
    "Android product ownership": "[STE-02]" in contract and "[STE-00]" in contract,
    "Release history ownership": "[STE-06]" in contract,
    "App identity preserved": 'applicationId = "com.stevora.companion"' in app,
    "Release signing secrets used": "STEVORA_KEYSTORE_BASE64" in workflow,
    "Full signed release build": ":app:assembleFullRelease" in workflow,
    "APK signature verification": 'apksigner" verify' in workflow,
    "APK package verification": "com.stevora.companion" in workflow,
    "Version code from CI": "VERSION_CODE:" in workflow,
    "Update contract still enabled": "check_stevora_update_contract.py" in workflow,
    "Ecosystem gate enabled": "python3 scripts/check_stevora_ecosystem.py" in workflow,
    "Roadmap covers 2.1 through 3.0": all(x in roadmap for x in ("2.1", "2.2", "2.3", "2.4", "3.0")),
    "Existing app upgrade contract intact": "STEVORA applicationId" in gate,
    "STEVORA native implementation present": bool(sources.strip()),
    "No forbidden backlight entity controls": not any(
        token in sources.lower()
        for token in ('light.backlight', 'switch.backlight', 'light.lamp_backlight')
    ),
    "Security and consent requirements documented": all(
        phrase in contract for phrase in ("OAuth/2FA", "explicit confirmation", "HTTPS")
    ),
    "Bilingual and voice requirements documented": all(
        phrase in contract for phrase in ("Bulgarian and English", "Hey Stevora")
    ),
}
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if not all(checks.values()):
    sys.exit("STEVORA Ecosystem compliance gate failed")
print("Static ecosystem contract passed. Device and cross-product acceptance still required.")
