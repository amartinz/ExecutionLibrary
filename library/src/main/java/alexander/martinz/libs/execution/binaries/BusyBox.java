package alexander.martinz.libs.execution.binaries;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;

import alexander.martinz.libs.execution.ShellLogger;

public class BusyBox {
    private static final String TAG = BusyBox.class.getSimpleName();

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
