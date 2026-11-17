# Hey STEVORA Voice ID — shared-device prototype

**Scope:** multiple speaker enrollment profiles under one signed-in STEVORA/Home Assistant account, suitable for a wall tablet. This is not Home Assistant user authentication or a substitute for authorization.

## Implemented
- Up to 12 locally named speaker profiles, each with a random UUID.
- Select active profile on a shared device; record up to 20 local samples per profile.
- Per-profile opt-in for **future** confirmed self-learning (no actual learning engine is running).
- Remove one speaker with confirmation, including that speaker's local audio; delete samples of selected speaker.
- Existing voice model detection and Assist settings remain unchanged.

## Not implemented
- Speaker recognition, voice embeddings, biometric matching, secure template encryption.
- Continuous wake-word listener or working Hey Stevora microWakeWord TFLite model.
- Autonomous self-training or cross-device profile synchronization.
- Person-specific HA permissions. Voice identification must never grant sensitive actions on its own.

## Next implementation gates
1. Build debug APK from `feature/hey-stevora-voice-id-prototype` and test microphone consent, recording, per-profile isolation and deletion.
2. Add silence/clipping/duration quality checks and sample-consent confirmation.
3. Integrate licensed speaker embedding model, calibration and impostor/replay testing.
4. Only after validated recognition: high-confidence quarantine of candidate samples, explicit owner confirmation, per-profile learning history and rollback.
5. Shared wall-panel policy: unrecognized speaker gets guest-safe responses; require authenticated confirmation for sensitive actions; separate panel device identity from actual person identity.
6. iOS foreground enrollment and shared server mapping, with Apple background-audio restrictions respected.

**Safety:** do not auto-associate a voice with the signed-in account merely because the wall panel uses that account. Enrollment audio stays in app-private storage; shared tablet users must explicitly consent.

## 2026-11-17 — Zebra device feedback and UI/recording fix

Owner confirmed Voice ID controls were not accessible from the STEVORA-native AI settings page; only legacy Android preferences exposed them. Owner also reported sample count increments without observable microphone activation. Added native multi-speaker enrollment directly to STEVORA AI page (`StevoraSettingsHome.kt`) and required >=1 second of recorded, decodable audio with audio metadata before accepting an M4A (`StevoraVoiceEnrollment.kt`). The reported symptom has not yet been reproduced or root-caused on Zebra hardware. **New build CI pending**; test microphone permission, Android mic indicator, duration, file existence and playback on-device. Self-learning and speaker identification are still unimplemented.

## Combined upgrade candidate — 2026-11-17

User-approved scope: sample capture + review/playback + quality validation + personalized speaker learning + local Hey Stevora activation per speaker and device + install-over-existing test APKs.

**Implemented in code:** local multi-profile sample collection, duration/decodable-audio metadata acceptance, count only valid files, replay latest sample, 20/20 collection-complete status, explicit consent preference, deletion, signed full-release test workflow reusing existing STEVORA signing identity and test-track version codes.

**Not implemented, and MUST NOT be represented as implemented:** robust silence/clipping/speech checks, real speaker embedding model, automatic self-training, independent voice identity validation, trained Hey Stevora microWakeWord TFLite, live local wake-word activation, per-device speaker policy. Activation remains disabled pending validated runtime models. A user's 20 samples do not automatically train a new acoustic wake-word detector. Speaker verification is not a secure authorization factor by itself.

**Install-over-existing:** debug variants generally use a different signing identity from the full signed release and cannot replace it in-place. Use the signed **full** APK from `.github/workflows/stevora-voice-id-signed.yml` for subsequent test-track updates; preserve applicationId `com.stevora.companion`, existing release certificate and strictly increasing versionCode. Existing installations signed with a different debug key cannot be updated in place by the release-signed build; a one-time migration may be unavoidable. Test versionCode range starts at 2,000,001; future production releases must exceed the highest test versionCode for an in-place upgrade. Do not distribute this build as production.

**Acceptance:** verify certificate fingerprint matches installed package, package ID and versionCode with aapt, install with `adb install -r`, confirm login and settings persist, test real microphone capture/playback and sample deletion on Zebra, then separately train/validate compatible models before enabling automatic activation.

## Next review iteration — 2026-11-17

Added per-sample listing, playback, and confirmation-gated individual deletion in native STEVORA settings, alongside existing latest-sample replay. This lets the owner remove poor-quality takes without resetting all 20 samples. Implementation committed on the prototype branch; CI and Zebra on-device tests pending. No acoustic speaker verification or personalized wake-word activation is claimed.

## Continued implementation — 2026-11-17

- Added per-sample playback and deletion controls to native STEVORA AI settings, with confirmation before removal. Owner can replace a bad sample rather than resetting a profile.
- Recording now rejects too-short, non-decodable, and low-peak-amplitude captures; this is a **basic signal sanity check**, not speech detection or a proof of correct phrase.
- Aligned signed test APK build with the established clean, ordered Hilt Gradle workflow to reduce release build races.
- GitHub Actions compile/test outcomes and real-device microphone/playback behavior remain unverified at the time of this update.
- **Still pending:** speaker embedding model, true per-speaker verification, confirmed learning, valid microWakeWord TFLite wake-word detector, and device-level activation. Do not enable automatic Hey Stevora until both models pass real-device acceptance.

## CI recovery — 2026-11-17

Signed test run 44162755615 failed because workflow supplied a doubled shell backslash before the Gradle flags, causing Gradle to receive an invalid task named `\\`. Corrected the workflow to use a single shell continuation in commit `6da6f6edf5c0fba82f3c4b848c5af7966b59b70d`. Replacement signed run 44163234760 started; success, package signing, versionCode and physical installation remain pending verification. No Voice ID model or live wake-word activation was added in this CI-only fix.
