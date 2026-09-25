# Fork signing identity

The SleepManager fork uses one permanent signing identity for the main app and
Helper.

- Certificate SHA-256:
  `87c2f2f5d5dac021d3ee26bb6b361f722410122e2dedc465a37e5b9e0276ecec`
- Local keystore:
  `C:\Users\darka\.codex\credentials\sleepmanager-signing\sleepmanager-release.p12`
- Windows Credential Manager target: `Codex/SleepManagerForkSigning`
- Key alias: `sleepmanager-fork`
- GitHub repository secrets:
  `SLEEPMANAGER_KEYSTORE_B64`, `SLEEPMANAGER_KEYSTORE_PASSWORD`,
  `SLEEPMANAGER_KEY_ALIAS`, and `SLEEPMANAGER_KEY_PASSWORD`

The password is not stored in the repository or this document. Do not replace
the keystore or certificate pin for subsequent releases; doing so would make
installed fork builds non-upgradeable.
