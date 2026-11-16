# Hey Stevora — model integration contract

The Android fork already includes the Home Assistant microWakeWord inference runtime and Assist pipeline. This change wires STEVORA's settings to that real runtime. **It does not create or claim a trained wake-word model.**

## Supplying the trained model

Train and evaluate a microWakeWord-compatible TensorFlow Lite model for the exact phrase **"Hey Stevora"** using properly licensed positive and negative audio. Validate detection accuracy, false activations, noise robustness and on-device latency before release. Do not substitute an arbitrary TFLite file or a renamed "Okay Nabu" model.

Place these two files under `app/src/main/assets/wakeword/`:

- `hey_stevora.tflite` — trained and verified compatible model
- `hey_stevora.json` — model metadata with `wake_word: "Hey Stevora"`, `model: "hey_stevora.tflite"`, author, website, trained languages, version, and calibrated `micro` settings (`probability_cutoff`, `feature_step_size`, `sliding_window_size`) in the exact JSON format already used by bundled models.

Android Assist scans this directory at runtime. The STEVORA Voice page reports availability through `AssistConfigManager.getAvailableModels()`. The loader rejects metadata with no corresponding TFLite asset.

## Runtime activation

1. Connect STEVORA Companion to the user's Home Assistant server.
2. Configure the Assist pipeline (OpenAI can be the conversation agent).
3. Make STEVORA Companion the Android default digital assistant, grant microphone permission, and opt in to wake-word listening in Android Assist.
4. Select "Hey Stevora" once the trained asset is included.
5. Verify the foreground microphone notification, screen-locked behavior, battery optimization behavior, and wake-word detection on physical Android hardware.

**OpenAI on the server is not a wake-word detector** and cannot automatically make an absent local model available. Existing supported wake words can be used while the custom model is being trained.

## Release gates

A build succeeding is not evidence of a functional custom wake word. Require real-device tests with multiple voices and environments, privacy review, microphone-permission handling, and false-positive/false-negative measurements before calling Hey Stevora production-ready.
