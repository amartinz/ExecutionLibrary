package alexander.martinz.libs.execution;

public class Command implements CommandListener {
    public int id;
    public int exitCode;

    private final int timeout;

    private boolean isExecuting;
    private boolean isFinished;
    private boolean isTerminated;

    public int totalOutput;
    public int totalOutputProcessed;

    private String[] commands;

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

    public final String getCommand() {
        if (commands == null) {
            ShellLogger.wtf(this, "No commands?");
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < commands.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(commands[i]);
        }
        return sb.toString();
    }

    public Command waitFor() {
        while (!isFinished) {
            // do nothing
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

    public final void resetCommand() {
        this.isFinished = false;
        this.totalOutput = 0;
        this.totalOutputProcessed = 0;
        this.isExecuting = false;
        this.isTerminated = false;
        this.exitCode = -1;
    }

    protected final void commandFinished() {
        if (!isTerminated) {
            synchronized (this) {
                onCommandCompleted(id, exitCode);

                ShellLogger.v(this, "finished command with id \"%s\"", id);
                finishCommand();
            }
        }
    }

    protected final void finishCommand() {
        isExecuting = false;
        isFinished = true;
        this.notifyAll();
    }

    public final void terminate(String reason) {
        synchronized (this) {
            onCommandTerminated(id, reason);

            ShellLogger.w(this, "command \"%s\" did not finish because it was terminated!\n%s",
                    id, reason);
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
        ShellLogger.v(this, "terminated command with id \"%s\": %s", id, reason);
    }

    @Override public void onCommandOutput(int id, String line) {
        // needs to be overwritten to implement
        // WARNING: do not forget to call super!
        ShellLogger.v(this, "%s -> %s", id, line);
        totalOutputProcessed++;
    }

    private class ExecutionMonitor extends Thread {
        @Override public void run() {
            if (timeout <= 0) {
                return;
            }

            while (!isFinished) {
                synchronized (Command.this) {
                    try {
                        Command.this.wait(timeout);
                    } catch (InterruptedException ignored) { }
                }

                if (!isFinished) {
                    terminate("Timeout exception");
                }
            }
        }
    }

}
