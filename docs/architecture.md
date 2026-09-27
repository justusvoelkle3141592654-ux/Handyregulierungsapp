# Architecture

Single Gradle module (`:app`) with strict package boundaries. Kotlin, Jetpack Compose,
Material 3 with a custom glass design system, coroutines and Flow, Room, DataStore,
WorkManager, `androidx.biometric`. Dependencies are wired manually in `AppContainer`.

## Packages

| Package | Responsibility | Android-free? |
| --- | --- | --- |
| `core.model` | Domain models (`AppLimit`, `GroupLimit`, `AppUsage`, `RegulationEvent`, `ExtensionPolicy`, `UserPreferences`), `LimitRules` | yes |
| `core.time` | `DayBoundaries` (DST-aware local days), `AppClock` | yes |
| `core.format` | `DurationFormatter`, overflow-safe `saturatingAdd` | yes |
| `usage` | `UsageAggregator` (events → sessions → per-window totals), `UsageRepository`, platform sources | aggregator: yes |
| `limits` | `LimitEvaluator` (precedence, group sums), `LimitRepository` | evaluator: yes |
| `regulation` | `ExtensionRules`, `RegulationRepository`, `RegulationCoordinator`, regulation screen, activity and view model | rules: yes |
| `enforcement` | `EnforcementEngine` implementations and capability levels, `LimitCheckWorker`, `LimitNotifier`, `RegulationAccessibilityService` | no |
| `biometric` | `ExtensionAuthenticator` interface, `BiometricPrompt` implementation, result mapping | mapping: yes |
| `account` | `AccountRepository` boundary, `UnconfiguredAccountRepository`, state mapping | yes |
| `permissions` | Permission snapshot, mapping and monitor | mapping: yes |
| `data.db` | Room entities, DAOs, database (schema exported to `app/schemas`) | no |
| `data.prefs` | DataStore-backed `PreferencesRepository` | no |
| `data.management` | Export and deletion | exporter: yes |
| `ui.*` | Design system and feature screens; each screen has a stateless composable plus a route that binds a view model | no |

## Data flow

```
UsageStatsManager events ──► UsageEventSource ──► UsageAggregator ──► UsageRepository
                                                                         │  ├─ StateFlow<TodayUsageState> (Loading / AccessRequired / Error / Ready)
                                                                         │  └─ Room: usage_snapshot + day_record
Room: app_limit, group_limit ──► LimitRepository ──┐                     │
Room: regulation_event ──► RegulationRepository ───┼──► RegulationCoordinator ──► LimitEvaluator ──► LimitEvaluation
                                                   │         ▲       ▲
                              UI view models ◄─────┘         │       │
                                              LimitCheckWorker   RegulationAccessibilityService
```

- The UI never interprets raw events. It observes `TodayUsageState` and Room flows.
- "No data" (`AccessRequired`, uncaptured `day_record`) is modelled separately from zero usage.
- Extensions are events (`EXTENSION_GRANTED`); the evaluator adds them per limit and day,
  so they expire at midnight without extra state.

## Key rules

- **Day:** local calendar day in the device's current zone (`DayBoundaries`).
- **Precedence:** for a package, the enabled limit with the least remaining time governs;
  ties go to the app limit (`LimitEvaluation.governingFor`).
- **No double counting:** group usage sums each member once; "total in limited apps" uses
  the union of packages (`LimitEvaluator.totalLimitedUsageMs`).
- **Extensions:** only after `AuthResult.Success`; daily cap per limit; allowed values
  are fixed (5/10/15 minutes, 1–3 per day) and sanitised when read.
- **Totals:** home launchers and user-excluded apps are excluded from screen-time totals,
  not from limits.

## Testing seams

`AppContainer` takes interfaces for the clock, usage source, installed apps, permissions,
authenticator and account. `testing/TestHzvApplication` (Robolectric) supplies fakes and an
in-memory database, so repository, worker, view-model and Compose tests run on the JVM.

## Future module extraction

When the codebase grows, extract in this order without changing package names:
`core` (model/time/format) → `data` (db/prefs/management) → `usage` + `limits` + `regulation`
domain → `designsystem` → feature modules. Replace `AppContainer` with Hilt at that point.
