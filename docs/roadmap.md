# Roadmap

Items deliberately deferred from the MVP, with the extension point in the code.

| Item | Why deferred | Extension point |
| --- | --- | --- |
| Device validation | No emulator/KVM in the build environment | Run the checklist in `docs/verification-report.md` on at least one Android 8–9, one Android 12–13 and one Android 14+ device, including one OEM with aggressive background limits |
| Instrumented tests | Same reason | Add `androidTest` source set; the Robolectric tests can largely be moved |
| Weekly limits | Not required for the MVP; adds UI and precedence complexity | `AppLimit` / `GroupLimit` models, `LimitEvaluator.evaluate` (add a second status per limit with a weekly window) |
| Time-window schedules (incl. overnight windows) | Deferred per master prompt | Add schedule fields to `AppLimitEntity` (Room migration to v2) and filter statuses in `LimitEvaluator` by the current local time |
| Custom start of day (e.g. 04:00) | Not requested | `DayBoundaries.windowFor` is the single place that defines a day |
| "Almost reached" reminder | 15-minute worker cannot time it reliably | Accessibility service already re-checks at the remaining time; a reminder could be posted there |
| Account provider | Product decision and credentials needed | Replace `UnconfiguredAccountRepository` in `AppContainer` |
| Opt-in settings sync | Depends on account provider | New `sync` package, strictly separate from Room usage tables |
| Database encryption | Decision pending | Room `openHelperFactory` |
| Dependency injection framework | Manual container is sufficient for one module | Replace `AppContainer` with Hilt when modules are extracted |
| Module extraction | Single module keeps the MVP simple | See `docs/architecture.md` |
| Release signing | Key must be created and kept by the product owner | `keystore.properties` (git-ignored), read in `app/build.gradle.kts` |
| Google Play distribution | Not planned | Review Accessibility API and usage-access policies first |
