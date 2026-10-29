/*
 * Copyright (C) 2026 - ImapNotes3 contributors
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

package nl.enimatek.imapnotesx;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import nl.enimatek.imapnotesx.Data.ImapNotesAccount;
import nl.enimatek.imapnotesx.Miscs.SwipeRevealRow;
import nl.enimatek.imapnotesx.Miscs.Utilities;

import java.util.ArrayList;
import java.util.List;

public class AccountListActivity extends AppCompatActivity {
    private static final String TAG = "IN_AccountListActivity";
    private static final int ADD_ACCOUNT = 1;
    private static final int EDIT_ACCOUNT = 2;
    /**
     * Account that was opened last, so a later start of the app can land in its notes list.
     */
    private static final String PREF_LAST_ACCOUNT_NAME = "LastAccountName";
    private static final String STATE_PENDING_SHARE = "PendingShare";

    @NonNull
    private final List<ImapNotesAccount> accounts = new ArrayList<>();
    @NonNull
    private AccountManager accountManager;
    @NonNull
    private AccountAdapter accountAdapter;
    @NonNull
    private final OnAccountsUpdateListener accountsUpdateListener = new OnAccountsUpdateListener() {
        @Override
        public void onAccountsUpdated(@NonNull Account[] myAccounts) {
            Log.d(TAG, "onAccountsUpdated");
            reloadAccounts();
        }
    };

    /**
     * A share that has to wait for an account, set while account setup runs for a share that arrived
     * when no account existed.  It is kept over a rotation so setup can still finish the share.
     */
    @Nullable
    private Intent pendingShare = null;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        setContentView(R.layout.account_list);
        // The launcher label stays the app name, so the in-app title is set here.
        setTitle(R.string.account_overview);

        accountManager = AccountManager.get(getApplicationContext());
        accountAdapter = new AccountAdapter();

        ListView accountListView = findViewById(R.id.accountList);
        accountListView.setAdapter(accountAdapter);

        accountListView.setOnItemClickListener((parent, view, position, id) -> {
            Log.d(TAG, "onItemClick: " + position);
            // If a row is slid open, a tap anywhere closes it instead of opening the notes list.
            if (SwipeRevealRow.concealRevealedRow()) {
                Log.d(TAG, "onItemClick: closed the revealed row");
                return;
            }
            ImapNotesAccount account = accountAdapter.getItem(position);
            openAccountNotes(account.accountName);
        });

        FloatingActionButton addButton = findViewById(R.id.addAccountButton);
        addButton.setOnClickListener(v -> createAccount());

        // Only for a cold start: doing this in onNewIntent or onStart would bounce back and forth,
        // because openAccountNotes brings an existing notes list to the front.
        if (savedInstanceState == null) {
            Intent startIntent = getIntent();
            if (IsShareIntent(startIntent)) {
                // Route the share instead of opening the last account, because the shared note would
                // otherwise go to that account without the user choosing it.
                routeShareIntent(startIntent);
            } else {
                restoreLastAccount();
            }
        } else {
            pendingShare = savedInstanceState.getParcelable(STATE_PENDING_SHARE);
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (pendingShare != null) {
            outState.putParcelable(STATE_PENDING_SHARE, pendingShare);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");
        accountManager.addOnAccountsUpdatedListener(accountsUpdateListener, null, true);
        reloadAccounts();
    }

    @Override
    protected void onStop() {
        Log.d(TAG, "onStop");
        super.onStop();
        accountManager.removeOnAccountsUpdatedListener(accountsUpdateListener);
    }

    @Override
    protected void onNewIntent(@Nullable Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent");
        setIntent(intent);
        reloadAccounts();
        if (IsShareIntent(intent)) {
            routeShareIntent(intent);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        reloadAccounts();
        if (requestCode != ADD_ACCOUNT || pendingShare == null) {
            return;
        }
        if (resultCode == ListActivity.ResultCodeSuccess && data != null) {
            // Setup succeeded: the note that was shared can now go into the new account.
            String newAccountName = data.getStringExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME);
            if (newAccountName != null && !newAccountName.isEmpty()) {
                Log.i(TAG, "onActivityResult: continuing the share with the new account " + newAccountName);
                sendShareToAccount(pendingShare, newAccountName);
                return;
            }
        }
        // Setup was cancelled, so nothing the shared note could be stored in exists after all.
        Log.i(TAG, "onActivityResult: dropping the share that was waiting for an account");
        pendingShare = null;
    }

    private void reloadAccounts() {
        accounts.clear();

        Account[] configuredAccounts = accountManager.getAccountsByType(Utilities.PackageName);
        for (Account account : configuredAccounts) {
            accounts.add(new ImapNotesAccount(account, getApplicationContext()));
        }

        accountAdapter.notifyDataSetChanged();

        View emptyState = findViewById(R.id.emptyAccounts);
        ListView accountListView = findViewById(R.id.accountList);
        if (accounts.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            accountListView.setVisibility(View.GONE);
        } else {
            emptyState.setVisibility(View.GONE);
            accountListView.setVisibility(View.VISIBLE);
        }
    }

    private void openAccountNotes(@NonNull String accountName) {
        Log.d(TAG, "openAccountNotes: " + accountName);
        saveLastAccountName(accountName);
        Intent res = new Intent(this, ListActivity.class);
        res.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        res.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, accountName);
        startActivity(res);
    }

    /**
     * Opens the notes list of the account that was opened last, if it still exists. The overview stays
     * below it in the task stack, so Back brings the user back here and other accounts remain reachable.
     */
    private void restoreLastAccount() {
        String lastAccountName = loadLastAccountName();
        if (lastAccountName == null) {
            Log.d(TAG, "restoreLastAccount: nothing to restore");
            return;
        }
        for (Account account : accountManager.getAccountsByType(Utilities.PackageName)) {
            if (account.name.equals(lastAccountName)) {
                Log.d(TAG, "Restoring last account: " + lastAccountName);
                openAccountNotes(lastAccountName);
                return;
            }
        }
        Log.d(TAG, "restoreLastAccount: account does not exist anymore: " + lastAccountName);
        clearLastAccountName(lastAccountName);
    }

    @Nullable
    private String loadLastAccountName() {
        return getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE)
                .getString(PREF_LAST_ACCOUNT_NAME, null);
    }

    private void saveLastAccountName(@NonNull String accountName) {
        getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit()
                .putString(PREF_LAST_ACCOUNT_NAME, accountName).apply();
    }

    private void clearLastAccountName(@NonNull String accountName) {
        if (accountName.equals(loadLastAccountName())) {
            getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit()
                    .remove(PREF_LAST_ACCOUNT_NAME).apply();
        }
    }

    /**
     * Asks before an account is removed. The notes themselves stay in the mailbox on the server.
     */
    private void confirmDeleteAccount(@NonNull ImapNotesAccount account) {
        Log.d(TAG, "confirmDeleteAccount: " + account.accountName);
        // The row closes as the action is tapped, so no row stays open behind the dialog.
        SwipeRevealRow.concealRevealedRow();
        new AlertDialog.Builder(this)
                .setTitle(R.string.delete_account)
                .setMessage(R.string.are_you_sure_you_wish_to_delete_the_account)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.yes, (dialog, which) -> removeAccount(account))
                .setNegativeButton(R.string.no, null)
                .show();
    }

    private void removeAccount(@NonNull ImapNotesAccount account) {
        Log.d(TAG, "removeAccount: " + account.accountName);
        // The local note files, the database entries and the preferences are removed by
        // SyncUtils.RemoveAccount, which the authenticator calls for the account being removed.
        accountManager.removeAccount(account.GetAccount(), null, null, null);
        clearLastAccountName(account.accountName);
    }

    private void createAccount() {
        Log.d(TAG, "createAccount");
        Intent res = new Intent(this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.CREATE_ACCOUNT);
        startActivityForResult(res, ADD_ACCOUNT);
    }

    private void editAccount(@NonNull String accountName) {
        Log.d(TAG, "editAccount: " + accountName);
        SwipeRevealRow.concealRevealedRow();
        Intent res = new Intent(this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.EDIT_ACCOUNT);
        res.putExtra(AccountConfigurationActivity.ACCOUNTNAME, accountName);
        startActivityForResult(res, EDIT_ACCOUNT);
    }

    /**
     * True for the intents this screen receives from other apps when something is shared into a note.
     */
    private static boolean IsShareIntent(@Nullable Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action);
    }

    /**
     * Decides which account a shared text, image or mail belongs to and opens that account's notes
     * list with the share attached.  This replaces the account Spinner of the old unscoped list: the
     * payload itself is never handled here, it stays with {@code ListActivity.Check_Action_Send()}.
     */
    private void routeShareIntent(@NonNull Intent shareIntent) {
        Log.d(TAG, "routeShareIntent: " + shareIntent.getAction() + " " + shareIntent.getType());
        Account[] configured = accountManager.getAccountsByType(Utilities.PackageName);

        if (configured.length == 0) {
            // A note needs an account to be stored in.  Remember the share and come back to it once
            // setup succeeded, instead of offering the share again on every visit of this screen.
            Log.i(TAG, "routeShareIntent: no account exists, account setup comes first");
            pendingShare = shareIntent;
            createAccount();
            return;
        }
        if (configured.length == 1) {
            sendShareToAccount(shareIntent, configured[0].name);
            return;
        }

        // Several accounts: the user names the target, a share going to the wrong account is worse
        // than one extra tap.
        CharSequence[] names = new CharSequence[configured.length];
        for (int i = 0; i < configured.length; i++) {
            names[i] = configured[i].name;
        }
        SwipeRevealRow.concealRevealedRow();
        new AlertDialog.Builder(this)
                .setTitle(R.string.select_one_account)
                .setItems(names, (dialog, which) -> sendShareToAccount(shareIntent, configured[which].name))
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    Log.i(TAG, "routeShareIntent: the share was cancelled");
                    pendingShare = null;
                })
                .show();
    }

    /**
     * Hands the incoming share on to the notes list of one account.  The intent is copied so its
     * action, MIME type and extras (EXTRA_TEXT / EXTRA_STREAM) arrive unchanged, and the account name
     * gives the list its scope.
     */
    private void sendShareToAccount(@NonNull Intent shareIntent, @NonNull String accountName) {
        Log.d(TAG, "sendShareToAccount: " + accountName);
        pendingShare = null;
        saveLastAccountName(accountName);
        Intent res = new Intent(shareIntent);
        res.setClass(this, ListActivity.class);
        res.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        // The read permission the sender granted for EXTRA_STREAM lives in the flags that were just
        // replaced, so it is set explicitly for the copy.
        res.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        res.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, accountName);
        startActivity(res);
    }

    private class AccountAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return accounts.size();
        }

        @NonNull
        @Override
        public ImapNotesAccount getItem(int position) {
            return accounts.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @NonNull
        @Override
        public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(parent.getContext()).inflate(R.layout.account_element, parent, false);
            }

            ImapNotesAccount account = getItem(position);

            // As for the note rows: a reused row must never still be slid open, and its buttons have
            // to act on the account that is shown in this row right now.
            if (row instanceof SwipeRevealRow) {
                ((SwipeRevealRow) row).concealImmediately();
            }
            View editButton = row.findViewById(R.id.editAccountRowButton);
            if (editButton != null) {
                editButton.setOnClickListener(v -> editAccount(account.accountName));
            }
            View deleteButton = row.findViewById(R.id.deleteAccountRowButton);
            if (deleteButton != null) {
                deleteButton.setOnClickListener(v -> confirmDeleteAccount(account));
            }

            TextView accountName = row.findViewById(R.id.accountName);
            TextView accountServer = row.findViewById(R.id.accountServer);
            TextView accountUsername = row.findViewById(R.id.accountUsername);

            accountName.setText(account.accountName);
            accountServer.setText(account.server);
            accountUsername.setText(account.username);

            return row;
        }
    }
}
