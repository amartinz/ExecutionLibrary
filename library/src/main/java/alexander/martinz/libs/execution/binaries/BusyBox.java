package alexander.martinz.libs.execution.binaries;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;

import alexander.martinz.libs.logger.Logger;

public class BusyBox {
    private static final String TAG = BusyBox.class.getSimpleName();

    @Nullable public static String callBusyBoxApplet(@NonNull final Context context,
            @NonNull final String applet, @Nullable final String args) {
        final File fileDir = context.getFilesDir();
        if (!fileDir.exists()) {
            Logger.e(TAG, "Files folder does not exist!");
            return null;
        }

        final File busybox = new File(fileDir, "busybox");
        if (!busybox.exists()) {
            Logger.e(TAG, "busybox binary does not exist!");
            return null;
        }

        String cmd = String.format("%s %s", busybox.getAbsolutePath(), applet);
        if (TextUtils.isEmpty(args)) {
            Logger.v(TAG, "No args specified, returning -> %s", cmd);
            return cmd;
        }

        cmd = String.format("%s %s", cmd, args);
        Logger.v(TAG, "Calling applet with args -> %s", cmd);
        return cmd;
    }
}
