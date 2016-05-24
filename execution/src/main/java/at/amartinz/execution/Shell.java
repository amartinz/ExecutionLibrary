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

import android.text.TextUtils;
import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import at.amartinz.execution.exceptions.RootDeniedException;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

public abstract class Shell {
    private static final String TAG = Shell.class.getSimpleName();

    public static final int DEFAULT_TIMEOUT = 15000;

    private static final String TOKEN = "Y#*N^W^T@#@G";

    public int shellTimeout = DEFAULT_TIMEOUT;

    public boolean isRoot;

    private boolean isCleaning;
    private boolean isClosed;
    private boolean isExecuting;

    public String error;

    private final Process process;

    private final BufferedSource bufferedSourceInput;
    private final BufferedSource bufferedSourceError;

    private final BufferedSink bufferedSinkOutput;

    private boolean shouldClose;

    private final List<Command> commands = new ArrayList<>();
    private final int maxCommands = 1000;
    private int totalExecuted;
    private int toWrite;
    private int totalRead;
    private int toRead;

    protected Shell(boolean isRoot) throws IOException, TimeoutException, RootDeniedException {
        this.isRoot = isRoot;

        final String cmd = (isRoot ? "su" : "/system/bin/sh");
        this.process = Runtime.getRuntime().exec(cmd);

        this.bufferedSourceInput = Okio.buffer(Okio.source(process.getInputStream()));
        this.bufferedSourceError = Okio.buffer(Okio.source(process.getErrorStream()));
        this.bufferedSinkOutput = Okio.buffer(Okio.sink(process.getOutputStream()));

        final Worker worker = new Worker(this);
        worker.start();

        try {
            worker.join(this.shellTimeout);

            switch (worker.exitCode) {
                case Worker.EXIT_TIMEOUT: {
                    try {
                        this.process.destroy();
                    } catch (Exception ignored) { }

                    closeStreams();

                    throw new TimeoutException(this.error);
                }

                case Worker.EXIT_ERROR: {
                    try {
                        this.process.destroy();
                    } catch (Exception ignored) { }

                    closeStreams();

                    throw new RootDeniedException(this.error);
                }

                default:
                case Worker.EXIT_SUCCESS: {
                    final Thread inputThread = new Thread(this.inputRunnable, "Shell input");
                    inputThread.setPriority(Thread.NORM_PRIORITY);
                    inputThread.start();

                    final Thread outputThread = new Thread(this.outputRunnable, "Shell doOutput");
                    inputThread.setPriority(Thread.NORM_PRIORITY);
                    outputThread.start();
                }
            }
        } catch (InterruptedException ie) {
            worker.interrupt();
            Thread.currentThread().interrupt();
            throw new TimeoutException();
        }
    }

    public synchronized boolean isCleaning() {
        return isCleaning;
    }

    public synchronized boolean shouldClose() {
        return shouldClose;
    }

    public synchronized boolean isClosed() {
        return isClosed;
    }

    public synchronized boolean isExecuting() {
        return isExecuting;
    }

    public Command add(final Command command) {
        if (shouldClose) {
            throw new IllegalStateException("Unable to add commands to a closed shell");
        }

        while (isCleaning()) {
            // wait until we are done cleaning
        }
        command.resetCommand();
        commands.add(command);

        notifyThreads();
        return command;
    }

    protected static Command fireAndBlockInternal(final Command command, final Shell shell) {
        return shell.add(command).waitFor();
    }

    protected static Command fireAndBlockStringInternal(final Command command, final Shell shell) {
        return shell.add(command.setOutputType(Command.OUTPUT_STRING)).waitFor();
    }

    protected static Command fireAndBlockStringNewlineInternal(final Command command, final Shell shell) {
        return shell.add(command.setOutputType(Command.OUTPUT_STRING_NEWLINE)).waitFor();
    }

    protected static Command fireAndBlockListInternal(final Command command, final Shell shell) {
        return shell.add(command.setOutputType(Command.OUTPUT_LIST)).waitFor();
    }

    protected static Command fireAndForgetInternal(final Command command, final Shell shell) {
        return shell.add(command);
    }

    protected void notifyThreads() {
        final Thread t = new Thread(new Runnable() {
            @Override public void run() {
                synchronized (commands) {
                    commands.notifyAll();
                }
            }
        });
        t.start();
    }

    public void close() {
        int count = 0;
        while (isExecuting()) {
            if (ShellLogger.DEBUG) {
                Log.v(TAG, "Waiting on shell to finish executing before closing...");
            }
            count++;

            // fail safe
            if (count > 10000) {
                break;
            }
        }

        synchronized (commands) {
            this.shouldClose = true;
            this.notifyThreads();
        }

        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("Shell closed! - %s", this));
        }
    }

    private void closeStreams() {
        IoUtils.closeQuietly(this.bufferedSourceInput);
        IoUtils.closeQuietly(this.bufferedSourceError);
        IoUtils.closeQuietly(this.bufferedSinkOutput);
    }

    private synchronized void cleanupCommands() {
        this.isCleaning = true;
        final int toClean = Math.abs(this.maxCommands - (this.maxCommands / 4));
        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("Cleaning up: %s", toClean));
        }

        for (int i = 0; i < toClean; i++) {
            this.commands.remove(0);
        }

        this.toRead = this.toWrite = this.commands.size() - 1;
        this.isCleaning = false;
    }

    private final Runnable inputRunnable = new Runnable() {
        @Override public void run() {
            try {
                while (true) {
                    synchronized (commands) {
                        while (!shouldClose && toWrite >= commands.size()) {
                            isExecuting = false;
                            commands.wait();
                        }
                    }

                    if (toWrite >= maxCommands) {
                        if (ShellLogger.DEBUG && (toRead != toWrite)) {
                            Log.v(TAG, "Waiting for r/w to catch up before cleanup");
                        }
                        while (toRead != toWrite) {
                            // wait
                        }
                        cleanupCommands();
                    }

                    if (toWrite < commands.size()) {
                        isExecuting = true;
                        final Command cmd = commands.get(toWrite);
                        cmd.startExecution();

                        final String[] toExecute = cmd.getCommands();
                        for (final String cmdToExecute : toExecute) {
                            if (TextUtils.isEmpty(cmdToExecute)) {
                                continue;
                            }
                            bufferedSinkOutput.writeUtf8(cmdToExecute);
                        }

                        final String line = String.format("\necho %s %s $?\n", TOKEN, totalExecuted);
                        bufferedSinkOutput.writeUtf8(line);
                        bufferedSinkOutput.flush();
                        toWrite++;
                        totalExecuted++;
                    } else if (shouldClose) {
                        isExecuting = false;
                        bufferedSinkOutput.writeUtf8("\nexit 0\n");
                        bufferedSinkOutput.flush();
                        return;
                    }
                }
            } catch (IOException | InterruptedException e) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, "IOException | InterruptedException", e);
                }
            } finally {
                toWrite = 0;
                closeStreams();
            }
        }
    };

    private final Runnable outputRunnable = new Runnable() {
        @Override public void run() {
            Command command = null;

            try {
                //as long as there is something to read, we will keep reading.
                while (!shouldClose || IoUtils.isReady(bufferedSourceInput) || toRead < commands.size()) {
                    String outputLine = bufferedSourceInput.readUtf8Line();

                    // EOF, shell closed?
                    if (outputLine == null) {
                        break;
                    }

                    if (command == null) {
                        if (toRead >= commands.size()) {
                            if (shouldClose) {
                                break;
                            }
                            continue;
                        }
                        command = commands.get(toRead);
                    }

                    final int pos = outputLine.indexOf(TOKEN);
                    if (pos == -1) {
                        // send the doOutput for the implementer to process
                        command.doOutput(command.id, outputLine);
                    } else if (pos > 0) {
                        // token is suffix of doOutput, send doOutput part to implementer
                        command.doOutput(command.id, outputLine.substring(0, pos));
                    }

                    if (pos < 0) {
                        continue;
                    }

                    outputLine = outputLine.substring(pos);
                    final String fields[] = outputLine.split(" ");

                    if (fields.length < 2 || fields[1] == null) {
                        continue;
                    }

                    int id = 0;
                    try {
                        id = Integer.parseInt(fields[1]);
                    } catch (NumberFormatException ignored) { }

                    int exitCode = -1;
                    try {
                        exitCode = Integer.parseInt(fields[2]);
                    } catch (NumberFormatException ignored) { }

                    if (id != totalRead) {
                        continue;
                    }
                    processErrors(command);

                    // processing doOutput
                    int iterations = 0;
                    while (command.totalOutput > command.totalOutputProcessed) {
                        if (iterations == 0) {
                            iterations++;
                        }

                        synchronized (this) {
                            try {
                                this.wait(shellTimeout);
                            } catch (Exception ignored) { }
                        }
                    }

                    command.setExitCode(exitCode);
                    command.commandFinished();
                    command = null;

                    toRead++;
                    totalRead++;
                }

                try {
                    process.waitFor();
                    process.destroy();
                } catch (Exception ignored) { }

                while (toRead < commands.size()) {
                    if (command == null) {
                        command = commands.get(toRead);
                    }

                    if (command.totalOutput < command.totalOutputProcessed) {
                        command.terminate("Did not process all doOutput!");
                    } else {
                        command.terminate("Unexpected termination!");
                    }

                    command = null;
                    toRead++;
                }
                toRead = 0;
            } catch (IOException e) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, "IOException", e);
                }
            } finally {
                closeStreams();
                isClosed = true;
            }
        }
    };

    public void processErrors(Command command) {
        try {
            while (command != null && IoUtils.isReady(bufferedSourceError)) {
                final String line = bufferedSourceError.readUtf8Line();
                if (line == null) {
                    // EOF, shell closed?
                    break;
                }
                command.doOutput(command.id, line);
            }
        } catch (Exception e) {
            if (ShellLogger.DEBUG) {
                Log.e(TAG, "Error while processing errors. Can you see the irony?", e);
            }
        }
    }

    protected static class Worker extends Thread {
        private static final String OPENING = "echo Opening\n";

        public static final int EXIT_TIMEOUT = -10239;
        public static final int EXIT_ERROR = -10339;
        public static final int EXIT_SUCCESS = 1;

        public final Shell shell;
        private int exitCode;

        private Worker(Shell shell) {
            this.shell = shell;
            this.exitCode = EXIT_TIMEOUT;
        }

        @Override public void run() {
            try {
                shell.bufferedSinkOutput.writeUtf8(OPENING);
                shell.bufferedSinkOutput.flush();

                // Check if we get "Opening" returned to check if we have properly opened a shell
                while (true) {
                    final String line = shell.bufferedSourceInput.readUtf8Line();
                    if (line == null) {
                        // we are done and still did not get our "Opening" so something is fishy
                        throw new EOFException();
                    } else if ("".equals(line)) {
                        // let's continue checking
                    } else if ("Opening".equals(line)) {
                        this.exitCode = EXIT_SUCCESS;
                        setupShellOom();
                        break;
                    }
                }

                shell.error = "Unknown error occurred";
            } catch (IOException ioe) {
                this.exitCode = EXIT_ERROR;

                final StringBuilder sb = new StringBuilder();
                sb.append("Could not open shell -> ").append(ioe.getMessage()).append('\n');
                if (shell.isRoot) {
                    sb.append("Maybe root got denied?\n");
                }
                shell.error = sb.toString();
            }
        }

        private void setupShellOom() {
            // we need the shell process' pid
            int pid;

            final Class<?> processClass = shell.process.getClass();
            Field field;
            try {
                field = processClass.getDeclaredField("pid");
            } catch (NoSuchFieldException e) {
                try {
                    field = processClass.getDeclaredField("id");
                } catch (NoSuchFieldException e1) {
                    field = null;
                }
            }

            if (field != null) {
                field.setAccessible(true);
                try {
                    pid = (Integer) field.get(shell.process);
                } catch (IllegalAccessException iae) {
                    if (ShellLogger.DEBUG) {
                        Log.e(TAG, "IllegalAccessException", iae);
                    }
                    pid = -1;
                }
            } else {
                pid = -1;
            }

            if (pid == -1) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, "could not get pid via reflection!");
                }
                return;
            }

            try {
                setupOomAdj(pid);
            } catch (Exception e) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, String.format("Could not set shell oom adj for pid %s!", pid), e);
                }
            }

            try {
                setupOomScoreAdj(pid);
            } catch (Exception e) {
                if (ShellLogger.DEBUG) {
                    Log.e(TAG, String.format("Could not set shell oom score adj for pid %s!", pid), e);
                }
            }
        }

        private void setupOomAdj(final int pid) throws Exception {
            shell.bufferedSinkOutput.writeUtf8("(echo -17 > /proc/" + pid + "/oom_adj) &> /dev/null\n");
            shell.bufferedSinkOutput.writeUtf8("(echo -17 > /proc/$$/oom_adj) &> /dev/null\n");
            shell.bufferedSinkOutput.flush();
        }

        private void setupOomScoreAdj(final int pid) throws Exception {
            shell.bufferedSinkOutput.writeUtf8("(echo -1000 > /proc/" + pid + "/oom_score_adj) &> /dev/null\n");
            shell.bufferedSinkOutput.writeUtf8("(echo -1000 > /proc/$$/oom_score_adj) &> /dev/null\n");
            shell.bufferedSinkOutput.flush();
        }
    }
}
