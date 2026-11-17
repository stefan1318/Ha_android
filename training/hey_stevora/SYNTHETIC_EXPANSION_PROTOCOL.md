# Synthetic corpus expansion protocol — 2026-11-17

Generate both positive and hard-negative phrases in Bulgarian and English. Distinguish:
- voice engine identity, locale/accent preset, eSpeak voice variant, pitch and rate;
- augmentation (noise, reverb, gain, equalization), which does NOT create a new speaker;
- perceived timbre (masculine/feminine/youthful/older), which does NOT prove actual sex or age.

Approved positive target: `Hey Stevora` / `Хей Стевора`.
Hard negatives: Hey Siri, OK Google, OK Nabu, Alexa, Hey Jarvis, Stevora alone, OK Stevora, Hey Steven, Hey Stella, etc.

Every WAV must be 16kHz, mono, PCM16, have a manifest with text, language, engine, voice profile, variation, and origin. Never put synthetic derivatives from the same source/voice family on opposite sides of the evaluation split. Synthetic-only metrics are NOT a substitute for real, speaker-disjoint and continuous background-hour tests. Synthetic child-like voices must not be labeled as actual children. License terms of each TTS engine/model must be checked before redistributing trained weights or shipping a commercial product.

The current environment exposes eSpeak, but no separately available neural TTS model or voice service has been verified. We can vary eSpeak language/voice/accent presets and apply audio augmentation, but must not misrepresent these variations as distinct engines or authentic human demographics. Public GitHub stores only code/manifest summaries; raw audio stays in private archives.
