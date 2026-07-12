# Amin Native Shell v0.9.1 Preview

## Added native capabilities

- Android network status bridge using `ConnectivityManager.NetworkCallback`
- Native capability and version contract delivered to the web runtime
- `ACTION_VIEW`, `ACTION_SEND`, and `ACTION_SEND_MULTIPLE` cartridge entry points
- Same-origin native cartridge streaming endpoint for `.gba`, `.bin`, and `.zip`
- APK-embedded offline recovery page
- Existing native controller bridge and save flush remain enabled

## Preview install safety

The preview APK uses the package ID:

```text
com.amin.pocketgba.preview091
```

It installs beside the existing v0.9.0 debug app instead of replacing it. This protects the existing app's WebView storage, ROM library, and saves from deletion during preview testing.

## Validation

GitHub Actions run `29175909803` passed:

- Java and Android manifest compilation
- runtime JavaScript syntax
- runtime manifest and asset completeness
- atomic update and rollback tests
- package ID, version code, version name, and app label checks
- `ACCESS_NETWORK_STATE` permission check
- embedded `assets/bootstrap/index.html` check
- APK SHA-256 verification
- artifact upload

Independent artifact verification:

```text
APK SHA-256: 66ca41e956a99de5c0101922f69f615e5e57db08472c398a9616ed3abab7d292
Signer certificate SHA-256: 3af661929c8e6ad526ea486aab5637c6ae9b30fae61a4e1f9816c5bf0b42438f
```

## Important signing boundary

GitHub-hosted debug builds generate different debug certificates across runs. This preview therefore must not be treated as the permanent update channel. A final production package needs a persistent private signing key or Play App Signing before in-place native APK updates are enabled.
