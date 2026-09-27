# Privacy and data model

This document describes the behaviour that is actually implemented. It is not a legal
assessment. Statements about GDPR or other legal compliance require a qualified review.

## 1. Stored data types

| Data | Storage | Content | Leaves the device? |
| --- | --- | --- | --- |
| `usage_snapshot` | Room (`handyzeitvertreib.db`) | Per local day and package: foreground milliseconds, number of opens, last used timestamp | No |
| `day_record` | Room | Which days were captured, when, and in which time zone | No |
| `known_app` | Room | Last known label per package (keeps history readable after uninstall) | No |
| `app_limit` | Room | Package, daily minutes, enabled flag, timestamps | No |
| `group_limit`, `group_member` | Room | Group name, daily minutes, enabled flag, member packages | No |
| `regulation_event` | Room | Limit reached, extension granted/denied/cancelled, left app; with time and minutes | No |
| Settings | DataStore (`settings`) | Onboarding state, theme, reduced motion, retention, notification toggle, extension policy, packages excluded from totals, last refresh time | No |
| Raw usage events | Memory only | Read from `UsageStatsManager` during a refresh, aggregated, then discarded | No |
| Biometric data | Never accessible | Android returns only success or an error code | No |

The export (Settings › Manage data › Export) writes a JSON file to a location the user
chooses through the Storage Access Framework. That is the only way data leaves the app,
and it is always user-initiated.

## 2. Network and third parties

- The merged manifest contains **no `INTERNET` permission**; the app manifest removes it
  explicitly with `tools:node="remove"`, so a dependency cannot add it silently.
- `ACCESS_NETWORK_STATE` is merged from WorkManager. It only reads connectivity state and
  is not used by the app, which schedules no network-constrained work.
- No advertising, analytics, crash reporting or tracking SDKs are included.
- No log statements write usage data, tokens or identifiers.

## 3. Backups

`android:allowBackup="false"`, `fullBackupContent="false"` and data-extraction rules
exclude all domains from cloud backup and device transfer. Consequence: reinstalling the
app or changing phones starts with empty history (documented trade-off).

## 4. Retention and deletion

| Mechanism | Behaviour |
| --- | --- |
| Automatic retention | 30, 90 (default) or 365 days, chosen in onboarding or Settings › Privacy. The periodic worker deletes older snapshots, day records and events. |
| Delete usage history | Removes snapshots, day records and known labels. Limits stay. |
| Delete events | Removes all regulation events; extensions granted today expire. |
| Delete everything | Removes all tables and all settings; the app restarts with onboarding. |
| Uninstall | Android deletes the app's private storage. |

## 5. Permission dependencies

| Permission | Required? | Why |
| --- | --- | --- |
| Usage access (`PACKAGE_USAGE_STATS`) | Required for measuring | Read foreground events |
| Notifications (`POST_NOTIFICATIONS`) | Optional | Limit-reached notification |
| Accessibility service | Optional, off by default | Show the regulation screen when a limited app opens |
| Biometric (`USE_BIOMETRIC`) | Optional | Confirm extensions |

Each permission has an explanation screen before the user is sent to system settings.

## 6. Account-related data

No identity provider is integrated. `AccountRepository` has a single implementation,
`UnconfiguredAccountRepository`, which always reports `NotConfigured` and never claims a
sign-in. Rules for a future provider (enforced by review, documented in the interface):

- An account may hold identity, consent and non-sensitive preferences only.
- Raw usage history is not part of the account interface. Any future sync of usage data
  must be opt-in, documented and controlled by a clear privacy setting.
- Tokens must be stored with Android Keystore-backed storage and never logged.
- Sign-out and account deletion paths are already modelled (`AccountStateMapping`).

## 7. Known limitations and open decisions

- The database is not encrypted at rest beyond Android's file-based encryption of app
  storage. SQLCipher or similar was not added; decide whether that is needed.
- The privacy policy shown in the app is a placeholder and needs legal review before any
  public distribution.
- Export files are unencrypted JSON; the user decides where they are stored.
- Account provider and any server-side processing are open decisions.
