package alexander.martinz.libs.execution;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ShellHelper {

    @Nullable public static String findBinary(@NonNull String binaryName) {
        return findBinary(binaryName, null);
    }

    @Nullable public static String findBinary(@NonNull String binaryName, @Nullable List<String> searchPaths) {
        final List<String> foundLocations = findBinaryLocations(binaryName, searchPaths);
        if (foundLocations.isEmpty()) {
            return null;
        }

        final String firstLocation = foundLocations.get(0);
        if (TextUtils.isEmpty(firstLocation)) {
            return null;
        }
        return firstLocation;
    }

    @NonNull public static List<String> findBinaryLocations(@NonNull String binaryName) {
        return findBinaryLocations(binaryName, null);
    }

    @NonNull public static List<String> findBinaryLocations(@NonNull String binaryName, @Nullable List<String> searchPaths) {
        final ArrayList<String> foundLocations = new ArrayList<>();
        if (searchPaths == null) {
            searchPaths = getPath();
        }

        for (final String searchPath : searchPaths) {
            final File expectedBinary = new File(searchPath, binaryName);
            if (expectedBinary.exists()) {
                final String path = (searchPath.endsWith("/") ? searchPath : String.format("%s/", searchPath));
                foundLocations.add(path);
            }
        }

        if (!foundLocations.isEmpty()) {
            return foundLocations;
        }

        for (final String searchPath : searchPaths) {
            final String path = (searchPath.endsWith("/") ? searchPath : String.format("%s/", searchPath));
            final String binary = String.format("%s%s", path, binaryName);
            final String cmdResult = RootShell.fireAndBlockString(String.format("stat %s", binary));

            if (!TextUtils.isEmpty(cmdResult) && cmdResult.contains("File:") && cmdResult.contains(binaryName)) {
                foundLocations.add(path);
            }
        }
        return foundLocations;
    }

    @NonNull public static List<String> getPath() {
        final String path = System.getenv("PATH");
        if (TextUtils.isEmpty(path)) {
            return Collections.EMPTY_LIST;
        }
        return Arrays.asList(path.split(":"));
    }
}
