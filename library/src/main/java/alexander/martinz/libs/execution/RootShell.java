package alexander.martinz.libs.execution;

import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.exceptions.RootDeniedException;

public class RootShell extends Shell {
    protected RootShell() throws IOException, TimeoutException, RootDeniedException {
        super(true);
    }

    @Nullable public static String fireAndBlock(Command command) {
        final RootShell shell = ShellManager.get().getRootShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockInternal(command, shell).getOutput();
    }

    @Nullable public static String fireAndBlockString(Command command) {
        final RootShell shell = ShellManager.get().getRootShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockStringInternal(command, shell).getOutput();
    }

    @Nullable public static List<String> fireAndBlockList(Command command) {
        final RootShell shell = ShellManager.get().getRootShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockListInternal(command, shell).getOutputList();
    }

    @Nullable public static Command fireAndForget(Command command) {
        final RootShell shell = ShellManager.get().getRootShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndForgetInternal(command, shell);
    }
}
