# TailDNS integration specification

Status: authoritative

## Context

SleepManager 0.6.0 supports the official Tailscale Android client by sending
package-scoped `CONNECT_VPN` and `DISCONNECT_VPN` broadcasts. TailDNS preserves
that control contract under its independent Android package,
`io.github.darkaxt.taildns`. The current hard-coded official package prevents
SleepManager from disconnecting TailDNS before SleepManager turns Wi-Fi off.
On the AYN Thor this exposed a TailDNS network-change deadlock and a subsequent
restart crash loop.

## Requirements

- **TD-1 — Supported clients:** Treat `com.tailscale.ipn` and
  `io.github.darkaxt.taildns` as supported Tailscale-compatible clients without
  removing official-client support.
- **TD-2 — Active-client ownership:** When a supported client owns the active
  Tailscale VPN, direct sleep control to that client. If no supported client is
  active, do not claim or create a restore transaction.
- **TD-3 — Exact restore target:** Persist the package targeted by a successful
  disconnect request and reconnect only that same package on wake. Never switch
  between official Tailscale and TailDNS during one sleep transaction.
- **TD-4 — Existing safety semantics:** Preserve the existing state-aware rule:
  reconnect only when SleepManager initiated and verified the disconnect.
- **TD-5 — Package visibility and launch:** Installation checks, version
  reporting, diagnostics, and the Open action must work when TailDNS is the only
  supported client installed.
- **TD-6 — Compatibility:** Existing pending transactions that use the 0.6.0
  legacy `verify_disconnect` and `restore` tokens must remain recoverable for
  the official Tailscale package.
- **TD-7 — User-facing truthfulness:** The integration must visibly describe
  support for both Tailscale and TailDNS; diagnostics must identify the selected
  package.

## Constraints

- Do not change TailDNS, the SleepManager Helper, Wi-Fi timing, or the existing
  VPN verification/retry policy as part of this integration.
- Do not require root or privileged APIs.
- Do not disconnect an arbitrary non-Tailscale VPN.
- Do not introduce a user-maintained package selector when the active VPN or
  installed-client set provides a deterministic target.

## Acceptance criteria

- **AC-1:** With only official Tailscale installed and active, SleepManager sends
  official-package disconnect/connect broadcasts and preserves current behavior.
- **AC-2:** With only TailDNS installed and active, SleepManager sends
  `io.github.darkaxt.taildns.DISCONNECT_VPN`, verifies disconnection, and later
  sends `io.github.darkaxt.taildns.CONNECT_VPN`.
- **AC-3:** With both clients installed, the active VPN owner's package is used;
  the other client receives no control broadcast.
- **AC-4:** When neither supported package owns an active Tailscale VPN, no
  disconnect ownership or restore token is recorded.
- **AC-5:** Restore-token parsing rejects unknown packages and malformed tokens,
  while accepting legacy 0.6.0 tokens as official-client transactions.
- **AC-6:** TailDNS-only installation is reported as available in the UI and
  diagnostics, and Open launches TailDNS.
- **AC-7:** Focused unit tests, the affected Android build checks, and a clean
  release assembly pass from the committed source.

