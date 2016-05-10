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

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

public class RootCheck {
    private static final String TAG = RootCheck.class.getSimpleName();

    private static final String[] PATH_SU = new String[]{
            "/system/bin/su", "/system/xbin/su", "/system/bin/.ext/.su", "/system/xbin/sugote",
            // "system less" root (supersu)
            "/su/bin/su"
    };

    private static Boolean isRooted = null;
    private static Boolean isRootGranted = null;
    private static String suVersion = null;

    /**
     * @return true if the device is rooted, false if not
     * <br>
     * The result is cached, for a non cached result, use {@link RootCheck#isRooted(boolean)}
     */
    public static boolean isRooted() {
        return isRooted(false);
    }

    /**
     * @param forceCheck Whether to use the cached result or force a new check
     * @return true if the device is rooted, false if not
     */
    public static boolean isRooted(boolean forceCheck) {
        if (!forceCheck && isRooted != null) {
            return isRooted;
        }

        final String suPath = getSuPath();
        if (!TextUtils.isEmpty(suPath)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found su path: %s", suPath));
            }
            isRooted = true;
            return true;
        }
        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no su binary found, trying with hit and miss");
        }

        // fire and forget id, just for fun
        RootShell.fireAndForget("id");

        final RootShell rootShell = ShellManager.get().getRootShell();
        isRooted = (rootShell != null);
        if (ShellLogger.DEBUG) {
            Log.d(TAG, String.format("is rooted: %s", isRooted));
        }
        return isRooted;
    }

    /**
     * @return true if the device is rooted and root is granted, false if not
     * <br>
     * The result is cached, for a non cached result, use {@link RootCheck#isRootGranted(boolean)}
     */
    @WorkerThread public static boolean isRootGranted() {
        return isRootGranted(false);
    }

    /**
     * @param forceCheck Whether to use the cached result or force a new check
     * @return true if the device is rooted and root is granted, false if not
     */
    @WorkerThread public static boolean isRootGranted(boolean forceCheck) {
        if (!forceCheck && isRootGranted != null) {
            return isRootGranted;
        }

        // no root available means we can not get root granted as well
        if (!isRooted()) {
            isRootGranted = false;
            return false;
        }

        final String result = RootShell.fireAndBlockString("id");
        if (TextUtils.isEmpty(result)) {
            // we did not get any result, means the shell did not work
            isRootGranted = false;
            return false;
        }

        isRootGranted = result.contains("uid=0");
        return isRootGranted;
    }

    @WorkerThread @NonNull public static String getSuVersion() {
        return getSuVersion(false);
    }

    @WorkerThread @NonNull public static String getSuVersion(boolean forceCheck) {
        if (forceCheck || suVersion == null) {
            String version = isRooted() ? NormalShell.fireAndBlockString("su -v") : "-";
            if (TextUtils.isEmpty(version)) {
                version = RootShell.fireAndBlockString("su -v");
            }
            suVersion = TextUtils.isEmpty(version) ? "-" : version;
        }
        return suVersion;
    }

    /**
     * @return The path of the su binary or null if none found
     */
    @Nullable public static String getSuPath() {
        for (final String path : PATH_SU) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }
}
