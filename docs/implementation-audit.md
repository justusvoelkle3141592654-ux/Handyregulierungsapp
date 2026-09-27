# Implementation audit

Date of audit: 2026-09-27
Repository: `justusvoelkle3141592654-ux/Handyregulierungsapp`
Product name used in the app: **Handyzeitvertreib**

## 1. What already exists

The repository was **completely empty** at the start of this work: no commits on any
branch (local or remote), no README, no Gradle files, no source code, no CI, no
signing configuration. There was nothing to preserve, reuse or migrate.

Consequences:

- There is no existing package name, persistence layer, authentication, backend,
  localisation or theme to stay compatible with.
- The repository name (`Handyregulierungsapp`) differs from the product name in the
  master prompt (`Handyzeitvertreib`). The app is named *Handyzeitvertreib*; the
  repository name is left unchanged.

## 2. What can be reused

Nothing from the repository. The native Android foundation was created from scratch.

## 3. What is incomplete or broken

Not applicable (empty repository). Environment findings that affect verification:

| Finding | Impact |
| --- | --- |
| No Android SDK pre-installed in the build container | Installed command-line tools, `platforms;android-37.0`, `build-tools;37.0.0`, `platform-tools` into `/opt/android-sdk`. |
| No `/dev/kvm` in the build container | No emulator can run here. Instrumented tests cannot be executed in this environment; UI tests run on the JVM via Robolectric instead. On-device validation remains open. |
| Maven Central intermittently returned HTTP 429 through the sandbox proxy | Worked around with an environment-local Gradle init script (not part of the repository) that uses Google's Maven Central mirror. Normal developer machines need nothing special. |

## 4. What must be added

Everything required by the master prompt: Gradle project, app module, design system,
Room database, DataStore preferences, usage tracking, limit engine, enforcement,
regulation screen, biometric extension, onboarding, account boundary, statistics,
settings, tests and documentation.

## 5. Decisions taken with the product owner (2026-09-27)

| Topic | Decision |
| --- | --- |
| Application ID | `de.handyzeitvertreib.app` |
| Distribution | Private APK (sideload), no Google Play release planned for now |
| Account | Interface only: a clean `AccountRepository` boundary with an honest "not configured" state. No fake sign-in. |
| UI language | German default, English as second locale |

## 6. Technology choices

| Area | Choice | Reason |
| --- | --- | --- |
| Language / UI | Kotlin, Jetpack Compose, Material 3 + custom glass design system | Master-prompt default; nothing existing to stay compatible with |
| Build | Gradle 9.8.0, AGP 9.4.1 (built-in Kotlin), Kotlin 2.3.21, KSP 2.3.12 | Current AndroidX releases (core 1.19, Compose UI 1.12) require AGP ≥ 9.1 and compileSdk 37 |
| SDK levels | compileSdk 37, targetSdk 36, minSdk 26 | targetSdk 36 keeps well-understood Android 16 behaviour; minSdk 26 covers Android 8.0+ |
| Persistence | Room (usage snapshots, limits, groups, regulation events), DataStore (preferences) | Local-first |
| DI | Manual constructor injection through one `AppContainer` | Small MVP; avoids an annotation-processing framework. Can be replaced by Hilt later. |
| Background work | WorkManager (15-minute periodic check, the platform minimum) | Battery-friendly, survives reboots |
| Biometrics | `androidx.biometric` `BiometricPrompt` 1.1.0 (latest stable) | Official API, capability abstraction |
| Module structure | Single `app` module with strict package boundaries | Module-per-feature would be overhead for an MVP; extraction path documented in `docs/architecture.md` |

## 7. Android-version and policy constraints

Details are in `docs/android-capability-matrix.md`. Summary:

- `PACKAGE_USAGE_STATS` is a special app-op permission. It can only be granted by the
  user in system settings; the app can only link there and re-check on resume.
- Usage data comes from `UsageStatsManager.queryEvents`. It is event-based, not a
  live stream; a background check can run at most every 15 minutes via WorkManager.
- Since Android 10 apps may not start activities from the background. Exception used
  here: an app with a **bound accessibility service** may do so. Without the optional
  accessibility service the app can only notify and show the regulation screen when
  opened.
- Accessibility services are powerful. The service is opt-in, off by default,
  explained on a dedicated screen, only reads the package name of the foreground
  window and does not read window content. For a later Google Play release the
  Accessibility API policy and a prominent disclosure would have to be reviewed
  (release blocker, not relevant for private APK distribution).
- `POST_NOTIFICATIONS` is a runtime permission on Android 13+.
- Device-owner / managed-device APIs require provisioning a dedicated device and are
  out of scope for a personal app.
- Not every device has a fingerprint sensor. `BiometricManager.canAuthenticate` decides
  what is offered.

## 8. Implementation plan (phases)

1. Understand — this audit.
2. Foundation — Gradle, design system, DI container, baseline tests.
3. Local data and usage — models, Room, DataStore, usage access state, event aggregation.
4. Limits — app timers, group limits, evaluation engine with precedence tests.
5. Regulation — capability detection, notification fallback, optional accessibility
   enforcement, regulation screen, biometric extension.
6. Account and onboarding — wizard, account boundary.
7. Polish — dashboard, statistics, accessibility, empty/error states, dark mode.
8. Verify and document — quality gates, README, privacy, verification report.

## 9. Open decisions that must not be guessed

| Topic | Status |
| --- | --- |
| Account provider (Firebase, Credential Manager, own backend) | Open. Only the interface exists. Needs a provider choice and credentials from the product owner. |
| Release signing key | Open. The key must be created and kept by the product owner; `keystore.properties` is git-ignored. |
| Privacy policy text | Placeholder only; requires legal review before any public distribution. |
| Google Play distribution | Not planned. If it becomes a goal, the accessibility-service policy review is a blocker. |
| Weekly limits and time-window schedules | Deferred, see `docs/roadmap.md`. |
