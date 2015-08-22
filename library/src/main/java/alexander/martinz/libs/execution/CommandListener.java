package alexander.martinz.libs.execution;

public interface CommandListener {
    void onCommandCompleted(int id, int exitCode);

    void onCommandTerminated(int id, String reason);

    void onCommandOutput(int id, String line);
}
