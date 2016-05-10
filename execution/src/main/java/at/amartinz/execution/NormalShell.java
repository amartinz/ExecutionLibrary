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

import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeoutException;

import at.amartinz.execution.exceptions.RootDeniedException;

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

    @Nullable public static String fireAndBlockStringNewline(String command) {
        return fireAndBlockStringNewline(new Command(command));
    }

    @Nullable public static String fireAndBlockStringNewline(Command command) {
        final NormalShell shell = ShellManager.get().getNormalShell();
        if (shell == null) {
            return null;
        }
        return Shell.fireAndBlockStringNewlineInternal(command, shell).getOutput();
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
