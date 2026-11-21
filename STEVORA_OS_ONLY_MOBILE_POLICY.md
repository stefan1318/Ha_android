# STEVORA OS-only mobile connection policy — 2026-11-21

**Decision: approved; implementation pending.** Applies to Android 2.2+ and the next iOS release. This document supersedes any older instruction to support ordinary Home Assistant onboarding, independent HA servers or fallback to HA.

## Mandatory behavior

- The mobile client MUST connect **only** to a genuine, compatible STEVORA OS installation.
- No ordinary Home Assistant server, HA Companion setup, HA frontend fallback, HA-only manifest or standalone STEVORA Universal Integration is sufficient.
- A STEVORA OS login must be branded, but use the OS-backed secure authentication flow; preserve OAuth/MFA, HTTPS validation, permissions and secure token storage.
- The client MUST verify an OS-owned, versioned discovery/identity contract before offering login or issuing home/device commands. Never trust branding, a URL path, a client-provided flag, or an unauthenticated self-asserted product name alone.
- Define a verifiable server identity / trust bootstrap and its key rotation, downgrade resistance and migration policy in STEVORA OS first. TLS must remain enforced. Do not invent an endpoint or claim cryptographic verification exists before implementation.
- If identity, trust, API compatibility, authentication or authorization fails, show a clear BG/EN unsupported-server/error state and do not connect. No fallback to regular HA.
- Rooms, devices, translations, scoped manifest, capabilities, voice providers, permissions and entitlements are provided only by authenticated STEVORA OS APIs. Fail closed if unavailable.
- Preserve existing installed app package/bundle identity, release signer, increasing build/version numbers and local data. Provide an explicit migration notice for previously paired plain HA servers; never silently reuse their tokens or clear data.
- Maintain separate developer fixtures/mocks only in CI tests, never as a production bypass.

## Release gate

1. OS-owned documented identity and API contract, with security review and version negotiation.
2. Android and iOS OS-only onboarding and rejection of ordinary HA, spoofed OS headers, mismatched identities and incompatible versions.
3. Positive connection and reconnection tests with two genuine STEVORA OS installations and two roles.
4. TLS/MFA/session tests, offline safe behavior, data isolation and BG/EN UI.
5. Signed in-place upgrade tests and no unintended data loss.
6. CI checks, signed builds and real-device acceptance before Firebase/TestFlight or public stable labeling.

Status of these gates at document creation: **NOT RUN**. This is a policy specification, not evidence of implemented OS-only enforcement.
