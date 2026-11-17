#!/usr/bin/env python3
"""Guard the STEVORA in-place update contract before signed release builds."""
from pathlib import Path
import sys

root = Path(__file__).resolve().parents[1]
app = (root / "app/build.gradle.kts").read_text()
workflow = (root / ".github/workflows/stevora-full-release.yml").read_text()
checks = {
    "STEVORA applicationId": 'applicationId = "com.stevora.companion"' in app,
    "release signing secret": "STEVORA_KEYSTORE_BASE64" in workflow,
    "signed APK verification": 'apksigner" verify' in workflow,
    "expected package validation": "package: name='com.stevora.companion'" in workflow,
    "build number source": "VERSION_CODE:" in workflow,
    "release 2.1 versionName": 'versionName = "2.1.0"' in app,
    "release 2.1 APK validation": "versionName=\'2.1.0-full\'" in workflow,
    "full release flavor": ":app:assembleFullRelease" in workflow,
    "APK artifact upload": "actions/upload-artifact@" in workflow,
    "approved roadmap": (root / "STEVORA_ANDROID_ROADMAP.md").exists(),
}
for name, passed in checks.items():
    print(f"{'PASS' if passed else 'FAIL'}: {name}")
if not all(checks.values()):
    sys.exit("STEVORA upgrade contract failed")
print("Repository upgrade contract validated; on-device update acceptance still required.")
