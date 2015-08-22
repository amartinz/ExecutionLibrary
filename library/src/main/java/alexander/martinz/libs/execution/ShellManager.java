package alexander.martinz.libs.execution;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.binaries.Installer;
import alexander.martinz.libs.execution.exceptions.RootDeniedException;
import alexander.martinz.libs.logger.Logger;

public class ShellManager {
    private static ShellManager sInstance;

    private static ArrayList<RootShell> rootShells = new ArrayList<>();
    private static ArrayList<NormalShell> normalShells = new ArrayList<>();

    private ShellManager(@NonNull Context context) {
        cleanupShells();

        Installer.installBusyBox(context);
    }

    @NonNull public static ShellManager get(@NonNull Context context) {
        if (sInstance == null) {
            sInstance = new ShellManager(context);
        }
        return sInstance;
    }

    @Nullable public RootShell getRootShell() {
        return getRootShell(false);
    }

    @Nullable public RootShell getRootShell(boolean newShell) {
        RootShell rootShell;
        if (!newShell && rootShells.size() > 0) {
            rootShell = rootShells.get(0);
            if (rootShell != null) {
                return rootShell;
            }
        }

        rootShell = createRootShell();
        rootShells.add(rootShell);
        return rootShell;
    }

    @Nullable private RootShell createRootShell() {
        try {
            return new RootShell();
        } catch (IOException | TimeoutException | RootDeniedException e) {
            Logger.e(this, "Error creating new root shell", e);
        }
        return null;
    }

    @Nullable public NormalShell getNormalShell() {
        return getNormalShell(false);
    }

    @Nullable public NormalShell getNormalShell(boolean newShell) {
        NormalShell normalShell;
        if (!newShell && normalShells.size() > 0) {
            normalShell = normalShells.get(0);
            if (normalShell != null) {
                return normalShell;
            }
        }

        normalShell = createNormalShell();
        normalShells.add(normalShell);
        return normalShell;
    }

    @Nullable private NormalShell createNormalShell() {
        try {
            return new NormalShell();
        } catch (IOException | TimeoutException | RootDeniedException e) {
            Logger.e(this, "Error creating new shell", e);
        }
        return null;
    }

    public synchronized void cleanupRootShells() {
        if (rootShells == null) {
            rootShells = new ArrayList<>();
        }
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

    public synchronized void cleanupNormalShells() {
        if (normalShells == null) {
            normalShells = new ArrayList<>();
        }
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

    public synchronized int getNormalShellCount() {
        return normalShells.size();
    }

    public synchronized int getRootShellCount() {
        return rootShells.size();
    }

    public synchronized void cleanupShells() {
        cleanupRootShells();
        cleanupNormalShells();
    }

    public synchronized void onDestroy() {
        cleanupShells();
    }
}
