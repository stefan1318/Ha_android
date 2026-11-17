# First private pilot training experiment (2026-11-17)

The owner-approved 49 audio segments (37 positive, 12 hard negatives, one speaker) were used for a **local spectral logistic-regression sanity check**, not for a deployable microWakeWord model.

- In-sample confusion matrix (negative, positive): [[12, 0], [0, 37]].
- In-sample balanced accuracy: 1.0. **This is NOT a validation metric**, because it was measured on the training examples.
- Speaker-independent accuracy: **unknown**.
- False wake-ups/hour: **unknown**.
- Android/iOS wake-word TFLite model: **not yet available**.

The baseline uses STFT-derived summary features and logistic regression; it is not streaming, not compatible with microWakeWord, and should not be shipped. Private recordings, manifest, and fitted model remain outside public GitHub.

**Next engineering gate:** assemble consented, speaker-diverse and ambient-noise datasets; validate held-out speakers; run the actual micro-wake-word trainer with matching TFLite metadata and test on physical Android devices. The next private recording can add background noise, but multiple speakers remain essential.

This report is intentionally explicit about the difference between a reproducible training-pipeline smoke test and a production-ready wake word.
