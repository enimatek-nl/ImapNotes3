# TODO.md — ImapNotes3 iOS Notes-style navigation plan

Goal: make the app feel closer to Apple Notes:

1. Start screen shows the configured IMAP accounts.
2. If there are no accounts, show a prominent add-account action.
3. Tapping an account opens that account’s notes list.
4. The last opened account becomes the app’s normal landing screen on later launches.
5. In the notes list, tapping a note opens the editor.
6. Swiping a note row reveals row actions, starting with delete.

This document is intentionally phased. Each phase can be implemented and tested independently.

---

## Current app behavior

Relevant current entry points:

- `src/main/AndroidManifest.xml`
    - `ListActivity` is currently the launcher activity.
    - `ListActivity` also handles `ACTION_SEND` / `ACTION_SEND_MULTIPLE` share intents.
- `src/main/java/de/niendo/ImapNotes3/ListActivity.java`
    - Shows a top account spinner.
    - Shows notes for the selected account, with an “All accounts” option.
    - Opens `AccountConfigurationActivity` automatically when no account exists.
    - Uses `AccountManager` to list accounts of type `de.niendo.ImapNotes3`.
- `src/main/res/layout/main.xml`
    - Top account spinner + edit account button.
    - `ListView` for notes.
    - Floating add-note button.
- `src/main/res/layout/note_element.xml`
    - Current note row: title and date.
- `src/main/java/de/niendo/ImapNotes3/AccountConfigurationActivity.java`
    - Existing add/edit IMAP account screen.
- `src/main/java/de/niendo/ImapNotes3/NotesListAdapter.java`
    - Custom `BaseAdapter` used by `ListActivity`.

---

## Design decisions

Keep these decisions unless explicitly revisited:

- Keep Java only for now, matching the existing codebase.
- Reuse `AccountConfigurationActivity` for add/edit account.
- Do not hand-edit translation files under `values-*/strings.xml`. Add new English strings only in `src/main/res/values/strings.xml`.
- Do not change existing string keys unless unavoidable, because Weblate translations depend on them.
- Prefer incremental UI refactoring over replacing the whole list immediately.
- Preserve existing IMAP sync, `.eml` file storage, and `UpdateThread` delete/save behavior.
- Use PascalCase for new Java methods/classes, matching existing project style.
- Add new Java files with the standard GPLv3 header used by existing sources.

---

## Phase 1 — Add an account overview screen

This is the new start screen.

**Status:** implemented for manual testing from the notes-list overflow menu. The app does not start
on this screen yet; that belongs to Phase 2.

### Deliverable

A new screen that lists configured ImapNotes3 accounts.

### Steps

- [x] Create `AccountListActivity.java`.
- [x] Create `src/main/res/layout/account_list.xml`.
- [x] Create `src/main/res/layout/account_element.xml` for one account row.
- [x] Add UI elements:
    - [x] `RecyclerView` or `ListView` for accounts.
    - [x] Empty-state text such as “No accounts yet”.
    - [x] Add-account button, preferably a `FloatingActionButton` with `+`.
- [x] Query accounts through `AccountManager`.
- [x] Filter accounts to `Utilities.PackageName`, similar to the current `AccountsUpdateListener` logic in `ListActivity.java`.
- [x] For each account row show at least:
    - [x] Account display name.
    - [x] IMAP server as secondary text.
    - [x] Optional: username or sync interval later.
- [x] Add account list item click behavior:
    - [x] Open `ListActivity`.
    - [x] Pass the tapped account name as an intent extra, for example `ListActivity.EDIT_ITEM_ACCOUNTNAME`.
- [x] Add account row action affordance:
    - [x] Tap on row opens account notes.
    - [x] Optional long-press or overflow button opens `AccountConfigurationActivity` with `Actions.EDIT_ACCOUNT`.
- [x] Add “add first account” behavior:
    - [x] If `AccountManager` returns zero ImapNotes3 accounts, hide the list and show empty state + `+`.
    - [x] `+` starts `AccountConfigurationActivity` with `Actions.CREATE_ACCOUNT`.
- [x] Make account list refresh when accounts change.
- [x] Move account-add logic out of `ListActivity.noAccountExists()` once `AccountListActivity` is the launcher.
    - Done in Phase 2. `noAccountExists()` now returns to the account overview, except for share intents.

### Acceptance criteria

- [x] App starts at the account overview.
- [x] With no accounts, the screen clearly shows an add-account button.
- [x] With one or more accounts, each account is tappable.
- [x] Tapping an account opens the notes list for that account.

---

## Phase 2 — Make the account overview the launcher

### Deliverable

The new account overview becomes the normal app start screen.

**Status:** implemented and installed. Verified on the emulator: the launcher resolves to
`AccountListActivity`, the app icon label stays “ImapNotes3”, the empty state shows “No accounts yet”,
the `+` button opens account setup, and Up from account setup returns to the overview.

### Steps

- [x] Add `AccountListActivity` to `AndroidManifest.xml`.
    - Added in Phase 1, then made exported and launcher-capable in Phase 2.
- [x] Move the `MAIN` / `LAUNCHER` intent filter from `ListActivity` to `AccountListActivity`.
- [x] Keep `ListActivity` declared and exported as needed for internal navigation.
- [x] Keep share-intent filters working during migration:
    - [x] Option A: temporarily keep `ACTION_SEND` / `ACTION_SEND_MULTIPLE` / `ACTION_SEARCH` filters on `ListActivity`.
    - [ ] Option B: move share intents to `AccountListActivity` and then forward them to `ListActivity`.
    - Option A chosen for now; the manifest has a comment marking this as temporary.
- [x] Keep `AccountConfigurationActivity` parent activity as `AccountListActivity` instead of `ListActivity`.
- [x] Keep `NoteDetailActivity` parent as `ListActivity`.
- [x] Remove or disable automatic account-setup launch from `ListActivity` once `AccountListActivity` owns empty-state handling.
    - `noAccountExists()` now calls `finish()` back to the overview, but still opens account setup when
      `ListActivity` was started by a share intent, so shared content is not lost.
- [x] Ensure back button flow is coherent:
    - [x] Notes list → back → account overview.
        - Also set `android:parentActivityName=".AccountListActivity"` on `ListActivity`.
    - [x] Edit account → back → account overview or notes list depending where it was opened.
        - `android.R.id.home` in `AccountConfigurationActivity` now calls `finish()` instead of
          `NavUtils.navigateUpFromSameTask()`, so Up returns to whoever opened it.
    - [x] Add account from empty overview → after success → return to the account overview with the
          new account listed (superseded by Phase 2b; it used to open the new account's notes list).
    - [x] `AccountListActivity` is `singleTop` and both navigation directions use
          `FLAG_ACTIVITY_CLEAR_TOP | FLAG_ACTIVITY_SINGLE_TOP`, so the overview/notes pair does not stack up.

### Acceptance criteria

- [x] Installing and starting the app lands on the account overview when there is no saved last account.
- [x] First launch with no account goes to account overview, not directly to account setup.
- [x] Existing account creation flow still completes successfully.
    - Verified on the emulator against a throwaway local IMAP listener (`10.0.2.2:1143`): account was
      created, listed on the overview, opened its notes list, and was removed again. No changes to the
      IMAP/sync code were needed.

---

## Phase 2b — Account setup result flow

### Deliverable

Account setup becomes a two-page flow: the form, then the outcome of the connection check.

**Status:** implemented and verified on the emulator (form has no Cancel, button is “Next”, failed check
shows the reason with “Help” / “I understand”, Back from a failed check returns to the form, and a
successful check shows “Account has been added” with “Finished” landing on the account overview that
lists the new account).

### Steps

- [x] Rework `src/main/res/layout/account_setup.xml` into two pages inside one activity:
    - [x] Keep the existing form in a `ScrollView` (`@+id/scrollView`) with all current field ids intact.
    - [x] Add `@+id/accountResult` result page: `resultSpinner`, `resultIcon`, `resultHeading`,
          `resultMessage`, `resultButton`, `resultHelpButton`.
- [x] In `AccountConfigurationActivity`, drop the Cancel button for the create path and label the single
      button `@string/next` (“Next”). Up/Back already leaves the screen, so Cancel is redundant.
      The edit path keeps Cancel / Delete / Save.
- [x] Keep check and create as one network step (`LoginThread` still does connect + `addAccountExplicitly`);
      “Next” starts it and the second page displays the outcome.
- [x] `DoLogin()` switches to the checking page (spinner + `@string/logging_in`) before starting the thread.
- [x] On success: check icon, localized “Account has been added” heading, account name, `@string/finished`
      button that clears the form and finishes.
- [x] On failure: error icon, `@string/IMAP_operation_failed` heading, the error reason, `@string/help`
      opening the existing IMAP help dialog, and `@string/i_understand` returning to the form with all
      entries intact.
- [x] Do not set `ResultCodeError` on failure; the activity keeps the `ResultCodeNeutral` from `onCreate`,
      so leaving with Back behaves like a cancel (no caller treats Error specially).
- [x] Back from the failed-check page returns to the form instead of leaving setup. Because the manifest
      sets `android:enableOnBackInvokedCallback="true"`, `onBackPressed()` is not called on Android 16, so
      the failure page registers a platform `OnBackInvokedCallback` (API 33+) and the `onBackPressed()`
      override keeps older releases covered.
- [x] `AccountListActivity.onActivityResult` no longer auto-opens the notes list after `ADD_ACCOUNT` +
      success; it only reloads the account list, so “Finished” lands on the overview with the new account.
- [x] New strings added in base English only: `next`, `finished`, `i_understand`. `check_and_create_account`
      is left in place (unused) to avoid Weblate churn.
- [x] New colors for the status icon in `values/colors.xml` and `values-night/colors-night.xml`
      (`ResultOkColor`, `ResultFailedColor`); the existing `green`/`red` note-background colors invert in
      dark mode and must not be used for status.
- [x] New drawables `ic_action_check_circle` and `ic_action_error`.
- [x] Crash fix found while verifying the success path: `ListActivity.Check_Action_Send()` assumed a
      non-null intent action and threw `NullPointerException` when `ListActivity` was started from an
      account row (explicit intent, no action). Comparisons are now null-safe.

### Acceptance criteria

- [x] “Next” with a bad server shows the reason, “Help” and “I understand”; “I understand” and Back both
      return to the form with the entered values intact.
- [x] “Next” with a reachable server shows the finished page; “Finished” returns to the account overview,
      which lists the new account.

---

## Phase 2c — Editing an account uses the same flow as adding

### Deliverable

Editing an account shows the same form as adding one (server settings are editable) and ends with the
same check + result page: “Finished” back to the overview when the connection still works, “I understand”
back to the form when it does not. The Delete button disappears from this screen (it moves to the account
overview — see Phase 5b).

### Steps

- [ ] In `AccountConfigurationActivity`, drop the programmatic button trio for the edit path (currently
      built in the `EDIT_ACCOUNT` branch of the layout setup: Cancel → `clickListenerAbort`, Delete →
      `clickListenerRemove`, Save → `clickListenerSave`) and use the same single `@string/next` button +
      `clickListenerLogin` the create path already builds.
- [ ] Reuse the Phase 2b two-page machinery for the edit path — `formScrollView` (`@+id/scrollView`) and
      `resultLayout` (`@+id/accountResult` with `resultSpinner` / `resultIcon` / `resultHeading` /
      `resultMessage` / `resultButton` / `resultHelpButton`) already exist in `account_setup.xml`, so this
      is mostly wiring, not new UI.
- [ ] Verify `DoLogin()`’s `EDIT_ACCOUNT` branch does a fresh connection check with the entered settings
      before it writes them (`LoginThread` already connects first and only then updates userdata, but the
      check result now has to be shown on the result page rather than as a Snackbar).
- [ ] Keep the existing anti-spoofing rule: if the server field changed and the password field is empty,
      reject with `R.string.imap_server_changed_new_password` and stay on the form (show it on the form
      page, not the result page — nothing was checked yet).
- [ ] Result page wording for the edit path: “Account has been updated” + `@string/finished` on success;
      on failure the same error page as create (`@string/IMAP_operation_failed` + reason + `@string/help` + `@string/i_understand` returning to the form with the edited entries intact).
- [ ] Keep the Back-from-result-page behavior from Phase 2b (the `OnBackInvokedCallback` for the failure
      page). For a _successful_ edit, Back/Up should behave like “Finished”.
- [ ] Add one new base-English string for the “account has been updated” heading; leave `save` and `delete`
      keys in place (unused for now) to avoid Weblate churn.
- [ ] `AccountListActivity.onActivityResult`: an edited account must refresh the list (it already reloads
      on `EDIT_ACCOUNT` + OK — confirm “Finished” is the only way to get there, and that a failed edit that
      the user backs out of leaves the old settings untouched).

### Acceptance criteria

- [ ] Opening an account row’s edit action shows the form pre-filled, with only “Next” as the button.
- [ ] “Next” re-runs the connection check against the entered server settings; the result page shows the
      outcome.
- [ ] Success → “Finished” returns to the account overview, which shows the changed account name/settings.
- [ ] Failure → the reason plus “I understand” returns to the form with the edits intact and the old
      account settings unchanged on disk.
- [ ] Changing the server while leaving the password empty is refused with the existing message.

### Notes / remaining

- `clickListenerRemove` currently ends the activity with
  `setResult(ListActivity.ResultCodeRemoveAccount, intent)`; once delete is gone from this screen that
  result code has no producer left and can be retired together with the unscoped list (Phase 9).

---

## Phase 3 — Remember and restore the last opened account

### Deliverable

After a user opens an account, later app launches land in that account’s notes overview if the account still exists.

### Steps

- [ ] Add a preference key, for example:
    - [ ] `LAST_ACCOUNT_NAME`
    - Store it in `SettingsActivity.MAIN_PREFERENCE_NAME`.
- [ ] When `AccountListActivity` launches `ListActivity` for an account, save that account name as last opened.
- [ ] When app starts:
    - [ ] Load `LAST_ACCOUNT_NAME`.
    - [ ] Verify the account still exists.
    - [ ] If it exists, start `ListActivity` directly for that account.
    - [ ] If it does not exist, stay on account overview.
- [ ] Add an escape hatch / navigation behavior:
    - [ ] Back from the restored notes list should go to the account overview.
    - [ ] Do not trap the user in a single account.
- [ ] If exactly one account exists, optionally open it directly by default.
- [ ] Consider adding a debug log line using the project’s logging pattern:
    - `Log.d(TAG, "Restoring last account: " + lastAccountName);`

### Acceptance criteria

- Opening account A, killing the app, reopening the app → app opens account A’s notes list.
- Deleting account A and reopening the app → app returns to account overview.
- Pressing back from notes list still lets the user choose another account.

---

## Phase 4 — Make the notes screen account-scoped

**Status:** implemented and verified on the emulator. Opening an account from the overview now hides the
account spinner row, titles the action bar with the account name, and adds an Up arrow back to the
overview. Share/search entry points keep the old spinner mode.

### Deliverable

`ListActivity` becomes the notes overview for one selected account instead of relying on the top account spinner.

### Steps

- [x] Update `ListActivity` to read the account name from the launch intent.
    - `onCreate` and `onNewIntent` both read `EDIT_ITEM_ACCOUNTNAME`; `scopedAccountName` keeps the scope
      for the lifetime of the screen (rotation, returning from the editor), unlike `requestedAccountName`,
      which is cleared once applied.
- [x] Use the passed account name as the active account on startup.
    - `applyRequestedAccountToSpinner()` now targets the scoped name, so the existing spinner/refresh
      machinery (`getSelectedAccount()`, `TriggerSync`, `newNote`, hashtag filter) keeps working untouched.
- [x] Hide or remove the account spinner in `main.xml`:
    - [x] First step: hide it conditionally — `R.id.account_layout` is set to `View.GONE` in scoped mode
          (kept attached, because some `ImapNotes3.ShowMessage` calls are anchored on `accountSpinner`).
    - [ ] Later: remove it completely once account switching moves fully to `AccountListActivity`.
- [x] Show the account name as the toolbar title, similar to iOS Notes.
- [x] Add back/up navigation from the notes list to the account overview.
    - Up arrow (`android.R.id.home`) calls `finish()`; the overview is already underneath it. Hardware Back works too.
- [x] Keep the floating add-note button.
- [x] Keep the existing search and filter behavior for the selected account.
- [x] Decide what to do with “All accounts”:
    - [x] Short-term: kept as an overflow item (`R.id.all_accounts`, reusing the existing `all_accounts`
          string = “All”) that toggles between the scoped account and all accounts. It is a checkable item,
          visible only in scoped mode and only when more than one account exists — with a single account
          “All” would be identical and the legacy code disables the spinner anyway.
    - [ ] Long-term: remove “All accounts” if it conflicts with the Apple Notes-like account-first model.
- [x] Account management moves out of the notes screen in scoped mode: the “New account” action-bar icon is
      hidden (the overview owns adding accounts, and it leaves room for the long account name in the title).
- [x] If the scoped account is deleted while the notes screen exists, that screen finishes itself and lands on
      the overview (verified in logcat: `onAccountsUpdated: scoped account is gone`).
- [x] Preserve existing behaviors:
    - [x] swipe-to-refresh sync
    - [x] sort by date/title/color
    - [x] hashtag filtering
    - [x] note save-state guards when opening notes

### Acceptance criteria

- [x] Tapping account A opens only A’s notes.
- [x] The toolbar clearly indicates the selected account (title = account e-mail/name).
- [x] The back button returns to the account overview (verified: Up and Back both land on `AccountListActivity`).
- [x] Existing note operations still work for one account.
- [x] Share intents (`ACTION_SEND`) still open the legacy spinner mode: the account row stays visible and the
      title stays “ImapNotes3”.

### Notes / remaining quirks

- Side effect: with the Up arrow plus a long account name as the title, fewer action-bar icons fit —
  “Filter by HashTag” and “Refresh” collapse into the overflow menu. Refresh is still available by
  swipe-to-refresh.
- Unchanged pre-existing quirk: sharing into the app with zero accounts still re-opens account setup when
  you press Back there (the old `noAccountExists()` loop).
- Unchanged pre-existing wrinkle: `ListActivity` never unregisters its `OnAccountsUpdateListener`, so a
  finished instance keeps reacting to account changes (visible in logcat). Harmless today, but worth fixing
  in a later cleanup pass.

---

## Phase 4b — Read-only mailbox on the server (bug fix, found while testing Phase 4)

### Deliverable

When the IMAP mailbox cannot be written to, the notes are still downloaded and shown, and the user gets a
readable explanation instead of JavaMail’s `Cannot open in desired mode`.

### Steps

- [x] `SyncUtils.OpenRemoteIMAPNotesFolder(int mode)` ignored its `mode` argument and always opened the
      folder `READ_WRITE`, so a plain “Refresh” demanded write access and aborted the whole sync before any
      note was downloaded.
- [x] Honour the requested mode; reopen the folder when an open read-only folder has to be written to
      (JavaMail cannot change the mode of an open folder).
- [x] `ConnectToRemote` now opens the folder once for writing so a read-only mailbox is detected at the start
      of every sync; on `ReadOnlyFolderException` it falls back to `READ_ONLY` and remembers the condition
      (`SyncUtils.RemoteFolderIsReadOnly()`).
- [x] `SyncAdapter` collects the first failure worth showing (`RememberError`) — including the previously
      silent failures in `handleNewNotes` / `handleDeletedNotes` — and maps `ReadOnlyFolderException` /
      `IllegalWriteException` to a new string, `sync_error_read_only_folder`.
- [x] New string only (no existing keys touched), so Weblate translations are unaffected.
- [x] Found while verifying the above: the scoped notes list stayed **empty** even though the notes were
      downloaded. A `Spinner` that is not shown never fires `onItemSelected` (see `Spinner.setSelection`,
      which only calls `checkSelectionChanged()` when `isShown()`), and Phase 4 hides the account row — so
      nothing ever called `RefreshList()`. Fixed by refreshing explicitly from `updateAccountSpinner()` and
      `onCreateOptionsMenu()` (which also covers `RefreshList()`'s `actionMenu == null` guard).
- [x] `applyRequestedAccountToSpinner()` now also refreshes for the “All” state instead of doing nothing.

### Acceptance criteria

- [x] Refresh on a read-only mailbox downloads and lists the notes (verified on the emulator: `4544.eml`,
      `4541.eml`, … written after the `read-only` warning in logcat).
- [x] Opening an account shows its notes straight away, without a manual refresh (verified: 19 rows in the
      uiautomator dump right after `onCreate`).
- [x] The Snackbar says the folder is read-only and that changes cannot be uploaded, instead of
      “Cannot open in desired mode”.

### Notes / remaining quirks

- Unchanged pre-existing behaviour: a note that cannot be uploaded stays in `new/` and keeps the save state
  `SYNCING`, so tapping it reports “please wait until sync is complete” and does not open it. Same for a
  pending delete: the file in `deleted/` is dropped even when the server delete failed, so the note reappears
  on the next sync. Worth revisiting in a later pass (and relevant for the swipe-to-delete in Phase 5).
- Likely causes of a read-only mailbox are server-side: quota full, missing write rights, or a folder name that
  points at someone else’s/shared mailbox.

---

## Phase 4c — Notes screen: no “Accounts” menu entry, and a faster refresh

### Deliverable

The quick menu of the notes screen no longer offers “Accounts” (Up/Back already returns to the account
overview), and a refresh that finds nothing new no longer takes about twenty seconds.

### Steps

- [x] Dropped `@+id/account_list` from `res/menu/list.xml` and its case in
      `ListActivity.onOptionsItemSelected`. The `account_overview` string key is kept (it is the title of
      `AccountListActivity`), so no translation work is caused.
- [x] `SyncAdapter.onPerformSync` logs the duration of every step (`onPerformSync connect: … ms` …
      `onPerformSync total: … ms`), so the cost of a sync can be read from logcat instead of guessed at.
- [x] `SyncUtils.handleRemoteNotes` asked the server for the UID and the flags of every message one message
      at a time — one IMAP round trip per note. Both are now fetched for the whole folder in a single
      `FetchProfile`, and the headers of the notes that really have to be downloaded in one more command.
- [x] Local / remote UID comparisons use `HashSet` instead of `ArrayList.contains()` inside a loop.
- [x] The body that was just received is used directly for hashtag extraction
      (`ListActivity.searchHTMLTagsInHtml`) instead of reading back and re-parsing the `.eml` file that was
      written a moment before.
- [x] The pass that re-scans the tags of **every** stored note is skipped when the sync changed nothing —
      that scan was the bulk of an unchanged refresh. It still runs when notes were imported, uploaded or
      removed, and imported/saved notes already get their tags updated where they are handled.

### Measurements (emulator, 39 notes, read-only mailbox)

| step                                 | before         | after                                            |
| ------------------------------------ | -------------- | ------------------------------------------------ |
| connect + login + open folder        | ~4.5 s         | 2.8 s                                            |
| compare server with stored notes     | ~1 s           | 0.37 s                                           |
| re-scan tags of all notes            | ~15 s (always) | skipped; ~0.34 s (incl. disconnect) when it runs |
| **whole refresh that finds nothing** | **~20 s**      | **3.5 s**                                        |

### Acceptance criteria

- [x] Overflow menu of the notes screen offers Filter by HashTag, Refresh, Sort by …, Settings — no
      “Accounts”.
- [x] Up / Back still returns to the account overview.
- [x] The notes of the account are still listed, and the hashtag filter still finds the tags.
- [x] The duration of every sync step is in the log.

### Notes / remaining

- In the legacy unscoped mode (reached through a share intent) there is no menu route to the account
  overview any more; the account row with its edit / remove buttons is still there.
- The body of a note that is really new is still fetched one note at a time: JavaMail’s `FetchProfile` has
  no item for the content, so the first import of a large mailbox stays slow (measured 0.75–2 s per note).
  Turning on `mail.imap.pipelining` for the IMAP session would let those commands overlap, but it changes
  the conversation with the server and is known to upset some servers, so it was left alone.
- The list is still only redrawn once the whole sync has finished, which makes a refresh feel slower than
  it is.

---

## Phase 4d — Up from Settings landed on an unscoped notes list (bug fix, found while testing)

### Deliverable

Opening Settings from an account’s notes list and going back (or up) returns to that same notes list.

### Steps

- [x] `SettingsActivity.onSupportNavigateUp()`: the Up button used the parent declared in the manifest
      (`ListActivity`) and re-launched it with a bare intent (`CLEAR_TOP`), instead of simply leaving the
      Settings screen. Up now just finishes Settings — Settings is only ever opened from the notes list.
- [x] Root cause kept covered as well: `ListActivity.onNewIntent` reset `scopedAccountName` to the account
      name carried by the incoming intent, which is `null` for an intent that only wants to bring the
      running list back to the front — the list silently turned into the old unscoped main screen. An
      intent without an account name now keeps the current scope (and puts the extra back, so recreating
      the activity restores the same account).

### Acceptance criteria

- [x] Notes list → Settings → Up arrow: the scoped list is shown again (account name as title, no account
      spinner, notes listed).
- [x] Notes list → Settings → Back: same.
- [x] A bare `CLEAR_TOP` relaunch of `ListActivity` keeps the account it was opened for (verified through
      logcat: `onNewIntent: staying with the account …`).

### Notes / remaining

- The unscoped notes list (title “ImapNotes3”, account spinner) still exists on purpose: it is what a
  share intent (`ACTION_SEND`) opens, and the account row there keeps the edit / remove buttons. It is no
  longer reachable by navigating inside the app. Its removal is tracked by **Phase 9**.
- Settings can only be reached from the notes list, not from the account overview.

---

## Phase 5 — Add swipe-to-reveal actions to note rows

### Deliverable

User can swipe a note row sideways to reveal extra actions.

### Recommended approach

Use the existing `ListView` and custom row layout first. A full `RecyclerView` migration can be deferred.

### Steps

- [ ] Add a callback interface for row actions, for example:
    - `onNoteDeleteRequested(OneNote note)`
    - `onNoteEditRequested(OneNote note)` later
- [ ] Modify `note_element.xml` to contain:
    - [ ] A visible normal content area.
    - [ ] A hidden action panel behind or beside the content.
    - [ ] At least a delete button.
    - [ ] Optional buttons later: open, share, color, pin, archive.
- [ ] Add swipe gesture handling:
    - [ ] Simple `GestureDetector` + `translationX` on the row view.
    - [ ] Reveal action buttons when the row is translated far enough.
    - [ ] Snap back to closed state if swipe distance is too small.
- [ ] Handle action-button clicks:
    - [ ] Delete calls back into `ListActivity`.
    - [ ] `ListActivity` invokes the existing delete path:
        - `UpdateList(suid, null, null, accountName, UpdateThread.Action.Delete)`
- [ ] Add confirmation or undo:
    - [ ] Preferred: delete immediately and show Snackbar with Undo, closer to iOS.
    - [ ] Acceptable first pass: show confirmation dialog.
- [ ] Avoid opening the note editor when the row is already revealed:
    - [ ] If action panel is open, first tap closes it.
    - [ ] Only tap normal note area opens the editor.
- [ ] Ensure swipe does not conflict with swipe-to-refresh:
    - [ ] Horizontal swipe reveals actions.
    - [ ] Vertical swipe triggers sync.
- [ ] Ensure list recycling works:
    - [ ] Reset row translation when rows are reused.
    - [ ] Do not leave stale translated rows when scrolling.

### Acceptance criteria

- Swiping a note row horizontally reveals a visible delete action.
- Tapping delete removes/marks the note for deletion using the existing sync flow.
- Tapping the rest of the row still opens the note editor.
- Scrolling the list does not leave stuck swipe states.

---

## Phase 5b — Account rows: clickable shelf items with swipe actions

### Deliverable

An account row in the account overview is a “shelf” item: tap it to open that account’s notes list, and
slide it sideways to reveal action icons — a red delete and an edit button. Delete is removed from the
account edit screen (Phase 2c) and lives only here, iOS-style.

### Steps

- [ ] **Fix `SyncUtils.RemoveAccount` before exposing delete prominently.** It currently deletes **every**
      file under `ImapNotes3.GetRootDir()` — that wipes the local caches of _all_ other accounts, not just
      the one being removed. It must delete only the removed account’s directory
      (`GetAccountDir(accountName)`, incl. `new/` and `deleted/`), alongside the existing `ClearDb` and
      shared-prefs cleanup.
- [ ] In `AccountListActivity` / `account_element.xml`, make the whole row tappable to open the scoped
      notes list (currently only parts of the row react), and add a horizontal swipe/reveal: content
      slides sideways to reveal the action buttons underneath.
- [ ] Reveal two actions: edit (opens `AccountConfigurationActivity` with `Actions.EDIT_ACCOUNT`, i.e. the
      Phase 2c flow) and a red-tinted delete. For the red, do **not** reuse the note-background `@color/red`
      (it inverts in dark mode); add a dedicated `DestructiveActionColor` in both `values/colors.xml` and
      `values-night/colors-night.xml`, like `ResultOkColor` / `ResultFailedColor` in Phase 2b.
- [ ] Delete should ask for confirmation (or delete immediately + Snackbar with Undo, matching the note
      delete UX from Phase 5) and then call `AccountManager.removeAccount` + `SyncUtils.RemoveAccount`.
- [ ] Reuse the same reveal mechanism as Phase 5 for note rows where possible, so both lists behave alike
      (and share the recycling/reset pitfalls listed in the Risk notes).
- [ ] Only one row revealed at a time; tapping elsewhere closes the revealed row; vertical scrolling must
      not trigger the reveal.

### Acceptance criteria

- [ ] Tapping an account row opens that account’s notes list.
- [ ] Swiping a row sideways reveals edit and (red) delete.
- [ ] Edit takes the user through the Phase 2c form → Next → check → result flow.
- [ ] Delete removes the account, and after it the _other_ accounts still have their local note caches
      intact (this is the `RemoveAccount` fix).
- [ ] Scrolling the list does not leave rows stuck revealed.

### Notes / remaining

- `AccountListActivity` currently has an `editAccountButton`-style affordance per row; fold it into the
  revealed actions instead of a permanently visible button.
- The listActivity-side `editAccountButton` / account spinner belong to the unscoped list and stay until
  Phase 9.

---

## Phase 6 — Polish the account overview visually

### Deliverable

The account list feels closer to Apple Notes folders/accounts.

### Steps

- [ ] Add visual hierarchy:
    - [ ] Account name as primary text.
    - [ ] Server or username as secondary text.
    - [ ] Optional note count per account.
- [ ] Use consistent spacing and row height.
- [ ] Add subtle dividers or card spacing.
- [ ] Add an “Add account” row or FAB at the bottom.
- [ ] Add icons only if they do not overcomplicate the screen.
- [ ] Ensure light and dark themes work.
- [ ] Keep text colors compatible with `values-night`.
- [ ] Use `Material` components where already used by project, such as FAB and cards.

### Acceptance criteria

- Account overview looks less like a utility screen and more like a primary Notes screen.
- Empty state is clean and obvious.
- Dark mode remains readable.

---

## Phase 7 — Polish the notes overview visually

### Deliverable

The account’s note list feels closer to Apple Notes’ folder view.

### Steps

- [ ] Replace the account spinner area with a simple top bar:
    - [ ] Back arrow.
    - [ ] Account/folder name.
    - [ ] Search action.
- [ ] Keep floating `+` note button.
- [ ] Make note rows visually similar:
    - [ ] Bold title.
    - [ ] Smaller timestamp line.
    - [ ] Optional first-line snippet beneath title.
- [ ] Consider using note background color as a subtle row background.
- [ ] Consider grouping by date sections later.
- [ ] Keep sort options accessible, but less prominent than iOS-style actions.

### Acceptance criteria

- Notes list opens as a clean account-scoped folder view.
- Note titles are visually prioritized.
- The account name is clear in the toolbar.

---

## Phase 8 — Future note editor theming

This is intentionally vague until later.

### Possible follow-up TODOs

- [ ] Make note editor screen resemble Apple Notes:
    - [ ] Cleaner title/date header.
    - [ ] Less prominent toolbar.
    - [ ] Simpler formatting toolbar.
    - [ ] Background color picker matching iOS-style sheet.
- [ ] Decide whether `RichEditor` should keep current formatting layout.
- [ ] Add editor visual polish without changing sync format.
- [ ] Keep Apple Notes wire format intact.

---

## Phase 9 — Get rid of the unscoped notes list (share intents)

### Deliverable

`ListActivity` only ever shows one account’s notes. The legacy “all accounts” list — the one with the
account spinner that share intents currently open — is replaced by a proper routing step, so it can be
deleted.

Why this exists: everything else in the app is now account-scoped, but `ACTION_SEND` / `SEND_MULTIPLE`
/ `SEARCH` intent filters are still registered on the unscoped `ListActivity` (see the manifest comment
“Share and search entry points stay here until they can be forwarded from `AccountListActivity`”).
Every time a share intent fires, the app reopens that legacy screen. This needs a new “which account?”
reference step instead.

### Steps

- [ ] Move the share / search `intent-filter`s from `.ListActivity` to `.AccountListActivity` in
      `AndroidManifest.xml` (keep the `intent_save_as_note` / `intent_import_note` labels).
- [ ] In `AccountListActivity`, detect a share intent in `onCreate` / `onNewIntent` and decide the target
      account — the “new reference system”:
    - [ ] Zero accounts: start account setup first, then come back to the share (today: Back from setup
          re-opens setup in a loop — decide the intended UX here).
    - [ ] One account: use it directly.
    - [ ] More than one: use the last-opened account (Phase 3’s `LAST_ACCOUNT_NAME` pref), or show a
          lightweight account picker sheet.
- [ ] Forward the payload (the incoming intent’s `EXTRA_TEXT` / `EXTRA_STREAM` / `EXTRA_EMAIL`, plus
      `FLAG_GRANT_READ_URI_PERMISSION`) into the scoped `ListActivity` via the existing
      `EDIT_ITEM_ACCOUNTNAME` extra, so the existing `Check_Action_Send()` handling (Insert / Update via
      `UpdateList` and `UpdateThread.Action.Insert`) runs unchanged under a scope.
- [ ] Then delete unscoped mode from `ListActivity`:
    - [ ] Drop `account_layout` / `accountSpinner` / `editAccountButton` from `main.xml` and their wiring
          (they are already hidden in scoped mode since Phase 4).
    - [ ] Drop `showingAllAccounts` and the spinner position 0 = “all” special case in `getSpinnerPos`.
    - [ ] Drop the `all_accounts` menu item and its string, and `select_one_account` if unused after that.
    - [ ] Drop `ListActivity.Check_Action_Send()`’s cross-account lookup (`GetAccountNameForItem`) and
          `ResultCodeRemoveAccount` plumbing that Phase 2c/5b leave orphaned.
- [ ] Decide the fate of “All accounts” as a concept: with the unscoped list gone there is no screen that
      shows notes of every account at once. Keep the menu item and have it mean something else, or remove
      it (removing a string key costs Weblate re-translation; keeping it unused is cheaper).

### Acceptance criteria

- [ ] Sharing text / an image / an rfc822 message from another app lands in the chosen account’s notes
      list with the new note visible after the upload, and never shows the spinner list.
- [ ] Sharing with zero accounts leads to account setup without a Back-loop, then to a note.
- [ ] `grep -rn "accountSpinner\|all_accounts\|showingAllAccounts"` finds nothing in code or layouts.
- [ ] Normal navigation (overview → notes → back) is unaffected.

### Notes / remaining

- This is the cleanup phase for things deliberately left behind by Phases 2b/4/4d: the legacy unscoped
  list still exists only as the share-intent entry point and as the holder of the account spinner/edit
  button.
- `SEARCH` filter is registered on `ListActivity` but the app never triggers system search; fold it into
  the same routing or drop it after checking.

---

## Suggested implementation order

Pick them one by one:

1. [x] `TODO.md` approved.
2. [x] Phase 1: create non-launcher account overview screen manually reachable for testing.
3. [x] Phase 2: make it launcher and clean up navigation.
4. [x] Phase 2b: turn account setup into a form + result flow.
5. [x] Phase 4: hide the account spinner and scope the notes screen to one account (done early — opening
       an account from the overview needed a notes screen that reads as that account’s list).
6. [x] Phase 4b: handle a read-only mailbox and fix the empty scoped list (found while testing Phase 4).
7. [x] Phase 4c: remove the “Accounts” menu entry and speed up a refresh.
8. [x] Phase 4d: Up from Settings returned an unscoped notes list (found while testing).
9. Phase 3: persist last opened account.
10. Phase 2c: account edit uses the add flow (check + result page).
11. Phase 5: add swipe actions to note rows.
12. Phase 5b: account rows as shelf items with swipe actions (fix `SyncUtils.RemoveAccount` first).
13. Phase 6: polish account overview.
14. Phase 7: polish notes overview.
15. Phase 9: delete the unscoped notes list once share intents are routed.
16. Later: editor theming pass.

---

## Risk notes

- The share-intent flow is currently tied to `ListActivity`. Be careful when moving launcher filters.
  Phase 9 covers re-routing it before the unscoped list is deleted.
- `SyncUtils.RemoveAccount` currently deletes _all_ local files under the app root dir, not just the
  removed account’s. Fix that before making delete easy to reach (Phase 5b).
- The existing `ListActivity` uses static state and account listeners; moving navigation around should not break account updates.
- Swipe behavior on `ListView` can fight with recycling and swipe-to-refresh.
- Deleting notes must continue to route through the existing delete/sync mechanism so server state stays correct.
- New strings should be added only in base English `strings.xml`; translations should come through Weblate.
