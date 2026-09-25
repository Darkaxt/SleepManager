# TailDNS integration implementation plan

Authorization: `already_authorized` by the request to fork SleepManager and add
support for TailDNS.

The specification in [SPECIFICATION.md](SPECIFICATION.md) is authoritative.

## Stage 1 — Package-aware control transaction

Status: **COMPLETE**

Objective: make the existing Tailscale connector select, control, and remember
the exact supported VPN package.

Requirements: TD-1, TD-2, TD-3, TD-4, TD-6.

Changes:

- Add supported-client target resolution, including active VPN owner matching.
- Encode the selected package into disconnect-verification and restore tokens.
- Carry those tokens unchanged through the existing service transaction.
- Retain legacy-token compatibility for official Tailscale.

Verification:

- Focused tests cover target selection and token parsing.
- Existing connector/service tests pass.

## Stage 2 — Visibility, diagnostics, and documentation

Status: **COMPLETE**

Objective: expose truthful TailDNS support everywhere the integration is shown.

Requirements: TD-5, TD-7.

Changes:

- Add TailDNS to Android package visibility.
- Make version/Open/diagnostics use the selected target.
- Update UI copy and public documentation.

Verification:

- Focused UI/source assertions and Android compilation pass.

## Stage 3 — Integrated verification and commit

Status: **COMPLETE**

Objective: reconcile the fork against every acceptance criterion and commit the
verified implementation.

Requirements: TD-1 through TD-7.

Verification:

- Run focused tests and the release assembly from a clean source state.
- Confirm manifest visibility and compiled broadcast targets.
- Reconcile AC-1 through AC-7 with blockers and deferrals both equal to zero.

## Reconciliation ledger

| Requirement | Stage | State | Evidence / remaining work |
| --- | --- | --- | --- |
| TD-1 | 1 | satisfied | Official and TailDNS packages are modeled as supported targets; focused tests pass. |
| TD-2 | 1 | satisfied | Active owner is preferred, sole-installed fallback is bounded, and ambiguous ownership is rejected. |
| TD-3 | 1 | satisfied | Package-bearing disconnect and restore tokens round-trip the exact client. |
| TD-4 | 1 | satisfied | The existing verify-before-restore service transaction remains in force. |
| TD-5 | 2 | satisfied | TailDNS is package-visible; shared selection drives version, diagnostics, and Open. |
| TD-6 | 1 | satisfied | Legacy official-client tokens are accepted by focused tests. |
| TD-7 | 2 | satisfied | UI, diagnostics, README, and changelog describe Tailscale / TailDNS support. |
| TD-8 | 4 | satisfied | Main and Helper share the pinned permanent fork certificate; the four signing inputs are configured as GitHub secrets. |
| TD-9 | 4 | satisfied | The updater accepts only `Darkaxt/SleepManager`; release `v0.6.0.1` publishes both signed APKs and a hash-matching `update.json`. |
| TD-10 | 4 | satisfied | Thor retained its preferences across the signer transition; admin, foreground service, Helper permission, and TailDNS controls were verified live. |

Blockers: none.

Tracked deferrals: none.

## Stage 3 reconciliation

| Acceptance criterion | State | Evidence |
| --- | --- | --- |
| AC-1 | satisfied | Official package remains supported, legacy tokens resolve to it, and package/action tests pass. |
| AC-2 | satisfied | TailDNS package, action, and exact-target token tests pass through the shared production policy used by the connector. |
| AC-3 | satisfied | Active-owner selection tests cover both installed clients and reject ambiguous ownership. |
| AC-4 | satisfied | Selection returns no target without a Tailscale-addressed VPN; the connector therefore records no transaction. |
| AC-5 | satisfied | Focused token tests accept legacy tokens and reject malformed/unknown targets. |
| AC-6 | satisfied | TailDNS appears in the compiled manifest; selected-package logic drives availability, version, diagnostics, and Open. |
| AC-7 | satisfied | Fresh clean unit tests, release compilation, release lint-vital analysis, APK assembly, and `git diff --check` pass. |

Stage 3 blockers: zero.

Stage 3 tracked deferrals: zero.

## Stage 4 — Signed fork release and Thor replacement

Status: **COMPLETE**

Objective: turn the verified source change into a recoverable, updateable Thor
deployment while retaining all SleepManager state.

Requirements: TD-8, TD-9, TD-10.

Changes and deployment transaction:

- Establish and pin one permanent fork signing identity for both APKs.
- Point the built-in updater and public links at `Darkaxt/SleepManager`.
- Publish a fork-subversion release containing both APKs and `update.json`.
- Capture the currently installed upstream APKs, data, versions, certificate,
  preferences, admin state, and service state.
- Remove active admin, uninstall both packages with retained data, install the
  fork-signed pair, and restore admin/service state.
- Roll back with the captured official APKs and data if any required check fails.

Verification:

- Tests and clean signed release build pass.
- Published artifacts independently match package IDs, versions, checksums, and
  the pinned common signer.
- Thor preserves preferences and passes package, signer, admin, service,
  Helper-permission, and TailDNS control-state checks.

## Stage 4 reconciliation

| Acceptance criterion | State | Evidence |
| --- | --- | --- |
| AC-8 | satisfied | Clean signed builds and the GitHub release both identify `com.med.sleepmanager` 0.6.0.1 and `com.med.sleepmanager.helper` 1.1.0.1 with certificate SHA-256 `87c2f2f5d5dac021d3ee26bb6b361f722410122e2dedc465a37e5b9e0276ecec`. |
| AC-9 | satisfied | The live `update.json` points only to `Darkaxt/SleepManager`, its hashes match the published assets, and focused tests reject upstream and lookalike origins. |
| AC-10 | satisfied | Thor preserved all selected pre-deployment preferences, enabled TailDNS management, reactivated the device admin and foreground service, exchanged a protected Helper broadcast, and kept TailDNS alive through disconnect/connect controls. |

Stage 4 blockers: zero. The initially installed TailDNS 1.103.312+7 cold-start crash was resolved by the same-signer in-place update to the already-published 1.103.312+18 build before repeating the control verification.

Stage 4 tracked deferrals: zero.
