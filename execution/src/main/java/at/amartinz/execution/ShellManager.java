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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import at.amartinz.execution.exceptions.RootDeniedException;

public class ShellManager {
    private static final String TAG = ShellManager.class.getSimpleName();

    private static ShellManager sInstance;

    private static final ArrayList<RootShell> rootShells = new ArrayList<>();
    private static final ArrayList<NormalShell> normalShells = new ArrayList<>();

    private ShellManager() {
        cleanupShells();
    }

    @NonNull public static ShellManager get() {
        if (sInstance == null) {
            sInstance = new ShellManager();
        }
        return sInstance;
    }

    public static void enableDebug(boolean enableDebug) {
        ShellLogger.DEBUG = enableDebug;
    }

    public static boolean isDebug() {
        return ShellLogger.DEBUG;
    }

    public ShellManager installBusyBox(@NonNull Context context) {
        Installer.installBusyBox(context);
        return this;
    }

    @Nullable public RootShell getRootShell() {
        return getRootShell(false);
    }

    @Nullable public RootShell getRootShell(boolean newShell) {
        RootShell rootShell;

        synchronized (rootShells) {
            if (!newShell && rootShells.size() > 0) {
                rootShell = rootShells.get(0);
                if (rootShell != null) {
                    return rootShell;
                }
            }
        }

        rootShell = createRootShell();

        synchronized (rootShells) {
            rootShells.add(rootShell);
        }
        return rootShell;
    }

    @Nullable private RootShell createRootShell() {
        try {
            return new RootShell();
        } catch (IOException | TimeoutException | RootDeniedException e) {
            if (ShellLogger.DEBUG) {
                Log.e(TAG, "Error creating new root shell", e);
            }
        }
        return null;
    }

    @Nullable public NormalShell getNormalShell() {
        return getNormalShell(false);
    }

    @Nullable public NormalShell getNormalShell(boolean newShell) {
        NormalShell normalShell;

        synchronized (normalShells) {
            if (!newShell && normalShells.size() > 0) {
                normalShell = normalShells.get(0);
                if (normalShell != null) {
                    return normalShell;
                }
            }
        }

        normalShell = createNormalShell();

        synchronized (normalShells) {
            normalShells.add(normalShell);
        }
        return normalShell;
    }

    @Nullable private NormalShell createNormalShell() {
        try {
            return new NormalShell();
        } catch (IOException | TimeoutException | RootDeniedException e) {
            if (ShellLogger.DEBUG) {
                Log.e(TAG, "Error creating new shell", e);
            }
        }
        return null;
    }

    public void cleanupRootShells() {
        synchronized (rootShells) {
            if (rootShells.size() > 0) {
                final Iterator<RootShell> rootShellIterator = rootShells.iterator();
                while (rootShellIterator.hasNext()) {
                    final RootShell rootShell = rootShellIterator.next();
                    if (rootShell != null) {
                        rootShell.close();
                    }
                    rootShellIterator.remove();
                }
                rootShells.clear();
            }
        }
    }

    public void cleanupNormalShells() {
        synchronized (normalShells) {
            if (normalShells.size() > 0) {
                final Iterator<NormalShell> normalShellIterator = normalShells.iterator();
                while (normalShellIterator.hasNext()) {
                    final NormalShell normalShell = normalShellIterator.next();
                    if (normalShell != null) {
                        normalShell.close();
                    }
                    normalShellIterator.remove();
                }
                normalShells.clear();
            }
        }
    }

    public int getNormalShellCount() {
        synchronized (normalShells) {
            return normalShells.size();
        }
    }

    public int getRootShellCount() {
        synchronized (rootShells) {
            return rootShells.size();
        }
    }

    public void cleanupShells() {
        cleanupRootShells();
        cleanupNormalShells();
    }

    public void onDestroy() {
        cleanupShells();
    }
}
