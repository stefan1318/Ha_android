# STEVORA Android 2.2 — STEVORA OS Dev2 integration candidate

Status: **implementation in progress, NOT an installable or tested release**.
Branch: `feature/stevora-os-dev2-android-2-2`.
Baseline: signed, Firebase-distributed Android 2.1 Full Release (run #44870622618).

## Contract and migration

STEVORA OS is the only authoritative source for rooms, devices, scoped manifest, translations, user roles, permissions, available assistants and entitlements. Android is a native client, not an independent Home Assistant product. Existing HA connections remain a compatibility adapter until an authenticated, versioned STEVORA OS API exists and has been tested.

The new `StevoraOsDiscovery` parser is a non-networking, fail-closed building block for a **proposed** version-1 discovery response. The STEVORA OS server must implement and approve this response before it is used by Android. No endpoint path or server availability is assumed.

Proposed JSON discovery body (not yet a deployed API):

```json
{
  "api_version": 1,
  "installation_id": "unique-installation-identifier",
  "capabilities": ["rooms", "devices", "manifest", "translations", "permissions", "home_panel"],
  "home_panel_path": "/stevora"
}
```

Only known capabilities are recognized; missing capabilities are treated as unavailable. Discovery alone never authorizes control. Every subsequent request must be authenticated and server-authorized, scoped to installation and user.

## Required work before any Firebase 2.2 build

1. Agree the OS-owned discovery endpoint, versioning and auth contract; implement it on STEVORA OS Dev2.
2. Implement Android authenticated discovery, session lifecycle, fallback and safe reconnect. Do not bypass HA OAuth/MFA.
3. Consume OS-scoped rooms/devices/manifest/translations/permissions and render native Home Panel; do not invent entity state or expose backlight controls.
4. Gate Hey Stevora on OS-reported configured providers, entitlements, platform consent and actual execution availability.
5. Add contract tests for valid, invalid, absent, unauthorized, offline and cross-home responses; test admin and read-only roles on two separate OS installations.
6. Preserve `com.stevora.companion`, signer, increasing versionCode, user settings and in-place upgrade from 2.1.
7. Pass Android lint, detekt, ktlint, build, APK signature, Firebase distribution and real-device acceptance before labeling release.

## Evidence

- Android 2.1 signed Firebase release: https://github.com/stefan1318/Ha_android/actions/runs/44870622618
- STEVORA OS native-image development: https://github.com/stefan1318/stevora-home-assistant-apps/tree/feature/stevora-os-native-integrated-rc0-20261121
- No claim is made that OS Dev2 currently exposes this discovery contract.
