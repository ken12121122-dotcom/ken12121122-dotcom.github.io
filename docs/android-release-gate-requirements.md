# Android Production Release Gate Requirements

Status: required for the production release workflow.

## Goal

A green Android Production Release must prove both that the release identity advanced and what actually changed. A version-only republish must be visible and must not be mistaken for a feature release.

## Gate 1 — Next production version

Before build/publish, compare `android-native/release-version.json` with the currently published `main:amin-vault/native-release-manifest.json`.

For `release/android` production releases:

- `versionCode` MUST be exactly `published.latestVersionCode + 1`.
- Full identity `versionName-bridgeN` MUST differ from the published `latestVersionName`.
- `releaseSequence` is required in release metadata.
- Future manifests MUST persist `releaseSequence`; once the published manifest contains it, the next release MUST use `published.releaseSequence + 1`.
- Failure of any required monotonic check MUST stop the workflow before expensive build/publish steps.

This gate is about release identity progression. It MUST NOT change updater `mandatory` to `true`; forced end-user updates are a separate policy.

## Gate 2 — Release Change Summary

Before build/publish, generate a human-readable release change summary against the previously published production source/release where possible.

The summary MUST include at least:

1. Previous published version → candidate version.
2. Candidate source commit SHA.
3. Commits included since the previous production release/source reference when resolvable.
4. Changed Android files and counts.
5. Whether user-visible Android code/resources changed.
6. Whether the release is metadata-only / version-only.
7. Release notes from `release-version.json`.

If no user-visible Android code/resource changed, the workflow MUST explicitly print a warning such as:

`WARNING: release contains no detected user-visible Android change; this may be a metadata-only release.`

The warning itself does not block legitimate infrastructure/metadata releases, but it must be unmistakable in Actions output.

## Manifest / evidence

Future `amin-vault/native-release-manifest.json` should include enough provenance to audit releases, including:

- `releaseSequence`
- `sourceRevision`
- existing APK SHA-256 / size / publishedAt

The generated Release Change Summary should also be uploaded as release evidence/artifact.

## Acceptance

A production release is considered trustworthy only when Actions can answer:

- Is this definitely the next version?
- Which commit produced it?
- What changed since the last published version?
- Did Android user-visible code/resources actually change?
- Is this only a metadata/version bump?

Do not modify the existing updater `mandatory` behavior as part of this requirement.