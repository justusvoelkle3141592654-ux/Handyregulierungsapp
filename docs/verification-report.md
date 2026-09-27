# Verification report

Date: 2026-09-27. Environment: Linux build container, OpenJDK 21, Gradle 9.8.0,
AGP 9.4.1, Kotlin 2.3.21, Android SDK platform 37.0, build-tools 37.0.0.
No KVM was available, so no emulator could run.

## Commands and results

| Gate | Command | Result |
| --- | --- | --- |
| Clean build from fresh state | `./gradlew clean spotlessCheck :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-configuration-cache` | BUILD SUCCESSFUL |
| Formatting / static style | `./gradlew spotlessCheck` (ktlint 1.8.0, `ktlint_official`) | Passed |
| Android lint | `./gradlew :app:lintDebug` (`abortOnError = true`) | 0 errors, 2 warnings (see below) |
| Unit, integration and UI tests (JVM + Robolectric) | `./gradlew :app:testDebugUnitTest` | 116 tests: 104 passed, 12 screenshot renders skipped (opt-in), 0 failures |
| Debug build | `./gradlew :app:assembleDebug` | `app-debug.apk` produced |
| Release build | `./gradlew :app:assembleRelease` (R8 minify + resource shrinking, key from `keystore.properties`) | `app-release.apk`, verified with `apksigner verify` (APK Signature Scheme v2). Application, activities, accessibility service and worker are present in the minified dex; the manifest contains no `INTERNET` permission |
| Instrumented tests | — | **Not run**: no emulator/device available |
| Dependency vulnerability scan | `./scripts/osv-scan.sh` (OSV database, 156 release runtime packages) | 0 known vulnerabilities on 2026-09-27; the query was cross-checked against a known vulnerable version |
| Visual review | `./gradlew :app:testDebugUnitTest --tests '*ScreenshotRenderTest' -Phzv.screenshots=<dir>` | 12 screens rendered (light/dark) with Robolectric native graphics and reviewed; see below |

### Remaining lint warnings (accepted)

| Warning | Reason |
| --- | --- |
| `OldTargetApi` (targetSdk 36, latest 37) | Deliberate: Android 16 behaviour is well understood; raise after device testing |
| `UnusedAttribute` `isAccessibilityTool` below API 31 | Harmless; the attribute is ignored on older versions |

Suppressed in `app/lint.xml` with reasons: `ObsoleteSdkInt` for `mipmap-anydpi-v26`
(aapt2 requires it for adaptive icons) and `NewerVersionAvailable` downgraded to
informational (version updates are reviewed deliberately).

## Visual review findings (fixed)

Rendering the screens revealed six defects the semantic tests did not catch:

| Finding | Fix |
| --- | --- |
| Week bar chart drew all bars flat | Chart redrawn as a single fixed-height canvas |
| "45 Min.." double period in the regulation text | Copy rephrased without a trailing period after durations |
| App name shown twice on limit cards | `LimitProgressRow(showTitle = false)` inside cards |
| Selected chips/segments used Material's default lavender | `secondaryContainer` mapped to the accent token |
| Number and unit wrapped onto separate lines ("45 / Min.") | Non-breaking spaces in duration formats |
| Last onboarding button touched the scroll edge | Bottom padding in the onboarding scroll area |

Robolectric rendering is not identical to a device (fonts, app icons are placeholders).

## What the tests cover

| Area | Tests |
| --- | --- |
| Duration formatting, limit rules, overflow | `DurationFormatterTest` |
| Day boundaries, DST (23/25-hour days), time zones, yesterday comparison | `DayBoundariesTest` |
| Event aggregation: multiple intervals, activity switches, midnight split, screen off, shutdown/startup, open sessions, unsorted input, split screen, empty data | `UsageAggregatorTest` |
| Limit evaluation: boundaries, disabled limits, groups, precedence both ways, tie, extensions, no double counting | `LimitEvaluatorTest` |
| Extension rules and sanitising | `ExtensionRulesTest` |
| Permission, biometric, enforcement-level, account-state and event-type mapping | `MappingTest` |
| Sorting, filtering, search, loosening detection | `AppListFilterTest` |
| Insights aggregation (uncaptured days never count as zero), export escaping | `InsightsAggregationTest` |
| Repositories with Room + DataStore: missing/revoked access, snapshots, coverage of past days, CRUD, groups, extensions, day rollover, retention, delete everything, offline/no account | `RepositoryIntegrationTest` |
| Room schema v1 against entities | `DatabaseSchemaTest` |
| Worker records "limit reached" once; does nothing without access | `LimitCheckWorkerTest` |
| Dashboard: permission missing, sample data, no data, dark theme with font scale 2 | `DashboardScreenTest` |
| Regulation screen: copy, exact extension duration, disabled/cap/no hardware, enrol link, outcome | `RegulationScreenTest` |
| Extension flow with fake authenticator: success, cap, cancel, lockout, failure, unavailable, deleted limit | `RegulationViewModelTest` |
| App search, create/edit limit, minimum bound | `AppsAndLimitEditorTest` |
| Onboarding: full flow to dashboard, back, configuration change, missing permission | `OnboardingFlowTest` |

## Not verified — requires a device

- Real `UsageStatsManager` data on different OEMs and Android versions.
- The accessibility service opening the regulation screen from the background.
- `BiometricPrompt` with real sensors, lockout behaviour and device-credential fallback.
- WorkManager timing under Doze and battery saver.
- Notification appearance and tap behaviour.
- Visual quality of the glass design on real screens, TalkBack behaviour, very small and
  very large screens.

### Suggested manual checklist

1. Install `app-debug.apk`, complete onboarding without granting anything → dashboard shows "usage access missing", not zero.
2. Grant usage access, return → today's total and top apps appear; "last updated" changes on refresh.
3. Create a 5-minute limit for an app, use it for 6 minutes → notification within ~15 minutes (service off).
4. Enable the accessibility service, reopen the app → regulation screen appears.
5. Request more time with fingerprint → app usable for exactly the shown minutes; second request today is refused with the cap message.
6. Cancel the prompt → no time added. Remove all fingerprints → set-up hint appears.
7. Create a group limit that is stricter than an app limit → the group governs.
8. Switch to dark mode and the largest font size; check every screen.
9. Export data; delete everything → onboarding starts again.
