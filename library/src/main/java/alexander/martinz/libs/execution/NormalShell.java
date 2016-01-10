package alexander.martinz.libs.execution;

import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.exceptions.RootDeniedException;

public class NormalShell extends Shell {
    protected NormalShell() throws IOException, TimeoutException, RootDeniedException {
        super(false);
    }

    @Nullable public static String fireAndBlock(String command) {
        return fireAndBlock(new Command(command));
    }

    @Nullable public static String fireAndBlock(Command command) {
        final NormalShell shell = ShellManager.get().getNormalShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockInternal(command, shell).getOutput();
    }

    @Nullable public static String fireAndBlockString(String command) {
        return fireAndBlockString(new Command(command));
    }

    @Nullable public static String fireAndBlockString(Command command) {
        final NormalShell shell = ShellManager.get().getNormalShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockStringInternal(command, shell).getOutput();
    }

    @Nullable public static List<String> fireAndBlockList(String command) {
        return fireAndBlockList(new Command(command));
    }

    @Nullable public static List<String> fireAndBlockList(Command command) {
        final NormalShell shell = ShellManager.get().getNormalShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockListInternal(command, shell).getOutputList();
    }

    @Nullable public static Command fireAndForget(String command) {
        return fireAndForget(new Command(command));
    }

    @Nullable public static Command fireAndForget(Command command) {
        final NormalShell shell = ShellManager.get().getNormalShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndForgetInternal(command, shell);
    }
}
