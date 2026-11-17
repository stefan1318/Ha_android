# STEVORA Companion — approved development roadmap

Approved by project owner: 2026-11-17. Baseline: installed and device-tested **2.0.0 Full Release**.

## Immutable upgrade contract
- Keep Android application ID `com.stevora.companion` and the **same release signing key/certificate**.
- Increase Android `versionCode` above the version installed on the device; update `versionName` for each shipped release.
- Install new APK as an **update** over the current Full Release; do not uninstall or clear app data.
- Preserve authenticated server connections, settings, role boundaries and permission state; never log secrets.
- Validate OAuth/2FA, local and remote HTTPS, session refresh, offline/reconnect, Android 10 and newer.
- Do not treat passing CI as proof that the upgrade works on-device.
- Keep previously working features and STEVORA branding. FCM requires actual Firebase configuration.
- Before distributing an update, archive the previous known-good signed APK and its signing fingerprint.

## Release roadmap
| Release | Scope | Acceptance |
|---|---|---|
| 2.0.1 | Stability & Update Foundation | Version/signing continuity, migration checks, error diagnostics, upgrade test |
| 2.1 | Native Experience | Native dashboard, rooms, lighting, climate, WebSocket updates, offline handling, widgets |
| 2.2 | AI Control Center | AI chat, BG/EN voice, trained wake word, diagnostics, safe automation authoring |
| 2.3 | Presence & Automation | Opt-in geofencing, Home/Away, Wi-Fi/BLE presence, scenarios |
| 2.4 | Premium Ecosystem | Biometric access, notifications, energy center, multi-home |
| 3.0 | Unified STEVORA | Shared design/onboarding and secure interoperability across Android, iOS, Home Hub |

## 2.0.1 first implementation phase
1. Add repository-level update-contract checks; do not modify the installed 2.0.0 application yet.
2. Confirm signed baseline artifact metadata: package, versionCode and signer SHA-256.
3. Build 2.0.1 with the same signing secrets and a strictly higher versionCode.
4. On a test device, install the new APK over 2.0.0 and confirm retained login/server/settings and native controls.
5. Only after device acceptance, label 2.0.1 as tested.

**Status:** Roadmap approved; individual future features remain planned until implemented and tested.

## 2.1 implementation log — 2026-11-17
- Native room panel now subscribes to HA `state_changed` events via the existing authenticated WebSocket repository.
- Snapshot is fetched on connection/reconnection; incoming changes update only allowlisted, visible entity IDs.
- Five-second delay is now only a reconnection retry, not periodic whole-home polling.
- Follow-up action refresh remains for immediate feedback; device-originated events are authoritative.
- Pending: CI build, real-device reconnect/session testing, remaining 2.1 UX and widget features.
