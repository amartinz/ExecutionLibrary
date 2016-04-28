/*
 * The MIT License
 *
 * Copyright (c) 2016 Alexander Martinz
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package at.amartinz.execution;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Installer {
    private static final String TAG = Installer.class.getSimpleName();

    private static final String KEY_BUSYBOX_VERSION = "busybox_version";
    // increment whenever you update assets/busybox
    private static final int BUSYBOX_VERSION = 1;

    public static boolean installBusyBox(@NonNull final Context context) {
        return installBusyBox(context, false);
    }

    public static boolean installBusyBox(@NonNull final Context context, boolean force) {
        if (!force && binaryExists(context, "busybox")) {
            if (ShellLogger.DEBUG) {
                Log.v(TAG, "busybox already installed!");
            }
            return false;
        }
        return extractBinary(context, "busybox", KEY_BUSYBOX_VERSION, BUSYBOX_VERSION);
    }

    public static boolean extractBinary(@NonNull final Context context,
            @NonNull final String binaryName) {
        return extractBinary(context, binaryName, null, -1);
    }

    public static boolean extractBinary(@NonNull final Context context,
            @NonNull final String binaryName, @Nullable final String versionKey,
            final int versionNew) {
        final boolean checkVersion = (!TextUtils.isEmpty(versionKey) && versionNew != -1);
        final File filesDir = context.getFilesDir();
        if (!filesDir.exists()) {
            filesDir.mkdirs();
        }

        final File binary = new File(filesDir, binaryName);

        // extract the binary if it does not exist
        boolean shouldExtract = !binary.exists();

        // if we get binary versioning, also check if we have a newer version available
        final SharedPreferences prefs;
        if (checkVersion) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
            shouldExtract = shouldExtract || (prefs.getInt(versionKey, 0) < versionNew);
        } else {
            prefs = null;
        }
        if (shouldExtract) {
            boolean extractedBinary = false;

            final AssetManager am = context.getAssets();
            InputStream is = null;
            OutputStream os = null;
            try {
                is = am.open(binaryName);
                binary.createNewFile();
                os = new FileOutputStream(binary);

                BufferedInputStream bis = new BufferedInputStream(is);
                BufferedOutputStream bos = new BufferedOutputStream(os);
                try {
                    final byte[] buffer = new byte[1024];
                    int read;
                    while ((read = bis.read(buffer)) != -1) {
                        bos.write(buffer, 0, read);
                    }
                } finally {
                    IoUtils.closeQuietly(bos);
                    IoUtils.closeQuietly(bis);
                }

                // make it executable!
                binary.setExecutable(true);

                // we got until here, extraction is successful!
                extractedBinary = true;
            } catch (IOException ioe) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, String.format("Could not extract %s binary", binary), ioe);
                }
            } finally {
                IoUtils.closeQuietly(os);
                IoUtils.closeQuietly(is);
            }

            // update binary version if successfully extracted binary
            if (prefs != null && extractedBinary) {
                if (ShellLogger.DEBUG) {
                    Log.v(TAG, String.format("Successfully extracted %s version \"%s\"", binaryName, versionNew));
                }
                prefs.edit().putInt(versionKey, versionNew).apply();
            }
            return extractedBinary;
        }
        return false;
    }

    public static boolean binaryExists(@NonNull final Context context, @NonNull final String name) {
        final File filesDir = context.getFilesDir();
        if (!filesDir.exists()) {
            return false;
        }

        final File binary = new File(filesDir, name);
        return binary.exists() && binary.canExecute();
    }

}
