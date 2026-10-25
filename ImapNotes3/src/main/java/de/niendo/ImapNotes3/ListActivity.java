/*
 * Copyright (C) 2022-2026 - Peter Korf <peter@niendo.de>
 * Copyright (C)      2023 - woheller69
 * Copyright (C)         ? - kwhitefoot
 * Copyright (C)      2016 - Martin Carpella
 * Copyright (C) 2014-2015 - c0238
 * Copyright (C) 2014-2015 - nb
 * and Contributors.
 *
 * This file is part of ImapNotes3.
 *
 * ImapNotes3 is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package de.niendo.ImapNotes3;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.view.menu.MenuBuilder;
import androidx.appcompat.widget.SearchView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ListView;

import de.niendo.ImapNotes3.Data.NotesDb;
import de.niendo.ImapNotes3.Data.OneNote;
import de.niendo.ImapNotes3.Miscs.BackupRestore;
import de.niendo.ImapNotes3.Miscs.HtmlNote;
import de.niendo.ImapNotes3.Miscs.SwipeRevealRow;
import de.niendo.ImapNotes3.Miscs.SyncThread;
import de.niendo.ImapNotes3.Miscs.UpdateThread;
import de.niendo.ImapNotes3.Miscs.Utilities;
import de.niendo.ImapNotes3.Miscs.ZipUtils;
import de.niendo.ImapNotes3.Sync.SyncUtils;
import eltos.simpledialogfragment.SimpleDialog;
import eltos.simpledialogfragment.list.SimpleListDialog;
import org.woheller69.freeDroidWarn.FreeDroidWarn;

import org.jsoup.Jsoup;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ListActivity extends AppCompatActivity implements BackupRestore.INotesRestore, Filterable, SimpleDialog.OnDialogResultListener, UpdateThread.FinishListener {
    private static final int SEE_DETAIL = 2;
    public static final int DELETE_BUTTON = 3;
    private static final int NEW_BUTTON = 4;
    private static final int EDIT_BUTTON = 6;
    private static final int SELECT_ARCHIVE_FOR_RESTORE = 8;
    public static final int EDIT_SETTINGS = 9;

    public static final int ResultCodeSuccess = 1;
    public static final int ResultCodeMakeArchive = 2;
    public static final int ResultCodeRestoreArchive = 3;
    public static final int ResultCodeNeutral = 0;
    public static final int ResultCodeError = -1;

    public static final String EDIT_ITEM_NUM_IMAP = "EDIT_ITEM_NUM_IMAP";
    public static final String EDIT_ITEM_COLOR = "EDIT_ITEM_COLOR";
    public static final String EDIT_ITEM_ACCOUNTNAME = "EDIT_ITEM_ACCOUNTNAME";
    public static final String SYNCINTERVAL = "SYNCINTERVAL";
    public static final String CHANGED = "CHANGED";
    public static final String SYNCED = "SYNCED";
    public static final String REFRESH_TAGS = "REFRESH_TAGS";
    public static final String SYNCED_ERR_MSG = "SYNCED_ERR_MSG";
    private static final String DELETE_ITEM_NUM_IMAP = "DELETE_ITEM_NUM_IMAP";
    private static final String SORT_BY_DATE = "SORT_BY_DATE";
    private static final String SORT_BY_TITLE = "SORT_BY_TITLE";
    private static final String SORT_BY_COLOR = "SORT_BY_COLOR";
    private static final String DLG_FILTER_HASHTAG = "DLG_FILTER_HASHTAG";

    private Intent intentActionSend;
    // Set when the list was opened for a single account from AccountListActivity.  The list only ever
    // shows one account, so this is the scope of the whole screen.  It outlives the launch intent so
    // the scope survives a rotation or returning from the note editor.
    @Nullable
    private String scopedAccountName;
    private ArrayList<OneNote> noteList;
    private NotesListAdapter listToView;
    private static final String AUTHORITY = Utilities.PackageName + ".provider";
    private static AccountManager accountManager;
    @Nullable
    private static NotesDb storedNotes = null;
    private static List<String> accountList;
    private static Menu actionMenu;
    private static CharSequence mFilterString = "";
    static String[] hashFilter;
    private static ArrayList<String> hashFilterSelected = new ArrayList<>();
    private ContentObserver mObserver;
    private OnAccountsUpdateListener accountsUpdateListener;
    private SwipeRefreshLayout swipeLayout;
    // Ensure that we never have to check for null by initializing reference.
    @NonNull
    private static Account[] accounts = new Account[0];
    private static final String TAG = "IN_Listactivity";
    //@Nullable
    private ListView listview;
    private AsyncTask updateThread;

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mObserver);
        ListActivity.accountManager.removeOnAccountsUpdatedListener(accountsUpdateListener);
    }

    public static ArrayList<String> getAccountList() {
        ArrayList<String> accounts = new ArrayList<>();
        for (Account mAccount : ListActivity.accounts) {
            accounts.add(mAccount.name);
        }
        return accounts;
    }

    public static List<String> searchHTMLTags(@NonNull File nameDir, @NonNull String uid, String searchTerm, boolean useRegex) {
        String html = HtmlNote.GetNoteFromMessage(SyncUtils.ReadMailFromNoteFile(nameDir, uid)).text;
        return searchHTMLTagsInHtml(html, searchTerm, useRegex);
    }

    /**
     * Looks for hashtags in the body of a note that is already in memory.  Reading a note file back
     * and parsing it again right after it was written costs a noticeable amount of time for every
     * note, so importing uses this variant with the body that was received from the server.
     *
     * @param html       Body of the note as HTML
     * @param searchTerm Hashtag pattern, or a plain string when {@code useRegex} is false
     * @param useRegex   Whether {@code searchTerm} is a regular expression
     * @return List of the hashtags found in the body
     */
    public static List<String> searchHTMLTagsInHtml(@NonNull String html, String searchTerm, boolean useRegex) {
        // Compile the regular expression pattern if necessary
        Pattern pattern;
        List<String> retVal = new ArrayList<String>();
        if (useRegex) {
            try {
                pattern = Pattern.compile(searchTerm);
            } catch (PatternSyntaxException e) {
                Log.e(TAG, "searchHTMLTags compile failed:", e);
                return retVal;
            }
        } else {
            pattern = Pattern.compile(Pattern.quote(searchTerm), Pattern.CASE_INSENSITIVE);
        }

        Matcher matcher = pattern.matcher(Jsoup.parse(html).text());
        while (matcher.find()) {
            retVal.add(matcher.group());
        }
        return retVal;
    }


    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        if (which == BUTTON_NEGATIVE) return false;
        switch (dialogTag) {
            case DLG_FILTER_HASHTAG:
                if (which == BUTTON_POSITIVE) {
                    hashFilterSelected = extras.getStringArrayList(SimpleListDialog.SELECTED_LABELS);
                    if (hashFilterSelected.isEmpty())
                        hashFilter = null;
                    else {
                        hashFilter = new String[hashFilterSelected.size()];
                        hashFilterSelected.toArray(hashFilter);
                    }
                }
                if (which == BUTTON_NEUTRAL) {
                    hashFilter = null;
                    hashFilterSelected.clear();
                }
                RefreshList();
                return true;
        }
        return false;
    }


    @Override
    protected void onNewIntent(@NonNull Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        String accountName = intent.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
        if ((accountName == null) && IsAccountScoped()) {
            // An intent that does not name an account (the Up button of another activity brings this
            // instance to the front again) must not drop out of the account the list was opened for.
            // The extra is put back so that recreating the activity restores the same account.
            Log.d(TAG, "onNewIntent: staying with the account " + scopedAccountName);
            intent.putExtra(EDIT_ITEM_ACCOUNTNAME, scopedAccountName);
        }
        setIntent(intent);
        scopedAccountName = intent.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
        if (actionMenu != null) {
            UpdateScopeUi();
            RefreshScopedList();
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        Intent startIntent = getIntent();
        if (startIntent != null) {
            scopedAccountName = startIntent.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
        }
        setContentView(R.layout.main);
        getSupportActionBar().setElevation(0); // or other
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getColor(R.color.ActionBgColor)));

        ImapNotes3.setContent(findViewById(android.R.id.content));

        ListActivity.accountList = new ArrayList<>();
        ListActivity.accountManager = AccountManager.get(getApplicationContext());
        accountsUpdateListener = new AccountsUpdateListener();
        ListActivity.accountManager.addOnAccountsUpdatedListener(
                accountsUpdateListener, null, true);

        this.noteList = new ArrayList<>();

        this.listToView = new NotesListAdapter(
                this,
                this.noteList,
                new String[]{OneNote.TITLE, OneNote.DATE, OneNote.SNIPPET},
                new int[]{R.id.noteTitle, R.id.noteDate, R.id.noteSnippet},
                OneNote.BGCOLOR);
        // The swipe actions of a note row are handled here, in the same way the editor does it.
        this.listToView.setRowActionListener(this::ConfirmAndDeleteNote);

        listview = findViewById(R.id.notesList);
        listview.setAdapter(this.listToView);

        listview.setTextFilterEnabled(true);

        storedNotes = NotesDb.getInstance(getApplicationContext());

        // When item is clicked, we go to NoteDetailActivity
        listview.setOnItemClickListener((parent, widget, selectedNote, rowId) -> {
            Log.d(TAG, "onItemClick");
            // If a row is slid open, a tap anywhere closes it instead of opening the editor.
            if (SwipeRevealRow.concealRevealedRow()) {
                Log.d(TAG, "onItemClick: closed the revealed row");
                return;
            }
            Intent toDetail;

            String saveState = storedNotes.GetSaveState(noteList.get(selectedNote).GetUid(), noteList.get(selectedNote).GetAccount());

            if (saveState.equals(OneNote.SAVE_STATE_SAVING)) {
                ImapNotes3.ShowMessage(R.string.save_wait_necessary, listview, 3);
                return;
            } else if (saveState.equals(OneNote.SAVE_STATE_SYNCING)) {
                ImapNotes3.ShowMessage(R.string.sync_wait_necessary, listview, 3);
                return;
            } else if (saveState.equals(OneNote.SAVE_STATE_FAILED)) {
                ImapNotes3.ShowMessage(R.string.save_note_failed, listview, 3);
                Log.i(TAG, "message was not correctly saved");
                return;
            }
            if (intentActionSend != null)
                // FIXME StrictMode policy violation: android.os.strictmode.UnsafeIntentLaunchViolation: Launch of unsafe intent: Intent
                toDetail = intentActionSend;
            else
                toDetail = new Intent(widget.getContext(), NoteDetailActivity.class);
            toDetail.putExtra(NoteDetailActivity.selectedNote, (OneNote) parent.getItemAtPosition(selectedNote));
            toDetail.putExtra(NoteDetailActivity.ActivityType, NoteDetailActivity.ActivityTypeEdit);
            startActivityForResult(toDetail, SEE_DETAIL);
            setIntentAsProcessed();
            Log.d(TAG, "onItemClick, back from detail.");
        });

        // Getting SwipeContainerLayout
        swipeLayout = findViewById(R.id.swipeContainer);
        // Adding Listener
        swipeLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                swipeLayout.setRefreshing(false);
                TriggerSync(true);
            }
        });

        mObserver = new ContentObserver(new Handler(Looper.getMainLooper())) {
            public void onChange(boolean selfChange) {
                Log.d(TAG, "ContentObserver.OnChange");
                swipeLayout.setRefreshing(false);
                if (selfChange || ImapNotes3.intent == null) return;
                String accountName = ImapNotes3.intent.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                boolean isChanged = ImapNotes3.intent.getBooleanExtra(CHANGED, false);
                // boolean isSynced = ImapNotes3.intent.getBooleanExtra(SYNCED, false);
                String errorMessage = ImapNotes3.intent.getStringExtra(SYNCED_ERR_MSG);
                // SyncInterval syncInterval = SyncInterval.from(ImapNotes3.intent.getStringExtra(SYNCINTERVAL));
                if (accountName.equals(getSelectedAccountName())) {
                    if ((errorMessage != null) && !errorMessage.isEmpty()) {
                        ImapNotes3.ShowMessage(errorMessage, listview, 5);
                    }
                }
                if (isChanged) RefreshList();
            }
        };
        getContentResolver().registerContentObserver(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/"), false, mObserver);

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);

        if (!IsAccountScoped()) {
            // Every entry point names an account now (the account overview, or a share forwarded by
            // it).  Without one this screen has nothing to show, and the overview owns that state.
            Log.w(TAG, "onCreate: the intent does not name an account");
            finish();
        }
    }

    void handleSendMultipleImages(@NonNull Intent intent) {
        Log.d(TAG, "handleSendMultipleImages");
        ArrayList<Uri> messageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        String accountName = getSelectedAccountName();
        if (messageUris != null) {
            ImapNotes3.ShowMessage(R.string.importing, listview, 2);
            UpdateList(messageUris, accountName);
        }
    }

    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        UpdateScopeUi();
        RefreshScopedList();
        Check_Action_Send();
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        savePreferences();
        if (!(updateThread == null)) {
            // for some reason this helps...
            synchronized (ImapNotes3.MainLock) {
                if (updateThread.getStatus() == AsyncTask.Status.RUNNING) {
                    Log.d(TAG, "onPause RUNNING");
                }
            }
        }
    }

    @Override
    public void onPostCreate(final Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        Log.d(TAG, "onPostCreate");
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        Log.d(TAG, "onSaveInstanceState");
        super.onSaveInstanceState(outState);
        savePreferences();
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        Log.d(TAG, "onRestoreInstanceState");
    }

    private void savePreferences() {
        SharedPreferences.Editor preferences = getApplicationContext().getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit();
        if (actionMenu == null) return;
        preferences.putBoolean(SORT_BY_DATE, actionMenu.findItem(R.id.sort_date).isChecked());
        preferences.putBoolean(SORT_BY_TITLE, actionMenu.findItem(R.id.sort_title).isChecked());
        preferences.putBoolean(SORT_BY_COLOR, actionMenu.findItem(R.id.sort_color).isChecked());
        preferences.apply();
    }

    private void RefreshList() {
        RefreshList(getSelectedAccountName());
    }

    private void RefreshList(String accountName) {
        if(actionMenu == null)
            return;
        if (accountName.isEmpty()) {
            // An empty name would ask the database for the notes of every account, which this screen
            // does not show any more.
            Log.w(TAG, "RefreshList: no account to refresh");
            return;
        }
        Log.d(TAG, "RefreshList: ");
        listToView.setSortOrder(getSortOrder());
        synchronized (ImapNotes3.MainLock) {
            new SyncThread(
                    accountName,
                    noteList,
                    listToView,
                    getSortOrder(),
                    hashFilter,
                    mFilterString,
                    // FIXME: this. ?
                    getApplicationContext()).execute();
        }
    }

    private void UpdateList(
            String suid,
            String noteBody,
            String bgColor,
            String accountName,
            UpdateThread.Action action) {
        synchronized (ImapNotes3.MainLock) {
                updateThread = new UpdateThread(accountName,
                        this,
                        noteList,
                        listToView,
                        R.string.updating_notes_list,
                        suid,
                        noteBody,
                        bgColor,
                        // FIXME: this. ?
                        getApplicationContext(),
                        action).execute();
            }
    }

    /**
     * Asks before a note is deleted, and then uses the same delete path as the button of the editor.
     * A Snackbar with Undo is not offered: deleting moves the local mail file to the deleted
     * directory and updates the database, which cannot simply be rolled back.
     */
    private void ConfirmAndDeleteNote(@NonNull OneNote note) {
        Log.d(TAG, "ConfirmAndDeleteNote: " + note.GetUid() + " " + note.GetAccount());
        // The row closes as the action is tapped, so no row stays open behind the dialog.
        SwipeRevealRow.concealRevealedRow();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_note)
                .setMessage(R.string.are_you_sure_you_wish_to_delete_the_note)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.yes, (dialog, which) ->
                        UpdateList(note.GetUid(), null, null, note.GetAccount(),
                                UpdateThread.Action.Delete))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void UpdateList(
            ArrayList<Uri> uris,
            String accountName) {
        synchronized (ImapNotes3.MainLock) {
            updateThread = new UpdateThread(accountName,
                    this,
                    noteList,
                    listToView,
                    R.string.updating_notes_list,
                    uris,
                    getApplicationContext()).execute();
        }
    }

    @Override
    public void onFinishPerformed(Boolean result, String ErrText) {
        if (result) {
            TriggerSync(false);
        }
        if (!ErrText.isEmpty()) {
            ImapNotes3.ShowMessage(ErrText, listview, 30);
        }
    }

    @SuppressLint("RestrictedApi")
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        super.onCreateOptionsMenu(menu);
        actionMenu = menu;
        getMenuInflater().inflate(R.menu.list, menu);

        MenuBuilder m = (MenuBuilder) menu;
        m.setOptionalIconsVisible(true);

        MenuItem menuItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setQueryHint(getText(R.string.search_any_keyword));
        SearchView.OnQueryTextListener textChangeListener = new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextChange(String newText) {
                // FIXME THREAD!
                // search only if user pushes glases or enter .. its faster
                // this is your adapter that will be filtered
                // mFilterString = newText;
                // listToView.getFilter().filter(newText);
                if ((newText == null) || (newText.isEmpty())) {
                    mFilterString = "";
                    listToView.ResetFilterData(noteList);
                    RefreshList();
                }
                return true;
            }

            @Override
            public boolean onQueryTextSubmit(String query) {
                // this is your adapter that will be filtered
                mFilterString = query;
                listToView.getFilter().filter(query);
                return true;
            }
        };
        // restore List and Filter after closing search
        searchView.setOnCloseListener(() -> {
            mFilterString = "";
            this.listToView.ResetFilterData(noteList);
            RefreshList();
            return true;
        });

        menuItem.setOnActionExpandListener(new MenuItem.OnActionExpandListener() {
            @Override
            public boolean onMenuItemActionExpand(MenuItem item) {
                //searchView.requestFocus(); - doesn't work properly
                searchView.setIconifiedByDefault(false);
                searchView.setIconified(false);
                mFilterString = "";
                listToView.getFilter().filter("");
                return true;
            }

            @Override
            public boolean onMenuItemActionCollapse(MenuItem item) {
                mFilterString = "";
                searchView.clearFocus();
                listToView.ResetFilterData(noteList);
                return true;
            }
        });

        // searchView.requestFocus(); - doesn't work properly
        searchView.setIconifiedByDefault(false);
        searchView.setIconified(false);

        searchView.setOnQueryTextListener(textChangeListener);
        // load values from disk
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE);

        if (preferences.getBoolean(SORT_BY_TITLE, false))
            actionMenu.findItem(R.id.sort_title).setChecked(true);
        else if (preferences.getBoolean(SORT_BY_COLOR, false))
            actionMenu.findItem(R.id.sort_color).setChecked(true);
        else
            actionMenu.findItem(R.id.sort_date).setChecked(true);


        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> newNote());

        UpdateScopeUi();
        // RefreshList() needs the menu to exist, so the list is filled here as well as in onResume().
        RefreshScopedList();
        return true;
    }

    private String getSortOrder() {
        if (actionMenu.findItem(R.id.sort_title).isChecked())
            return "UPPER(" + OneNote.TITLE + ") ASC";
        if (actionMenu.findItem(R.id.sort_color).isChecked()) return OneNote.BGCOLOR + " ASC";

        return OneNote.DATE + " DESC";
    }

    private void Check_Action_Send() {
        Log.d(TAG, "Check_Action_Send");
        // Get intent, action and MIME type
        Intent intent = getIntent();
        String action = intent.getAction();
        Log.d(TAG, "Check_Action_Send:" + action);

        intentActionSend = null;
        if (intent.getBooleanExtra(NoteDetailActivity.ActivityTypeProcessed,false)) {
            Log.d(TAG, "Check_Action_Send: Intent already processed");
            return;
        }
        if (!IsAccountScoped()) {
            // AccountListActivity always names the account a share goes to before it forwards one.
            Log.w(TAG, "Check_Action_Send: the list is not scoped to an account");
            return;
        }

        if (Intent.ACTION_SEND.equals(action)) {
            intentActionSend = (Intent) intent.clone();
            intentActionSend.setClass(this, NoteDetailActivity.class);
            intentActionSend.setFlags(0);
            intentActionSend.putExtra(NoteDetailActivity.ActivityType, NoteDetailActivity.ActivityTypeAddShare);

            ImapNotes3.ShowAction(listview, R.string.insert_as_new_note, R.string.ok, 0,
                    () -> {
                        intentActionSend.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, getSelectedAccountName());
                        startActivityForResult(intentActionSend, ListActivity.NEW_BUTTON);
                        setIntentAsProcessed();
                    });
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if ("message/rfc822".equals(intent.getType())) {
                Intent finalIntent = intent;
                ImapNotes3.ShowAction(listview, R.string.insert_as_new_note, R.string.ok, 0,
                        () -> {
                            try {
                                handleSendMultipleImages(finalIntent);
                            } catch (Exception e) {
                                Log.e(TAG, "handleSendMultipleImages faled", e);
                            }
                        });
            }
        }
    }

    synchronized private void TriggerSync(boolean refreshTags) {
        Log.d(TAG, "TriggerSync");
        Account mAccount = getSelectedAccount();
        if (mAccount == null) {
            Log.w(TAG, "TriggerSync: Account==null");
            return;
        }
        swipeLayout.setRefreshing(true);
        Bundle settingsBundle = new Bundle();
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        settingsBundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
        settingsBundle.putBoolean(REFRESH_TAGS, refreshTags);
        ContentResolver.cancelSync(mAccount, AUTHORITY);
        ContentResolver.requestSync(mAccount, AUTHORITY, settingsBundle);
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                // Up from an account-scoped notes list goes back to the account overview.
                Log.d(TAG, "onOptionsItemSelected: home");
                finish();
                return true;
            case R.id.refresh:
                TriggerSync(true);
                return true;
            case R.id.newnote:
                newNote();
                return true;
            case R.id.sort_date:
            case R.id.sort_title:
            case R.id.sort_color: {
                item.setChecked(true);
                RefreshList();
                return true;
            }
            case R.id.filter_by_hash: {
                NotesDb storedNotes = NotesDb.getInstance(getApplicationContext());
                List<String> tags = storedNotes.GetTags("", getSelectedAccountName());
                List<Integer> positions = new ArrayList<>();
                for (String tag : tags) {
                    if (hashFilterSelected.contains(tag))
                        positions.add(tags.indexOf(tag));
                }
                String[] tagArray = new String[tags.size()];
                tags.toArray(tagArray);
                SimpleListDialog.build()
                        .title(R.string.filter_by_hash)
                        .choiceMode(SimpleListDialog.MULTI_CHOICE)
                        .filterable(true)
                        .choicePreset(positions)
                        .items(tagArray)
                        .filterable(true)
                        .neg(R.string.cancel)
                        .neut(R.string.reset_filter)
                        .show(this, DLG_FILTER_HASHTAG);
                return true;
            }
            case R.id.settings: {
                Intent res = new Intent(ListActivity.this, SettingsActivity.class);
                startActivityForResult(res, ListActivity.EDIT_SETTINGS);
                return true;
            }
            default:
                return super.onOptionsItemSelected(item);
        }
    }


    public void openFileSelector() {
        Context context = ImapNotes3.getAppContext();

        if (!ZipUtils.checkPermissionStorage(context)) {
            ZipUtils.requestPermission(this);
        } else {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.setType("application/zip");
            intent.addCategory(Intent.CATEGORY_OPENABLE);

            try {
                startActivityForResult(
                        Intent.createChooser(intent, "Select a ZIP file"),
                        SELECT_ARCHIVE_FOR_RESTORE);
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "ActivityNotFoundException: ", e);
                ImapNotes3.ShowMessage("Please install a file manager.", listview, 3000);
            }
        }
    }

    /**
     * The account whose notes are shown.  This screen has no account selection of its own any more,
     * so the name it was opened with is the answer.
     */
    @NonNull
    private String getSelectedAccountName() {
        return (scopedAccountName == null) ? "" : scopedAccountName;
    }

    /**
     * The {@code Account} behind {@link #getSelectedAccountName()}, needed to request a sync.
     */
    @Nullable
    private Account getSelectedAccount() {
        if (!IsAccountScoped()) {
            return null;
        }
        return getAccountFromName(scopedAccountName);
    }

    /**
     * Points the list at the scoped account and loads its notes.  Nothing reports a selection change
     * any more, so this is called whenever the scope or the set of accounts changed.
     */
    private void RefreshScopedList() {
        if (!IsAccountScoped() || listToView == null) {
            return;
        }
        if (!ListActivity.accountList.contains(scopedAccountName)) {
            // The accounts are collected asynchronously, so the scoped account may simply not be
            // known here yet.  AccountsUpdateListener calls this again once it is.
            Log.w(TAG, "RefreshScopedList: account is not available yet: " + scopedAccountName);
            return;
        }
        listToView.setAccountName(scopedAccountName);
        RefreshList(scopedAccountName);
    }

    /**
     * True when this screen belongs to one account.  Every way of opening it names an account, so a
     * list without a scope has nothing to show.
     */
    private boolean IsAccountScoped() {
        return scopedAccountName != null && !scopedAccountName.isEmpty();
    }

    /**
     * Adapts the action bar to the scoped list: an Up button towards the account overview, and the
     * account name as the title.
     */
    private void UpdateScopeUi() {
        ActionBar bar = getSupportActionBar();
        if (bar != null) {
            bar.setDisplayHomeAsUpEnabled(true);
            bar.setHomeButtonEnabled(true);
        }
        if (IsAccountScoped()) {
            setTitle(scopedAccountName);
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        switch (requestCode) {
            case ListActivity.SEE_DETAIL:
                // Returning from NoteDetailActivity
                if (resultCode == ListActivity.DELETE_BUTTON) {
                    // Delete Message asked for
                    // String suid will contain the Message Imap UID to delete
                    String suid = data.getStringExtra(DELETE_ITEM_NUM_IMAP);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList(suid, null, null, accountName, UpdateThread.Action.Delete);
                }
                if (resultCode == ListActivity.EDIT_BUTTON) {
                    String txt = ImapNotes3.AvoidLargeBundle;  //data.getStringExtra(EDIT_ITEM_TXT);
                    String suid = data.getStringExtra(EDIT_ITEM_NUM_IMAP);
                    String bgcolor = data.getStringExtra(EDIT_ITEM_COLOR);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList(suid, txt, bgcolor, accountName, UpdateThread.Action.Update);
                }
                break;
            case ListActivity.NEW_BUTTON:
                // Returning from NewNoteActivity
                if (resultCode == ListActivity.EDIT_BUTTON) {
                    String txt = ImapNotes3.AvoidLargeBundle; //data.getStringExtra(EDIT_ITEM_TXT);
                    String bgcolor = data.getStringExtra(EDIT_ITEM_COLOR);
                    String accountName = data.getStringExtra(EDIT_ITEM_ACCOUNTNAME);
                    UpdateList("", txt, bgcolor, accountName, UpdateThread.Action.Insert);
                }
                break;
            case ListActivity.SELECT_ARCHIVE_FOR_RESTORE:
                if (resultCode == Activity.RESULT_OK) {
                    if (data != null) {
                        Uri uri = data.getData();
                        if (uri != null) {
                            BackupRestore backupRestore = new BackupRestore(uri, new ArrayList<>(accountList));
                            backupRestore.show(getSupportFragmentManager(), "restore_dialog");
                        }
                    }
                }
                break;
            case ListActivity.EDIT_SETTINGS:
                switch (resultCode) {
                    case ResultCodeMakeArchive:
                        BackupRestore.CreateArchive(this, getSelectedAccountName());
                        break;
                    case ResultCodeRestoreArchive:
                        openFileSelector();
                        break;
                    default:
                        RefreshList();
                }
                break;
            default:
                Log.e(TAG, "onActivityResult: unknown result code");
        }
    }

    ;

    public Account getAccountFromName(String accountName) {
        for (Account account : ListActivity.accounts
        ) {
            if (account.name.equals(accountName))
                return account;
        }
        Log.e(TAG, "getAccountFromName: no account found: " + accountName);
        return null;
    }

    private void newNote() {
        Intent toNew;

        if (intentActionSend != null)
            toNew = intentActionSend;
        else
            toNew = new Intent(this, NoteDetailActivity.class);
        toNew.putExtra(NoteDetailActivity.ActivityType, NoteDetailActivity.ActivityTypeAdd);
        toNew.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, getSelectedAccountName());
        startActivityForResult(toNew, ListActivity.NEW_BUTTON);
    }

    public void setIntentAsProcessed() {
        Intent intent = getIntent();
        intent.putExtra(NoteDetailActivity.ActivityTypeProcessed, true);
        if (intentActionSend != null)
           intentActionSend.putExtra(NoteDetailActivity.ActivityTypeProcessed, true);
    }

    @Nullable
    @Override
    public Filter getFilter() {
        return null;
    }

    @Override
    public void onSelectedData(ArrayList<Uri> messageUris, String accountName) {
        UpdateList(messageUris, accountName);
    }

    private class AccountsUpdateListener implements OnAccountsUpdateListener {

        @Override
        public void onAccountsUpdated(@NonNull Account[] myAccounts) {
            Log.d(TAG, "onAccountsUpdated");
            List<String> newList;

            //invoked when the AccountManager starts up and whenever the account set changes
            ArrayList<Account> newAccounts = new ArrayList<>();
            for (final Account account : myAccounts) {
                if (account.type.equals(Utilities.PackageName)) {
                    newAccounts.add(account);
                }
            }
            accounts = newAccounts.toArray(new Account[0]);
            newList = new ArrayList<>();
            for (final Account account : newAccounts) {
                newList.add(account.name);
            }
            boolean equalLists = true;
            ListIterator<String> iter = ListActivity.accountList.listIterator();
            while (iter.hasNext()) {
                String s = iter.next();
                if (!(newList.contains(s))) {
                    iter.remove();
                    // The account is gone: only drop it from the cached list here.  Deleting the
                    // local directory is SyncUtils.RemoveAccount's job, which runs when
                    // AccountManager confirms the removal.
                    equalLists = false;
                }
            }
            for (String accountName : newList) {
                if (!(accountList.contains(accountName))) {
                    accountList.add(accountName);
                    equalLists = false;
                    SyncUtils.CreateLocalDirectories(ImapNotes3.GetAccountDir(accountName));
                }
            }
            if (accountList.isEmpty()) {
                // The account of this list is gone, and the overview owns the empty state.
                Log.w(TAG, "onAccountsUpdated: no account is left");
                finish();
            } else if (IsAccountScoped() && !ListActivity.accountList.contains(scopedAccountName)) {
                // The account this list belongs to was removed. The overview owns that state.
                Log.w(TAG, "onAccountsUpdated: scoped account is gone: " + scopedAccountName);
                finish();
            } else if (!equalLists) {
                RefreshScopedList();
            }
        }
    }
}
