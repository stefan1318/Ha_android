# STEVORA Ecosystem — Platform Agent Supplement

This repository is part of STEVORA Ecosystem. Read the existing root AGENTS.md and its platform-specific skills first. These upstream rules remain in force; this supplement adds STEVORA product requirements and does not override the Open Home Foundation AI Policy.

## Shared contract
- Keep Home Assistant independent-server onboarding; never hardcode Dunav, URLs, credentials, rooms, or entity IDs.
- Preserve BG/EN localization and user-defined names; never overwrite manual room/device mapping silently.
- Distinguish disabled, unavailable, unsupported, hidden and included entities in diagnostics.
- Maintain the STEVORA premium UI and responsive, accessible layouts, where STEVORA UI is actually implemented.
- Do not implement controls for lamp backlights; preserve their always-on policy.
- Require HA authentication, backend authorization, TLS verification, secure secret handling, and safe failure paths.
- Avoid breaking changes to Home Panel / Home Hub / Universal Integration contracts; document schema and API changes with compatibility tests.
- Never assert a feature or build passed without evidence. Report PASS, FAIL or NOT RUN, with exact commands and limitations.
- Protect existing configurations, permissions and settings across upgrades; separate beta from stable releases.
- Do not deploy to Dunav, merge into main, or publish releases without explicit human approval.

## Collaboration
Use a focused branch, small changes, test evidence, and human review. Respect the repository's AI_POLICY.md: do not autonomously submit issues, PRs or comments without the user's review. Coordinate contract changes with the central STEVORA rules in stefan1318/stevora-home-assistant-apps/AGENTS.md (main).

## Release report
Include changed files, test results, compatibility impact, rollback instructions and any unresolved risks.

## Platform-specific requirements
Android: Kotlin-only; keep Gradle conventions, upstream agent instructions and Android compatibility. Test independent-server connection, manifest/room mapping, BG/EN, and relevant Android variants.
