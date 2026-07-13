# Amin Pocket GBA Update Bridge Signing

The update bridge must be signed with one permanent Android signing key from its first real installation onward.

## Fixed update identity

- Package ID: `com.amin.pocketgba`
- First bridge version: `0.9.2-bridge1`
- First bridge version code: `97`
- Runtime entry: `https://ken12121122-dotcom.github.io/amin-vault/gba.html?native=1&channel=bridge`
- Native release manifest: `https://ken12121122-dotcom.github.io/amin-vault/native-release-manifest.json`

Preview 4 and Preview 5 use different package IDs, so Bridge 1 can remain installed beside them during acceptance testing.

## Required GitHub Actions secrets

Create these four repository secrets before treating the workflow artifact as the permanent bridge:

1. `AMIN_KEYSTORE_BASE64`
2. `AMIN_KEYSTORE_PASSWORD`
3. `AMIN_KEY_ALIAS`
4. `AMIN_KEY_PASSWORD`

`AMIN_KEYSTORE_BASE64` must contain the signing keystore encoded as one uninterrupted Base64 string.

## Generate a permanent keystore

Run this once on a trusted computer with JDK 17 or newer:

```bash
keytool -genkeypair \
  -keystore amin-pocket-gba-release.jks \
  -alias amin-pocket-gba \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -storetype JKS
```

Create the Base64 value:

### Linux

```bash
base64 -w 0 amin-pocket-gba-release.jks > amin-pocket-gba-release.base64.txt
```

### macOS

```bash
base64 < amin-pocket-gba-release.jks | tr -d '\n' > amin-pocket-gba-release.base64.txt
```

### PowerShell

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes('amin-pocket-gba-release.jks')) |
  Set-Content -NoNewline amin-pocket-gba-release.base64.txt
```

## Backup rule

Keep at least two offline copies of:

- `amin-pocket-gba-release.jks`
- keystore password
- alias
- key password

Never commit the keystore, Base64 text, or passwords to the repository. Losing this key permanently breaks future in-place APK updates.

## Workflow artifact labels

The Android workflow clearly separates signing states:

- `Amin-Pocket-GBA-v0.9.2-bridge1`: permanent signing secrets were available and the APK may begin the fixed update chain.
- `Amin-Pocket-GBA-v0.9.2-bridge1-CI-ONLY-NOT-UPDATABLE`: GitHub used a temporary debug certificate. Do not use it as the permanent bridge.

The artifact also contains:

- `SHA256SUMS.txt`
- `APK_SIGNING_CERTIFICATE.txt`
- `SIGNING_STATUS.txt`

## Acceptance sequence

1. Keep Preview 4 installed.
2. Install the permanently signed Bridge 1 APK.
3. Confirm the app shows `0.9.2-bridge1 · code 97`.
4. Confirm Runtime updates to `0.9.2-rc6`.
5. Import a ROM and start the game.
6. Verify the game screen opens instead of displaying HTML source.
7. Verify save data survives app restart.
8. Only after passing all checks, prepare native Bridge 2 and enable the APK release manifest.
