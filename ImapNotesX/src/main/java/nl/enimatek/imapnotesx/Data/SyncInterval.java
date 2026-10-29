/*
 * Copyright (C) 2022-2023 - Peter Korf <peter@niendo.de>
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

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import de.niendo.ImapNotes3.R;


public enum SyncInterval {
    off(R.string.off, 0),
    t15min(R.string.t15min, 15),
    t1h(R.string.t1h, 60),
    t6h(R.string.t6h, 6 * 60),
    t1d(R.string.t1d, 24 * 60);

    @NonNull
    static final String TAG = "IN_SyncInterval";
    @SuppressLint("UseSparseArrays") // False positive, SparseArray cannot be used here.
    private static final Map<Integer, SyncInterval> _map = new HashMap<>();

    static {
        for (SyncInterval syncInterval : SyncInterval.values())
            _map.put(syncInterval.ordinal(), syncInterval);
    }

    public final int time;
    public final int textID;

    SyncInterval(int textID,
                 int time) {
        this.textID = textID;
        this.time = time;
    }

    @NonNull
    public static List<String> Printables(Resources res) {
        List<String> list = new ArrayList<>();
        for (SyncInterval e : SyncInterval.values()) {
            list.add(res.getString(e.textID));
        }
        return list;
    }

    @NonNull
    public static SyncInterval from(int ordinal) {
        SyncInterval interval = _map.get(ordinal);
        if (interval == null) interval = _map.get(t1h.ordinal());
        return interval;
    }

    @NonNull
    public static SyncInterval from(String name) {
        Log.d(TAG, "from: <" + name + ">");
        for (SyncInterval syncInterval : SyncInterval.values()) {
            if (Objects.equals(syncInterval.name(), name)) {
                return syncInterval;
            }
        }
        return t1h;
    }


}
