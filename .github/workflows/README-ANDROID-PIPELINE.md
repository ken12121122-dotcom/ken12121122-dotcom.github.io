# Android workflow ownership

- `android-ci.yml`: pull-request validation for `release/android`.
- `android-release.yml`: the only production signing and publication workflow.
- `android-nightly-emulator.yml`: non-blocking scheduled emulator regression.

Legacy Android build and emulator workflows have been retired to prevent duplicate PR and push runs.
