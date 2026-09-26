# FlowState performance tests

This module contains device-only Macrobenchmark and Baseline Profile tests.
It targets the generated `benchmarkRelease`/`nonMinifiedRelease` variants;
the production release manifest is not made profileable. Both generated app
variants use the isolated package `com.markel.flowstate.benchmark`, so running
them cannot replace or modify the database of `com.markel.flowstate`.

## Generate the profile

Connect a physical Android 12+ device, then run:

```shell
.\gradlew.bat :app:generateBaselineProfile
```

Generation is intentionally not attached to every release build. The generated
profile is saved into the app source tree by the AndroidX Baseline Profile
plugin and should be reviewed before committing.

## Run benchmarks

```shell
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest
```

The checked-in scenarios cover cold startup, primary-tab navigation, Flow and
Calendar scrolling, and the performance-sensitive Habits interactions. Flow
and Habits tests intentionally fail with a clear missing-tag/precondition
message unless the benchmark installation contains representative seed data:
enough rows to scroll, at least one Boolean habit, at least one numeric habit,
and at least two visible habits for reordering. Seed only the isolated
benchmark package; the production package is intentionally never targeted.
