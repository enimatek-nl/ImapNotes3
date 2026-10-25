# TODO.md — ImapNotes3 code-quality backlog

Result of a dead-code / boy-scout scan on 2026-10-24, after the account-first navigation rewrite
(`v1.4-07` / `10407`). Every "unused" verdict was checked against real call sites, and every line
reference was re-verified against the current files.

**Work top to bottom.** §1 (dead code and resources) and §2 (small real fixes) are done. §3 is lint
configuration — worth doing next, because it turns the numbers in _Baseline_ into a regression
detector for all later work. §4 is deliberately deferred (design, device, or artwork needed), and §5
collects what the scan and the §1/§2 work turned up on the way.

## Baseline (measure before and after)

```sh
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-21.jdk/Contents/Home
export ANDROID_HOME="$HOME/Library/Android/sdk"
./gradlew lintDebug
# report: ImapNotes3/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt
./gradlew assembleDebug
```

Current (after §2, 2026-10-25): **192 errors / 121 warnings**, and **every remaining error is a
`MissingTranslation`** — base-English strings Weblate has not translated yet, i.e. expected noise
here (the 3 `UseAppTint` errors are gone, and the two new base keys of §2.5 added 2 to the count).
Warnings went 138 → 134 in §1 and 134 → 121 in §2. The 13 that §2 removed: `UseAppTint` was an
error, and the warnings `ClickableViewAccessibility`, `GestureBackNavigation`, `HardcodedText` ×3,
`AlwaysShowAction`, `UnusedAttribute`, `InefficientWeight`, `Overdraw`, `SetTextI18n`, `RtlEnabled`
and `StaticFieldLeak` ×2 are fixed or deliberately suppressed.
What is left: `NonConstantResourceId` 84, `UnusedResources` 16, `StringFormatCount` 6,
`Untranslatable` 3, `Typos` 2, `MonochromeLauncherIcon` 2, and one each of
`TypographyEllipsis`, `StaticFieldLeak`, `ScopedStorage`, `OldTargetApi`, `NewerVersionAvailable`,
`IconMissingDensityFolder`, `GradleDynamicVersion`, `AndroidGradlePluginVersion` — all of them are
either §3 (lint config / dependencies) or §2.11 (Weblate).

`lintDebug` fails the task on findings by design — a non-zero exit is not a broken build. Read the
report file, not the exit code, until §3.1 lands.

---

## 1. Dead code and dead resources — ✅ done 2026-10-24

All of §1 is committed as `156c7fe`; `assembleDebug` builds and lint went 138 → 134 warnings with
no new errors (see _Baseline_). Kept here as a record of what was removed.

### 1.1 Whole dead class

- [x] `Sync/Security.java` deleted — 53-line enum, **zero references**. Both users in that package
      import the live one: `Sync/SyncUtils.java` and `Sync/SyncAdapter.java` import
      `de.niendo.ImapNotes3.Data.Security`.

### 1.2 Dead methods and fields

- [x] `Sync/SyncUtils.java` — `finalize()` override whose whole body was commented out
      (`store.close()`). Deprecated since Java 9, and `SyncAdapter` closes its stores explicitly.
- [x] `Miscs/UpdateThread.java` — private `MakeMessageWithAttachment()`, never called; the
      attachment feature never landed (`Data/Attachment.java` was removed earlier). Its five now
      orphaned imports (`Multipart`, `Session`, `MimeBodyPart`, `MimeMessage`, `MimeMultipart`)
      went with it.
- [x] `Data/ImapNotesAccount.java` — the 14 "accessors" turned out to be **commented-out blocks**
      (`GetAccountName`, `GetUsername`/`SetUsername`, `GetPassword`/`SetPassword`,
      `GetServer`/`SetServer`, `GetPortnum`/`SetPortnum`, `GetSecurity`, `SetSecurity` ×2,
      `GetUsesticky`, `SetaccountHasNotChanged`, `GetaccountHasChanged`, `SetFolderName`, `Clear`,
      `toString`, plus the `SetAccountname`/`SetUsesticky`/`SetSyncinterval` line comments).
      Several referenced fields that no longer exist — the remaining fields are `final` and are
      populated from `AccountManager` in the constructors, which is why setters never came back.
- [x] `Data/OneNote.java` — `SetBGColor()` and `GetSnippet()`. `SetSnippet()` stays; the list
      adapter uses it (`NotesListAdapter.java:486`) together with its private `GetSnippetOfNote`.
- [x] `Miscs/SwipeRevealRow.java` — `isRevealed()`. The `revealed` field itself stays (written in
      `concealImmediately` / `reveal`).
- [x] `Miscs/NDSpinner.java` — `isDropDownMenuShown()` / `setDropDownMenuShown()`, and with them the
      public field and the `performClick()` override that existed only to set it: once the accessors
      were gone the field was write-only. `initIsDone` **is** used externally
      (`NoteDetailActivity.java:339`) and stays.
- [x] `Miscs/SyncThread.java` — the `@StringRes int resId` field, constructor parameter and
      assignment (only consumer was the commented-out `Notifier.Show`), the `@StringRes` import,
      the now-false `// TODO: remove unused arguments.`, and the 2016 `--Commented out by
    Inspection` leftovers. One call site in `ListActivity.RefreshList` updated.
      Consequence: `R.string.refreshing_notes_list` is now unused (§2.11).
      Kept: `// TODO: NoteDb should probably never be null.` — that one is a real note.

### 1.3 Dead resources

- [x] `res/values/arrays.xml` deleted — `reply_entries` / `reply_values` were `SettingsActivity`
      template leftovers with zero references.
- [x] `res/drawable/ic_action_add_user.xml` deleted — orphaned when the account row/FAB became
      `ic_action_account_add`.
- [x] `ShareActionActionColor` and `transparent` deleted from **both** `values/colors.xml` and
      `values-night/colors-night.xml` (the `@android:color/transparent` in `account_setup.xml:121`
      is the framework colour, not this one — contrast with §3.3, where the _note_ colours only
      look unused).
- [x] `res/xml/searchable.xml` deleted, with the manifest meta-data block and its
      _"What is this meta-data for?"_ comment, and the commented `SearchManager` /
      `setSearchableInfo` lines in `ListActivity.onCreateOptionsMenu`. The `SearchView` is fully
      configured in code, and the `ACTION_SEARCH` intent filter was dropped in the rewrite.

### 1.4 Stale comments

- [x] The `// TODO Auto-generated constructor/method stub` comments in `Miscs/EditorMenuAdapter`
      (×4, one of which also hid a commented-out `setOnClickListener` block referencing the
      long-gone `mEditor` field), and in `AccountConfigurationActivity.onNothingSelected`
      (replaced with a sentence saying why the body is empty).
- [x] `ImapNotes3.java` `//FIXME - do something usefull` — resolved by §2.1.
- [ ] `AccountConfigurationActivity.java:337` `// TODO: find out if extras can be null.` — a real
      open question, not generator noise; left in place.

---

## 2. Small real fixes (behaviour, correctness, leaks) — ✅ done 2026-10-25

`assembleDebug` builds and lint is 192 errors / 121 warnings with no real errors left (see
_Baseline_). Four items came out differently than first scoped; each says why, and the parts that
could not be done safely moved to §4.

### 2.1 The uncaught-exception handler swallows the crash it records

- [x] `ImapNotes3.java` — `System.exit(2)` deleted, so the previous default handler really runs and
      the logcat stacktrace, DropBox entry and crash dialog survive; the stacktrace is still stored
      for the debug report. The `//FIXME - do something usefull` is resolved.

### 2.2 `ListActivity`: listener never unregistered, and destructive work in a UI callback

- [x] `FileUtils.deleteDirectory(ImapNotes3.GetAccountDir(s))` removed from `onAccountsUpdated` —
      `SyncUtils.RemoveAccount` (reached through
      `ImapNotesAuthenticatorService.Authenticator.getAccountRemovalAllowed`) already owns that
      deletion, so the listener duplicated it and did filesystem I/O on the main thread. The
      `FileUtils` and `IOException` imports went with it; the comment says who owns the deletion.
- [x] The listener is held in a field and unregistered in `onDestroy`, next to the `ContentObserver`,
      so it no longer outlives the activity.
- **Deviation — it could not be deleted outright.** It is the only thing filling the static
      `accounts` / `accountList` cache that this screen and `getAccountList()` read; without it
      `RefreshScopedList()` would bail at its `accountList.contains(scopedAccountName)` guard and the
      screen would stay empty. The comment at that guard still describes it correctly.
      Registration stays in `onCreate` (not `onStart`/`onStop` as in `AccountListActivity`) on
      purpose: the list must also notice accounts added or removed while it is stopped behind the
      editor, and `onResume` → `RefreshScopedList()` relies on the cache being current.

### 2.3 The only 3 real lint errors: `android:tint` → `app:tint`

- [x] `account_element.xml` (both row buttons) and `note_element.xml` (the delete button) use
      `app:tint`; both roots gained `xmlns:app`. **Zero real lint errors remain.**

### 2.4 Back navigation: platform callback mixed with a deprecated override — ⚠️ not as planned

- [x] `AccountConfigurationActivity.onBackPressed()` is kept and annotated
      `@SuppressLint("GestureBackNavigation")`, with the reason in a comment. The androidx
      `OnBackPressedDispatcher` is **not available here**: the activity extends
      `AccountAuthenticatorActivity`, which must stay (its `finish()` returns the
      `AccountAuthenticatorResponse` that `Authenticator.addAccount` passes in), and that framework
      class is a plain `Activity` — which is also why the class carries all that manual
      `getDelegate()` boilerplate.
- [ ] Base-class migration → §4.

### 2.5 Menus: hardcoded titles and inconsistent search widget

- [x] `menu/detail.xml` — `@string/search` (it existed), plus two new base-English keys,
      `next_item` and `previous_item` (no locale had anything similar). Weblate fills the
      translations; those two keys are the +2 in _Baseline_.
- [x] `menu/list.xml` now uses `androidx.appcompat.widget.SearchView` — the class
      `ListActivity.onCreateOptionsMenu` imports, which was switched to appcompat accordingly.
- [x] `showAsAction="always"` **kept** on the three editor search items, documented with
      `tools:ignore="AlwaysShowAction"`: the editor shows at most four items at a time, and
      `itemNext` / `itemPrevious` are invisible until a search is running
      (`NoteDetailActivity`, in the query-text listener), which is exactly the case `always` is for.

### 2.6 A bare `%` in two base strings — ⚠️ reverted, it made it worse

- [x] `formatted="false"` on `link_image_width` / `link_image_height` turned the base string into a
      zero-argument one, so every locale that keeps a bare `%` started to warn: 6 findings became 12.
      Reverted; the base strings now only carry a comment naming the offending locales.
- [ ] The real fix is on Weblate (§2.11): `values-ru:105,106`, `values-cs:115,132` and
      `values-zh-rCN:117,118` escape the percent signs as `%%`, and lint compares against them.

### 2.7 Context leaks (`StaticFieldLeak` × 3)

- [x] `ImapNotes3.mContext` — `@SuppressLint("StaticFieldLeak")` + rationale: it is
      `getApplicationContext()` and cannot hold an activity.
- [x] `ImapNotes3.mContent` — the real one. It was only ever set by `ListActivity.onCreate` and then
      kept a destroyed activity's entire content view alive for the life of the process. Now a
      `WeakReference<View>`; `ShowMessage` / `ShowAction` log and return when no anchor is alive
      instead of throwing. Note for anyone chasing a missing Snackbar: **only `ListActivity` calls
      `setContent`**, so a screen that passes `null` as the view (two calls in `NoteDetailActivity`)
      borrows the list's view.
- [ ] `Miscs/LoginThread.java` **not** fixed — the plan was wrong. The activity field is used **six**
      times in `doInBackground` (`setResult`, `setAccountAuthenticatorResult`,
      `SyncUtils.SetUIDValidity`, `AccountManager.get`, two `getString`), not once, and
      `FinishListener` only declares `onFinishPerformed`, so it cannot carry those. Dropping the
      field would not remove the leak either, because the listener _is_ the activity. → §4.

### 2.8 Accessibility

- [x] `Miscs/SwipeRevealListView.performClick()` activates the row that holds accessibility focus
      through `AbsListView.performItemClick`, and `onTouchEvent` calls it for a tap that was not a
      swipe (that is what the `ClickableViewAccessibility` check asks for). Touch behaviour is
      unchanged: `isTap()` is pure and the list has no `OnClickListener`.
- [ ] Needs a TalkBack pass on a device. If the screen reader sends `ACTION_CLICK` to the row rather
      than the list, `SwipeRevealRow` needs the same routing plus an
      `onInitializeAccessibilityNodeInfo` that advertises the click action — a row is deliberately
      not clickable, because a clickable item view fires next to the list's own item click.

### 2.9 Manifest hygiene

- [x] `<dist:module dist:instant="true" />` and the `xmlns:dist` declaration removed.
- [x] `android:supportsRtl="false"` set explicitly. The layouts use `start`/`end` attributes, RTL was
      never finished, and no translated locale is RTL, so this states the current state instead of
      leaving lint to guess. Finishing RTL → §4.
- [x] `WRITE_EXTERNAL_STORAGE` (already capped at `maxSdkVersion="32"`) **stays**:
      `ImapNotes3.GetDocumentDir()` and `BackupRestore` write the backup zip into the public
      `Documents` directory, which on API 25-32 still needs the permission. The manifest says so now.
- [ ] `<monochrome>` layer for the adaptive icons needs artwork → §4. The shipped foregrounds are
      PNGs and the sources in `art/icons/` are 0.9 MB Inkscape SVGs with filters and gradients; a
      monochrome layer wants a flat silhouette.

### 2.10 Small things noticed while reading

- [x] `AccountConfigurationActivity` — the `"Trash"` literal became `DEFAULT_COPY_IMAP_FOLDER` with a
      comment (an IMAP folder name must not be translated) and `@SuppressLint("SetTextI18n")`.
- [x] `note_detail.xml` — `RichEditor` height `wrap_content` → `0dp`; it is the only weighted child.
- [x] `editor_row.xml` — background **kept**, `tools:ignore="Overdraw"` + comment: those rows are
      Spinner dropdown rows and a popup window does not inherit the colour of the editor strip.
- [x] `account_setup.xml` — `tools:ignore="UnusedAttribute"` on `tooltipText` (API 26 attribute in a
      minSdk 25 app; it is simply ignored below 26).

### 2.11 Findings inside translations — fix via Weblate, never by hand

Per `AGENTS.md`, do not edit `values-*/strings.xml` directly. Hand these to Weblate:

- [ ] `app_name` is `translatable="false"` in base but is translated in `values-de:3`,
      `values-nb:3`, `values-zh-rCN:3` (`Untranslatable`).
- [ ] `Typos`: `values-nb/strings.xml:51` "Internet" → "Internett"; `values-es/strings.xml:55`
      "Ultimo" → "Último".
- [ ] `TypographyEllipsis`: `values-fr/strings.xml:143` uses `...` instead of `…`.
- [ ] Percent signs in `link_image_width` / `link_image_height`: `values-ru:105,106`,
      `values-cs:115,132`, `values-zh-rCN:117,118` escape them as `%%` while other locales keep a
      bare `%`. The strings are only ever used as EditText hints and are never formatted, so a single
      `%` is right — see §2.6 for why the base-side `formatted="false"` is not the answer.
- [ ] New keys to translate: `next_item`, `previous_item` (note-editor search navigation).
- [ ] Unused string keys (zero references anywhere, with base line numbers):
      `new_account:6`, `account_removed:13`, `remove:17`, `check_and_create_account:19`,
      `syncing:51`, `refreshing_notes_list:52`, `Waiting_for_sync:55`, `txt_color:64`,
      `AccountList:83`, `Last_sync:84`, `all_accounts:145`, `OAUTH2:200`.
      Of these, `all_accounts`, `AccountList`, `new_account`, `check_and_create_account`,
      `account_removed` are **intentionally kept** (see `AGENTS.md` — deleting keys forces
      re-translation work), and `refreshing_notes_list` only became unused when §1 removed the
      `SyncThread.resId` argument, so it joins that group rather than being a new problem.
      `remove`, `syncing`, `Waiting_for_sync`, `txt_color`, `Last_sync` and `OAUTH2` are genuine
      candidates for a Weblate-side cleanup.
      Do **not** add `save` / `delete` to that list — `menu/detail.xml` uses them.

---

## 3. Lint configuration — do this next; it makes everything after it measurable

- [ ] 3.1 Uncomment the lint block in `build.gradle:83-85`
      (`lint { baseline = file("lint-baseline.xml") }`) and run `lintDebug` once to create the
      baseline (no `lint-baseline.xml` exists yet). After committing it, a _rising_ finding count
      means a real regression instead of 313 findings of known noise.
- [ ] 3.2 `NonConstantResourceId` is **84 of the 121 warnings** and is a false positive here:
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
      `res/values/strings.xml:200` `OAUTH2` is unreferenced, while `build.gradle:89` keeps
      `play-services-auth` commented out. Either delete all three remnants (Google-dependent, and
      F-Droid does not want non-free SDKs) or implement it. Leaving the trio half-alive is the
      worst option.
- [ ] **`AsyncTask` migration**: `UpdateThread`, `SyncThread`, `LoginThread` — deprecated since
      Android 11 but pervasive. Only worth touching when one of them changes anyway.
- [ ] `AndroidManifest.xml:122` `TODO: What is StubProvider for?` — answer: the sync adapter needs a
      content authority (`de.niendo.ImapNotes3.provider`), so `Sync/StubProvider` is required.
      Replace the TODO with that sentence rather than deleting the provider.
- [ ] **`AccountConfigurationActivity` base class.** It extends `AccountAuthenticatorActivity` and
      re-implements what `AppCompatActivity` does by hand (about 15 `getDelegate()` calls:
      `installViewFactory`, `setContentView` ×3, `onPostResume`, `onStop`, `onTitleChanged`, …).
      Moving to `AppCompatActivity` would give it the androidx `OnBackPressedDispatcher` (closing
      §2.4) and drop the boilerplate, but the authenticator response that
      `ImapNotesAuthenticatorService.Authenticator.addAccount` puts into the intent has to be
      forwarded by hand — needs the "add account from the system's account settings" flow retested on
      a device.
- [ ] **`Miscs/LoginThread.java` holds the activity** (`:46`) across a slow IMAP connect, so a
      rotation or a left screen leaks it. It genuinely needs a context plus
      `setResult` / `setAccountAuthenticatorResult`, which `FinishListener` does not carry (see §2.7)
      — fix it together with the `AsyncTask` migration below, not by deleting the field.
- [ ] **RTL support.** The layouts use `start`/`end` attributes, the manifest now says
      `supportsRtl="false"`. Finishing it means auditing every layout for leftover
      `left`/`right`, then flipping it to `true` and looking at the mirrored screens.
- [ ] **Monochrome launcher icon** (`MonochromeLauncherIcon` ×2): needs a flat silhouette layer, i.e.
      real artwork (`art/icons/ic_launcher_foreground*.svg` are filter-heavy Inkscape files, the
      shipped foregrounds are PNGs).
- [ ] `Miscs/SmtpServerNameFinder.java` is wired to the account form only:
      `AccountConfigurationActivity.java:261` fills the server field from the e-mail domain.
      Confirm that is the intended UX, or the class is doing less than it looks like.

## 5. Noticed while working §1 and §2

Small, verified, and not worth a separate scan — pick these up while you are in the file.

- [ ] `ListActivity.java:747` — `ImapNotes3.ShowMessage("Please install a file manager.", listview,
      3000)`: a user-facing string that never went through `res/values/strings.xml`, and a duration of
      3000 _seconds_ (fifty minutes) where every other call passes single digits. Needs a base-English
      key and something like `5`.
- [ ] `ImapNotes3.java:178` `public static String AvoidLargeBundle` — the note body is passed between
      `NoteDetailActivity:919` and `ListActivity:827,837` through a static field to dodge a
      `TransactionTooLargeException`. It is never cleared, so the last note's text stays in memory,
      and it silently breaks if two editors are ever on the back stack. A local file or a `Bundle`
      with a proper size check would be the honest version.
- [ ] `ImapNotes3.intent` — a `public static Intent` that `SyncAdapter:225-230` fills and
      `ListActivity:334-339` reads: the sync-to-UI channel is process-global mutable state, which is
      also why the `ContentObserver` has the `selfChange` guard. Worth knowing before touching either
      side; the `SYNCED` / `SYNCINTERVAL` extras are already written but never read.
- [ ] `Data/ImapNotesAccount.ClearHomeDir()` catches `IOException | Error` "for anbox" and only logs.
      Called once, from `SyncAdapter:131`, right after `ClearDb` — swallowing the failure there leaves
      note files of a deleted account on disk.
- [ ] Deprecated `fill_parent` / `FILL_PARENT` still in `editor_row.xml`, `account_setup.xml`
      (`layout_width="fill_parent"`, `android:layout_height="0px"`) and
      `AccountConfigurationActivity:107` (`LinearLayout.LayoutParams.FILL_PARENT`); `match_parent` /
      `MATCH_PARENT` are the same value with the modern name.

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
