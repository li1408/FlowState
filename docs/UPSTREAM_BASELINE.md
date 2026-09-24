# Upstream Baseline

## Status

Accepted on 2026-09-24.

## Decision

Use FlowState `v.3.5.2` as the pinned engineering baseline for the new 30-day / 30-item Android product. Keep the upstream Apache-2.0 license and develop product changes on top of the pinned source instead of modifying or tracking a moving upstream branch.

This baseline commit intentionally contains no product, UI, package-name, or branding changes.

## Provenance

- Upstream: <https://github.com/Markel15/FlowState>
- Tag: `v.3.5.2`
- Commit: `4420523c6d3a0e9d6da32d4e89279f807a35a815`
- Local development branch: `chore/establish-baseline`
- License: Apache-2.0, retained in `LICENSE`

## Why this baseline

FlowState provides a native Kotlin and Jetpack Compose application with Room, Hilt, modular feature boundaries, offline-first task data, and existing Android navigation support. It is not yet the intended product: challenge lifecycle, exactly 30 independent items, remaining-count emphasis, completion records with optional images, strong audiovisual/haptic completion feedback, and the new white glass visual system still need to be designed and implemented.

## Verified toolchain

- Temurin JDK `17.0.20+8`, matching the upstream CI major version
- Gradle `9.3.1`
  - Distribution SHA-256: `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06`
- Android Gradle Plugin `9.1.1`
- Kotlin `2.2.10`
- Android SDK Platform `37.0` revision 2
- Android SDK Build Tools `36.0.0`
- App SDK levels: compile `37`, target `36`, minimum `31`

## Baseline verification

The original source was verified with:

```text
gradlew.bat --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
```

Result: `BUILD SUCCESSFUL in 32m 32s`; 600 actionable tasks executed.

After normalizing the local SDK registration, the same checks were repeated without network access:

```text
gradlew.bat --offline --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
```

Result: `BUILD SUCCESSFUL in 1m 22s`; 57 tasks executed and 543 were up-to-date. The SDK location warning did not recur.

### Unit tests

- JUnit XML suites: 31
- Tests: 345
- Failures: 0
- Errors: 0
- Skipped: 0

The repository also contains 10 Android instrumented-test source files. They were not executed during this desktop baseline because no device or emulator was in scope.

### Lint

- Module reports: 11
- Errors: 0
- Warnings: 112
- Hints: 6

The largest existing categories are spelling (`Typos`, 35), unused resources (17), ellipsis typography (15), and dependency-version suggestions (12). The successful Lint task means these are non-blocking baseline findings, not a clean-warning claim.

### Debug APK

- Artifact: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 50,637,920 bytes
- SHA-256: `99e9861986c291a0a58716ec2578697a9dc347e83914153fb5b9f0adddbeb34b`
- Application ID: `com.markel.flowstate`
- Version name/code: `3.5.2` / `1`
- Minimum/target SDK: `31` / `36`
- Debuggable: yes
- Signature verification: APK Signature Scheme v2 verified
- Debug certificate SHA-256: `e2a5ffe2a6c16ba54d56c3ece02c9504290a770c069940c79af6e5f14695abd6`

This is a local debug artifact, not a release-signed deliverable. Its hash and certificate can change when the APK is rebuilt with another debug keystore.

## Known upstream findings

- Room reports that `CheckListItemEntity.listId` is a foreign-key column without an index and may cause full table scans when the parent table changes.
- Kotlin reports future annotation default-target behavior changes in several injected constructor parameters.
- The build uses Android Gradle Plugin compatibility flags and DSLs scheduled for removal in AGP 10.
- Debug packaging keeps `libandroidx.graphics.path.so` and `libdatastore_shared_counter.so` unstripped.

These findings were preserved as baseline evidence rather than silently changed before product work begins.

## Not yet verified

- Xiaomi 15 / HyperOS 4 beta installation and runtime behavior
- Predictive Back behavior on the target device
- Android instrumented tests
- Release signing, release APK reproducibility, or distribution
- Any 30-day challenge feature or new visual/feedback behavior
