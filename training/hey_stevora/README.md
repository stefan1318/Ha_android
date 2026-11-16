# Hey Stevora — hybrid synthetic + real speech training

Status: **dataset preparation only**. No trained or validated TFLite model is included.

## Target
- Phrase: **Hey Stevora**
- Bulgarian and English accents/pronunciations; record natural pronunciation, not a forced phonetic spelling.
- 16 kHz, mono, 16-bit PCM WAV after preprocessing.
- Target runtime: existing Android microWakeWord streaming inference, then Home Assistant Assist pipeline with the user's configured STEVORA conversation agent.

## Dataset collection
Use `prepare_dataset.py` to validate and normalize WAV recordings and build speaker-disjoint splits. Organize input WAV files as:

```
training/hey_stevora/raw/
  positive/<speaker_id>/*.wav
  negative_speech/<speaker_id>/*.wav
  negative_ambient/<source_id>/*.wav
```

Obtain informed consent for every human speaker. Do not commit raw human voice recordings to GitHub. Record at least several sessions per speaker, varying distance, phone orientation, background noise and accent. Include negatives such as "Hey Siri", "Hey Google", "Stevora", "Hey Steven", ordinary Bulgarian conversation and TV audio. Maintain explicit provenance and rights for synthetic voices and noise datasets.

Start with a pilot of 20–30 speakers, around 30–50 positive utterances per speaker, and expand based on independent evaluation. These are collection targets, not a claim of sufficient training quality. Generate synthetic positives using the upstream Piper sample generator and use distinct TTS speakers for validation. Do not let the same speaker or synthetic voice appear in both training and held-out test sets.

## Training and release
1. Use the upstream [OHF micro-wake-word](https://github.com/OHF-Voice/micro-wake-word) training pipeline, following its current notebook and dataset/feature-generation documentation. The script in this folder prepares input audio; it does **not** train the neural network.
2. Train and quantize the streaming TensorFlow Lite model with the exact frontend/feature parameters supported by Android.
3. Evaluate real held-out speech, near-miss phrases, and many hours of real ambient audio. Track recall and false accepts/hour separately; tune probability cutoff and sliding window size against held-out data.
4. Test the exact exported model on real Android devices before release.
5. Only then place `hey_stevora.tflite` and matching `hey_stevora.json` under `app/src/main/assets/wakeword/` as described in `docs/HEY_STEVORA_MODEL.md`.

## Local privacy
Keep recordings on the training workstation or approved restricted storage, with explicit retention/deletion rules. Never upload them to the public repository. Local wake-word detection does not imply the subsequent Assist conversation is local: STT, OpenAI, and TTS depend on the selected Home Assistant pipeline.
