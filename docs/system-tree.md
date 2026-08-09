# Amin Platform System Tree

Version: 0.1.0-draft  
Status: generated

```text
AMIN PLATFORM [AMIN-0000]
│
├─ Android Application [AMIN-1000]
│  ├─ Voice System [AMIN-1100]
│  ├─ Prompt and Keyboard System [AMIN-1200]
│  ├─ Visual Node and Architecture System [AMIN-1300]
│  └─ Runtime and Update System [AMIN-1400]
│
├─ Knowledge and Wiki [AMIN-2000]
│
├─ Control and Automation [AMIN-3000]
│
├─ AI and Agent Layer [AMIN-4000]
│
├─ CI Validation and Release [AMIN-5000]
│
└─ Work Governance [AMIN-6000]
   ├─ Master BOM
   ├─ Node Registry
   ├─ GitHub Project: Amin Platform Development
   ├─ Issue → Agent → Branch → PR
   └─ Integration → Validation → Release
```

## Interpretation

- The tree is a functional BOM, not a directory listing.
- `node_id` is the stable identity used to connect features, issues, PRs, dependencies, and releases.
- A branch is a temporary execution location and must not become a permanent substitute for the functional tree.
- The registry is intentionally incomplete where repository evidence has not yet been inventoried.

## Current verified release anchor

```text
release/android/android-native/release-version.json
              ↓
Android development/release metadata
              ↓
main/amin-vault/native-release-manifest.json
              ↓
main/amin-vault/releases/
```

Observed at creation of this draft:

- package: `com.amin.pocketgba`
- versionCode: `146`
- versionName: `0.10.18`
- bridge: `50`
- production manifest latestVersionName: `0.10.18-bridge50`

## Next inventory pass

The next pass should populate source paths for each functional node and distinguish:

```text
confirmed implementation
partial implementation
planned capability
deprecated capability
external/local-only component
```

No unknown path should be fabricated to make the tree appear complete.
