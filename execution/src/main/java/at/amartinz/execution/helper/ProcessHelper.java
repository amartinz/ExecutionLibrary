package at.amartinz.execution.helper;

import android.text.TextUtils;

import java.util.List;

import at.amartinz.execution.NormalShell;

/**
 * Created by amartinz on 06.09.16.
 */

public class ProcessHelper {
    public static final int INVALID = -1;

    public static int getUidFromPid(int pid) {
        int result = getUidFromPidPerStat(pid);
        if (result == INVALID) {
            result = getUidFromPidPerStatusFile(pid);
        }
        return result;
    }

    public static int getUidFromPidPerStat(int pid) {
        final String path = String.format("/proc/%s", pid);
        final String cmd = String.format("stat -c %%u %s", path);
        final String result = NormalShell.fireAndBlockString(cmd);

        if (!TextUtils.isEmpty(result)) {
            try {
                return Integer.parseInt(result.trim());
            } catch (NumberFormatException ignored) { }
        }
        return INVALID;
    }

    public static int getUidFromPidPerStatusFile(int pid) {
        final String path = String.format("/proc/%s/status", pid);
        final String cmd = String.format("cat %s", path);
        final List<String> result = NormalShell.fireAndBlockList(cmd);

        if (result == null || result.size() < 5) {
            return INVALID;
        }

        String uidLine = "";
        for (final String line : result) {
            if (line == null || !line.toLowerCase().startsWith("uid")) {
                continue;
            }

            uidLine = line;
            break;
        }

        // Uid: 10102 10102 10102 10102
        uidLine = uidLine.replace("\t", " ");
        final String[] splitted = uidLine.split(" ");
        if (splitted.length < 2) {
            return INVALID;
        }

        try {
            return Integer.parseInt(splitted[1]);
        } catch (Exception ignored) { }

        // try as a fallback
        try {
            return Integer.parseInt(splitted[2]);
        } catch (Exception ignored) { }

        return INVALID;
    }
}
