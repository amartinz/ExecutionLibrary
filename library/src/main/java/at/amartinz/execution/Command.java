package at.amartinz.execution;

import android.support.annotation.Nullable;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class Command implements CommandListener {
    private static final String TAG = Command.class.getSimpleName();

    public static final int OUTPUT_NONE = -1;
    public static final int OUTPUT_ALL = 1;
    public static final int OUTPUT_STRING = 2;
    public static final int OUTPUT_STRING_NEWLINE = 3;
    public static final int OUTPUT_LIST = 4;

    public int id;
    public int exitCode;

    private final int timeout;

    private boolean isExecuting;
    private boolean isFinished;
    private boolean isTerminated;

    public int totalOutput;
    public int totalOutputProcessed;

    private String[] commands;

    private int outputType = OUTPUT_NONE;
    private StringBuilder outputBuilder;
    private List<String> outputList;

    public Command(String... commands) {
        this(0, Shell.DEFAULT_TIMEOUT, commands);
    }

    public Command(int id, String... commands) {
        this(id, Shell.DEFAULT_TIMEOUT, commands);
    }

    public Command(int id, int timeout, String... commands) {
        this.id = id;
        this.timeout = timeout;
        this.commands = commands;
    }

    public final String[] getCommands() {
        if (commands == null || commands.length == 0) {
            throw new RuntimeException("No commands?");
        }
        return commands;
    }

    public Command waitFor() {
        while (!isFinished()) {
            synchronized (this) {
                try {
                    wait(timeout);
                } catch (Exception ignored) { }
            }

            if (!isFinished() && !isExecuting()) {
                if (ShellLogger.RAMPAGE) {
                    throw new RuntimeException("Something is really wrong");
                }
            }
        }
        return this;
    }

    public synchronized final boolean isExecuting() {
        return this.isExecuting;
    }

    public synchronized final boolean isFinished() {
        return this.isFinished;
    }

    public synchronized final boolean isTerminated() {
        return this.isTerminated;
    }

    public synchronized Command setOutputType(int outputType) {
        this.outputType = outputType;
        switch (this.outputType) {
            default:
            case OUTPUT_NONE: {
                outputBuilder = null;
                outputList = null;
                break;
            }
            case OUTPUT_ALL: {
                outputBuilder = new StringBuilder();
                outputList = new ArrayList<>();
                break;
            }
            case OUTPUT_STRING:
            case OUTPUT_STRING_NEWLINE: {
                outputBuilder = new StringBuilder();
                outputList = null;
                break;
            }
            case OUTPUT_LIST: {
                outputBuilder = null;
                outputList = new ArrayList<>();
                break;
            }
        }
        return this;
    }

    public synchronized final int getExitCode() {
        return this.exitCode;
    }

    protected final void setExitCode(int code) {
        synchronized (this) {
            exitCode = code;
        }
    }

    protected final void doOutput(int id, String line) {
        totalOutput++;
        synchronized (this) {
            onCommandOutput(id, line);
        }
    }

    public synchronized final void resetCommand() {
        this.isFinished = false;
        this.totalOutput = 0;
        this.totalOutputProcessed = 0;
        this.isExecuting = false;
        this.isTerminated = false;
        this.exitCode = -1;
    }

    protected final void commandFinished() {
        if (!isTerminated()) {
            synchronized (this) {
                onCommandCompleted(id, exitCode);

                if (ShellLogger.DEBUG) {
                    Log.v(TAG, String.format("finished command with id \"%s\"", id));
                }
                finishCommand();
            }
        }
    }

    protected final void finishCommand() {
        synchronized (this) {
            isExecuting = false;
            isFinished = true;
            this.notifyAll();
        }
    }

    public final void terminate(String reason) {
        synchronized (this) {
            onCommandTerminated(id, reason);

            if (ShellLogger.DEBUG) {
                Log.w(TAG, String.format("command \"%s\" did not finish because it was terminated!\n%s", id, reason));
            }
            setExitCode(-1);
            isTerminated = true;
            finishCommand();
        }
    }

    protected final void startExecution() {
        final ExecutionMonitor executionMonitor = new ExecutionMonitor();
        executionMonitor.setPriority(Thread.MIN_PRIORITY);
        executionMonitor.start();
        isExecuting = true;
    }

    @Override public void onCommandCompleted(int id, int exitCode) {
        // needs to be overwritten to implement
        setExitCode(exitCode);
    }

    @Override public void onCommandTerminated(int id, String reason) {
        // needs to be overwritten to implement
        if (ShellLogger.DEBUG) {
            Log.v(TAG, String.format("terminated command with id \"%s\": %s", id, reason));
        }
    }

    @Override public void onCommandOutput(int id, String line) {
        // needs to be overwritten to implement
        // WARNING: do not forget to call super!
        if (outputBuilder != null) {
            outputBuilder.append(line);
            if (outputType == OUTPUT_STRING_NEWLINE) {
                outputBuilder.append('\n');
            }
        }
        if (outputList != null) {
            outputList.add(line);
        }
        totalOutputProcessed++;
    }

    @Nullable public String getOutput() {
        return (outputBuilder != null ? outputBuilder.toString().trim() : null);
    }

    @Nullable public List<String> getOutputList() {
        return outputList;
    }

    private class ExecutionMonitor extends Thread {
        @Override public void run() {
            if (timeout <= 0) {
                return;
            }

            while (!isFinished()) {
                synchronized (Command.this) {
                    try {
                        Command.this.wait(timeout);
                    } catch (InterruptedException ignored) { }
                }

                if (!isFinished()) {
                    terminate("Timeout exception");
                }
            }
        }
    }

}
