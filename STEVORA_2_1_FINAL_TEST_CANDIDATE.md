# STEVORA Companion 2.1.0 — Final Test Candidate

## Implemented in this candidate
- Native STEVORA Home Overview and room controls with event-driven WebSocket state updates.
- Reconnection after disconnection; control commands disabled while the live stream is down.
- Last-known values retained as read-only on Home Overview when the connection drops.
- State_changed drives control tile updates; no whole-home getStates request after each action.
- Existing native lighting, LED color/brightness, climate, media and room filtering.
- STEVORA settings shortcut to pin the existing Android entity widget, with launcher fallback.
- Same com.stevora.companion package and signing secrets in Full Release workflow.

## Acceptance checklist — requires a real Android device
- [ ] Full Release GitHub Actions build succeeds, signature and package validated.
- [ ] Compare signing certificate SHA-256 with the installed 2.0 APK.
- [ ] Compare 2.1 versionCode against the installed 2.0 versionCode (must be strictly greater).
- [ ] Install APK over existing 2.0 without uninstalling; account, Home Hub URL, settings and session retained.
- [ ] Verify instant updates from a separate Home Assistant controller for all visible rooms.
- [ ] Switch Wi-Fi off and on; verify offline indicator, disabled controls, reconnection and restored state.
- [ ] Force stop, resume, lock/unlock and network switch; verify WebSocket updates resume.
- [ ] Verify climate temperature/modes and LED color/brightness on actual devices.
- [ ] Add entity widget from STEVORA Settings; test supported and unsupported launchers.
- [ ] Verify Bulgarian/English, tablet layout, no backlight controls and read-only HomePod metrics.
- [ ] Test sign-out and account security; no unauthorized admin actions.
- [ ] Validate Firebase/FCM separately if push is required; build success does not prove push provisioning.

## Release decision
This is a **final test candidate**, not an official 2.1 stable release until all acceptance checks pass. The separate minimal APK workflow is not the canonical Full Release distribution.
