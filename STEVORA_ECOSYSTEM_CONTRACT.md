# STEVORA Ecosystem — Android integration contract

Status: **approved project-wide standards incorporated as Android release checks**, 2026-11-18.
Scope: Android Companion, with cross-product interfaces coordinated in **[STE-00] Ecosystem Core**. Implementation remains in **[STE-02] Android**; verified build/release history belongs in **[STE-06] Memory & Releases**.

## Product boundaries
- STEVORA Home Panel, Android Companion, iOS Companion and Hey Stevora are distinct products sharing a brand, user-facing conventions and secure Home Assistant interoperability.
- Android is a client of the existing Home Assistant authenticated API. Never assume undocumented iOS, Home Panel or voice endpoints exist.
- Cross-product API/schema changes require a separate agreed specification and compatibility tests; no silent breaking changes.

## Non-negotiable Android behavior
1. **Update continuity:** preserve `com.stevora.companion`, existing release signer, strictly increasing `versionCode`, authenticated account, Home Hub URL, settings, and permissions. No uninstall/data reset.
2. **Security and consent:** preserve OAuth/2FA and HTTPS validation; never hard-code secrets or log credentials; require explicit confirmation for destructive or security-sensitive AI actions; respect HA role and entity permissions.
3. **Reliable state:** live WebSocket state changes are authoritative; on disconnect show offline state and last-known read-only readings, and prevent commands until connected. No fabricated device readings.
4. **Device scope:** only expose intended entities and permitted actions. **Do not add or operate lamp backlight controls**; their backlights are intended to remain ON.
5. **Climate and lighting:** retain whole-degree climate adjustment for the configured device and supported RGB/brightness controls; do not claim unsupported device capabilities.
6. **Presentation:** STEVORA branding, dark premium design, Bulgarian and English user-facing text, consistent navigation and accessible controls. Do not reintroduce default Home Assistant branding in STEVORA-specific surfaces.
7. **Voice and AI:** `Hey Stevora` is a planned integration, not a shipped Android feature. Microphone, wake word, background listening and AI automation changes require explicit permission, transparent status and device acceptance.
8. **Distribution:** signed full APK must pass static gates, signature/package/version verification and actual on-device upgrade testing. CI success alone is not a public Stable release approval.

## Evidence and release states
Track each capability separately as **planned / implemented / tested / released**. Include GitHub commit, Actions run, artifact and on-device acceptance where known. Avoid claiming untested functionality or Firebase push provisioning.

## Scope of the automated gate
`scripts/check_stevora_ecosystem.py` validates repository-level invariants and the presence of this contract. It is **not** a comprehensive security audit, UI localization test, device compatibility test or verification of the other products. Human acceptance remains mandatory.
