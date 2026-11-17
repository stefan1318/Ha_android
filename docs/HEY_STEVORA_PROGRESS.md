# HEY STEVORA — approved living implementation & training plan

**Owner approval:** 2026-11-17
**Status:** Phase 1 started; no trained or validated Hey Stevora model yet.
**Working branch:** `feature/hey-stevora-training`
**Protected Android baseline:** STEVORA Companion 2.1.0 owner-tested release candidate on `feature/stevora-companion`.
**iOS development branch:** `feature/stevora-ios`.

## Objective
Deliver an opt-in, privacy-conscious **Hey STEVORA** voice activation experience integrated with the existing Home Assistant Assist/STT/conversation/TTS pipeline and both STEVORA Android and iOS applications. Android may use microWakeWord with supported microphone foreground service/assistant integration; iOS must respect OS restrictions (in-app listening, Siri/App Intents; do not promise always-on arbitrary wake-word detection when locked). Consider a dedicated always-powered Home Hub voice satellite for hands-free home use.

## Approved phases and acceptance gates

| Phase | Work | Completion evidence | Status |
| --- | --- | --- | --- |
| 1 | Prepare licensed BG/EN positive/negative voice dataset, provenance and consent; deterministic speaker-disjoint splits and audio normalization | Dataset manifest, preprocessing tests, reproducible split; no raw voice in public Git | IN PROGRESS |
| 2 | Train, quantize, calibrate and validate microWakeWord-compatible `hey_stevora.tflite` plus matching metadata | Held-out speaker tests, noise/near-miss evaluations, measured false accepts/hour, recall, latency, battery/CPU | NOT STARTED |
| 3 | Confirm Home Assistant Assist/STT/AI/TTS pipeline and secure audio routing; diagnose failed Wyoming iPhone endpoint if used | End-to-end authorized BG/EN voice commands and responses, errors/reconnect tests | NOT STARTED |
| 4 | Wire Android model, opt-in microphone permissions, sensitivity and UX into actual runtime | Signed test APK, real Android wake/lock/reconnect/privacy tests, no regression to 2.1 | NOT STARTED |
| 5 | Implement native iOS foreground voice activation, Siri/App Intents and shared server conversation backend | Signed/testable IPA, real iPhone foreground/Siri/auth/network tests; documented OS limitations | NOT STARTED |
| 6 | Privacy and access-control hardening, confirmation for sensitive actions, audio handling policy | Security review, explicit opt-in, microphone indicator and deletion/retention controls | NOT STARTED |
| 7 | Cross-platform acceptance and release candidate integration | Test matrix and reproducible builds; owner-confirmed device tests | NOT STARTED |

## Existing work verified
- Android fork includes microWakeWord runtime and Assist integration.
- `docs/HEY_STEVORA_MODEL.md` describes the model/metadata contract; `training/hey_stevora/prepare_dataset.py` prepares data but does **not** train a model.
- Android `assets/wakeword` has other wake-word models, **not** `hey_stevora.tflite`.
- Android voice settings have model availability checks; sensitivity currently stores a preference and calibration is not active.
- iOS STEVORA SwiftUI prototype is separate from upstream iOS Companion app; voice integration is pending.
- Home Assistant has available STT/TTS/conversation entities; iPhone Wyoming entry previously showed setup retry (endpoint unreachable).

## Implementation log (append only)
| Date (Europe/Sofia) | Change | Evidence | Verification |
| --- | --- | --- | --- |
| 2026-11-17 | Approved seven-phase plan and platform-specific activation constraints | Project conversation | Approved by owner |
| 2026-11-17 | Created isolated `feature/hey-stevora-training` branch | GitHub branch | Created |
| 2026-11-17 | Added synthetic audio preprocessing unit tests, Python dependency list and GitHub Actions workflow | `training/hey_stevora/test_prepare_dataset.py`, `requirements-test.txt`, `.github/workflows/hey-stevora-training.yml` | Committed; CI outcome not yet verified |

| 2026-11-17 | Received 39.23s positive distance recording and 25.41s hard-negative recording; extracted 20 positive and 12 negative candidate WAV clips at 16kHz mono, with segment manifest; private ZIP v02 prepared | User audio and local sandbox artifact; not published to GitHub | Automated segmentation only, manual review pending; all examples from same speaker; no trained model |

| 2026-11-17 | Owner listened to and approved the current pilot audio set (first positive, distance positive, and hard-negative recordings) | Explicit owner confirmation in project conversation | **OWNER APPROVED audio content**; technical dataset validation and model training still pending |

| 2026-11-17 | Ran private first training sanity-check: spectral logistic-regression baseline on 49 approved clips (37 positive, 12 hard negatives) | `training/hey_stevora/PILOT_EXPERIMENT_2026-11-17.md`; private report and fitted model | In-sample balanced accuracy 1.0 (not generalization); no held-out speakers; **not** a microWakeWord/TFLite model; Phase 2 remains IN PROGRESS |

| 2026-11-17 | Generated 24 eSpeak synthetic positive WAV clips (Bulgarian and English, masculine/feminine/youthful synthetic timbres, four delivery variations each), 16kHz mono; published reproducible generator and kept audio ZIP as private artifact | `training/hey_stevora/generate_synthetic.py`; private `Hey_Stevora_Synthetic_v01.zip` | Audio generated, **not yet listened to or approved**; youthful timbre is not a real child's voice; synthetic examples do not count as independent speakers |

| 2026-11-17 | Expanded private pilot archive v03 to 2,833 WAV clips: 2,760 new synthetic (eSpeak 2,520 BG/EN + Flite 240 EN), 24 earlier synthetic v01, and 49 owner-approved real clips; 1,099 positive, 1,734 hard negatives | `training/hey_stevora/generate_multiengine_seeds.py`, `training/hey_stevora/SYNTHETIC_EXPANSION_PROTOCOL.md`; private `Hey_Stevora_Expanded_v03.zip` | WAV format and counts checked; **new synthetic voices unreviewed**; no independent speaker generalization or production wake-word model; generator produces reproducible seed corpus, archive also uses augmentation |

| 2026-11-17 | Owner explicitly approved **all 2,833 examples** in expanded v03 corpus for training; verified every indexed WAV is 16kHz mono PCM16 and produced private approved manifest + SHA-256 registry | `training/hey_stevora/register_approved_corpus.py`; `Hey_Stevora_Approved_Registry_v03.zip` (private) | **APPROVED FOR TRAINING**, not yet trained; source-group isolation required; no independent speaker validation |

## Update policy (approved)
1. Update this document **after each verified successful implementation**; log date, change, exact commit/workflow/artifact link, test evidence and owner acceptance if required.
2. Keep status **IN PROGRESS** until acceptance criteria have been met; distinguish code committed, CI passed, device tested, and owner accepted.
3. Record blockers, regressions and next actions; never claim a model is trained or a release works without evidence.
4. Maintain original scope and Android signing/application-ID continuity. Never alter the known-good 2.1 baseline as part of voice experiments.
5. Synchronize milestones into the general STEVORA roadmap when appropriate, but this file is the authoritative Hey STEVORA implementation log.
6. Updates happen when development/test results are reviewed and committed; this document does not itself run a background monitor.

| 2026-11-17 | Received pilot recording (35.99s, AAC 48kHz stereo), converted to 16kHz mono PCM, extracted 17 candidate positive clips from one speaker; created local private pilot ZIP and segment manifest | User-supplied audio; local sandbox artifact, not uploaded to public GitHub | Segmentation is heuristic; clips need listening review; no trained model yet |

## Immediate next actions
1. Verify the dataset CI workflow execution and fix any failures.
2. Collect consented audio samples; use pilot 20–30 speakers, roughly 30–50 positive examples each, and diverse negative/background samples; expand based on evaluation.
3. Establish upstream micro-wake-word training environment, reproducible training configuration and measurable acceptance targets.
4. Train first candidate model, evaluate held-out speakers, then integrate into Android test builds.
