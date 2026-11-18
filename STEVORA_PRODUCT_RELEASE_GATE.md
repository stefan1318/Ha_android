# STEVORA Android — authoritative product build

The product source branch is `feature/stevora-companion` (version 2.1.0,
application ID `com.stevora.companion`), **not**
`feature/stevora-manifest-sync-v1`. The latter is an integration experiment
based on upstream Home Assistant and must not be distributed as STEVORA.

## Release gate

- [ ] Merge or port universal manifest discovery to the product branch.
- [ ] Verify launcher label, launcher/splash icon and onboarding branding.
- [ ] Verify native STEVORA UI, controls, Hey Stevora, and HA OAuth login.
- [ ] Build the product branch and verify installed APK package ID and version.
- [ ] Check upgrade compatibility with the last signed 2.1.0 test APK.
- [ ] Test on a physical phone, then publish an explicitly named beta artifact.

Never label an upstream Home Assistant APK as STEVORA. A successful upstream
Gradle build is **not** evidence that STEVORA features are present.
