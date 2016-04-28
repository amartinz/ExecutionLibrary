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
        searchPaths = ensureListFormat(searchPaths);

        for (final String searchPath : searchPaths) {
            final File expectedBinary = new File(searchPath, binaryName);
            if (expectedBinary.exists()) {
                foundLocations.add(searchPath);
            }
        }

        if (!foundLocations.isEmpty()) {
            return foundLocations;
        }


        final String busybox = RootCheck.isRooted()
                ? RootShell.fireAndBlockString("which busybox")
                : NormalShell.fireAndBlockString("which busybox");
        if (!TextUtils.isEmpty(busybox) && busybox.endsWith("/busybox")) {
            foundLocations.add(0, busybox.trim());
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

    @NonNull private static List<String> ensureListFormat(@NonNull final List<String> listToFormat) {
        final ArrayList<String> formattedList = new ArrayList<>();

        // ensure that all our paths end with /
        for (final String searchPath : listToFormat) {
            final String path = (searchPath.endsWith("/") ? searchPath : String.format("%s/", searchPath));
            formattedList.add(path);
        }

        return formattedList;
    }
}
