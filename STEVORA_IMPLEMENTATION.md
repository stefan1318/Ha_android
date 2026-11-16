# STEVORA Companion Android — implementation specification

Target: fork of home-assistant/android, branch feature/stevora-companion.

## Requirements
- Wake word: **Hey Stevora only**, custom trained microWakeWord TFLite model. Do not ship placeholder weights or claim trained detection.
- App startup: after authentication, open the existing **STEVORA Home panel** route (NOT the Lovelace dashboard). Discover/confirm the actual registered panel path and persist a per-server preferred start path; validate it against same-origin Home Assistant URLs.
- Fallback: if panel route unavailable, show recovery UI with Open STEVORA panel, Home Assistant default, and Settings; avoid infinite redirect loops.
- Branding: use the **original STEVORA logo asset** supplied by the user (PNG source); do not redraw or substitute an invented logo. Do not redraw or substitute an invented logo. Adapt launcher adaptive icon, splash, notification icon, app title and theme.
- Android support: phone/tablet responsive layouts, portrait/landscape, variable densities, supported ABI variants, Android version floor matching upstream compatibility. Avoid ZB10S-specific hardcoding.
- Keep official Home Assistant Companion installed alongside STEVORA using a distinct applicationId; audit OAuth redirect, deep links, notification registration, Firebase and background services before release.
- Security: preserve Home Assistant authentication, HTTPS and secure token storage. Never hardcode external IPs, credentials or server addresses.
- Wake-word listening: retain microphone permission, Android assistant role / foreground service requirements, clear privacy indication and a user-controlled disable toggle.
- Builds: upstream Gradle and CI compatibility; reproducible signed test APK, signing secrets kept outside repository.
- Testing: ZB10S primary device plus at least one Android phone, locked screen, background listening, reconnection, audio focus, battery use, false positives, and existing app coexistence.

## Work items
1. Locate startup navigation, WebView and server routing classes in fork.
2. Verify STEVORA panel's actual URL path in Home Assistant.
3. Obtain original STEVORA vector logo and generate Android density variants.
4. Train and validate microWakeWord model; package model and metadata.
5. Implement startup navigation, branding and model selection.
6. Compile, test, sign and publish APK.

Status: specification committed; code changes, trained model and APK not yet completed.

## Product decision — full STEVORA white-label branding (approved)
- All user-facing surfaces must use STEVORA branding: app label, launcher and adaptive icon, splash, onboarding, server discovery/connect, settings, notifications, Assist UI, permission explanations, about/version screens.
- Maintain original upstream license notices and attribution; do not misrepresent Home Assistant open-source provenance.
- Do not replace or weaken secure authentication screens; customize surrounding application UI only where supported.
- App should open the STEVORA **custom panel** on launch after login; route is server-specific and must be verified, with a safe fallback and no hard-coded address.
- Include localized Bulgarian and English UI text; maintain Android accessibility and adaptive phone/tablet layouts.
- Use separate applicationId and signing key; verify integration behavior with companion app, push/Firebase, Android App Links and deep links.
- ZB10S is the primary test device, not the minimum supported hardware.
- Only custom **Hey Stevora** microWakeWord model selectable; training and false-activation validation are release gates.
- Branding asset supplied in chat as steورا logo PNG; source file must be imported into Android drawable/mipmap resources before implementation is considered complete.
