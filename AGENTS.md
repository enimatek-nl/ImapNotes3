# AGENTS.md — ImapNotes3

Guide for AI agents working in this repository. Read this before making changes.

## What this project is

**ImapNotes3** is a FOSS Android notes/checklist app (GPLv3) that synchronizes notes
through an **IMAP mailbox** using the **Apple Notes wire format**: each note is a
standard HTML e-mail (`X-Uniform-Type-Identifier: com.apple.mail-note`,
`X-Universally-Unique-Identifier: <uuid>`) stored in an IMAP folder named `Notes`.
This makes notes interoperable with Apple devices and the "iOS IMAP Notes" Thunderbird add-on.

Fork lineage: `imapnote` → `nbenm/ImapNote2` → `kwhitefoot/ImapNote2` → `niendo1/ImapNotes3` (current, https://github.com/niendo1/ImapNotes3). Published on F-Droid as `de.niendo.ImapNotes3`. Translations are managed on **hosted.weblate.org** — translation PRs come from Weblate; do not hand-edit `values-*/strings.xml`.

## Tech stack

- **Language: Java only** (~8,200 LOC, 31 files). No Kotlin. `compileOptions` = Java 17.
- Single Gradle module `:ImapNotes3` (`settings.gradle`), old Groovy DSL (`build.gradle`).
- **Gradle wrapper 9.7**, **AGP 9.3.1**, `foojay-resolver-convention` plugin (auto-downloads JDK toolchain).
- `compileSdk 37`, `targetSdk 35`, `minSdk 25`, namespace/applicationId `de.niendo.ImapNotes3`.
- **`versionCode` / `versionName` live in `AndroidManifest.xml`** (root `<manifest>` attrs), not in `build.gradle`.
- UI stack: `androidx.appcompat` + Material 1.14 (`ImapNotesTheme`, light/dark via `values-night`), `swiperefreshlayout`, `androidx.preference`.
- Key libraries (`ImapNotes3/build.gradle`):
  - `com.sun.mail:android-mail` / `android-activation` 1.6.8 — JavaMail (IMAP client + MIME parsing)
  - `org.jsoup:jsoup` — HTML manipulation of note bodies
  - `com.github.niendo1:richeditor-android:3.0.4` — **the maintainer's own fork** of RichEditor-Android, resolved from JitPack; API diverges from upstream (tables, checkboxes, colors, JS click/HTML callbacks)
  - `io.github.eltos:simpledialogfragments:3.10` — all dialogs
  - `commons-io`, `com.github.woheller69:FreeDroidWarn` (non-free-SDK warning dialog)
- Repos: `google()`, `mavenCentral()`, **`jitpack.io`** (required for the RichEditor fork + FreeDroidWarn).

## Repository layout

```
build.gradle / settings.gradle / gradle.properties   # root build (AGP version, repos, gradle flags)
ImapNotes3/                                          # the only module
  build.gradle                                       # android config + deps
  src/main/AndroidManifest.xml                       # versionCode/Name, activities, services, provider
  src/main/java/de/niendo/ImapNotes3/                # all code, packages below
  src/main/res/                                      # layouts, menus, values-* (15+ locales), xml/ configs
  src/main/assets/custom.css                         # CSS injected into the RichEditor WebView
fastlane/metadata/android/                           # F-Droid store listings only (no Fastfile)
art/                                                 # artwork sources
```

There are **no tests, no CI workflows, no lint baseline** (it's commented out in `build.gradle`).

## Package map (`de.niendo.ImapNotes3`)

| Package / class | Role |
|---|---|
| `ImapNotes3.java` | `Application` subclass. Static app context, `GetRootDir()`/`GetAccountDir()`, Snackbar helpers (`ShowMessage`, `ShowAction`), color-preference helpers, global `UncaughtExceptionHandler` (stores stacktrace in prefs key `uncaughtException`). Static `MainLock` guards UI/sync races. |
| `ListActivity` (990 LOC) | Main screen: account spinner, note list (`NotesListAdapter`), search box, hashtag filter, swipe-to-refresh, share-intent entry points (`SEND` text/image/rfc822), sync trigger, delete with undo Snackbar. Implements `UpdateThread.FinishListener`, `BackupRestore.INotesRestore`. `searchHTMLTags()` scans `.eml` files for `#tags`. |
| `NoteDetailActivity` (1,085 LOC) | Editor screen built on `jp.wasabeef.richeditor.RichEditor` (WebView): formatting, colors, background color, hashtag chips, save/share/delete, prev/next note navigation. |
| `AccountConfigurationActivity` | Create/edit IMAP account (server, port, security, folder, sync interval); test connection via `LoginThread`; writes into `AccountManager`. |
| `SettingsActivity` + `res/xml/root_preferences.xml` | Appearance (editor colors per light/dark mode), backup/restore entries, debug report, about. Main prefs file: `MAIN_PREFERENCE_NAME = "<applicationId>_preferences"`. |
| `NotesListAdapter` | Filterable list adapter; notes are `OneNote` maps. |
| `Data/` | `NotesDb` (SQLiteOpenHelper singleton, DB `NotesDb` v4: `notesTable`, `tagsTable`, join view `tagsView`); `OneNote` (note metadata as `HashMap<String,String>`, incl. `save_state`: `""`/SAVING/SYNCING/FAILED); `ImapNotesAccount` (wraps `AccountManager` account + userdata); `ConfigurationFieldNames` (userdata keys: username, imapfolder, server, portnum, syncinterval, security, copyImapFolder*); `Security` enum (None/SSL_TLS/STARTTLS ± accept-all-certs, default ports 143/993, OAUTH2 commented out — **not yet supported**); `SyncInterval` enum (off/15min/1h/6h/1d). |
| `Sync/` | Android **AccountAuthenticator + SyncAdapter** framework glue. Account type = `de.niendo.ImapNotes3`, content authority = `de.niendo.ImapNotes3.provider` (needs `StubProvider`, a no-op ContentProvider — required by the sync framework, its purpose is noted as TODO in the manifest). `SyncAdapter.onPerformSync` = the real background sync: `ConnectToRemote` (IMAP), `handleNewNotes` (upload local), `handleDeletedNotes`, `SyncUtils.handleRemoteNotes` (download). `SyncService` broadcasts `SYNC_FINISHED`. `SyncUtils` also handles local `.eml` file I/O and UIDValidity tracking in per-account SharedPreferences. |
| `Miscs/` | `HtmlNote` — **the IMAP wire-format codec**: `GetMessageFromNote` (note → MimeMessage with Apple headers + body background-color) and `GetNoteFromMessage` (→ text + color). `UpdateThread` (AsyncTask: user-initiated insert/update/move-to-deleted on the server), `SyncThread`, `LoginThread` (connection test), `BackupRestore` + `ZipUtils` (zip archive backup/restore in `Documents/`), `SmtpServerNameFinder`, `Utilities` (package/app names, `EMAIL_FILE_EXT="eml"`, `HASHTAG_PATTERN` regex, color name↔id), `EditorMenuAdapter`, `AboutDialogFragment`, `NDSpinner`, `Result`/`ImapNotesResult`. |

## Data flow (how a note lives)

1. **On disk**: per-account dir at `filesDir/<sanitized account name>/` with mail files `<uid>.eml`, plus subdirs `new/` (pending upload) and `deleted/` (pending deletion) — created by `SyncUtils.CreateLocalDirectories`.
2. **In SQLite** (`NotesDb`, singleton via `getInstance`): metadata row (title, date, number=UID, bgcolor, saveState, accountname) + tag rows. DB is a cache; `.eml` files are the source of truth.
3. **On the server**: HTML e-mail in the account's IMAP folder (default `Notes`), Apple-compatible headers. Local UID ↔ server UID mapping uses `X-Universally-Unique-Identifier` and IMAP UIDs; `GetTempNumber`/`RemoveTempNumber` in `NotesDb` handle pre-sync negative temp numbers.
4. Sync is driven by the Android sync framework (`SyncUtils` requests/schedules, respects `SyncInterval`), plus `UpdateThread` for immediate user actions. UI observes changes via a ContentObserver on the sync authority and the `SYNC_FINISHED` broadcast.

## Build & run

```sh
./gradlew assembleDebug      # APK: ImapNotes3/build/outputs/apk/debug/ImapNotes3-<versionName>-debug.apk
./gradlew assembleRelease
./gradlew lint
```

Requirements: Android SDK with `compileSdk 37` + `local.properties` (sdk.dir) or `ANDROID_HOME`; JDK 17+ (toolchain auto-provisioned via foojay).

**Gotchas:**
- `build.gradle` defines a `release` signingConfig pointing to `../keystore.jks` **which is not in the repo**, and *both* `debug` and `release` build types use it. A fresh clone fails at signing. Workaround: generate a debug keystore at the repo root (`keytool -genkey -keystore keystore.jks -alias ImapNotes -keypass ImapNotes -storepass ImapNotes -dname CN=dev`) or point the build at your own config. Plaintext keystore passwords are committed in `build.gradle` (legacy, F-Droid-issued-style setup) — don't "fix" silently.
- APK output names are customized: `${project.name}-${versionName}-${buildType}.apk`.
- `minifyEnabled`/`shrinkResources` are **false**; `proguard-rules.pro` mainly silences JavaMail warnings and keeps `R.color` fields (colors are looked up reflectively by name in `Utilities.getColorIdByName` — renaming color resources breaks this).
- Gradle config cache and `nonTransitiveRClass=false` are set in `gradle.properties`; keep flags when touching that file.

## Code conventions (match these)

- Methods/classes use **PascalCase** (`GetMessageFromNote`, `RefreshList`) — legacy fork style, keep it consistent.
- Every file starts with the GPLv3 header + author copyright lines; preserve them.
- Logging: `private static final String TAG = "IN_<ClassName>"` and `Log.d/v/w/e` everywhere; keep this pattern — log volume is a deliberate debug feature, and debug reports (`send_debug_report` setting) export logs.
- Null-safety via `androidx.annotation` (`@NonNull`/`@Nullable`); `@SuppressLint` used where needed.
- Async work still uses **`AsyncTask`** (`UpdateThread`, `SyncThread`, `LoginThread`) — dated but pervasive; only modernize where the task demands it.
- DB access is `synchronized` methods on the `NotesDb` singleton; **SQL is string-concatenated** (known weakness; prefer parameter binding in new code).
- All user-facing strings go through `res/values/strings.xml` (Weblate keys — changing string *keys* forces re-translation work).
- Manifest contains explanatory TODO comments (StubProvider, unused meta-data) — treat as known-unknowns, don't delete blindly.

## Git / release practices

- Main branch `master`; remote `origin` = github.com/niendo1/ImapNotes3. Feature branches exist (`hashtag`, `Attachment`, `v1_1_richeditor_update`).
- Release flow: bump `versionCode` + `versionName` in **AndroidManifest.xml**, tag, GitHub release APK + F-Droid (fastlane metadata). Translations merge in as "Translated using Weblate (...)" commits.
- Commit style: short lowercase messages ("gradle and lib updates", "fix for #147").
- Dependency updates are a routine commit type; deps are pinned to recent versions — check upstream (JitPack for the RichEditor fork) when upgrading.

## Things to verify before claiming a change works

There is no automated test suite, so validation is: `./gradlew assembleDebug` compiles, then manual/device testing (or `adb`). UI changes touch `main.xml` (list), `note_detail.xml` (editor), menus `list.xml`/`detail.xml`. Editor behavior changes require checking the custom `assets/custom.css` and the forked RichEditor JS interface.
