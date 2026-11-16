# STEVORA Companion Hybrid Premium — beta readiness

## Implemented in this branch
- Product application label, STEVORA launcher/splash assets, and dedicated dark `Theme.Stevora` selected in AndroidManifest.xml.
- Existing Home Assistant Companion authentication, server onboarding, WebView frontend, native Android Assist entry points and sensors remain intact.
- STEVORA panel default path `/stevora` and separate STEVORA Voice settings entry.
- Hybrid training dataset preparation for future Hey Stevora model. **No Hey Stevora TFLite is bundled.**

## Latest development candidate (unvalidated)
- Native Premium Home and bottom navigation are wired to the existing frontend, settings, and Assist entry points.
- Native STEVORA AI landing screen offers authenticated Assist conversation and pipeline configuration, not a replacement AI backend.
- Add Hub now opens the existing secure server onboarding flow.
- New Premium Home and AI strings are localized in BG/EN.
- Runtime verification and a successful CI build for the latest commits are still required.

## Not yet a validated release
The live native device dashboard, complete BG/EN localization beyond the new STEVORA screens, seamless theme parity with the server-side `/stevora` dashboard, automatic STEVORA Assist pipeline selection, dedicated in-screen chat history, offline behavior, full accessibility review, production signing and device test matrix **remain open**. No claim is made that all runtime failures can be prevented in advance.

## Acceptance gates
1. CI: `./gradlew :app:assembleMinimalDebug`, APK branding verifier, `./gradlew :app:lintMinimalDebug` if available; retain CI logs and artifact.
2. On-device: clean install, upgrade from previous test build, authentication and token refresh, HTTPS certificate handling, connection loss/recovery, WebView `/stevora`, BG/EN, screen rotation, tablet layout, notification permission, app lock, foreground voice service, battery restrictions.
3. Visual QA: screenshots of launch, onboarding, dashboard, settings, voice, errors; verify native/server colors, navigation, touch targets and TalkBack.
4. Security: ensure no hard-coded server credentials, test redirect handling and deep links, signed release process and dependency scanning.
5. Release: publish only after beta testers verify a signed build.

## Architecture
Android native shell handles OS integration and voice; existing Home Assistant frontend hosts the STEVORA server panel. Do not replace upstream auth/WebSocket/Assist with mock endpoints. The `/stevora` server route must be available and authorized for the connected account.
