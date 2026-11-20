# STEVORA Ecosystem — Android integration contract

Status: **approved project-wide standards incorporated as Android release checks**, 2026-11-18.
Scope: Android Companion, with cross-product interfaces coordinated in **[STE-00] Ecosystem Core**. Implementation remains in **[STE-02] Android**; verified build/release history belongs in **[STE-06] Memory & Releases**.

## Superseding product boundaries — approved 2026-11-20

**STEVORA Ecosystem = STEVORA OS + STEVORA App for Android + STEVORA App for iOS.**

STEVORA Android is a **native client of STEVORA OS**. It is not a standalone
Home Assistant product and must not own a separate source of truth for rooms,
devices, profiles, manifests, categories, user roles, capabilities, package
availability or shared settings. STEVORA OS owns the authenticated versioned
API, per-home/per-user state, preferences and access rights. Android consumes
those services and implements platform-specific UX, platform permissions and
local client presentation without redefining the OS data model.

**Built-in STEVORA OS subsystems, never separate products:** Stevora Core,
Stevora Universal Integration, Stevora Home Panel 7.1.1 Premium, Stevora
Home Hub, Stevora Frontend, Stevora People & Presence and Stevora Manifest.
All future common functionality and cross-device UI specifications belong
to the STEVORA OS roadmap. Android releases implement compatible native views
of available OS functionality, rather than adding independent home logic.

**Hey Stevora is a built-in OS/Android/iOS feature**, not a standalone
customer-installed app. Voice/chat route only to the **installed and configured
AI or voice assistants** available in STEVORA OS for the customer's preference
and purchased package/entitlements. OS authorization and user consent
remain mandatory. If the provider/pipeline is absent, show unavailable
status; never claim that Hey Stevora is active. Native microphone/wake word
and background listening remain platform-dependent and consent-gated.

**STEVORA Memory & Sync is a private optional STEVORA OS extension for the
owner's homes only**. It is NOT a public product, is NOT bundled in Android,
and is NOT enabled by default. Android must not expose owner-only memory
or imply public support.

Until versioned STEVORA OS APIs and migration are implemented, existing
authenticated direct Home Assistant connections are **legacy compatibility
adapters only**, not the future public architecture. Keep working upgrades
and safety behavior; do not pretend the migration is already shipped.

Cross-platform contract changes require an OS-owned specification, version
and compatibility tests against Android and iOS; no silent breakages.

## Non-negotiable Android behavior
1. **Update continuity:** preserve `com.stevora.companion`, existing release signer, strictly increasing `versionCode`, authenticated account, selected STEVORA OS server URL (including legacy Home Hub URL during migration), settings, and permissions. No uninstall/data reset.
2. **Security and consent:** preserve OAuth/2FA and HTTPS validation; never hard-code secrets or log credentials; require explicit confirmation for destructive or security-sensitive AI actions; respect HA role and entity permissions.
3. **Reliable state:** live WebSocket state changes are authoritative; on disconnect show offline state and last-known read-only readings, and prevent commands until connected. No fabricated device readings.
4. **Device scope:** only expose intended entities and permitted actions. **Do not add or operate lamp backlight controls**; their backlights are intended to remain ON.
5. **Climate and lighting:** retain whole-degree climate adjustment for the configured device and supported RGB/brightness controls; do not claim unsupported device capabilities.
6. **Presentation:** STEVORA branding, dark premium design, Bulgarian and English user-facing text, consistent navigation and accessible controls. Do not reintroduce default Home Assistant branding in STEVORA-specific surfaces.
7. **Voice and AI:** Hey Stevora is a **built-in feature target** shared with STEVORA OS and iOS, using actual configured OS AI/voice providers, customer choice and purchased packages. Its future product role is approved; actual voice execution is not yet released. Microphone, wake word, background listening and AI automation require explicit permissions, transparent status and on-device acceptance.
8. **Distribution:** signed full APK must pass static gates, signature/package/version verification and actual on-device upgrade testing. CI success alone is not a public Stable release approval.

## OS-client release gate

- The selected STEVORA OS instance must provide the signed-in user's scoped manifest, devices, rooms, labels, translations, permissions, capabilities and voice/provider entitlements.
- Android must not assert a provider is installed, a package is purchased, or a device is controllable without OS evidence.
- App-specific Android UI/state/preferences may remain native; shared home models and common features are defined in STEVORA OS first.
- Preserve safe signed in-place upgrade from existing Android 2.1 clients while transitioning to OS-connected endpoints.
- Validate at least two OS installations and both admin/read-only roles; separate mobile/store release versions are technical, not independent product roadmaps.

## Evidence and release states
Track each capability separately as **planned / implemented / tested / released**. Include GitHub commit, Actions run, artifact and on-device acceptance where known. Avoid claiming untested functionality or Firebase push provisioning.

## Scope of the automated gate
`scripts/check_stevora_ecosystem.py` validates repository-level invariants and the presence of this contract. It is **not** a comprehensive security audit, UI localization test, device compatibility test or verification of the other products. Human acceptance remains mandatory.
