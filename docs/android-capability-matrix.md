# Android capability matrix

Status of every platform mechanism Handyzeitvertreib relies on. "Verified" means covered
by the automated tests in this repository (JVM/Robolectric). **Nothing in this table has
been validated on a physical device or emulator yet** (no emulator was available in the
build environment). Treat every row as "requires device validation".

## Mechanisms

| Mechanism | API / permission | Android versions | What the app does with it | Limitations |
| --- | --- | --- | --- | --- |
| Usage data | `UsageStatsManager.queryEvents`, special app-op `PACKAGE_USAGE_STATS` | 8.0+ (minSdk 26) | Reads activity resumed/paused/stopped, screen-off, shutdown/startup events and aggregates foreground time and opens per local day | Only the user can grant it (system settings). Event data is kept by Android for a limited, device-dependent time. It is not a live stream: values are as fresh as the last refresh. Sessions that began more than 6 hours before the queried range are not seen. |
| Periodic check | WorkManager `PeriodicWorkRequest` (15 min) | all | Refreshes usage, applies retention, records "limit reached" once per limit and day, posts one notification | 15 minutes is the platform minimum. Doze, battery saver and OEM task killers can delay it further. |
| Notifications | `POST_NOTIFICATIONS` (runtime on 13+) | all; runtime prompt 13+ | One quiet notification per limit and day that opens the regulation screen | If denied, the app falls back to showing reached limits only when opened. |
| Regulation screen on app open | Optional `AccessibilityService` (`typeWindowStateChanged`, `canRetrieveWindowContent=false`) | all | Learns the package name of the window that comes to the foreground; if its limit is reached, starts `RegulationActivity` | Off by default; the user must enable it in system settings. Starting an activity from the background relies on the exemption for apps with a bound accessibility service (to be validated on devices). OEMs may kill the service. The user can disable it at any time. There is a short delay between the app appearing and the regulation screen. |
| Biometric confirmation | `androidx.biometric.BiometricPrompt` 1.1.0, `BIOMETRIC_WEAK` (optionally `+ DEVICE_CREDENTIAL`) | all | Confirms an extension; the app receives only success/error codes | No fingerprint sensor → capability `NO_HARDWARE`; nothing enrolled → `NONE_ENROLLED` with a link to enrolment. Device credential fallback is opt-in. |
| Background activity launch without accessibility | — | 10+ restricts it | Not used | A normal app cannot reliably open a screen over another app from the background. |
| Full-screen intent notifications | `USE_FULL_SCREEN_INTENT` | 14+ restricts it | Not used | Intended for calls and alarms. |
| Overlay (`SYSTEM_ALERT_WINDOW`) | special permission | all | Not used | Considered too intrusive for this product; could be evaluated later. |
| Device owner / managed device | DevicePolicyManager | all | Not used | Requires provisioning a dedicated device; out of scope for self-regulation. |
| Foreground service | `FOREGROUND_SERVICE` | all | Not used by the app itself (the permission is merged from WorkManager) | A permanent notification would be required and battery cost increases. |

## Resulting enforcement levels

`EnforcementCapabilities.strongestLevel` derives the current level from the permission
snapshot; the UI shows it in Settings › Permissions and on the dashboard.

| Level | Condition | User experience |
| --- | --- | --- |
| `NONE` | Usage access missing | Nothing can be measured. The UI shows "usage access missing" instead of zero values. |
| `IN_APP_ONLY` | Usage access, no notifications (or disabled in app) | Reached limits appear on the dashboard when the app is opened. |
| `NOTIFICATION` | Usage access + notifications | Notification typically within ~15 minutes after the limit is reached. |
| `SCREEN_ON_APP_OPEN` | Usage access + accessibility service enabled | Regulation screen when a limited app comes to the foreground; re-check when remaining time runs out while the app stays open. |

No level is a guaranteed block. The regulation screen can be left through the home button
or by disabling the service.

## Distribution

The product owner chose **private APK distribution**. Google Play's policies on the
Accessibility API and on usage-access apps were therefore not reviewed. If Play
distribution becomes a goal, that review is a release blocker.
