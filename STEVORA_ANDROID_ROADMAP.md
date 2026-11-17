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

**Status:** Roadmap approved. STEVORA Companion 2.1 Full Release has a successful CI build and a user-confirmed in-place Android upgrade test. Future versions remain planned.

## 2.1 implementation log — 2026-11-17
- Native room panel now subscribes to HA `state_changed` events via the existing authenticated WebSocket repository.
- Snapshot is fetched on connection/reconnection; incoming changes update only allowlisted, visible entity IDs.
- Five-second delay is now only a reconnection retry, not periodic whole-home polling.
- As of final test candidate commit `09ce2c5`, control updates rely on incoming `state_changed` events rather than whole-home snapshot requests after each action.
- CI Full Release build and basic device upgrade test subsequently passed; detailed reconnect/session and STEVORA-specific widget UX acceptance remain outstanding.

## 2.1 offline safety hardening
- Disable native room controls when the live WebSocket connection is unavailable, while keeping last-known values visible for reference.
- Preserve existing room, light color/brightness, climate, and television controls; do not operate hidden or backlight entities.
- Update Settings release label to 2.1 Native Experience.
- Existing upstream Android entity, button, template and media-player widget implementations remain available; STEVORA-specific widget UX and on-device verification are still outstanding.
- No claim of completed offline cache, background push or device upgrade acceptance without tests.

## 2.1 native widget integration
- Added STEVORA Settings entry for Android launcher widget pinning using the existing entity widget provider, with a launcher-compatibility fallback.
- Widget provider and configuration screens are inherited from the Android Companion widget subsystem; this is not a newly authored STEVORA-specific widget design.
- Requires device/launcher acceptance testing.

## Current project checkpoint — 2026-11-17 (confirmed by project owner)
- **Current working Android version:** STEVORA Companion **2.1.0 Native Experience**, final test candidate.
- **Successful signed Full Release CI:** GitHub Actions run [44125247879](https://github.com/stefan1318/Ha_android/actions/runs/44125247879), commit `30c6801f5984b449727b9cbca349722eca8bb715`. GitHub reports conclusion `success`.
- **Real-device acceptance confirmed by project owner:** the APK works; it installed **over 2.0** without uninstalling and preserved the authenticated account, Home Hub address/connection, and settings.
- **Implemented:** native home/room controls, lighting/climate/LED controls, event-driven WebSocket updates, offline read-only last-known values, disabled commands while disconnected, reconnection attempts, and Android widget pinning through the existing provider.
- **Remaining before an unqualified public stable declaration:** archive the signed known-good APK and signing fingerprint; verify broader offline/reconnect, session/security, launcher/widget, and device-compatibility test matrix. The existing Android widget provider is **not yet a bespoke STEVORA-styled widget**. Firebase push provisioning is not assumed.
- **Release posture:** 2.1 is a **working, owner-tested release candidate** and the protected baseline for future development; do not assert full public stable QA or a published GitHub Release without separate verification.
- **Next planned version:** **2.2 AI Control Center** — AI chat, Bulgarian/English voice, trained wake word, diagnostics, and safe automation authoring. Subsequent planned versions remain 2.3 Presence & Automation, 2.4 Premium Ecosystem, 3.0 Unified STEVORA.
- **Upgrade invariants:** keep application ID `com.stevora.companion`, signing identity, and user data; increase versionCode; test upgrades on device. Develop 2.2 separately from the known-good 2.1 baseline.
