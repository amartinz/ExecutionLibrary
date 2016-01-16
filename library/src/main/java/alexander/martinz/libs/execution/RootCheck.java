/*
 * Copyright (C) 2013 - 2016 Alexander Martinz
 *
 * This program is free software: you can redistribute it and/or modify
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
package alexander.martinz.libs.execution;

import android.support.annotation.Nullable;
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

    private static Boolean sIsRooted = null;

    /**
     * @return true if the device is rooted, false if not
     * <p/>
     * The result is cached, for a non cached result, use {@link RootCheck#isRooted(boolean)}
     */
    public static boolean isRooted() {
        return isRooted(false);
    }

    /**
     * @param forceRootCheck Whether to use the cached result or force a new check
     * @return true if the device is rooted, false if not
     */
    public static boolean isRooted(boolean forceRootCheck) {
        if (!forceRootCheck && sIsRooted != null) {
            return sIsRooted;
        }

        final String suPath = getSuPath();
        if (!TextUtils.isEmpty(suPath)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found su path: %s", suPath));
            }
            sIsRooted = true;
            return true;
        }
        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no binary found, trying with hit and miss");
        }

        // fire and forget id, just for fun
        RootShell.fireAndForget("id");

        final RootShell rootShell = ShellManager.get().getRootShell();
        sIsRooted = (rootShell != null);
        if (ShellLogger.DEBUG) {
            Log.d(TAG, String.format("is rooted: %s", sIsRooted));
        }
        return sIsRooted;
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
