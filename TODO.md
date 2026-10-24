# TODO.md — ImapNotes3 code-quality backlog

Result of a dead-code / boy-scout scan on 2026-10-24, after the account-first navigation rewrite
(`v1.4-07` / `10407`). Every "unused" verdict was checked against real call sites, and every line
reference was re-verified against the current files.

**Work top to bottom.** §1 is deletion with no behaviour change. §2 are small real fixes.
§3 is lint configuration — worth doing early, because it turns the numbers in _Baseline_ into a
regression detector for all later work. §4 is deliberately deferred.

## Baseline (measure before and after)

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew lintDebug
# report: ImapNotes3/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt
./gradlew assembleDebug
```

Current: **193 errors / 138 warnings.** 190 of the errors are `MissingTranslation` (base-English
strings Weblate has not translated yet — expected noise here). The remaining **3 errors are
`UseAppTint`, fixed by §2.3**, so after §1 + §2.3 the expectation is _exactly_ 190 errors and zero
real errors.

`lintDebug` fails the task on findings by design — a non-zero exit is not a broken build. Read the
report file, not the exit code, until §3.1 lands.

---

## 1. Dead code and dead resources — safe to delete

Each item is self-contained and independently committable.

### 1.1 Whole dead class

- [ ] `Sync/Security.java` — 53-line enum, **zero references**. Both users in that package import
      the live one: `Sync/SyncUtils.java:39` and `Sync/SyncAdapter.java:46` import
      `de.niendo.ImapNotes3.Data.Security`. Delete the file.

### 1.2 Dead methods and fields

- [ ] `Sync/SyncUtils.java:658-669` — `finalize()` override whose whole body is commented out
      (`store.close()`). Deprecated since Java 9, and `SyncAdapter` closes its stores explicitly.
- [ ] `Miscs/UpdateThread.java:315` — private `MakeMessageWithAttachment()`, never called; the
      attachment feature never landed (`Data/Attachment.java` was removed earlier).
- [ ] `Data/ImapNotesAccount.java` — 14 stale accessors. The setup screen reads and writes userdata
      through `ConfigurationFieldNames` directly, which is why these went dead:
      `GetAccountName:142`, `GetUsername:167`, `SetUsername:171`, `GetPassword:177`,
      `SetPassword:181`, `GetServer:187`, `SetServer:191`, `GetPortnum:196`, `SetPortnum:200`,
      `GetSecurity:206`, `GetUsesticky:220`, `SetaccountHasNotChanged:237`,
      `GetaccountHasChanged:244`, `SetFolderName:264` (private).
      Keep the `SyncInterval` and `CopyFrom*` accessors — those **are** used.
- [ ] `Data/OneNote.java:103` `SetBGColor()` and `:112` `GetSnippet()`. The list adapter works with
      the `SNIPPET` map key and its own private `GetSnippetOfNote` (`NotesListAdapter.java:261`),
      and calls `SetSnippet` at `:486`. So `SetSnippet` stays.
- [ ] `Miscs/SwipeRevealRow.java:142` `isRevealed()` — the list tracks reveal state through the
      static `revealedRow` reference (`:64`) instead.
- [ ] `Miscs/NDSpinner.java:68` `isDropDownMenuShown()` and `:72` `setDropDownMenuShown()` — the
      public field at `:34` is written internally at `:64` and never read outside the class.
- [ ] `Miscs/SyncThread.java` — the `@StringRes int resId` field (`:44-45`), the constructor
      parameter (`:66`) and the assignment (`:75`) are never read; the only consumer is the
      commented-out `Notifier.Show(resId, …)` at `:79`. Drop field + parameter and update the call
      sites in `ListActivity` / `AccountListActivity`, then delete the now-false
      `// TODO: remove unused arguments.` at `:62`.
      Also remove the inspection leftovers in the same file: `:37-41`
      (`// --Commented out by Inspection …` + `ImapNotesResult res`), `:54` and `:82`
      (commented `applicationContextRef`), `:38` `bool_to_return`.
      Leave `:57` (`NoteDb should probably never be null`) — that one is a real note.

### 1.3 Dead resources

- [ ] `res/values/arrays.xml` — delete the whole file: `reply_entries` (`:3`) and `reply_values`
      (`:8`) are `SettingsActivity` template leftovers, zero references.
- [ ] `res/drawable/ic_action_add_user.xml` — orphaned when the account row/FAB became
      `ic_action_account_add`.
- [ ] `res/values/colors.xml:27` `ShareActionActionColor` and `:29` `transparent` — genuinely
      unused (contrast with §3.3, where the _note_ colours only look unused).
- [ ] `res/xml/searchable.xml` **plus** the manifest meta-data block
      (`AndroidManifest.xml:88-91` — note the manifest's own comment: _"What is this meta-data for?
      There doesn't seem to be any code that uses it"_). The `SearchView` is configured in code
      (`ListActivity.configureSearchView`), and `ListActivity.java:530-537` deliberately comments
      out `setSearchableInfo(...)`; the `ACTION_SEARCH` intent filter was dropped in the rewrite.
      Delete the xml, the meta-data block, and the commented `SearchManager` lines at `:531-537`.

### 1.4 Stale comments (fix or delete while you are in the file)

- [ ] `// TODO Auto-generated constructor stub` / `… method stub` that describe nothing:
      `Miscs/EditorMenuAdapter.java:53, 59, 66, 72`, `AccountConfigurationActivity.java:479`.
- [ ] `ImapNotes3.java:88` `//FIXME - do something usefull` — resolve together with §2.1.
- [ ] `AccountConfigurationActivity.java:332` `// TODO: find out if extras can be null.` —
      either assert it or explain why it cannot happen.
- [ ] `Sync/SyncUtils.java:661-668` and `ListActivity.java:530-537` — commented-out code that will
      disappear with §1.2 and §1.3.

---

## 2. Small real fixes (behaviour, correctness, leaks)

### 2.1 The uncaught-exception handler swallows the crash it records

- [ ] `ImapNotes3.java:87-97`: `System.exit(2)` at `:93` runs **before**
      `defaultUEH.uncaughtException(thread, e)` at `:96`, so the re-throw is unreachable and the
      default handler never runs (no logcat stacktrace, no DropBox entry, no crash dialog). The
      surrounding comments already doubt `System.exit()` is right, and a
      `finishAndRemoveTask()` variant sits commented out. Decide one behaviour and make the code
      match the comment — probably: store the stacktrace, then call the default handler and let the
      system kill the process.

### 2.2 `ListActivity`: listener never unregistered, and destructive work in a UI callback

- [ ] `ListActivity.java:263` registers `AccountsUpdateListener` (inner class at `:920`), but
      `onDestroy` (`:147-151`) only unregisters the `ContentObserver` (`:149`) — the listener
      outlives the activity.
      Worse, `onAccountsUpdated` performs filesystem cleanup:
      `FileUtils.deleteDirectory(ImapNotes3.GetAccountDir(s))` at `:947`. That removal is now owned
      by `SyncUtils.RemoveAccount`, reached through
      `ImapNotesAuthenticatorService.Authenticator.getAccountRemovalAllowed` (see `AGENTS.md`), and
      doing it from a UI callback duplicates it — a leftover from before account deletion moved.
      `AccountListActivity` does this correctly: `addOnAccountsUpdatedListener` in `onStart:137`,
      `removeOnAccountsUpdatedListener` in `onStop:145`.
      **Delete the listener from `ListActivity`** (and the `import android.accounts.OnAccountsUpdateListener:30`),
      then re-check the refresh path around `:789`, whose comment still refers to it.

### 2.3 The only 3 real lint errors: `android:tint` → `app:tint`

- [ ] `res/layout/account_element.xml:25`, `res/layout/account_element.xml:35`,
      `res/layout/note_element.xml:30` (`UseAppTint` from androidx.appcompat).

### 2.4 Back navigation: platform callback mixed with a deprecated override

- [ ] `AccountConfigurationActivity.java` registers a platform
      `android.window.OnBackInvokedCallback` (`:568-571`, unregister `:578`, API 33 guarded, see the
      explanation comment at `:135-136`) **and** still overrides the deprecated `onBackPressed()`
      at `:604-610` (lint `GestureBackNavigation`). `NoteDetailActivity.java:147` already uses the
      androidx `OnBackPressedDispatcher.addCallback`, which works on all API levels — switch to it
      and drop both the platform dispatcher code and the override.

### 2.5 Menus: hardcoded titles and inconsistent search widget

- [ ] `res/menu/detail.xml:9` `"Search"`, `:19` `"Next Item"`, `:27` `"Previous Item"`
      (`HardcodedText`). `@string/search` exists (`res/values/strings.xml:10`); there are **no**
      next/previous keys in any locale, so add two base-English keys (Weblate fills the rest) —
      `detail.xml` also reuses `@string/save:34`, `@string/delete:40`, `@string/share:46`.
- [ ] Search widget class differs between menus: `menu/list.xml:16` uses
      `android.widget.SearchView`, `menu/detail.xml:10` uses
      `androidx.appcompat.widget.SearchView`. Unify on appcompat (the class
      `ListActivity.configureSearchView` imports).
- [ ] `res/menu/detail.xml:11` — `app:showAsAction="always"` (`AlwaysShowAction`); three permanently
      shown items overflow on narrow screens. Review whether prev/next belong in the overflow.

### 2.6 A bare `%` in two base strings produces 6 format warnings

- [ ] `res/values/strings.xml:125-126` — `link_image_width` / `link_image_height` are
      `"Width (% or px)"` / `"Height (% or px)"`. Translations that escape the percent signs are
      counted as format strings with a different argument count, producing `StringFormatCount`
      warnings at `values-ru/strings.xml:105,106`, `values-cs/strings.xml:115,132`,
      `values-zh-rCN/strings.xml:117,118`. Both are **only ever used as EditText hints**
      (`NoteDetailActivity.java:511, 515, 1011, 1015`), never formatted — add `formatted="false"`
      in base English and all 6 warnings disappear without touching any translation.

### 2.7 Context leaks (`StaticFieldLeak` × 3)

- [ ] `Miscs/LoginThread.java:46` — holds the `AccountConfigurationActivity` in a field, so a slow
      IMAP connect leaks the activity. This is the only _real_ leak of the three: the class
      **already** keeps a `FinishListener listener` (`:49`, assigned from the same activity at
      `:55`), so the activity field is redundant — delete it and call back through the listener only
      (`:84` is the one other use, `AccountManager.get(accountConfigurationActivity)`).
- [ ] `ImapNotes3.java:46` `mContext` / `:47` `mContent` — statics, but `mContext` is
      `getApplicationContext()` (`:74`), so it cannot leak an activity. Suppress with
      `@SuppressLint("StaticFieldLeak")` + a one-line rationale. `mContent` does hold a view
      (the Snackbar anchor, set at `:80`) — worth checking it is refreshed per screen before you
      suppress that one.

### 2.8 Accessibility

- [ ] `Miscs/SwipeRevealListView.java:97` overrides `onTouchEvent` without ever calling
      `performClick()` (`ClickableViewAccessibility`): TalkBack cannot activate a row. Call
      `performClick()` on `ACTION_UP` when the gesture was not a horizontal drag.

### 2.9 Manifest hygiene

- [ ] No `android:supportsRtl` although the layouts use RTL attributes (`RtlEnabled`). Decide:
      support RTL (`supportsRtl="true"`, and then the `start`/`end` attrs in
      `account_setup.xml` / `note_detail.xml` etc. need to be finished properly), or set
      `supportsRtl="false"` and drop the RTL attrs. Leaving it undecided is what lint is flagging.
- [ ] `AndroidManifest.xml:8` `<dist:module dist:instant="true" />` — instant apps are gone and are
      irrelevant for an F-Droid APK. Remove.
- [ ] `res/mipmap-anydpi-v26/ic_launcher.xml:2` and `ic_launcher_round.xml:2` — adaptive icons need
      a `<monochrome>` layer for themed icons on Android 13+ (`MonochromeLauncherIcon`).
- [ ] `AndroidManifest.xml:20-21` `WRITE_EXTERNAL_STORAGE` with `maxSdkVersion="32` (`ScopedStorage`).
      It is capped at 32 already, so this is mostly informational — but if `BackupRestore` works on
      33+ without it, consider dropping the permission entirely.

### 2.10 Small things noticed while reading

- [ ] `res/values/colors.xml:25` — stray trailing `>`: `<color name="ShareActionBgColor">#F9FBE7</color>>`.
      Harmless to AAPT, but fix it.
- [ ] `AccountConfigurationActivity.java:115` — string literal in `setText(...)`
      (`SetTextI18n`); move to a string resource.
- [ ] `res/layout/note_detail.xml:13` — `InefficientWeight`: `layout_height="wrap_content"` with a
      weight; use `0dp`.
- [ ] `res/layout/editor_row.xml:5` — `Overdraw`: root paints a background while the theme paints
      one too.
- [ ] `res/layout/account_setup.xml:125` — `tooltipText` needs API 26 while `minSdk` is 25
      (`UnusedAttribute`); harmless, suppress or accept.

### 2.11 Findings inside translations — fix via Weblate, never by hand

Per `AGENTS.md`, do not edit `values-*/strings.xml` directly. Hand these to Weblate:

- [ ] `app_name` is `translatable="false"` in base but is translated in `values-de:3`,
      `values-nb:3`, `values-zh-rCN:3` (`Untranslatable`).
- [ ] `Typos`: `values-nb/strings.xml:51` "Internet" → "Internett"; `values-es/strings.xml:55`
      "Ultimo" → "Último".
- [ ] `TypographyEllipsis`: `values-fr/strings.xml:143` uses `...` instead of `…`.
- [ ] Unused string keys (zero references anywhere, with base line numbers):
      `new_account:6`, `account_removed:11`, `remove:15`, `check_and_create_account:17`,
      `syncing:49`, `Waiting_for_sync:53`, `txt_color:62`, `AccountList:81`, `Last_sync:82`,
      `all_accounts:141`, `OAUTH2:196`.
      Of these, `all_accounts`, `AccountList`, `new_account`, `check_and_create_account`,
      `account_removed` are **intentionally kept** (see `AGENTS.md` — deleting keys forces
      re-translation work); `remove`, `syncing`, `Waiting_for_sync`, `txt_color`, `Last_sync` and
      `OAUTH2` are genuine candidates for a Weblate-side cleanup.
      Do **not** add `save` / `delete` to that list — `menu/detail.xml:34,40` use them.

---

## 3. Lint configuration — do this early; it makes §1 and §2 measurable

- [ ] 3.1 Uncomment the lint block in `build.gradle:83-85`
      (`lint { baseline = file("lint-baseline.xml") }`) and run `lintDebug` once to create the
      baseline (no `lint-baseline.xml` exists yet). After committing it, a _rising_ finding count
      means a real regression instead of 331 lines of known noise.
- [ ] 3.2 `NonConstantResourceId` is **84 of the 138 warnings** and is a false positive here:
      `gradle.properties` sets `android.nonFinalResIds=false`, so the IDs _are_ final. Disable the
      check (`lintOptions`/`lint { disable 'NonConstantResourceId' }`) rather than rewriting every
      `switch` in `NoteDetailActivity` / `EditorMenuAdapter` / `ListActivity`.
- [ ] 3.3 Note-background colours must **not** be deleted (§4 traps) but lint calls them
      `UnusedResources` at `res/values/colors.xml:6` (pink), `:7` (green), `:8` (brown), `:9` (gray).
      Add `tools:ignore="UnusedResources"` on the note-colour block so the remaining
      `UnusedResources` warnings become actionable.
- [ ] 3.4 `res` — `IconMissingDensityFolder`: no `drawable-hdpi/mdpi/xhdpi`. Either accept
      vector-only drawables (they are all vectors — then suppress) or add raster fallbacks.
- [ ] 3.5 `build.gradle:101` `com.github.woheller69:FreeDroidWarn:V1.+` is the only dynamic
      dependency (`GradleDynamicVersion`). Pin it for reproducible builds (note: F-Droid rebuilds
      from source, so pinning is also what makes their build match yours).
- [ ] 3.6 Dependency bumps (`NewerVersionAvailable`): `build.gradle:99` jsoup 1.23.1 → 1.23.2;
      `gradle/wrapper/gradle-wrapper.properties:3` Gradle 9.7.0 → 9.8.2. Check JitPack separately
      for the `richeditor-android` fork.
- [ ] 3.7 `build.gradle:19` `OldTargetApi` (targetSdk 35, compileSdk 37). Bump only with a device to
      test — a targetSdk change alters runtime permissions and background behaviour.
- [ ] 3.8 Commented-out leftovers in `build.gradle`: `// buildToolsVersion "34.0.0"` (`:14`),
      `//implementation 'com.google.android.gms:play-services-auth:21.5.0'` (`:89`, see the OAuth2
      decision in §4), and the `// ToDo` `allprojects { … compilerArgs … }` block (`:104-109`) —
      either enable the compiler warnings or drop the block.
- [ ] 3.9 `gradle.properties` — keep the existing flags (`nonTransitiveRClass=false`,
      `nonFinalResIds=false`, config cache) when touching lint config.

---

## 4. Deferred deliberately (needs design, a device, or a product decision)

- [ ] **SQL is string-concatenated** in `Data/NotesDb.java`: `:150`, `:176`, `:178`, `:192`, `:203`
      (plus the migration statements at `:123, 127, 131, 136`, which are lower risk). Account names
      and hashtags flow into these strings — a name containing `'` breaks the statement. Move to
      `insert`/`update`/`delete` with `ContentValues` + `selectionArgs`, one method per commit.
- [ ] **Save-state edge cases** (carried over from the retired navigation plan, still true):
      a note whose upload fails stays in `new/` with state `SYNCING` and cannot be opened; a pending
      deletion drops the local `deleted/` file even when the server delete failed, so the note
      reappears. Needs a small state machine around `OneNote.save_state`.
- [ ] **Sync performance**, highest payoff first: redraw the list incrementally instead of only
      after a whole sync; batch the first-import body fetches (JavaMail has no `FetchProfile` item
      for content — may need IMAP pipelining, `mail.imap.pipelining`); the per-step duration logs
      (`onPerformSync …: … ms`) already give you the measurements.
- [ ] **OAuth2 decision**: `Data/Security.java:60` has the enum entry commented out and
      `res/values/strings.xml:196` `OAUTH2` is unreferenced, while `build.gradle:89` keeps
      `play-services-auth` commented out. Either delete all three remnants (Google-dependent, and
      F-Droid does not want non-free SDKs) or implement it. Leaving the trio half-alive is the
      worst option.
- [ ] **`AsyncTask` migration**: `UpdateThread`, `SyncThread`, `LoginThread` — deprecated since
      Android 11 but pervasive. Only worth touching when one of them changes anyway.
- [ ] `AndroidManifest.xml:126` `TODO: What is StubProvider for?` — answer: the sync adapter needs a
      content authority (`de.niendo.ImapNotes3.provider`), so `Sync/StubProvider` is required.
      Replace the TODO with that sentence rather than deleting the provider.
- [ ] `Miscs/SmtpServerNameFinder.java` is wired to the account form only:
      `AccountConfigurationActivity.java:256` fills the server field from the e-mail domain.
      Confirm that is the intended UX, or the class is doing less than it looks like.

## Traps — do not "clean" these

- The note-background colour names (`blue`, `yellow`, `green`, `red`, `brown`, `gray`, `pink`,
  `black`, `white`, `none`) are looked up **reflectively by name** in
  `Utilities.getColorIdByName` (`Miscs/Utilities.java`) and are persisted inside note HTML.
  Renaming or deleting them silently breaks existing notes, and lint keeps flagging them as unused
  (see §3.3). Status/action colours (`ResultOkColor`, `ResultFailedColor`, `DestructiveActionColor`,
  `EditActionColor`) are separate and must exist in **both** `values/colors.xml` and
  `values-night/colors-night.xml`.
- `NonConstantResourceId` and the note-colour `UnusedResources` warnings are false positives —
  see §3.2 and §3.3.
- Never hand-edit `values-*/strings.xml` (15+ locales, managed on Weblate) — see §2.11.
- `keystore.jks` is gitignored and absent while `build.gradle` commits plaintext keystore passwords
  (legacy F-Droid-style setup, and _both_ build types use that signingConfig). Do not "fix" either
  silently; see `AGENTS.md` for the local debug-keystore workaround.
- `ListActivity` is not exported and account scope must survive rotation and `CLEAR_TOP` relaunches —
  relevant if you touch §2.2.
