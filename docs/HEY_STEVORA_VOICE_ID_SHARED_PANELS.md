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
