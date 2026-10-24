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
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;

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

import org.apache.commons.io.FileUtils;
import org.jsoup.Jsoup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class ListActivity extends AppCompatActivity implements BackupRestore.INotesRestore, OnItemSelectedListener, Filterable, SimpleDialog.OnDialogResultListener, UpdateThread.FinishListener {
    private static final int SEE_DETAIL = 2;
    public static final int DELETE_BUTTON = 3;
    private static final int NEW_BUTTON = 4;
    private static final int EDIT_ACCOUNT = 5;
    private static final int EDIT_BUTTON = 6;
    private static final int ADD_ACCOUNT = 7;
    private static final int SELECT_ARCHIVE_FOR_RESTORE = 8;
    public static final int EDIT_SETTINGS = 9;

    public static final int ResultCodeSuccess = 1;
    public static final int ResultCodeMakeArchive = 2;
    public static final int ResultCodeRestoreArchive = 3;
    public static final int ResultCodeRemoveAccount = 4;
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
    private static final String ACCOUNTSPINNER_POS = "ACCOUNTSPINNER_POS";
    private static final String SORT_BY_DATE = "SORT_BY_DATE";
    private static final String SORT_BY_TITLE = "SORT_BY_TITLE";
    private static final String SORT_BY_COLOR = "SORT_BY_COLOR";
    private static final String DLG_FILTER_HASHTAG = "DLG_FILTER_HASHTAG";

    private Intent intentActionSend;
    @Nullable
    private String requestedAccountName;
    // Set when the list was opened for a single account from AccountListActivity.
    // It outlives requestedAccountName so the screen keeps its scope after a rotation
    // or when returning from the note editor.
    @Nullable
    private String scopedAccountName;
    // Set by the "All" menu item while the list is account-scoped.
    private boolean showingAllAccounts = false;
    private ArrayList<OneNote> noteList;
    private NotesListAdapter listToView;
    private ArrayAdapter<String> spinnerList;
    private static final String AUTHORITY = Utilities.PackageName + ".provider";
    private Spinner accountSpinner;
    private static AccountManager accountManager;
    @Nullable
    private static NotesDb storedNotes = null;
    private static List<String> accountList;
    private static Menu actionMenu;
    private static CharSequence mFilterString = "";
    static String[] hashFilter;
    private static ArrayList<String> hashFilterSelected = new ArrayList<>();
    private ContentObserver mObserver;
    private SwipeRefreshLayout swipeLayout;
    // Ensure that we never have to check for null by initializing reference.
    @NonNull
    private static Account[] accounts = new Account[0];
    private final OnClickListener clickListenerEditAccount = v -> {
        String accountName = getSelectedAccountName();
        if (accountName.isEmpty()) {
            ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
            return;
        }

        Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.EDIT_ACCOUNT);
        res.putExtra(AccountConfigurationActivity.ACCOUNTNAME, accountName);
        startActivityForResult(res, ListActivity.EDIT_ACCOUNT);
    };
    private static final String TAG = "IN_Listactivity";
    //@Nullable
    private ListView listview;
    private AsyncTask updateThread;

    @Override
    public void onDestroy() {
        super.onDestroy();
        getContentResolver().unregisterContentObserver(mObserver);
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
    protected void onNewIntent(Intent intent) {
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
        requestedAccountName = scopedAccountName;
        showingAllAccounts = false;
        if (actionMenu != null) {
            UpdateScopeUi();
            applyRequestedAccountToSpinner();
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
            requestedAccountName = scopedAccountName;
        }
        setContentView(R.layout.main);
        getSupportActionBar().setElevation(0); // or other
        getSupportActionBar().setBackgroundDrawable(new ColorDrawable(getColor(R.color.ActionBgColor)));

        this.accountSpinner = findViewById(R.id.accountSpinner);
        UpdateScopeUi();
        ListActivity.accountList = new ArrayList<>();

        this.accountSpinner.setOnItemSelectedListener(this);
        ImapNotes3.setContent(findViewById(android.R.id.content));

        ListActivity.accountManager = AccountManager.get(getApplicationContext());
        ListActivity.accountManager.addOnAccountsUpdatedListener(
                new AccountsUpdateListener(), null, true);

        spinnerList = new ArrayAdapter<>
                (this, R.layout.account_spinner_item, ListActivity.accountList);
        accountSpinner.setAdapter(spinnerList);

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

        ImageButton editAccountButton = findViewById(R.id.editAccountButton);
        editAccountButton.setOnClickListener(clickListenerEditAccount);

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
                    /*
                    if (isSynced) {
                        Date date = new Date();
                        String sdate;
                        try {
                            sdate = DateFormat.getDateTimeInstance().format(date);
                        } catch (Exception e) {
                            Log.e(TAG, "getDateTimeInstance failed: " + date, e);
                            sdate = "";
                        }
                        //ImapNotes3.ShowMessage(getText(R.string.Last_sync) + sdate + " (" + getText(syncInterval.textID) + ")", accountSpinner, 2);
                    }
                     */
                    if ((errorMessage != null) && !errorMessage.isEmpty()) {
                        ImapNotes3.ShowMessage(errorMessage, accountSpinner, 5);
                    }
                }
                if (isChanged) RefreshList();
            }
        };
        getContentResolver().registerContentObserver(Uri.parse("content://" + BuildConfig.APPLICATION_ID + "/"), false, mObserver);

        FreeDroidWarn.showWarningOnUpgrade(this, BuildConfig.VERSION_CODE);
    }

    void handleSendMultipleImages(Intent intent) {
        Log.d(TAG, "handleSendMultipleImages");
        ArrayList<Uri> messageUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
        String accountName = getSelectedAccountName();
        if (accountName.isEmpty()) {
            ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
            return;
        }
        if (messageUris != null) {
            ImapNotes3.ShowMessage(R.string.importing, accountSpinner, 2);
            UpdateList(messageUris, accountName);
        }
    }

    public void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        setPreferences();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");
        UpdateScopeUi();
        applyRequestedAccountToSpinner();
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
        preferences.putLong(ACCOUNTSPINNER_POS, accountSpinner.getSelectedItemId());
        if (actionMenu == null) return;
        preferences.putBoolean(SORT_BY_DATE, actionMenu.findItem(R.id.sort_date).isChecked());
        preferences.putBoolean(SORT_BY_TITLE, actionMenu.findItem(R.id.sort_title).isChecked());
        preferences.putBoolean(SORT_BY_COLOR, actionMenu.findItem(R.id.sort_color).isChecked());
        preferences.apply();
    }

    private void setPreferences() {
        Log.d(TAG, "setPreferences:");
        SharedPreferences preferences = getApplicationContext().getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        if (IsAccountScoped()) {
            // In scoped mode the launch intent decides which account is shown.
            return;
        }
        accountSpinner.setSelection((int) preferences.getLong(ACCOUNTSPINNER_POS, 0));
    }


    private void RefreshList() {
        RefreshList(getSelectedAccountName());
    }
    private void RefreshList(String accountName) {
        if(actionMenu == null)
            return;
        Log.d(TAG, "RefreshList: ");
        listToView.setSortOrder(getSortOrder());
        synchronized (ImapNotes3.MainLock) {
            new SyncThread(
                    accountName,
                    noteList,
                    listToView,
                    R.string.refreshing_notes_list,
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

        // Associate searchable configuration with the SearchView
        // disable SearchManager and setSearchableInfo .. it seems confusing and useless
        //SearchManager searchManager =
        //        (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        MenuItem menuItem = menu.findItem(R.id.search);
        SearchView searchView = (SearchView) menuItem.getActionView();
        searchView.setQueryHint(getText(R.string.search_any_keyword));

        // searchView.setSearchableInfo(
        //         searchManager.getSearchableInfo(getComponentName()));
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

        UpdateScopedMenuItems();
        // RefreshList() only works once the menu exists, and a Spinner that is hidden (scoped mode)
        // never reports a selection change, so the list is filled here as well.
        applyRequestedAccountToSpinner();
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
            case R.id.all_accounts:
                ShowAllAccounts(!showingAllAccounts);
                return true;
            case R.id.newaccount: {
                Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
                res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.CREATE_ACCOUNT);
                startActivityForResult(res, ListActivity.ADD_ACCOUNT);
                return true;
            }
            case R.id.refresh:
                if (getSelectedAccountName().isEmpty())
                    ImapNotes3.ShowMessage(R.string.select_one_account, accountSpinner, 3);
                else
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

    private Integer getSpinnerPos(String accountName) {
        int n = accountSpinner.getCount();
        for (int i = 1; i < n; i++) {
            if (accountName.equals(accountSpinner.getAdapter().getItem(i).toString())) {
                return (i);
            }
        }
        return 0;
    }

    // Spinner item selected listener
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        Log.d(TAG, "onItemSelected");
        String accountName = getSelectedAccountName();
        listToView.setAccountName(accountName);
        RefreshList(accountName);
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // TODO Auto-generated method stub
    }

    // Hack: if the Spinner isDisabled Search is active->
    //all accounts are selected
    private Account getSelectedAccount() {
        long pos = accountSpinner.getSelectedItemId();
        if (pos <= 0) {
            if (accounts.length == 1) {
                return accounts[0];
            }
        } else {
            try {
                return accounts[(int) pos - 1];
            } catch (Exception e) {
                Log.e(TAG, "getSelectedAccount wrong pos: " + pos, e);
            }
        }
        return null;
    }

    private String getSelectedAccountName() {
        Account account = getSelectedAccount();
        if (account == null)
            return "";
        return account.name;
    }

    private void applyRequestedAccountToSpinner() {
        String target = IsAccountScoped() ? scopedAccountName : requestedAccountName;
        if (target == null || target.isEmpty() || accountSpinner == null || listToView == null) {
            return;
        }
        if (showingAllAccounts) {
            // The user explicitly asked this screen to show all accounts.
            if (accountSpinner.getSelectedItemPosition() != 0) {
                accountSpinner.setSelection(0);
            }
            String allAccountsName = getSelectedAccountName();
            listToView.setAccountName(allAccountsName);
            RefreshList(allAccountsName);
            return;
        }

        int pos = getSpinnerPos(target);
        if (pos <= 0) {
            return;
        }

        accountSpinner.setSelection(pos);
        requestedAccountName = null;

        String accountName = getSelectedAccountName();
        if (!accountName.isEmpty()) {
            listToView.setAccountName(accountName);
            RefreshList(accountName);
        }
    }

    /**
     * True when this screen was started for one account (from the account overview),
     * in contrast to the legacy spinner / share-intent mode.
     */
    private boolean IsAccountScoped() {
        return scopedAccountName != null && !scopedAccountName.isEmpty();
    }

    /**
     * Adapts the action bar and the account row to scoped / unscoped mode.
     */
    private void UpdateScopeUi() {
        View accountLayout = findViewById(R.id.account_layout);
        if (accountLayout == null) return;
        ActionBar bar = getSupportActionBar();

        if (IsAccountScoped()) {
            // The row offers to switch accounts; the account overview does that instead.
            // It stays in the view tree so Snackbars anchored on the spinner still work.
            accountLayout.setVisibility(View.GONE);
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(true);
                bar.setHomeButtonEnabled(true);
            }
        } else {
            accountLayout.setVisibility(View.VISIBLE);
            if (bar != null) {
                bar.setDisplayHomeAsUpEnabled(false);
                bar.setHomeButtonEnabled(false);
            }
            setTitle(R.string.app_name);
        }
        UpdateScopeTitle();
        UpdateScopedMenuItems();
    }

    private void UpdateScopeTitle() {
        if (!IsAccountScoped()) return;
        setTitle(showingAllAccounts ? getText(R.string.all_accounts) : scopedAccountName);
    }

    private void UpdateScopedMenuItems() {
        if (actionMenu == null) return;

        // Account management lives in the account overview; dropping the icon also
        // leaves room for the account name in the title.
        MenuItem newItem = actionMenu.findItem(R.id.newaccount);
        if (newItem != null) newItem.setVisible(!IsAccountScoped());

        MenuItem allItem = actionMenu.findItem(R.id.all_accounts);
        if (allItem != null) {
            // With a single account "All" would be identical to that account.
            allItem.setVisible(IsAccountScoped() && (accountList != null) && (accountList.size() > 2));
            allItem.setChecked(showingAllAccounts);
        }
    }

    /**
     * Switches the scoped notes list between the scoped account and all accounts.
     */
    private void ShowAllAccounts(boolean all) {
        Log.d(TAG, "ShowAllAccounts: " + all);
        if (accountSpinner == null || listToView == null) return;

        int pos = all ? 0 : getSpinnerPos(scopedAccountName);
        if (!all && (pos <= 0)) {
            Log.w(TAG, "ShowAllAccounts: scoped account not available");
            UpdateScopedMenuItems();
            return;
        }

        showingAllAccounts = all;
        UpdateScopeTitle();
        UpdateScopedMenuItems();

        if (accountSpinner.getSelectedItemPosition() == pos) {
            String accountName = getSelectedAccountName();
            listToView.setAccountName(accountName);
            RefreshList(accountName);
        } else {
            // onItemSelected refreshes the list for us.
            accountSpinner.setSelection(pos);
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
            case ListActivity.ADD_ACCOUNT:
            case ListActivity.EDIT_ACCOUNT:
                // Returning from ADD
                // Cancel, when no Account exists
                if ((ListActivity.accountList.size() <= 1) && (resultCode != ResultCodeSuccess) ) {
                    noAccountExists();
                 }
                if (resultCode == ResultCodeRemoveAccount) {
                    accountSpinner.setSelection(1);
                    if(accounts.length >= 1) RefreshList(accounts[1].name);
                }
                if (resultCode == ResultCodeSuccess ) {
                    ListActivity.accountManager.addOnAccountsUpdatedListener(
                            new AccountsUpdateListener(), null, true);
                    if (data != null) {
                        Integer pos = getSpinnerPos(data.getStringExtra(EDIT_ITEM_ACCOUNTNAME));
                        if (pos > 0) {
                            accountSpinner.setSelection(pos);
                        }
                    }
                    TriggerSync(true);
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

    private void noAccountExists() {
        Log.d(TAG, "noAccountExists");
        if (IsShareIntent(getIntent())) {
            // Sharing into the app needs an account before the note can be stored.
            Intent res = new Intent(ListActivity.this, AccountConfigurationActivity.class);
            startActivityForResult(res, ListActivity.ADD_ACCOUNT);
            return;
        }
        // AccountListActivity owns the empty state and its add-account button.
        finish();
    }

    private boolean IsShareIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);
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

    private void updateAccountSpinner() {
        Log.d(TAG, "updateAccountSpinner");
        this.accountSpinner.setEnabled(true);
        this.spinnerList.notifyDataSetChanged();
        setPreferences();
        long id = this.accountSpinner.getSelectedItemId();
        // only one account active..disable selection
        if (ListActivity.accountList.size() == 2) {
            this.accountSpinner.setEnabled(false);
            this.accountSpinner.setSelection(1);
            id = 1;
        }
        if ((id == android.widget.AdapterView.INVALID_ROW_ID) || (id >= ListActivity.accountList.size())) {
            this.accountSpinner.setSelection(1);
        }
        // A Spinner that is not shown (scoped mode hides the row) does not report a changed selection,
        // so the note list has to be filled here instead of from onItemSelected.
        applyRequestedAccountToSpinner();
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
            Account[] tempAccounts = new Account[newAccounts.size()];
            newList = new ArrayList<>();
            newList.add(getString(R.string.all_accounts));
            int i = 0;
            for (final Account account : newAccounts) {
                tempAccounts[i++] = account;
                newList.add(account.name);
            }
            accounts = tempAccounts;
            boolean equalLists = true;
            ListIterator<String> iter = ListActivity.accountList.listIterator();
            // skip first entry (All)
            if (iter.hasNext()) iter.next();
            while (iter.hasNext()) {
                String s = iter.next();
                if (!(newList.contains(s))) {
                    iter.remove();
                    // Why try here?
                    try {
                        FileUtils.deleteDirectory(ImapNotes3.GetAccountDir(s));
                    } catch (IOException e) {
                        Log.e(TAG, "deleteDirectory failed:", e);
                    }
                    equalLists = false;
                }
            }
            boolean first = true;
            for (String accountName : newList) {
                if (!(accountList.contains(accountName))) {
                    accountList.add(accountName);
                    equalLists = false;
                    // skip first entry (All)
                    if (!first)
                        SyncUtils.CreateLocalDirectories(ImapNotes3.GetAccountDir(accountName));
                }
                first = false;
            }
            if (!equalLists) {
                updateAccountSpinner();
            }
            if (accountList.size() <= 1) {
                noAccountExists();
            } else if (IsAccountScoped() && !ListActivity.accountList.contains(scopedAccountName)) {
                // The account this list belongs to was removed. The overview owns that state.
                Log.w(TAG, "onAccountsUpdated: scoped account is gone: " + scopedAccountName);
                finish();
            } else {
                applyRequestedAccountToSpinner();
            }
        }
    }
}
