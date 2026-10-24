# AGENTS.md — ImapNotes3

Guide for AI agents working in this repository. Read this before making changes.

## What this project is

**ImapNotes3** is a FOSS Android notes/checklist app (GPLv3) that synchronizes notes
through an **IMAP mailbox** using the **Apple Notes wire format**: each note is a
standard HTML e-mail (`X-Uniform-Type-Identifier: com.apple.mail-note`,
`X-Universally-Unique-Identifier: <uuid>`) stored in an IMAP folder named `Notes`.
This makes notes interoperable with Apple devices and the "iOS IMAP Notes" Thunderbird add-on.

Fork lineage: `imapnote` → `nbenm/ImapNote2` → `kwhitefoot/ImapNote2` → `niendo1/ImapNotes3` (current, https://github.com/niendo1/ImapNotes3). Published on F-Droid as `de.niendo.ImapNotes3`. Translations are managed on **hosted.weblate.org** — translation PRs come from Weblate; do not hand-edit `values-*/strings.xml`.

Since the `v1.4-07` line the app uses an **account-first navigation** ("iOS Notes style"):
the app starts on the account overview, every notes screen belongs to exactly one account, and
there is **no "all accounts" screen any more**. See [Screen & navigation architecture](#screen--navigation-architecture).

## Tech stack

- **Language: Java only** (~9,500 LOC, 35 files). No Kotlin. `compileOptions` = Java 17.
- Single Gradle module `:ImapNotes3` (`settings.gradle`), old Groovy DSL (`build.gradle`).
- **Gradle wrapper 9.7**, **AGP 9.3.1**, `foojay-resolver-convention` plugin (auto-downloads JDK toolchain).
- `compileSdk 37`, `targetSdk 35`, `minSdk 25`, namespace/applicationId `de.niendo.ImapNotes3`.
- **`versionCode` / `versionName` live in `AndroidManifest.xml`** (root `<manifest>` attrs), not in `build.gradle` (currently `10407` / `v1.4-07`).
- UI stack: `androidx.appcompat` 1.8.0 + Material 1.14.0 (`ImapNotesTheme`, light/dark via `values-night`), `swiperefreshlayout` 1.2.0, `androidx.preference` 1.2.1.
- Key libraries (`ImapNotes3/build.gradle`):
    - `com.sun.mail:android-mail` / `android-activation` 1.6.8 — JavaMail (IMAP client + MIME parsing)
    - `org.jsoup:jsoup` — HTML manipulation of note bodies
    - `com.github.niendo1:richeditor-android:3.0.4` — **the maintainer's own fork** of RichEditor-Android, resolved from JitPack; API diverges from upstream (tables, checkboxes, colors, JS click/HTML callbacks)
    - `io.github.eltos:simpledialogfragments:3.10` — all dialogs
    - `commons-io`, `com.github.woheller69:FreeDroidWarn:V1.+` (non-free-SDK warning dialog; note the **dynamic version**)
    - `org.jetbrains:annotations` — mostly incidental, `androidx.annotation` is what the code uses
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

There are **no tests, no CI workflows, no lint baseline** (the `lint { baseline = ... }` block is commented out in `build.gradle`).

## Screen & navigation architecture

The activity graph (also enforced by `android:parentActivityName` in the manifest):

```
AccountListActivity  (LAUNCHER + all share intent filters, singleTop, exported)
  ├── ListActivity                 (notes of ONE account, exported="false", singleTop)
  │     └── NoteDetailActivity     (RichEditor editor)
  ├── AccountConfigurationActivity (create AND edit, one activity, two pages)
  └── SettingsActivity             (Up = plain finish(), never re-launches a list)
```

- **`AccountListActivity`** owns the account set: lists accounts as swipe-reveal rows
  (tap = open that account's notes, revealed buttons = edit / delete), starts account setup,
  restores the **last opened account** (`PREF_LAST_ACCOUNT_NAME = "LastAccountName"` in
  `SettingsActivity.MAIN_PREFERENCE_NAME`), and routes incoming shares to one account.
- **`ListActivity`** is always scoped: the account name arrives as the intent extra
  `ListActivity.EDIT_ITEM_ACCOUNTNAME` and is kept in `scopedAccountName` for the screen's
  lifetime. It is **not exported**, so `adb shell am start -n de.niendo.ImapNotes3/.ListActivity`
  is refused — drive it through `AccountListActivity` (e.g. by firing a share intent).
- **Share intents** (`SEND` text/image/rfc822, `SEND_MULTIPLE`) are registered on
  `AccountListActivity`: `IsShareIntent()` + `routeShareIntent()` pick the account (ask when more
  than one, `select_one_account`; remember as `pendingShare` when there is none) and
  `sendShareToAccount()` forwards a copy of the intent — with `EDIT_ITEM_ACCOUNTNAME` and
  `FLAG_GRANT_READ_URI_PERMISSION` — to the scoped list, where the existing `Check_Action_Send()`
  handling runs unchanged.
- **Two traps that cost real debugging time** (both are deliberate in the code):
    - `restoreLastAccount()` and `routeShareIntent()` are called from `onCreate` **only when
      `savedInstanceState == null`**. Doing them in `onStart`/`onNewIntent` bounces forever, because
      opening the notes list uses `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, which clears
      back to the overview, which would then restore again.
    - `onNewIntent` must keep the current scope when the incoming intent carries **no** account name
      (a bare `CLEAR_TOP` relaunch), otherwise the scoped list silently turns into a scope-less screen.
- **Account deletion** goes through `AccountManager.removeAccount` (UI) →
  `ImapNotesAuthenticatorService.Authenticator.getAccountRemovalAllowed` → `SyncUtils.RemoveAccount`,
  which only deletes that account's directory (this was once a whole-root-dir bug).
  The account-setup screen has **no Delete button**; deleting lives on the account row.
- **Account setup = create and edit share one flow**: one form page with a single "Next"
  (`@string/next`) → `LoginThread` connects first and only then writes → a result page
  (`@+id/accountResult`: spinner / icon / heading / message / "Finished" / Help). A failed check
  shows the reason with Help + "I understand", and Back returns to the form with the entries intact
  (needs a back callback: `enableOnBackInvokedCallback="true"` means `onBackPressed()` is not called
  on Android 16). Anti-spoofing rule kept: changed server + empty password →
  `R.string.imap_server_changed_new_password`, stay on the form.

## Package map (`de.niendo.ImapNotes3`)

| Package / class                                     | Role                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
| --------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `ImapNotes3.java`                                   | `Application` subclass. Static app context, `GetRootDir()`/`GetAccountDir()`, Snackbar helpers (`ShowMessage`, `ShowAction`), color-preference helpers (`loadPreferenceColor`/`savePreferenceColor`), global `UncaughtExceptionHandler` (stores stacktrace in prefs key `uncaughtException`). Static `MainLock` guards UI/sync races.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `AccountListActivity` (411 LOC)                     | Launcher + share entry point. Account list (`SwipeRevealListView`, `account_element.xml`), empty state, add/edit/delete account, last-account restore, share routing (`routeShareIntent` / `sendShareToAccount`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| `ListActivity` (974 LOC)                            | **Account-scoped** notes list (`NotesListAdapter`), search `SearchView`, hashtag filter, swipe-to-refresh, sort (date/title/color), delete with confirmation dialog, sync trigger, share-intent handling (`Check_Action_Send`), undo-capable Snackbar helpers. Implements `UpdateThread.FinishListener`, `BackupRestore.INotesRestore`, `NotesListAdapter.RowActionListener`. `searchHTMLTags()` / `searchHTMLTagsInHtml()` extract `#tags`. No account spinner, no "all accounts" mode.                                                                                                                                                                                                                                                                                                                                                                                                                                |
| `NoteDetailActivity` (1,085 LOC)                    | Editor screen built on `jp.wasabeef.richeditor.RichEditor` (WebView): formatting, colors, background color, hashtag chips, save/share/delete, prev/next note navigation, move-to-other-account dialog.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `AccountConfigurationActivity` (622 LOC)            | Create **and** edit an IMAP account in a two-page flow (`Actions.CREATE_ACCOUNT` / `Actions.EDIT_ACCOUNT`, `account_setup.xml` = `scrollView` + `accountResult`); connection check via `LoginThread`; writes into `AccountManager`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `SettingsActivity` + `res/xml/root_preferences.xml` | Appearance (editor colors per light/dark mode), backup/restore entries, debug report, about. Main prefs file: `MAIN_PREFERENCE_NAME = "<applicationId>_preferences"`.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `NotesListAdapter` (628 LOC)                        | Filterable list adapter; notes are `OneNote` maps. Renders title / short date / snippet, wires the swipe-revealed delete button (`RowActionListener`), and caches note snippets per file + mtime (`SNIPPET_CACHE_SIZE`, `SNIPPET_MAX_LENGTH`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `Data/`                                             | `NotesDb` (SQLiteOpenHelper singleton, DB `NotesDb` v4: `notesTable`, `tagsTable`, join view `tagsView`); `OneNote` (note metadata as `HashMap<String,String>`, incl. `save_state`: `""`/SAVING/SYNCING/FAILED, and the non-persistent `SNIPPET` key); `ImapNotesAccount` (wraps `AccountManager` account + userdata); `ConfigurationFieldNames` (userdata keys: username, imapfolder, server, portnum, syncinterval, security, copyImapFolder*); `Security` enum (None/SSL_TLS/STARTTLS ± accept-all-certs, default ports 143/993, OAUTH2 commented out — **not yet supported**); `SyncInterval` enum (off/15min/1h/6h/1d).                                                                                                                                                                                                                                                                                            |
| `Sync/`                                             | Android **AccountAuthenticator + SyncAdapter** framework glue. Account type = `de.niendo.ImapNotes3`, content authority = `de.niendo.ImapNotes3.provider` (needs `StubProvider`, a no-op ContentProvider — required by the sync framework, its purpose is noted as TODO in the manifest). `SyncAdapter.onPerformSync` = the real background sync: `ConnectToRemote` (IMAP), `handleNewNotes` (upload local), `handleDeletedNotes`, `SyncUtils.handleRemoteNotes` (download), plus `RememberError` collecting the first user-visible sync error and per-step duration logs. `SyncService` broadcasts `SYNC_FINISHED`. `SyncUtils` also handles local `.eml` file I/O (`GetNoteFile`, `CreateLocalDirectories`, `RemoveAccount`), remote folder opening (`OpenRemoteIMAPNotesFolder(mode)` + `RemoteFolderIsReadOnly()`), UIDValidity tracking in per-account SharedPreferences, and batched fetching via `FetchProfile`. |
| `Miscs/`                                            | `HtmlNote` — **the IMAP wire-format codec**: `GetMessageFromNote` (note → MimeMessage with Apple headers + body background-color) and `GetNoteFromMessage` (→ text + color). `UpdateThread` (AsyncTask: user-initiated insert/update/move-to-deleted on the server), `SyncThread`, `LoginThread` (connection check + account write), `BackupRestore` + `ZipUtils` (zip archive backup/restore in `Documents/`), `SmtpServerNameFinder`, `Utilities` (package/app names, `EMAIL_FILE_EXT="eml"`, `HASHTAG_PATTERN`, color name↔id, `GetShortDateString`), `EditorMenuAdapter`, `AboutDialogFragment`, `NDSpinner`, `Result`/`ImapNotesResult`, **`SwipeRevealRow` + `SwipeRevealListView`** (the shared swipe-to-reveal row mechanism used by both lists).                                                                                                                                                               |

## Swipe-reveal rows (`Miscs/SwipeRevealRow` + `SwipeRevealListView`)

Both the account list and the notes list use them, and the implementation has non-obvious
constraints that are easy to break:

- The **gesture lives in the `ListView` subclass**, not in the row: a row never receives move
  events unless one of its children is clickable. The list records DOWN, lets vertical scrolling
  pass to list + `SwipeRefreshLayout`, intercepts once the drag exceeds the touch slop
  horizontally, drags `@+id/rowContent` with `translationX`, and snaps open/closed at half the panel.
- In the row layout, `@+id/rowActions` is declared **before** `@+id/rowContent` (the panel sits
  underneath and is revealed as the content slides).
- `SwipeRevealRow.onFinishInflate()` sets `setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS)`.
  Without it `AbsListView` refuses to perform item clicks (an `ImageButton` constructor makes rows
  focusable), and **tapping any row does nothing** — this was the hardest bug to find in this area.
- Only one row is revealed at a time (single static weak reference); adapter `getView`/`bindView`
  must call `concealImmediately()`, and the action panel is only touch-enabled while revealed.

## Data flow (how a note lives)

1. **On disk**: per-account dir at `filesDir/<sanitized account name>/` with mail files `<uid>.eml`,
   plus subdirs `new/` (pending upload) and `deleted/` (pending deletion) — created by
   `SyncUtils.CreateLocalDirectories`.
2. **In SQLite** (`NotesDb`, singleton via `getInstance`): metadata row (title, date, number=UID,
   bgcolor, saveState, accountname) + tag rows. DB is a cache; `.eml` files are the source of truth.
   Row dates are stored as the fixed day-first string from `Utilities.GetShortDateString()`
   (`dd/MM/yyyy`), not the locale short format.
3. **On the server**: HTML e-mail in the account's IMAP folder (default `Notes`), Apple-compatible
   headers. Local UID ↔ server UID mapping uses `X-Universally-Unique-Identifier` and IMAP UIDs;
   `GetTempNumber`/`RemoveTempNumber` in `NotesDb` handle pre-sync negative temp numbers.
4. Sync is driven by the Android sync framework (`SyncUtils` requests/schedules, respects
   `SyncInterval`), plus `UpdateThread` for immediate user actions. UI observes changes via a
   ContentObserver on the sync authority and the `SYNC_FINISHED` broadcast.
5. **The row snippet is not in the database**: `NotesListAdapter` reads it from the note file
   (`SyncUtils.GetNoteFile`), takes the lines after the title line of the `Html.fromHtml()` output,
   and caches per file + modification time. Notes without a local file show no snippet.

## Sync behaviour worth knowing

- **Read-only mailbox is a supported state.** `SyncUtils.OpenRemoteIMAPNotesFolder(mode)` honours
  its mode and falls back to `READ_ONLY` on `ReadOnlyFolderException`, remembering it
  (`SyncUtils.RemoteFolderIsReadOnly()`); `ConnectToRemote` opens the folder for writing at the start
  of every sync so the condition is detected early. Notes are still downloaded; `SyncAdapter`
  surfaces `R.string.sync_error_read_only_folder` instead of JavaMail's "Cannot open in desired mode"
  (`ReadOnlyFolderException` / `IllegalWriteException` map to it via `RememberError`). An open folder
  cannot change mode in JavaMail, so it is reopened when a write becomes necessary.
- **A refresh is batched**: UIDs + flags for the whole folder in one `FetchProfile`, headers in a
  second one, `HashSet` UID comparisons, and the "re-scan tags of every stored note" pass is skipped
  when the sync changed nothing. Every sync step logs its duration
  (`onPerformSync connect: … ms` … `onPerformSync total: … ms`).
  Still open: first import of a mailbox fetches each body one note at a time (JavaMail has no
  `FetchProfile` item for content), and the list is only redrawn after the whole sync.

## Build & run

```sh
./gradlew assembleDebug      # APK: ImapNotes3/build/outputs/apk/debug/ImapNotes3-<versionName>-debug.apk
./gradlew assembleRelease
./gradlew lintDebug          # report: ImapNotes3/build/intermediates/lint_intermediate_text_report/debug/.../lint-results-debug.txt
```

Requirements: Android SDK with `compileSdk 37` + `local.properties` (sdk.dir) or `ANDROID_HOME`
(neither is in the repo — export `ANDROID_HOME` or create `local.properties` before building);
JDK 17+ (toolchain auto-provisioned via foojay; a system JDK 21 works fine).

**Gotchas:**

- `build.gradle` defines a `release` signingConfig pointing to `../keystore.jks` **which is not in the repo** (`keystore.jks` is gitignored), and _both_ `debug` and `release` build types use it. A fresh clone fails at signing. Workaround: generate a debug keystore at the repo root (`keytool -genkey -keystore keystore.jks -alias ImapNotes -keypass ImapNotes -storepass ImapNotes -dname CN=dev`) or point the build at your own config. Plaintext keystore passwords are committed in `build.gradle` (legacy, F-Droid-issued-style setup) — don't "fix" silently.
- APK output names are customized: `${project.name}-${versionName}-${buildType}.apk`.
- `minifyEnabled`/`shrinkResources` are **false**; `proguard-rules.pro` mainly silences JavaMail warnings and keeps `R.color` fields (colors are looked up reflectively by name in `Utilities.getColorIdByName` — renaming color resources breaks this, and lint's `UnusedResources` flags some note colours as dead because of it).
- `./gradlew lintDebug` currently **fails the task** on findings (last run: 193 errors / 138 warnings). 190 of the errors are `MissingTranslation` for base-English strings Weblate has not translated yet — that is expected noise here, not breakage.
- Gradle config cache and `nonTransitiveRClass=false` (plus `nonFinalResIds=false`, `newDsl=false`, `builtInKotlin=false`) are set in `gradle.properties`; keep flags when touching that file.

## Code conventions (match these)

- Methods/classes use **PascalCase** (`GetMessageFromNote`, `RefreshList`) — legacy fork style, keep it consistent (the newer swipe-reveal classes use lowerCamelCase, but follow the surrounding file).
- Every file starts with the GPLv3 header + author copyright lines; preserve them.
- Logging: `private static final String TAG = "IN_<ClassName>"` and `Log.d/v/w/e` everywhere; keep this pattern — log volume is a deliberate debug feature, and debug reports (`send_debug_report` setting) export logs.
- Null-safety via `androidx.annotation` (`@NonNull`/`@Nullable`); `@SuppressLint` used where needed.
- Async work still uses **`AsyncTask`** (`UpdateThread`, `SyncThread`, `LoginThread`) — dated but pervasive; only modernize where the task demands it.
- DB access is `synchronized` methods on the `NotesDb` singleton; **SQL is string-concatenated** (known weakness; prefer parameter binding in new code).
- All user-facing strings go through `res/values/strings.xml` (Weblate keys). Add new strings **in base English only**; changing or deleting string _keys_ forces re-translation work, so keys that became unused (e.g. `all_accounts`, `AccountList`, `new_account`, `check_and_create_account`, `save`, `delete`, `account_removed`) are deliberately **left in place**.
- Colors that represent **note backgrounds** (`blue`, `yellow`, `green`, `red`, `brown`, `gray`, `pink`, `black`, `white`, `none`) are referenced by _name_ at runtime; never rename or delete them. Status/action colors are separate (`ResultOkColor`, `ResultFailedColor`, `DestructiveActionColor`, `EditActionColor`) and must be defined in **both** `values/colors.xml` and `values-night/colors-night.xml` — the note colours invert in dark mode and must not be reused for UI status.
- Back navigation: `enableOnBackInvokedCallback="true"` is set, so prefer the androidx `OnBackPressedDispatcher` (as `NoteDetailActivity` does) over an `onBackPressed()` override.
- Manifest contains explanatory TODO comments (StubProvider, unused meta-data) — treat as known-unknowns, don't delete blindly.

## Git / release practices

- Main branch `master`; remote `origin` = github.com/niendo1/ImapNotes3. Feature branches exist (`hashtag`, `Attachment`, `v1_1_richeditor_update`).
- Release flow: bump `versionCode` + `versionName` in **AndroidManifest.xml**, tag, GitHub release APK + F-Droid (fastlane metadata). Translations merge in as "Translated using Weblate (...)" commits.
- Commit style: short lowercase messages ("gradle and lib updates", "fix for #147", "add swipe actions to account and note lists").
- Dependency updates are a routine commit type; deps are pinned to recent versions — check upstream (JitPack for the RichEditor fork) when upgrading.

## Things to verify before claiming a change works

There is no automated test suite, so validation is: `./gradlew assembleDebug` compiles, then
manual/device testing (or `adb`). Useful checks in this area:

- UI changes touch `main.xml` (notes list), `account_list.xml` / `account_element.xml` (account
  overview), `note_element.xml` (note rows), `account_setup.xml` (two-page setup form),
  `note_detail.xml` (editor), and menus `list.xml` / `detail.xml`.
- Navigation: overview → account notes → Up returns to the overview; notes → Settings → Up returns
  the **same** list instance; rotation and `CLEAR_TOP` relaunches keep the account scope.
- Shares: fire them at `AccountListActivity`, e.g.
  `adb shell am start -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "x" de.niendo.ImapNotes3/.AccountListActivity`.
- Sync: watch the `onPerformSync …: … ms` log lines, and test a read-only mailbox if you touch sync.
- Rows with swipe actions: tap, swipe, scroll (no stuck row), pull-to-refresh still syncs.
- Editor behavior changes require checking the custom `assets/custom.css` and the forked RichEditor JS interface.
