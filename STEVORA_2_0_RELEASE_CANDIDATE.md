# STEVORA Companion 2.0 — integrated Android test candidate

Branch: `feature/stevora-companion`

## Scope
- Native STEVORA Home overview, Rooms controls, System diagnostics, AI Assist text pipeline, and settings
- STEVORA onboarding, server address, and branded OAuth container
- Home Hub session switching with non-secret connection settings restored after login
- Bulgarian/English interface where implemented
- No Hey STEVORA wake-word recognition in 2.0 (deferred until trained model is ready)

## Required acceptance checks (real device)
- [ ] Debug and signed APK compile successfully from the **same commit**
- [ ] Clean install, first run, and server URL input
- [ ] Sign in with password, 2FA and an invalid-password attempt
- [ ] Logout and login with another account, preserve server address and internal network preferences, never retain prior user tokens
- [ ] Verify Admin vs User/Panel permissions; never expose admin-only actions
- [ ] Open Home, Rooms, AI, Settings, System; no dead buttons or accidental fallback to legacy HA WebView
- [ ] Verify live sensor states, on/off, climate adjustments, error and offline feedback
- [ ] Confirm AI Assist routes to correct conversation engine for each role
- [ ] Local network, remote connection, HTTPS certificate failure, reconnect and app restart
- [ ] Phone portrait, tablet landscape, system font scaling, BG and EN
- [ ] Android back navigation, keyboard, accessibility and contrast
- [ ] Battery and memory consumption; no unnecessary background polling

## Release blockers / unfinished features
- Memory Manager: protected API integration not complete
- Automation Studio: protected editor integration not complete
- Climate Analytics: Recorder history integration not complete
- Hey STEVORA: explicitly postponed to a later release
- UI branding of the **Home Assistant-hosted credential form** is not under app control. STEVORA wraps it without modifying credentials.

Do not tag 2.0 Stable until the acceptance checks pass. Do not label unavailable modules as implemented.
