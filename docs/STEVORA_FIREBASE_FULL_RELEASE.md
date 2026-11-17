# STEVORA Companion 2.0 — Firebase Full Release setup

## Firebase project
1. Open https://console.firebase.google.com/ and create/select STEVORA project.
2. Add Android app with package name **com.stevora.companion** (not com.stevora.companion.minimal).
3. Download the app's `google-services.json`.
4. Ensure the JSON has a `client[].client_info.android_client_info.package_name` equal to `com.stevora.companion`.

## GitHub Actions secret (Windows PowerShell)
Keep the JSON local; never commit it or paste it into an issue/chat.

Run in PowerShell from the folder containing `google-services.json`:

```powershell
$jsonPath = (Resolve-Path .\google-services.json).Path
[Convert]::ToBase64String([IO.File]::ReadAllBytes($jsonPath)) | Set-Clipboard
```

Then open:
https://github.com/stefan1318/Ha_android/settings/secrets/actions

Choose **New repository secret**:
- Name: `STEVORA_FIREBASE_CONFIG_BASE64`
- Secret: paste clipboard content
- Save.

Alternatively on macOS/Linux: `base64 < google-services.json | tr -d '\n'` and paste its output into the secret.

## Build
Run the **STEVORA Companion 2.0 Full Release** workflow on branch `feature/stevora-companion`:
https://github.com/stefan1318/Ha_android/actions/workflows/stevora-full-release.yml

The workflow:
- decodes the secret to `app/google-services.json` on the ephemeral GitHub runner
- verifies the Android package matches `com.stevora.companion`
- builds `:app:assembleFullRelease` with the configured signing secrets
- checks APK signature, package identity and version name `2.0.0-full`
- uploads the signed APK as `stevora-companion-2.0-full-signed-apk`

The `-full` suffix is from the existing Android Gradle product flavor; the package name is `com.stevora.companion`.

## Signing fingerprints
Obtain the SHA-1/SHA-256 from the **release keystore** (not the debug keystore), e.g.:
```bash
keytool -list -v -keystore /path/to/stevora-release.keystore -alias YOUR_RELEASE_ALIAS
```
Add fingerprints under Firebase Console > Project settings > Your apps > Android app, if required by enabled Google services. Do not expose the keystore or its passwords.

## Production readiness
- Firebase JSON registration alone does not prove FCM push delivery. Test registration, notification permission, delivery in foreground/background and server-side push setup.
- Verify fresh install, login, session persistence, logout, offline/reconnect, wake-word, sensors, location and native controls on target Android versions.
- `minimal` and `full` have different Android package IDs, so installing full is not an in-place update of minimal.
- Do not upload mock `.github/mock-google-services.json` as a real Firebase config.
