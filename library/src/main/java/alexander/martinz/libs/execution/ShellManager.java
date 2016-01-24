package alexander.martinz.libs.execution;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.binaries.Installer;
import alexander.martinz.libs.execution.exceptions.RootDeniedException;

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
                    rootShell.close();
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
                    normalShell.close();
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
