/*
 * Copyright (C) 2022-2025 - Peter Korf <peter@niendo.de>
 * Copyright (C)         ? - kwhitefoot
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

package nl.enimatek.imapnotesx;

import static android.os.Environment.DIRECTORY_DOCUMENTS;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.google.android.material.snackbar.BaseTransientBottomBar;
import com.google.android.material.snackbar.Snackbar;

import java.io.BufferedInputStream;
import java.io.File;
import java.lang.ref.WeakReference;

public class ImapNotes3 extends Application {
    private static final String TAG = "IN_ImapNotes3";
    // Set once in onCreate() to the application context, so it can never hold on to an activity -
    // which is what this lint check is normally about.
    @SuppressLint("StaticFieldLeak")
    private static Context mContext;
    // The content view of the screen that most recently offered itself as the Snackbar anchor, used
    // when a caller has no view of its own.  Held weakly on purpose: a strong reference would keep
    // the whole view tree of a destroyed activity alive for the lifetime of the process.
    @Nullable
    private static WeakReference<View> mContent;
    public static Intent intent; // For Data-Exchange SyncAdapater
    private static final String ReservedChars = "[\\\\/:*?\"<>|'!]";
    public static final Integer MainLock = 0;

    public static Context getAppContext() {
        return mContext;
    }

    public static File GetRootDir() {
        return mContext.getFilesDir();
    }

    public static File GetAccountDir(String account) {
        return new File(GetRootDir(), RemoveReservedChars(account));
    }

    public static String RemoveReservedChars(String data) {
        return data.replaceAll(ReservedChars, "_");
    }

    public static File GetSharedPrefsDir() {
        return new File(mContext.getFilesDir().getParent(), "shared_prefs");
    }

    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
    }

    private final Thread.UncaughtExceptionHandler defaultUEH;

    public static void setContent(View content) {
        mContent = new WeakReference<>(content);
    }

    @Nullable
    private static View getContent() {
        return mContent == null ? null : mContent.get();
    }
    // handler listener
    // from https://stackoverflow.com/questions/12077318/how-to-catch-the-unknown-exception-in-run-time-android
    private Thread.UncaughtExceptionHandler _unCaughtExceptionHandler =
            new Thread.UncaughtExceptionHandler() {
                @Override
                public void uncaughtException(Thread thread, Throwable e) {
                    // Keep the stacktrace in the preferences, the debug report exports it.
                    SharedPreferences.Editor preferences = mContext.getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit();
                    preferences.putString("uncaughtException", Log.getStackTraceString(e));
                    preferences.apply();

                    // Hand the exception on to the handler that was installed before ours, so the
                    // system still gets its logcat stacktrace, DropBox entry and crash dialog.  A
                    // System.exit() here used to run first and swallow all of that.
                    defaultUEH.uncaughtException(thread, e);
                }
            };
    public ImapNotes3() {
        defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
        // setup handler for uncaught exception
        Thread.setDefaultUncaughtExceptionHandler(_unCaughtExceptionHandler);
        if (BuildConfig.DEBUG)
//            StrictMode.enableDefaults();
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
//                    .penaltyDeath()
                    .build());
    }

    public static String UriToString(Uri uri) {
        StringBuilder sharedData = new StringBuilder();
        BufferedInputStream bufferedInputStream;
        try {
            bufferedInputStream =
                    new BufferedInputStream(mContext.getContentResolver().openInputStream(uri));
            byte[] contents = new byte[1024];
            int bytesRead;
            while ((bytesRead = bufferedInputStream.read(contents)) != -1) {
                sharedData.append(new String(contents, 0, bytesRead));
            }
            bufferedInputStream.close();
            return (sharedData.toString());
        } catch (Exception e) {
            Log.e(TAG, "UriToString failed", e);
            return e.getLocalizedMessage();
        }
    }

    public static Snackbar ShowAction(
            View view,
            @StringRes int actionTextId,
            @StringRes int actionButtonId,
            int durationSeconds,
            Runnable actionCallback) {

        if (view == null)
            view = getContent();
        if (view == null) {
            // Neither the caller nor the fallback anchor has a live view to attach to.
            Log.w(TAG, "ShowAction: no view to attach the action to");
            return null;
        }

        if (durationSeconds == 0)
            durationSeconds = BaseTransientBottomBar.LENGTH_INDEFINITE;
        else
            durationSeconds = durationSeconds * 1000;
        Snackbar snackbar =
                Snackbar.make(view, actionTextId, durationSeconds)
                        .setAction(actionButtonId, v -> actionCallback.run());

        snackbar
                .getView()
                .setBackgroundColor(mContext.getColor(R.color.ShareActionBgColor));
        snackbar.setTextColor(mContext.getColor(R.color.ShareActionTxtColor));
        snackbar.setActionTextColor(mContext.getColor(R.color.ShareActionTxtColor));
        snackbar.show();
        return snackbar;
    }

    /**
     * Carries a note body from the editor to the notes list outside of the Intent.  A long note can
     * be bigger than the binder transaction limit, so putting it into the result Intent kills the app
     * with a TransactionTooLargeException.  Package-private on purpose: only the two screens that
     * take part in the hand-over may touch it, and the receiver clears it when it reads it
     * ({@code ListActivity.TakeNoteBodyFromEditor}).
     */
    @Nullable
    static String AvoidLargeBundle;

    public static void ShowMessage(@StringRes int resId, View view, int durationSeconds) {
        ShowMessage(mContext.getResources().getString(resId), view, durationSeconds);
    }

    public static void ShowMessage(String message, View view, int durationSeconds) {

        if (view == null)
            view = getContent();
        if (view == null) {
            // Neither the caller nor the fallback anchor has a live view to attach to.
            Log.w(TAG, "ShowMessage: no view to attach the message to: " + message);
            return;
        }

        Snackbar snackbar =
                Snackbar.make(view, message, durationSeconds * 1000);
        snackbar
                .getView()
                .setBackgroundColor(mContext.getColor(R.color.ShareActionBgColor));
        snackbar.setTextColor(mContext.getColor(R.color.ShareActionTxtColor));
        snackbar.setActionTextColor(mContext.getColor(R.color.ShareActionTxtColor));

        snackbar.show();
    }


    public static int loadPreferenceColor(String name, int defValue) {
        SharedPreferences preferences = mContext.getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE);
        return preferences.getInt(name + "_" + mContext.getString(R.string.ColorMode), defValue);
    }

    public static void savePreferenceColor(String name, int value) {
        SharedPreferences.Editor preferences = mContext.getSharedPreferences(SettingsActivity.MAIN_PREFERENCE_NAME, MODE_PRIVATE).edit();
        preferences.putInt(name + "_" + mContext.getString(R.string.ColorMode), value);
        preferences.apply();
    }

    public static File GetDocumentDir() {
        return Environment.getExternalStoragePublicDirectory(DIRECTORY_DOCUMENTS);
    }
}
