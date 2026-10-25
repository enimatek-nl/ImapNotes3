/*
 * Copyright (C) 2022-2025 - Peter Korf <peter@niendo.de>
 * Copyright (C)           - kwhitefoot
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
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;

import de.niendo.ImapNotes3.Data.ConfigurationFieldNames;
import de.niendo.ImapNotes3.Data.ImapNotesAccount;
import de.niendo.ImapNotes3.Data.Security;
import de.niendo.ImapNotes3.Data.SyncInterval;
import de.niendo.ImapNotes3.Miscs.LoginThread;
import de.niendo.ImapNotes3.Miscs.Result;
import de.niendo.ImapNotes3.Miscs.SmtpServerNameFinder;
import de.niendo.ImapNotes3.Miscs.Utilities;
import eltos.simpledialogfragment.SimpleDialog;

import java.util.List;

public class AccountConfigurationActivity extends AccountAuthenticatorActivity implements OnItemSelectedListener, SimpleDialog.OnDialogResultListener, LoginThread.FinishListener {
    /**
     * Cannot be final or NonNull because it needs the application context which is not available
     * until onCreate.
     */

    public static final String ACTION = "ACTION";
    public static final String ACCOUNTNAME = "ACCOUNTNAME";
    public static final int TO_REFRESH = 999;
    public static final String AUTHORITY = Utilities.PackageName + ".provider";
    private static final String TAG = "IN_AccountConfActivity";


    @Nullable
    private static Account myAccount = null;
    private static AccountManager accountManager;
    private AppCompatDelegate mDelegate;
    private TextView accountnameTextView;
    private TextView usernameTextView;
    private TextView passwordTextView;
    private TextView serverTextView;
    private TextView portnumTextView;
    private Spinner syncIntervalSpinner;
    private TextView folderTextView;
    private TextView copyImapFolderNameTextView;
    private ImageButton expandMoreSettings;

    private final View.OnClickListener ClickExpandMoreSettings = (l) -> {
        expandMoreSettings.setVisibility(View.GONE);
         LinearLayout lLayout=findViewById(R.id.ViewExtendedAccountSettings);
        lLayout.setLayoutParams(new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.FILL_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
        lLayout.setVisibility(View.VISIBLE);
    };

    private final CheckBox.OnCheckedChangeListener FinishCopyFolderCheckBox = (v, r) -> {
        copyImapFolderNameTextView.setEnabled(v.isChecked());
        if (GetTextViewText(copyImapFolderNameTextView).isEmpty())
            copyImapFolderNameTextView.setText("Trash");
    };
    private Spinner securitySpinner;
    @NonNull
    private SyncInterval syncInterval = SyncInterval.t6h;
    @NonNull
    private Security security = Security.None;
    @Nullable
    private String accountname;

    // The setup screen has two pages: the form and the result of the connection check.
    private View formScrollView;
    private View resultLayout;
    private View resultSpinner;
    private ImageView resultIcon;
    private TextView resultHeading;
    private TextView resultMessage;
    private Button resultButton;
    private Button resultHelpButton;
    private boolean resultIsSuccess = false;
    // Predictive back: on API 33+ onBackPressed() is not called any more (the manifest enables
    // android:enableOnBackInvokedCallback), so the failed-check page registers a platform back
    // callback instead. Typed as Object to keep the class loadable on the older APIs we support.
    @Nullable
    private Object backToFormCallback = null;

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        getDelegate().onPostCreate(savedInstanceState);
    }

    @Override
    public boolean onResult(@NonNull String dialogTag, int which, @NonNull Bundle extras) {
        return false;
    }

    public ActionBar getSupportActionBar() {
        return getDelegate().getSupportActionBar();
    }

    /* is this important?
    @Override
    @NonNull
    public MenuInflater getMenuInflater() {
        return getDelegate().getMenuInflater();
    }
  */
    //@Override
    public void onFinishPerformed(@NonNull Result<String> result) {
        if (result.succeeded) {
            Intent intent = new Intent();
            intent.putExtra(ListActivity.EDIT_ITEM_ACCOUNTNAME, GetTextViewText(accountnameTextView));
            setResult(ListActivity.ResultCodeSuccess, intent);
        }
        // A failed check deliberately keeps the ResultCodeNeutral that onCreate() set, so leaving
        // the screen with Back behaves like a cancel.
        ShowResultPage(result.succeeded, result.result);
    }

    public void setSupportActionBar(@Nullable Toolbar toolbar) {
        getDelegate().setSupportActionBar(toolbar);
    }

    @Override
    public void setContentView(@LayoutRes int layoutResID) {
        getDelegate().setContentView(layoutResID);
    }

    @Override
    public void setContentView(View view) {
        getDelegate().setContentView(view);
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().setContentView(view, params);
    }

    @Override
    public void addContentView(View view, ViewGroup.LayoutParams params) {
        getDelegate().addContentView(view, params);
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        getDelegate().onPostResume();
    }

    @Override
    protected void onTitleChanged(CharSequence title, int color) {
        super.onTitleChanged(title, color);
        getDelegate().setTitle(title);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        getDelegate().onConfigurationChanged(newConfig);
    }

    @Override
    protected void onStop() {
        super.onStop();
        getDelegate().onStop();
    }

    @Override
    protected void onDestroy() {
        DisableBackToForm();
        super.onDestroy();
        getDelegate().onDestroy();
    }

    public void invalidateOptionsMenu() {
        getDelegate().invalidateOptionsMenu();
    }

    private AppCompatDelegate getDelegate() {
        if (mDelegate == null) {
            mDelegate = AppCompatDelegate.create(this, null);
        }
        return mDelegate;
    }

    @Nullable
    private Actions action;

    private final OnClickListener clickListenerLogin = v -> {
        // Click on Login Button
        Log.d(TAG, "clickListenerLogin  onClick");
        CheckNameAndLogIn();
    };
    private CheckBox copyImapFolderCheckBox;

    @SuppressLint("SetTextI18n")
    private final View.OnFocusChangeListener FinishEmailEdit = (v, r) -> {
        if (!v.hasFocus()) {
            String email = ((TextView) v).getText().toString();
            if (email.contains("@") && serverTextView.getText().toString().isEmpty()) {
                serverTextView.setText(SmtpServerNameFinder.getSmtpServerName(email));
            }
        }
    };

    /*
        private final TextWatcher textWatcher = new TextWatcher(){

            public void beforeTextChanged(CharSequence chars, int start, int count, int after){}
            public void afterTextChanged(Editable editable){}
            public void onTextChanged(CharSequence chars, int start, int before, int count) {

            }

        };*/

    private void CheckNameAndLogIn() {
        String name = accountnameTextView.getText().toString();
        if (name.isEmpty()) {
            name = GetTextViewText(usernameTextView);
            accountnameTextView.setText(name);
        }
        if (name.contains("'") || name.contains("\""))
            ImapNotes3.ShowMessage(R.string.quotation_marks_not_allowed, accountnameTextView, 3);
        else
            DoLogin();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        getDelegate().installViewFactory();
        getDelegate().onCreate(savedInstanceState);
        super.onCreate(savedInstanceState);
        setResult(ListActivity.ResultCodeNeutral);
        setContentView(R.layout.account_setup);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        TextView headingTextView = findTextViewById(R.id.heading);
        accountnameTextView = findTextViewById(R.id.accountnameEdit);
        usernameTextView = findTextViewById(R.id.usernameEdit);
        usernameTextView.setOnFocusChangeListener(FinishEmailEdit);
        passwordTextView = findTextViewById(R.id.passwordEdit);
        serverTextView = findTextViewById(R.id.serverEdit);
        portnumTextView = findTextViewById(R.id.portnumEdit);
        syncIntervalSpinner = findViewById(R.id.syncintervalSpinner);
        List<String> listInterval = SyncInterval.Printables(getResources());
        ArrayAdapter<String> dataAdapterInterval = new ArrayAdapter<>
                (this, R.layout.ssl_spinner_item, listInterval);
        syncIntervalSpinner.setAdapter(dataAdapterInterval);
        syncIntervalSpinner.setSelection(SyncInterval.t6h.ordinal());
        syncIntervalSpinner.setOnItemSelectedListener(this);

        folderTextView = findTextViewById(R.id.folderEdit);
        copyImapFolderNameTextView = findTextViewById(R.id.copyImapFolderName);
        copyImapFolderCheckBox = (CheckBox) findTextViewById(R.id.copyImapFolder);
        copyImapFolderCheckBox.setOnCheckedChangeListener(FinishCopyFolderCheckBox);
        securitySpinner = findViewById(R.id.securitySpinner);
        List<String> list = Security.Printables(getResources());
        ArrayAdapter<String> dataAdapter = new ArrayAdapter<>
                (this, R.layout.ssl_spinner_item, list);
        securitySpinner.setAdapter(dataAdapter);
        securitySpinner.setOnItemSelectedListener(this);
        securitySpinner.setSelection(Security.SSL_TLS.ordinal());

        expandMoreSettings = findViewById(R.id.BtnExpandAccountSettings);
        expandMoreSettings.setOnClickListener(ClickExpandMoreSettings);

        formScrollView = findViewById(R.id.scrollView);
        resultLayout = findViewById(R.id.accountResult);
        resultSpinner = findViewById(R.id.resultSpinner);
        resultIcon = findViewById(R.id.resultIcon);
        resultHeading = findViewById(R.id.resultHeading);
        resultMessage = findViewById(R.id.resultMessage);
        resultButton = findViewById(R.id.resultButton);
        resultHelpButton = findViewById(R.id.resultHelpButton);

        Bundle extras = getIntent().getExtras();
        // TODO: find out if extras can be null.
        if (extras != null) {
            if (extras.containsKey(ACTION)) {
                action = (Actions) (extras.getSerializable(ACTION));
            }
            if (extras.containsKey(ACCOUNTNAME)) {
                accountname = extras.getString(ACCOUNTNAME);
            }
        }

        LinearLayout layout = findViewById(R.id.buttonsLayout);
        accountManager = AccountManager.get(getApplicationContext());
        Account[] accounts = accountManager.getAccountsByType(Utilities.PackageName);
        for (Account account : accounts) {
            if (account.name.equals(accountname)) {
                myAccount = account;
                break;
            }
        }

        // action can never be null
        if (myAccount == null) {
            action = Actions.CREATE_ACCOUNT;
        }

        if (action == Actions.EDIT_ACCOUNT) {
            // Here we have to edit an existing account
            headingTextView.setText(R.string.editAccount);
            accountnameTextView.setText(accountname);
            accountnameTextView.setEnabled(false);
            usernameTextView.setText(GetConfigValue(ConfigurationFieldNames.UserName));
            //passwordTextView.setText(accountManager.getPassword(myAccount));
            serverTextView.setText(GetConfigValue(ConfigurationFieldNames.Server));
            portnumTextView.setText(GetConfigValue(ConfigurationFieldNames.PortNumber));
            //Log.d(TAG, "Security: " + GetConfigValue(ConfigurationFieldNames.Security));
            security = Security.from(GetConfigValue(ConfigurationFieldNames.Security));
            securitySpinner.setSelection(security.ordinal());
            syncInterval = SyncInterval.from(GetConfigValue(ConfigurationFieldNames.SyncInterval));
            syncIntervalSpinner.setSelection(syncInterval.ordinal());
            folderTextView.setText(GetConfigValue(ConfigurationFieldNames.ImapFolder));
            copyImapFolderNameTextView.setText(GetConfigValue(ConfigurationFieldNames.copyImapFolderName));

            String myTempStr = GetConfigValue(ConfigurationFieldNames.copyImapFolder);
            copyImapFolderCheckBox.setChecked(myTempStr != null && myTempStr.equals("true") && !(GetConfigValue(ConfigurationFieldNames.copyImapFolderName).isEmpty()));
        }

        // Creating and editing an account use the same flow: one "Next" button, then the outcome of
        // the connection check on the second page (ShowCheckingPage / ShowResultPage). There is no
        // cancel button, Back or Up returns to the caller.
        Button buttonView = new Button(this);
        buttonView.setText(R.string.next);
        buttonView.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        Log.d(TAG, "Set onclick listener login");
        buttonView.setOnClickListener(clickListenerLogin);
        layout.addView(buttonView);

        // Don't display keyboard when on note detail, only if user touches the screen
        getWindow().setSoftInputMode(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
        );
    }

    private TextView findTextViewById(int id) {
        return findViewById(id);
    }

    private String GetConfigValue(@NonNull String name) {
        return accountManager.getUserData(myAccount, name);
    }

    private String GetTextViewText(@NonNull TextView textView) {
        return textView.getText().toString().trim();
    }

    private String GetCheckBoxValue(@NonNull CheckBox checkBox) {
        return checkBox.isChecked() ? "true" : "false";
    }

    // DoLogin method is defined in account_selection.xml (account_selection layout)
    private void DoLogin() {
        Log.d(TAG, "DoLogin");

        //password will not shown if account is edit and have to be loaded;
        String password = GetTextViewText(passwordTextView);
        if ((action == Actions.EDIT_ACCOUNT) && (password.isEmpty())) {
            // Server name edited: new password required (avoid password spoofing)
            if (GetTextViewText(serverTextView).equals(GetConfigValue(ConfigurationFieldNames.Server))) {
                password = accountManager.getPassword(myAccount);
            } else {
                ImapNotes3.ShowMessage(R.string.imap_server_changed_new_password, accountnameTextView, 3);
                return;
            }
        }

        final ImapNotesAccount ImapNotesAccount = new ImapNotesAccount(
                GetTextViewText(accountnameTextView),
                GetTextViewText(usernameTextView),
                password,
                GetTextViewText(serverTextView),
                GetTextViewText(portnumTextView),
                security,
                syncInterval,
                GetTextViewText(folderTextView),
                GetTextViewText(copyImapFolderNameTextView),
                GetCheckBoxValue(copyImapFolderCheckBox));
        // No need to check for valid numbers because the field only allows digits.  But it is
        // possible to remove all characters which causes the program to crash.  The easiest fix is
        // to add a zero at the beginning so that we are guaranteed to be able to parse it but that
        // leaves us with a zero sync. interval.

        ShowCheckingPage();
        new LoginThread(
                ImapNotesAccount,
                this,
                action).execute();
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        return true;
    }

    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // This activity is started for a result, so Up has to return to whoever opened it
            // (the account overview or the notes list).
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent.getId() == R.id.syncintervalSpinner) {
            syncInterval = SyncInterval.from(position);
        } else if ( (parent.getId() == R.id.securitySpinner)) {
            if (!security.equals(Security.from(position))) {
                security = Security.from(position);
                portnumTextView.setText(security.defaultPort);
            }
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Nothing to do: the security spinner only matters when an entry is selected.
    }

    public void Clear() {

        accountnameTextView.setText("");
        usernameTextView.setText("");
        passwordTextView.setText("");
        serverTextView.setText("");
        portnumTextView.setText("");
        syncIntervalSpinner.setSelection(0);
        securitySpinner.setSelection(0);
        folderTextView.setText("");
        copyImapFolderNameTextView.setText("");
        copyImapFolderCheckBox.setChecked(false);
    }

    /**
     * Switches to the second page of the setup screen while the IMAP connection check runs.
     */
    private void ShowCheckingPage() {
        HideKeyboard();
        resultIsSuccess = false;
        resultSpinner.setVisibility(View.VISIBLE);
        resultIcon.setVisibility(View.GONE);
        resultMessage.setVisibility(View.GONE);
        resultButton.setVisibility(View.GONE);
        resultHelpButton.setVisibility(View.GONE);
        resultHeading.setText(R.string.logging_in);
        ShowResultPageLayout();
    }

    /**
     * Shows the outcome of the connection check: either finished, or the reason it failed with a
     * way back to the form.
     */
    private void ShowResultPage(boolean succeeded, @NonNull String message) {
        HideKeyboard();
        resultIsSuccess = succeeded;
        resultSpinner.setVisibility(View.GONE);
        resultIcon.setVisibility(View.VISIBLE);
        resultMessage.setVisibility(View.VISIBLE);
        resultButton.setVisibility(View.VISIBLE);

        if (succeeded) {
            resultIcon.setImageResource(R.drawable.ic_action_check_circle);
            // message already holds the localized "account added/modified" text from LoginThread.
            resultHeading.setText(message);
            resultMessage.setText(GetTextViewText(accountnameTextView));
            resultHelpButton.setVisibility(View.GONE);
            resultButton.setText(R.string.finished);
            resultButton.setOnClickListener(v -> {
                Clear();
                finish();
            });
        } else {
            resultIcon.setImageResource(R.drawable.ic_action_error);
            resultHeading.setText(R.string.IMAP_operation_failed);
            resultMessage.setText(message);
            resultHelpButton.setVisibility(View.VISIBLE);
            resultHelpButton.setOnClickListener(v -> ShowImapHelpDialog());
            resultButton.setText(R.string.i_understand);
            resultButton.setOnClickListener(v -> ShowFormPage());
            EnableBackToForm();
        }
        ShowResultPageLayout();
    }

    private void ShowResultPageLayout() {
        formScrollView.setVisibility(View.GONE);
        resultLayout.setVisibility(View.VISIBLE);
    }

    private void ShowFormPage() {
        DisableBackToForm();
        resultLayout.setVisibility(View.GONE);
        formScrollView.setVisibility(View.VISIBLE);
    }

    /**
     * Lets Back return to the form instead of leaving the setup screen. Only needed while the
     * result of a failed connection check is displayed.
     */
    private void EnableBackToForm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            // Older versions still deliver Back through onBackPressed().
            return;
        }
        if (backToFormCallback == null) {
            backToFormCallback = (OnBackInvokedCallback) this::ShowFormPage;
        }
        getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, (OnBackInvokedCallback) backToFormCallback);
    }

    private void DisableBackToForm() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || backToFormCallback == null) {
            return;
        }
        getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback((OnBackInvokedCallback) backToFormCallback);
    }

    private void ShowImapHelpDialog() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.help)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setMessage(R.string.imap_help_text)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                })
                .show();
    }

    private void HideKeyboard() {
        View focusedView = getCurrentFocus();
        if (focusedView == null) {
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (resultLayout.getVisibility() == View.VISIBLE && !resultIsSuccess) {
            // After a failed check, Back returns to the form so the entries can be corrected.
            ShowFormPage();
            return;
        }
        super.onBackPressed();
    }


    /**
     *
     */
    public enum Actions {
        CREATE_ACCOUNT,
        EDIT_ACCOUNT
    }

}
