# STEVORA Companion Hybrid Premium — Beta 1.0 release contract

Status: **NOT RELEASED**. This document combines the six workstreams into one release candidate gate. A successful debug APK is not a production beta.

## Product architecture
- Native Android: branding, authentication hand-off, onboarding, navigation, voice, notifications, permissions, app lock and settings.
- STEVORA Home Hub: authenticated server panel at `/stevora` for devices, rooms and automation.
- STEVORA AI: Android Assist with an explicitly selected and validated server pipeline; show connection, listening, processing and error states.
- Never hard-code credentials or replace upstream authenticated APIs with placeholder implementations.

## Release-blocking acceptance criteria
1. **Premium UI:** native start screen, navigation, consistent theme, BG/EN, phone and tablet layouts; no double navigation when rendering the server panel.
2. **AI:** tap-to-talk command and response against a real STEVORA server; pipeline selection and error recovery; no claim of Hey Stevora wake word without a validated TFLite model.
3. **Onboarding:** first launch, add/connect server, login, initial permissions, reconnect, accessible recovery messages and settings.
4. **Security:** TLS validation, secure token handling, app lock, safe deep links, privacy disclosures, dependency audit; no insecure bypass of certificates.
5. **QA:** CI compilation, resources validator, branding verifier, lint where supported, clean install and upgrade, BG/EN, screen sizes, offline recovery, notification permissions, app backgrounding and battery modes. Capture test results.
6. **Distribution:** beta versionCode/versionName, changelog, reproducible signed build, safe signing-key management, install/upgrade instructions and feedback channel.

## Required manual matrix
- Android phone and tablet; clean installation and upgrade.
- Server with trusted TLS and server with a correctly installed private CA.
- Valid login, expired session, unavailable server, malformed URL.
- Android Assist: microphone allowed/denied; configured/unconfigured pipeline.
- App lock enabled/disabled; notification permission allowed/denied.
- BG and EN, dark UI, landscape and portrait, TalkBack.

## Scope after Beta 1.0
- Trained offline `Hey Stevora` wake model, personal voice calibration, advanced animation and other optional features.

## Publication rule
Do not tag or advertise an official Beta 1.0 until all six gates pass and a signed APK is tested on real devices. Debug artifacts are for development only.
