/*
 * Copyright (C) 2022-2023 - Peter Korf <peter@niendo.de>
 * Copyright (C)      2016 - Martin Carpella
 * Copyright (C)      2015 - nb
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
package nl.enimatek.imapnotesx.Miscs;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;

import nl.enimatek.imapnotesx.Data.NotesDb;
import nl.enimatek.imapnotesx.Data.OneNote;
import nl.enimatek.imapnotesx.NotesListAdapter;

import java.util.ArrayList;

public class SyncThread extends AsyncTask<Object, Void, Boolean> {
    private static final String TAG = "SyncThread";
    private final NotesListAdapter adapter;
    private final ArrayList<OneNote> notesList;
    private final String sortOrder;
    private final String[] hashFilter;
    private final CharSequence filterString;
    private final String accountName;
    private ArrayList<OneNote> myNoteList;

    /**
     * SQLite database that holds status information about the notes.
     */
    // TODO: NoteDb should probably never be null.
    @NonNull
    private final NotesDb storedNotes;

    public SyncThread(String accountName,
                      ArrayList<OneNote> noteList,
                      NotesListAdapter listToView,
                      String sortOrder,
                      String[] hashFilter,
                      CharSequence filterString,
                      Context context) {
        this.accountName = accountName;
        this.notesList = noteList;
        this.adapter = listToView;
        this.sortOrder = sortOrder;
        this.hashFilter = hashFilter;
        this.filterString = filterString;
        this.storedNotes = NotesDb.getInstance(context);
    }

    // Do not pass arguments via execute; the object is never reused so it is quite safe to pass
    // the arguments in the constructor.
    @NonNull
    @Override
    protected Boolean doInBackground(Object... stuffs) {
        Log.d(TAG, "doInBackground");
        myNoteList = new ArrayList<>();
        storedNotes.GetStoredNotes(myNoteList, accountName, sortOrder, hashFilter);
        return true;
    }

    protected void onPostExecute(Boolean result) {
        Log.d(TAG, "onPostExecute");
        this.notesList.clear();
        this.notesList.addAll(myNoteList);
        if (filterString.length() > 0)
            this.adapter.getFilter().filter(filterString);
        this.adapter.notifyDataSetChanged();
    }
}
