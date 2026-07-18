# Amin Pocket GBA Permanent Signing Identity

This file records only the public certificate identity. It does not contain the private keystore or passwords.

- Package ID: `com.amin.pocketgba`
- Key alias: `amin-pocket-gba`
- Signer certificate SHA-256: `aff1bab8f364e1d0f248c6242da1e07a7114778a7347b4390f179948290c256e`
- First permanent bridge version: `0.9.2-bridge1`
- First permanent bridge version code: `97`

Every APK in this in-place update chain must be signed by the private key corresponding to this certificate fingerprint.

The private keystore and all passwords must remain outside Git and be stored in at least two encrypted offline backups. The native release manifest must stay disabled until GitHub Actions builds and verifies the permanently signed Bridge APK.
