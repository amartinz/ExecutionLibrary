package alexander.martinz.libs.execution.binaries;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

import alexander.martinz.libs.execution.RootShell;
import alexander.martinz.libs.execution.ShellHelper;
import alexander.martinz.libs.execution.ShellLogger;
import alexander.martinz.libs.execution.ShellManager;

public class BusyBox {
    private static final String TAG = BusyBox.class.getSimpleName();

    private static final String[] PATH_BUSYBOX = new String[]{
            "/system/bin/busybox", "/system/xbin/busybox"
    };

    private static Boolean sHasBusybox= null;

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
            return true;
        }
        if (ShellLogger.DEBUG) {
            Log.d(TAG, "no busybox binary found, trying with hit and miss");
        }

        final String busyboxLocation = ShellHelper.findBinary("busybox");
        if (!TextUtils.isEmpty(busyboxLocation)) {
            if (ShellLogger.DEBUG) {
                Log.d(TAG, String.format("Found busybox path: %s", busyboxLocation));
            }
            sHasBusybox = true;
            return true;
        }
        return sHasBusybox;
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

    @Nullable public static String callBusyBoxApplet(@NonNull Context context, @NonNull String applet, @Nullable String args) {
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
            Log.e(TAG, String.format("No args specified, returning -> %s", cmd));
            return cmd;
        }

        cmd = String.format("%s %s", cmd, args);
        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("Calling applet with args -> %s", cmd));
        }
        return cmd;
    }
}
