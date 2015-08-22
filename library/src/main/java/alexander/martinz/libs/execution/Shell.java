package alexander.martinz.libs.execution;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.exceptions.RootDeniedException;

public abstract class Shell {
    public static final int DEFAULT_TIMEOUT = 15000;

    private static final String ENCODING = "UTF-8";
    private static final String TOKEN = "Y#*N^W^T@#@G";

    public int shellTimeout = DEFAULT_TIMEOUT;

    public boolean isRoot;

    public boolean isCleaning;
    public boolean isClosed;
    public boolean isExecuting;

    public String error;

    private final Process process;

    private final InputStreamReader inputStreamReader;
    private final BufferedReader inputStream;

    private final InputStreamReader errorStreamReader;
    private final BufferedReader errorStream;

    private final OutputStreamWriter outputStream;

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

        this.inputStreamReader = new InputStreamReader(this.process.getInputStream(), ENCODING);
        this.inputStream = new BufferedReader(inputStreamReader);

        this.errorStreamReader = new InputStreamReader(this.process.getErrorStream(), ENCODING);
        this.errorStream = new BufferedReader(errorStreamReader);

        this.outputStream = new OutputStreamWriter(this.process.getOutputStream(), ENCODING);

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

    public Command add(final Command command) {
        if (shouldClose) {
            throw new IllegalStateException("Unable to add commands to a closed shell");
        }

        while (isCleaning) {
            // wait until we are done cleaning
        }
        command.resetCommand();
        commands.add(command);

        notifyThreads();
        return command;
    }

    protected void notifyThreads() {
        final Thread t = new Thread() {
            public void run() {
                synchronized (commands) {
                    commands.notifyAll();
                }
            }
        };
        t.start();
    }

    public void close() {
        int count = 0;
        while (isExecuting) {
            ShellLogger.v(this, "Waiting on shell to finish executing before closing...");
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

        ShellLogger.v(this, "Shell closed!");
    }

    private void closeStreams() {
        IoUtils.closeQuietly(this.inputStream);
        IoUtils.closeQuietly(this.inputStreamReader);

        IoUtils.closeQuietly(this.errorStream);
        IoUtils.closeQuietly(this.errorStreamReader);

        IoUtils.closeQuietly(this.outputStream);
    }

    private synchronized void cleanupCommands() {
        this.isCleaning = true;
        final int toClean = Math.abs(this.maxCommands - (this.maxCommands / 4));
        ShellLogger.v(this, "Cleaning up: %s", toClean);

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
                        while (toRead != toWrite) {
                            ShellLogger.v(this, "Waiting for r/w to catch up before cleanup");
                        }
                        cleanupCommands();
                    }

                    if (toWrite < commands.size()) {
                        isExecuting = true;
                        final Command cmd = commands.get(toWrite);
                        cmd.startExecution();
                        ShellLogger.v(this, "Executing: %s", cmd.getCommand());
                        outputStream.write(cmd.getCommand());

                        final String line = String.format("\necho %s %s $?\n",
                                TOKEN, totalExecuted);
                        outputStream.write(line);
                        outputStream.flush();
                        toWrite++;
                        totalExecuted++;
                    } else if (shouldClose) {
                        isExecuting = false;
                        outputStream.write("\nexit 0\n");
                        outputStream.flush();
                        ShellLogger.d(this, "Closing shell");
                        return;
                    }
                }
            } catch (IOException | InterruptedException e) {
                ShellLogger.e(this, "IOException | InterruptedException", e);
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
                while (!shouldClose || inputStream.ready() || toRead < commands.size()) {
                    String outputLine = inputStream.readLine();

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
                        /**
                         * send the doOutput for the implementer to process
                         */
                        command.doOutput(command.id, outputLine);
                    } else if (pos > 0) {
                        /**
                         * token is suffix of doOutput, send doOutput part to implementer
                         */
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
                            ShellLogger.v(this, "Waiting for doOutput to be processed - %s of %s",
                                    command.totalOutputProcessed, command.totalOutput);
                        }

                        synchronized (this) {
                            try {
                                this.wait(2000);
                            } catch (Exception ignored) { }
                        }
                    }
                    ShellLogger.v(this, "finished reading all doOutput");

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
                ShellLogger.e(this, "IOException", e);
            } finally {
                closeStreams();

                ShellLogger.v(this, "Shell destroyed");
                isClosed = true;
            }
        }
    };

    public void processErrors(Command command) {
        try {
            while (errorStream.ready() && command != null) {
                final String line = errorStream.readLine();
                if (line == null) {
                    // EOF, shell closed?
                    break;
                }
                command.doOutput(command.id, line);
            }
        } catch (Exception e) {
            ShellLogger.e(this, "Error when processing errors. Can you see the irony?", e);
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
                shell.outputStream.write(OPENING);
                shell.outputStream.flush();

                // Check if we get "Opening" returned to check if we have properly opened a shell
                while (true) {
                    final String line = shell.inputStream.readLine();
                    if (line == null) {
                        // we are done and still did not get our "Opening" so something is fishy
                        throw new EOFException();
                    } else if ("".equals(line)) {
                        // let's continue checking
                        continue;
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
                    ShellLogger.e(this, "IllegalAccessException", iae);
                    pid = -1;
                }
            } else {
                pid = -1;
            }

            if (pid == -1) {
                ShellLogger.e(this, "could not get pid via reflection!");
                return;
            }

            try {
                setupOomAdj(pid);
            } catch (Exception e) {
                ShellLogger.e(this, "Could not set shell oom adj for pid %s!", pid);
                if (ShellLogger.getEnabled()) {
                    e.printStackTrace();
                }
            }

            try {
                setupOomScoreAdj(pid);
            } catch (Exception e) {
                ShellLogger.e(this, "Could not set shell oom score adj for pid %s!", pid);
                if (ShellLogger.getEnabled()) {
                    e.printStackTrace();
                }
            }
        }

        private void setupOomAdj(final int pid) throws Exception {
            shell.outputStream.write("(echo -17 > /proc/" + pid + "/oom_adj) &> /dev/null\n");
            shell.outputStream.write("(echo -17 > /proc/$$/oom_adj) &> /dev/null\n");
            shell.outputStream.flush();
        }

        private void setupOomScoreAdj(final int pid) throws Exception {
            shell.outputStream
                    .write("(echo -1000 > /proc/" + pid + "/oom_score_adj) &> /dev/null\n");
            shell.outputStream.write("(echo -1000 > /proc/$$/oom_score_adj) &> /dev/null\n");
            shell.outputStream.flush();
        }
    }
}
