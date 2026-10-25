/*
 * Copyright (C) 2022-2025 - Peter Korf <peter@niendo.de>
 * Copyright (C) ?   -2022 - Axel Strübing
 * Copyright (C) ?   -2016 - Martin Carpella
 * Copyright (C) ?   -2015 - nb
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

package de.niendo.ImapNotes3.Data;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import android.util.Log;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;

import de.niendo.ImapNotes3.ImapNotes3;

public class ImapNotesAccount {

    private static final String TAG = "IN_ImapNotesAccount";
    private static final String DEFAULT_FOLDER_NAME = "Notes";

    @NonNull
    public final String accountName;
    @NonNull
    public final String username;
    @NonNull
    public final String password;
    @NonNull
    public final String server;
    @NonNull
    public final String portnum;
    @NonNull
    public final Security security;
    public final SyncInterval syncInterval;
    @NonNull
    public final String copyImapFolder;
    public final String copyImapFolderName;
    @NonNull
    private final String imapFolder;
    @Nullable
    private final Account account;
    private File dirForNewFiles;
    private File dirForDeletedFiles;
    private File rootDirAccount;

    public ImapNotesAccount(@NonNull String accountName,
                            @NonNull String username,
                            @NonNull String password,
                            @NonNull String server,
                            @NonNull String portNumber,
                            @NonNull Security security,
                            @NonNull SyncInterval syncInterval,
                            @NonNull String imapFolder,
                            @NonNull String copyImapFolderName,
                            @NonNull String copyImapFolder) {
        account = null;
        this.accountName = accountName;
        this.username = username;
        this.password = password;
        this.server = server;
        this.portnum = portNumber;
        this.security = security;
        this.syncInterval = syncInterval;
        this.imapFolder = imapFolder;
        this.copyImapFolderName = copyImapFolderName;
        this.copyImapFolder = copyImapFolder;
    }

    public ImapNotesAccount(@NonNull Account account,
                            @NonNull Context applicationContext) {
        this.accountName = account.name;
        rootDirAccount = ImapNotes3.GetAccountDir(accountName);
        dirForNewFiles = new File(rootDirAccount, "new");
        dirForDeletedFiles = new File(rootDirAccount, "deleted");

        this.account = account;
        AccountManager am = AccountManager.get(applicationContext);
        syncInterval = SyncInterval.from(am.getUserData(account, ConfigurationFieldNames.SyncInterval));
        username = am.getUserData(account, ConfigurationFieldNames.UserName);
        password = am.getPassword(account);
        server = am.getUserData(account, ConfigurationFieldNames.Server);
        portnum = am.getUserData(account, ConfigurationFieldNames.PortNumber);
        security = Security.from(am.getUserData(account, ConfigurationFieldNames.Security));
        imapFolder = am.getUserData(account, ConfigurationFieldNames.ImapFolder);
        copyImapFolderName = am.getUserData(account, ConfigurationFieldNames.copyImapFolderName);
        copyImapFolder = am.getUserData(account, ConfigurationFieldNames.copyImapFolder);
    }


    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void CreateLocalDirectories() {
        Log.d(TAG, "CreateLocalDirs(String: " + accountName);
        dirForNewFiles.mkdirs();
        dirForDeletedFiles.mkdirs();
    }


    public void ClearHomeDir() {
        try {
            FileUtils.deleteDirectory(rootDirAccount);
        } catch (IOException | Error e) {
            // Errors are caught on purpose: under anbox the deletion fails in a way that would
            // otherwise take the whole sync adapter down.  The price is that the note files of the
            // account stay on disk unnoticed, so the message names the directory that survived.
            Log.e(TAG, "ClearHomeDir failed, the directory stays behind: " + rootDirAccount, e);
        }
    }

    @NonNull
    public String GetCopyImapFolderName() {
        if (copyImapFolderName == null || copyImapFolderName.isEmpty() || !copyImapFolder.equals("true"))
            return "";
        else
            return copyImapFolderName;
    }

    @Nullable
    public Account GetAccount() {
        return this.account;
    }

    @NonNull
    public String GetImapFolder() {
        if (this.imapFolder.isEmpty())
            return DEFAULT_FOLDER_NAME;
        return this.imapFolder;
    }

    @NonNull
    public File GetRootDirAccount() {
        return rootDirAccount;
    }
}
