# STEVORA Companion — final test candidate checklist

## Automated release gates
- Android 8–11, Android 12+ and default splash resources point to STEVORA branding.
- Debug adaptive and legacy launcher assets are generated from STEVORA artwork.
- Both APK workflows validate the startup resources before building.
- Signed release validates APK signature and package identity.
- Existing Home Assistant OAuth authorize/redirect/token flow remains intact.

## Mandatory device acceptance tests (not covered by CI)
- Cold start, warm start and app switch on Android 10, 12, 13, 14+.
- Check splash, launcher, notification icon, onboarding and all settings for legacy logos.
- First-time login, invalid password, optional 2FA, OAuth consent, return from browser.
- Refresh expired access token; restart after refresh; sign out; revoke authorization.
- Local HTTPS certificate, remote URL, offline/reconnect and network handover.
- Existing installs: upgrade without losing server, permissions, entities or session.
- Native device control, Assist text and microphone permission, localization BG/EN.

## Server authentication branding — pending separate server-side implementation
The Home Assistant server owns its authentication pages, 2FA and consent UI.
Do not inject credentials, replace secure server login with a local password form,
or change the OAuth CLIENT_ID until a migration is designed and tested against
existing refresh tokens and client identity. A STEVORA-owned HTTPS client ID
and supported server-side auth UI implementation require domain ownership,
server deployment access, compatibility testing and license attribution.
Passing Android CI does not certify these server-side changes.
