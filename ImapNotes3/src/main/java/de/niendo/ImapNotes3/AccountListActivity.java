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

package de.niendo.ImapNotes3;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.OnAccountsUpdateListener;
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

import de.niendo.ImapNotes3.Data.ImapNotesAccount;
import de.niendo.ImapNotes3.Miscs.Utilities;

import java.util.ArrayList;
import java.util.List;

public class AccountListActivity extends AppCompatActivity {
    private static final String TAG = "IN_AccountListActivity";
    private static final int ADD_ACCOUNT = 1;
    private static final int EDIT_ACCOUNT = 2;

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
            ImapNotesAccount account = accountAdapter.getItem(position);
            openAccountNotes(account.accountName);
        });

        accountListView.setOnItemLongClickListener((parent, view, position, id) -> {
            ImapNotesAccount account = accountAdapter.getItem(position);
            editAccount(account.accountName);
            return true;
        });

        FloatingActionButton addButton = findViewById(R.id.addAccountButton);
        addButton.setOnClickListener(v -> createAccount());
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
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult: " + requestCode + " " + resultCode);
        reloadAccounts();
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
        Intent res = new Intent(this, ListActivity.class);
        res.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        res.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, accountName);
        startActivity(res);
    }

    private void createAccount() {
        Log.d(TAG, "createAccount");
        Intent res = new Intent(this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.CREATE_ACCOUNT);
        startActivityForResult(res, ADD_ACCOUNT);
    }

    private void editAccount(@NonNull String accountName) {
        Log.d(TAG, "editAccount: " + accountName);
        Intent res = new Intent(this, AccountConfigurationActivity.class);
        res.putExtra(AccountConfigurationActivity.ACTION, AccountConfigurationActivity.Actions.EDIT_ACCOUNT);
        res.putExtra(AccountConfigurationActivity.ACCOUNTNAME, accountName);
        startActivityForResult(res, EDIT_ACCOUNT);
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
