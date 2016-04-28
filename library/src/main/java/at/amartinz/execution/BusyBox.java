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
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

public class BusyBox {
    private static final String TAG = BusyBox.class.getSimpleName();

    // my favorite
    private static final String PLAY_STORE_BUSYBOX = "https://play.google.com/store/apps/details?id=stericson.busybox";

    private static final String[] PATH_BUSYBOX = new String[]{
            "/system/bin/busybox", "/system/xbin/busybox"
    };

    private static final String[] PATH_TOYBOX = new String[]{
            "/system/bin/toybox", "/system/xbin/toybox"
    };

    private static Boolean sHasBusybox = null;
    private static String sBusyBoxPath = null;

    public static boolean isAvailable() {
        return isAvailable(false);
    }

    public static boolean isAvailable(boolean forceCheck) {
        if (!forceCheck && sHasBusybox != null) {
            return sHasBusybox;
        }

        final String busyboxPath = getBusyboxPath();
        if (!TextUtils.isEmpty(busyboxPath)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found busybox path: %s", busyboxPath));
            }
            sHasBusybox = true;
            sBusyBoxPath = busyboxPath;
            return true;
        }

        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no busybox binary found, trying with toybox");
        }

        final String toyboxPath = getToyboxPath();
        if (!TextUtils.isEmpty(toyboxPath)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found toybox path: %s", toyboxPath));
            }
            sHasBusybox = true;
            sBusyBoxPath = toyboxPath;
            return true;
        }

        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no busybox nor toybox binary found, trying with hit and miss");
        }

        final String busyboxLocation = ShellHelper.findBinary("busybox");
        if (!TextUtils.isEmpty(busyboxLocation)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found busybox path: %s", busyboxLocation));
            }
            sHasBusybox = true;
            sBusyBoxPath = busyboxLocation.endsWith("/")
                    ? String.format("%s%s", busyboxLocation, "busybox")
                    : String.format("%s/%s", busyboxLocation, "busybox");
            return true;
        }

        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no busybox via hit and miss found, come on toybox...");
        }

        final String toyboxLocation = ShellHelper.findBinary("toybox");
        if (!TextUtils.isEmpty(toyboxLocation)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found toybox path: %s", toyboxLocation));
            }
            sHasBusybox = true;
            sBusyBoxPath = toyboxLocation.endsWith("/")
                    ? String.format("%s%s", toyboxLocation, "toybox")
                    : String.format("%s/%s", toyboxLocation, "toybox");
            return true;
        }

        if (ShellLogger.DEBUG) {
            Log.d(TAG, "alright, i give up. no busybox or toybox detected.");
        }

        sHasBusybox = false;
        sBusyBoxPath = null;
        return false;
    }

    public static boolean isActuallyToybox() {
        return !TextUtils.isEmpty(sBusyBoxPath) && sBusyBoxPath.endsWith("toybox");
    }

    /**
     * @return The path of the busybox binary or null if none found
     */
    @Nullable public static String getBusyboxPath() {
        for (final String path : PATH_BUSYBOX) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    /**
     * @return The path of the toybox binary or null if none found
     */
    @Nullable public static String getToyboxPath() {
        for (final String path : PATH_TOYBOX) {
            if (new File(path).exists()) {
                return path;
            }
        }
        return null;
    }

    @Nullable public static String callBusyBoxApplet(@NonNull String applet) {
        return callBusyBoxApplet(applet, null);
    }

    @Nullable public static String callBusyBoxApplet(@NonNull String applet, @Nullable String args) {
        if (!isAvailable() || TextUtils.isEmpty(sBusyBoxPath)) {
            return null;
        }

        String cmd = String.format("%s %s", sBusyBoxPath, applet);
        if (TextUtils.isEmpty(args)) {
            Log.v(TAG, String.format("No args specified, returning -> %s", cmd));
            return cmd;
        }

        cmd = String.format("%s %s", cmd, args);
        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("Calling applet \"%s\" with args -> %s", applet, args));
        }
        return cmd;
    }

    @Nullable public static String callBusyBoxAppletInternal(@NonNull Context context, @NonNull String applet) {
        return callBusyBoxAppletInternal(context, applet, null);
    }

    @Nullable
    public static String callBusyBoxAppletInternal(@NonNull Context context, @NonNull String applet, @Nullable String args) {
        final File fileDir = context.getFilesDir();
        if (!fileDir.exists()) {
            if (ShellLogger.DEBUG) {
                Log.e(TAG, "Files folder does not exist!");
            }
            return null;
        }

        final File busybox = new File(fileDir, "busybox");
        if (!busybox.exists()) {
            Log.e(TAG, "busybox binary does not exist!");
            return null;
        }

        String cmd = String.format("%s %s", busybox.getAbsolutePath(), applet);
        if (TextUtils.isEmpty(args)) {
            Log.v(TAG, String.format("No args specified, returning -> %s", cmd));
            return cmd;
        }

        cmd = String.format("%s %s", cmd, args);
        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("Calling applet \"%s\" with args -> %s", applet, args));
        }
        return cmd;
    }

    public static void offerBusyBox(@NonNull Context context) {
        final Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_STORE_BUSYBOX));
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(i);
        } catch (Exception ignored) { }
    }
}
