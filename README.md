# SyncBridge

**SyncBridge** links a local Android folder with a remote SFTP or FTP folder and keeps them
synchronized according to a mode you choose (one-way, two-way, or mirror), on a schedule or on
demand — the Android equivalent of a Syncthing/rsync profile pointed at your own server.

This repository is the MVP implementation described in the project spec (see "Scope and roadmap"
below for what's built vs. planned). It is a **real, buildable Gradle multi-module Kotlin project**,
not a mockup: the sync engine, SFTP/FTP clients, and file-filter logic are pure-Kotlin/JVM modules
with passing unit tests you can run right now with `./gradlew test` (no Android SDK or emulator
required for those modules — see "Build instructions").

---

## 1. Architecture

Clean-architecture-by-module, four layers:

```
UI (Compose screens + ViewModels)          →  :app
Domain / sync engine (pure Kotlin)         →  :core:sync, :core:common
Data (Room, Keystore, protocol clients)    →  :core:database, :core:security,
                                               :protocol:sftp, :protocol:ftp
```

The key architectural decision: **the sync engine (`:core:sync`) and the domain model
(`:core:common`) do not depend on Android at all.** They are plain `kotlin("jvm")` Gradle modules.
`RemoteFileClient` and `LocalFileSystem` are interfaces defined in `:core:common`; the SFTP/FTP
clients and the Storage-Access-Framework-backed local filesystem are the only things that touch a
real network socket or `ContentResolver`. This buys three things:

1. The diffing algorithm, conflict resolution, retry/backoff, and mass-deletion guard are unit
   tested with fakes, with no Android instrumentation test / emulator needed.
2. Adding a protocol (WebDAV, S3, a cloud provider SDK) is "implement `RemoteFileClient`", not "touch
   the engine."
3. `:app` is thin: Hilt wiring, Room entities, Compose screens, and Android system integration
   (SAF, WorkManager, a foreground service, notifications). It contains no sync *logic*, only sync
   *plumbing*.

```
                     ┌─────────────────────┐
                     │        :app         │  Compose UI, ViewModels, Hilt DI,
                     │ (Android application)│  WorkManager, foreground service,
                     └─────────┬────────────┘  Room-backed repository impls
                               │ implements/uses
        ┌──────────────────────┼───────────────────────┐
        │                      │                        │
┌───────▼────────┐   ┌─────────▼─────────┐   ┌──────────▼─────────┐
│ :core:database │   │   :core:security   │   │  :protocol:sftp /  │
│  (Room, Android)│   │ (Keystore, Android)│   │  :protocol:ftp     │
└─────────────────┘   └────────────────────┘   │  (pure JVM, SSHJ / │
                                                 │  Commons Net)      │
                                                 └──────────┬──────────┘
                                                            │ implements
                                                 ┌──────────▼──────────┐
                                                 │    :core:common     │  RemoteFileClient,
                                                 │    (pure JVM)       │  LocalFileSystem,
                                                 └──────────▲──────────┘  AppError, domain models
                                                            │ uses
                                                 ┌──────────┴──────────┐
                                                 │     :core:sync       │  SyncPlanner,
                                                 │     (pure JVM)       │  ConflictResolver,
                                                 └──────────────────────┘  SyncExecutor
```

---

## 2. Project structure

```
SyncBridge/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml          # version catalog — single source of truth for versions
├── app/                                # Android application module
│   └── src/main/kotlin/com/syncbridge/app/
│       ├── SyncBridgeApp.kt            # @HiltAndroidApp, WorkManager Configuration.Provider
│       ├── MainActivity.kt
│       ├── di/                         # Hilt modules (Database, Security, Repository, Coroutine)
│       ├── data/
│       │   ├── local/                  # SafLocalFileSystem (SAF-backed LocalFileSystem impl)
│       │   ├── remote/                 # RemoteClientFactory (protocol dispatch)
│       │   ├── mapper/                 # Entity <-> domain-model mapping, filter JSON (de)serialize
│       │   └── repository/             # Connection/Profile/History/Settings repositories
│       ├── sync/                       # SyncRunner, SyncWorker, SyncForegroundService,
│       │                                 SyncScheduler, NotificationHelper, BootCompletedReceiver
│       └── ui/                         # Compose screens + ViewModels, per feature package
│           ├── dashboard/  connections/  profile/  livesync/  history/  settings/  navigation/  theme/
├── core/
│   ├── common/                         # pure JVM: domain models, RemoteFileClient/LocalFileSystem
│   │   │                                 interfaces, AppError, FileFilterConfig, PathValidator
│   │   └── src/test/…                  # FileFilterMatcherTest
│   ├── sync/                           # pure JVM: SyncPlanner, ConflictResolver, SyncExecutor,
│   │   │                                 RetryPolicy, PauseController, RemoteTreeScanner
│   │   └── src/test/…                  # SyncPlannerTest, ConflictResolverTest, SyncExecutorTest
│   ├── database/                       # Android library: Room entities, DAOs, SyncBridgeDatabase
│   └── security/                       # Android library: KeystoreCredentialCipher, BiometricAppLockGate
└── protocol/
    ├── sftp/                           # pure JVM: SftpRemoteFileClient (SSHJ)
    └── ftp/                            # pure JVM: FtpRemoteFileClient (Apache Commons Net)
```

---

## 3. Database schema (Room, `:core:database`)

Eight tables, matching the spec's schema plus two support tables (`known_hosts`, `recycle_bin`)
needed to actually implement host-key pinning and the recycle-bin delete option. Enum-like columns
are stored as plain strings (`.name` of the corresponding `:core:common` enum) so this module has
zero dependency on how the domain layer models sync modes, conflict rules, etc. — only `:app`'s
repository layer knows that mapping.

| Table            | Purpose                                                                 |
|-------------------|--------------------------------------------------------------------------|
| `connections`     | Saved server connections. Secrets are Keystore-ciphertext, never plaintext. |
| `sync_profiles`   | One local-folder ↔ remote-folder link, its rules, schedule, running totals. |
| `sync_state`      | Per-file "last known good" record — the third reference point that lets two-way sync distinguish "deleted on one side" from "new on the other side" (see section 6). |
| `sync_runs`       | One row per executed sync (manual/scheduled/foreground) — backs the History screen. |
| `sync_errors`     | Per-file failures within a run, with the `AppError` code/message/fix — backs Error Details. |
| `transfer_queue`  | Reserved for surviving process death mid-transfer (schema present; the executor doesn't yet persist mid-run queue state — see roadmap). |
| `known_hosts`     | SFTP host-key fingerprints the user has explicitly accepted (`SftpKnownHostsStore`). |
| `recycle_bin`     | Bookkeeping for files moved aside instead of deleted (schema present; UI/restore flow not yet wired — see roadmap). |

`SyncBridgeDatabase` is version 1 with `exportSchema = true`; there are intentionally no
`Migration` objects yet (see the KDoc on `SyncBridgeDatabase.build`) — add real migrations before
shipping any schema change past v1.

---

## 4. Main Kotlin classes

**`:core:common`** (`com.syncbridge.core.common`)
- `model/*` — `ConnectionConfig`, `SyncProfile`, `SyncMode`, `ConflictRule`, `DeleteRule`,
  `ScheduleRule`, `NetworkRule`, `FileFilterConfig` + `FileFilterMatcher`, `FileTreeSnapshot`.
- `client/RemoteFileClient` — the protocol-agnostic remote interface every protocol implements.
- `client/LocalFileSystem` — the SAF-agnostic local interface `SafLocalFileSystem` implements.
- `error/AppError` — one sealed hierarchy for every failure mode in spec section 17 (technical code,
  human message, suggested fix, retryable flag).
- `util/PathValidator` — path-traversal / invalid-filename guards shared by the engine and both clients.

**`:core:sync`** (`com.syncbridge.core.sync.engine`)
- `SyncPlanner` — the diff algorithm (local snapshot × remote snapshot × prior state → operations).
- `ConflictResolver` — turns a detected conflict into a concrete action per `ConflictRule`.
- `SyncExecutor` — runs a plan: concurrency (`Semaphore`), retry (`RetryPolicy`), pause
  (`PauseController`), per-file error mapping, `sync_state` bookkeeping.
- `RemoteTreeScanner` — recursively walks a `RemoteFileClient` into a `FileTreeSnapshot`.

**`:protocol:sftp` / `:protocol:ftp`**
- `SftpRemoteFileClient` (SSHJ) — password/private-key auth, host-key verification (strict/pinned/
  accept-any), upload-to-temp-then-`rename(..., OVERWRITE)`, streamed transfer.
- `FtpRemoteFileClient` (Apache Commons Net) — passive/active mode, upload-to-temp-then-rename,
  streamed transfer via a progress-reporting `FilterInputStream`/`FilterOutputStream`.

**`:app`**
- `data/repository/*` — bridges Room ⇄ domain models; `SyncStateRepositoryImpl` and
  `KnownHostsRepositoryImpl` are the concrete implementations of `:core:sync`'s and
  `:protocol:sftp`'s repository interfaces.
- `data/local/SafLocalFileSystem` — `LocalFileSystem` over `DocumentFile`/SAF.
- `sync/SyncRunner` — the single place both `SyncWorker` (scheduled) and `SyncForegroundService`
  (manual/active) go through to run a profile, so live progress/history/notifications behave
  identically regardless of what triggered the run.
- `sync/SyncWorker`, `sync/SyncScheduler` — WorkManager scheduling.
- `sync/SyncForegroundService`, `sync/NotificationHelper` — active-sync notification with
  pause/resume/cancel actions.

---

## 5. UI screens (Jetpack Compose, Material 3, Navigation-Compose)

| Screen | Route | Notes |
|---|---|---|
| Dashboard | `dashboard` | Profile cards: direction icon, remote name, last sync, enable toggle, live progress bar. |
| Add/Edit Connection | `connections/edit` | Protocol picker, FTP insecure-connection warning, test connection. |
| Saved Connections | `connections` | Test/duplicate/delete; reachable from Settings → "Manage saved connections". |
| Remote Folder Browser | `connections/{id}/browse` | Live remote directory listing, breadcrumb, create folder, returns the picked path via `SavedStateHandle` (the standard Navigation-Compose result pattern). |
| Create/Edit Sync Profile | `profiles/edit` | SAF folder picker (`OpenDocumentTree` + persisted URI permission), connection + remote path pick, mode/conflict/delete/schedule pickers, mirror-mode warning dialog. |
| Profile Details | `profiles/{id}` | Settings summary, running totals, "Sync now" (starts the foreground service), links to Live Sync and History. |
| Live Sync | `profiles/{id}/live` | Current file, per-file and overall progress, bytes up/down, error count, pause/resume/cancel. |
| Sync History | `profiles/{id}/history` | Tabs: runs (counts + bytes transferred) and errors (code/message/suggested fix). |
| Settings | `settings` | Theme, app lock toggle, clear-all-credentials, default conflict/network rule, max parallel transfers, retry count, timeout. |

Screens not yet built as separate routes (dedicated Error Details drill-down, import/export
configuration) are covered functionally — errors are visible in the History screen's Errors tab —
but don't have their own route; see roadmap.

---

## 6. Sync engine logic

`SyncPlanner.plan(mode, conflictRule, deleteRule, local, remote, priorState)` is a **pure function**
of three snapshots (see `SyncPlannerTest` for the full behavioral spec via 16 test cases):

- **One-way / mirror** (`planOneWay`): for each relative path, compare the *source* side against
  `priorState` to decide upload/download; a file present at the destination but never known to the
  source is either left alone (plain one-way) or deleted (mirror — the destination must equal the
  source's tree exactly).
- **Two-way** (`planTwoWay`): the critical piece is that a **missing file is only treated as a
  deletion to propagate if `priorState` shows that side used to have it.** If neither snapshot nor
  prior state agrees, a file appearing on both sides with no history is either identical (skipped)
  or a same-name coincidence (flagged as `BOTH_CREATED_INDEPENDENTLY` conflict) — never silently
  overwritten.
- **Conflict detection**: a path present on both sides *with* prior state is a conflict only when
  **both** sides changed since that prior state (`BOTH_MODIFIED`); if only one side changed, that's
  a normal propagate, not a conflict.
- **Mass-deletion guard**: after planning, if the number of delete operations exceeds 20 **or**
  exceeds 20% of the tracked file count, every delete operation is flagged
  `requiresConfirmation = true` and a `PlanWarning.MassDeletion` is attached to the plan;
  `SyncExecutor` refuses to execute those deletes unless the caller passes
  `confirmedMassDeletion = true` (wired to the Live Sync screen showing a confirmation prompt).

`ConflictResolver.resolve(conflictInfo, rule, dateStamp)` turns a conflict into one of: keep local,
keep remote, keep newest (by mtime), keep both (renamed
`name.conflict-local-<date>.ext` / `name.conflict-remote-<date>.ext`, **both originals left
untouched, both copies pushed to the *other* side** — see `SyncExecutorTest`'s keep-both test),
skip, or "requires user input" (ask-every-time, or compare-checksum when checksums genuinely
differ).

`SyncExecutor` is the only IO-touching class in the engine: bounded concurrency via
`kotlinx.coroutines.sync.Semaphore`, retry with exponential backoff (`RetryPolicy`), cooperative
pause via a `MutableStateFlow`-backed `PauseController` (correct under multiple concurrent transfer
workers, unlike a single-slot `Channel`), and cancellation via ordinary structured-concurrency
`Job` cancellation of the calling coroutine — no custom cancel flag needed.

---

## 7. SFTP / FTP implementation

Both clients implement the same `RemoteFileClient` interface, verified against the **real** SSHJ
0.38.0 and Apache Commons Net 3.11.1 APIs (this repo's sandbox has no Android SDK, so these two
protocol modules — being plain JVM — are the parts that were actually compiled and unit-tested
against their real dependency jars during development, not just written from memory).

- **SFTP** (`SftpRemoteFileClient`): password or private-key (`OpenSSHKeyFile.init(Reader, …)` from
  an in-memory PEM string) auth; host-key verification has three modes
  (`StrictKnownHosts` / `PinnedFingerprint` / `AcceptAny`) implemented as a custom
  `HostKeyVerifier` that records *why* verification failed (unknown vs. mismatch) so the caller gets
  `AppError.HostKeyUnknown` / `AppError.HostKeyMismatch` instead of a generic transport exception;
  uploads go to `<name>.uploading`, are size-verified, then `rename(..., RenameFlags.OVERWRITE)`
  into place (falling back to remove-then-rename if the server doesn't support the extension).
- **FTP** (`FtpRemoteFileClient`): passive/active mode, `BINARY_FILE_TYPE`, the same
  temp-name-then-rename dance (Commons Net's `rename` has no atomic overwrite, so the existing
  destination is removed immediately before the rename — a brief window is unavoidable and is
  documented in the class KDoc), progress reported via a small `FilterInputStream`/
  `FilterOutputStream` wrapper rather than Commons Net's global `CopyStreamListener` (keeps progress
  scoped to one transfer instead of being process-wide state).
- **FTP security warning**: `ConnectionConfig.isInsecure` is true whenever protocol is plain FTP
  without explicit TLS; `AddConnectionScreen` shows a warning card the moment FTP is selected.

---

## 8. Background sync implementation

- **Scheduled**: `SyncScheduler` turns a profile's `ScheduleRule` into a
  `PeriodicWorkRequest` (`SyncWorker`, a `@HiltWorker` `CoroutineWorker`), with `Constraints` for
  network type and charging. WorkManager's periodic minimum is 15 minutes — profiles asking for
  faster manual iteration should use "Sync now" instead.
- **Manual / active**: `SyncForegroundService` (foreground service type `dataSync`) starts a
  persistent notification immediately (`startForeground` is called synchronously in
  `onStartCommand`, before any suspending work, per Android's requirement), then runs the profile
  through the same `SyncRunner` as the worker, updating the notification from `SyncRunner`'s
  `liveProgress` `StateFlow` with Pause/Resume/Cancel notification actions.
- **Boot**: `BootCompletedReceiver` defensively re-enqueues all enabled profiles' periodic work
  (WorkManager persists its own schedule across reboots on stock Android already; this is a
  belt-and-suspenders re-sync for OEMs whose battery managers are known to clear it more
  aggressively).
- **True real-time file-system watching is not possible for an arbitrary SAF tree on modern
  Android** — there is no `FileObserver`/`inotify`-equivalent API for a `content://` document tree
  (only for a raw filesystem path the app owns outright, which a user-picked SAF folder usually
  isn't). SyncBridge's near-real-time story for the MVP is: manual sync, scheduled sync down to
  WorkManager's 15-minute floor, and "Sync now" from the dashboard. A genuinely faster
  near-real-time mode is on the roadmap (see below) via a foreground service that polls the tree on
  a short timer while the app is in the foreground — still polling, not push, because push isn't
  available for this storage model.

---

## 9. Error handling implementation

Every failure funnels through `AppError` (`:core:common`), one sealed class per spec section 17
scenario (`AuthFailed`, `HostUnreachable`, `DnsFailure`, `ConnectionTimeout`, `PermissionDenied`,
`RemoteFolderNotFound`, `LocalPermissionRevoked`, `StorageFull`, `NetworkDisconnected`, `FileLocked`,
`PartialTransfer`, `ServerClosedConnection`, `FtpPassiveModeFailed`, `HostKeyMismatch`/`Unknown`,
`DuplicateFile`, `ConflictDetected`, `BackgroundRestricted`, `BatteryOptimizationBlocking`,
`TooManyFiles`, `FileTooLarge`, `InvalidFilename`, `UnsupportedSymlink`, `ServerQuotaExceeded`,
`MassDeletionBlocked`, `PathTraversalRejected`, `Unknown`), each carrying a technical `code`, a
`userMessage`, a `suggestedFix`, and a `retryable` flag. `SyncExecutor` maps any non-`AppError`
exception to `AppError.Unknown` before deciding whether to retry, so the retry policy and the
history/error UI always see the same contract. `SyncHistoryRepository.recordError` persists exactly
those four fields into `sync_errors`, which is what the History screen's Errors tab and (on the
roadmap) a dedicated Error Details screen render.

---

## 10. Security implementation

- **Credentials**: `KeystoreCredentialCipher` (`:core:security`) — AES-256-GCM key generated inside
  Android Keystore (`setUserAuthenticationRequired(false)` deliberately, so scheduled background
  sync can decrypt while the device is locked; app-level biometric lock is a separate UI gate, not
  tied to this key). Passwords/private keys/passphrases are ciphertext (`Base64(iv + ciphertext)`)
  in the `connections` table — never plaintext, never logged.
- **App lock**: `BiometricAppLockGate` wraps `BiometricPrompt` with `BIOMETRIC_STRONG or
  DEVICE_CREDENTIAL`, bridged to a suspend function via `suspendCancellableCoroutine`.
- **"Clear all saved credentials"** (Settings): calls `CredentialCipher.deleteKey()`, which deletes
  the Keystore key entry outright — every existing ciphertext blob becomes permanently
  undecryptable in one call, no need to touch each row.
- **Host key verification**: see section 7 above — strict-by-default, pinning support, and an
  explicit (loud, KDoc'd) `AcceptAny` escape hatch for trusted test environments only.
- **Path safety**: `PathValidator.resolveSafe` rejects `..`-escaping and null bytes before any local
  or remote path is touched; `PathValidator.hasInvalidFilenameCharacters` is checked before every
  transfer, mapping to `AppError.InvalidFilename` rather than letting a malformed name reach the
  filesystem/protocol layer.

---

## 11. Build instructions

**Requirements**: JDK 17+, Android Studio (Ladybird/Koala or newer) with an Android SDK
(compileSdk/targetSdk 35) for the `:app`, `:core:database`, and `:core:security` modules. The four
pure-JVM modules (`:core:common`, `:core:sync`, `:protocol:sftp`, `:protocol:ftp`) build with plain
Gradle and **no Android SDK at all** — this is exactly how they were verified while building this
repo, in a sandbox with no Android SDK installed:

```bash
./gradlew :core:common:test :core:sync:test :protocol:sftp:compileKotlin :protocol:ftp:compileKotlin
```

To build the full app (requires Android SDK / `ANDROID_HOME` set, e.g. via Android Studio):

```bash
./gradlew :app:assembleDebug
```

Note: this repo does not include a committed Gradle wrapper jar; generate one before building
outside Android Studio with `gradle wrapper --gradle-version 8.14.3` (Android Studio will also
offer to do this automatically on first open).

Library versions are centralized in `gradle/libs.versions.toml`. A few — `androidx.security-crypto`
and `androidx.biometric` — are pinned to alpha releases as of writing (the security-crypto artifact
in particular has been in alpha for a long time); check for a stable release before shipping and
bump there first.

---

## 12. Testing instructions

**Unit tests (run today, no emulator, no Android SDK)**:

```bash
./gradlew :core:common:test    # FileFilterMatcherTest — 8 tests
./gradlew :core:sync:test      # SyncPlannerTest (16), ConflictResolverTest (9), SyncExecutorTest (6)
```

`SyncExecutorTest` uses in-memory fakes (`FakeLocalFileSystem`, `FakeRemoteFileClient`,
`FakeSyncStateRepository`) to exercise the *whole* execute-a-plan pipeline — upload, retry-then-
succeed, mass-deletion pause vs. confirmed-proceed, dry-run, and the keep-both conflict path —
without any real network or SAF dependency. This is also where a real bug (the keep-both resolution
writing to the wrong side's path) was caught and fixed during development; see the `destinationPath`
vs. `relativePath` distinction in `SyncOperation`.

**What isn't unit-testable without Android**: `:core:database` (Room codegen needs the Android
Gradle Plugin), `:core:security` (Android Keystore only exists on-device), and all of `:app`
(Compose, WorkManager, foreground service). These need either Robolectric or an emulator/device —
not yet set up in this repo (see roadmap). Manual verification path once built:
1. Add a connection, confirm the FTP insecure warning appears only for plain FTP.
2. Point a sync profile at a real (or Docker-hosted) SFTP/FTP server, run "Sync now", watch the
   Live Sync screen's progress and the persistent notification's Pause/Resume/Cancel actions.
3. Force a conflict (edit the same file on both sides between syncs) and confirm the History
   screen's Errors tab / the conflict resolution behaves per the chosen `ConflictRule`.

---

## 13. Roadmap

**Already built (this repo)**: connection CRUD + test/duplicate/delete, SFTP + FTP clients, local
SAF folder picking, remote folder browsing, sync profile CRUD, one-way/two-way/mirror modes,
conflict rules (all seven, including keep-both renaming), delete rules, mass-deletion guard,
dry-run, pause/resume/cancel, retry with backoff, WorkManager scheduling, foreground service with
notification actions, Keystore-backed credential encryption, biometric app-lock primitive, sync
history + error log, settings (theme/app-lock/defaults/parallelism/retry/timeout).

**Near-term** (natural extensions of what's already scaffolded):
- Wire the `recycle_bin` table to an actual restore UI (schema exists; `SafLocalFileSystem.delete`
  currently just renames-in-place under a recycle marker without a DB record or restore screen).
- Wire `transfer_queue` so a killed process resumes exactly where it left off instead of re-planning
  from `sync_state` on the next run (functionally similar today, but not identical after a crash
  mid-transfer of a large file).
- File filters UI (the `FileFilterConfig` domain model and matcher are fully implemented and
  tested; `CreateSyncProfileScreen` doesn't yet expose custom include/exclude editing — every
  profile uses the sensible defaults).
- Dedicated Error Details screen (retry/ignore/open-logs actions) instead of the History screen's
  Errors tab.
- Import/export sync profiles and connections (without secrets by default; optional encrypted
  export with secrets, per spec section 25).

**Post-MVP** (per the original spec's phased scope): FTPS as its own verified client (the domain
model already has `ProtocolType.FTPS` and `ConnectionConfig.useExplicitTls`, routed to the FTP
client today — implicit/explicit TLS isn't implemented yet), WebDAV/SMB/cloud-provider
`RemoteFileClient` implementations (the interface is designed for exactly this), a genuinely
near-real-time local-change poller (short-interval foreground polling while the app is open — true
push-based watching isn't available for SAF trees, see section 8), checksum-based comparison as a
first-class option (the data model already carries an optional `checksum` field end to end),
structured log export, and Robolectric/instrumented test coverage for the Android-only modules.
