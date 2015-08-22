package alexander.martinz.libs.execution;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.exceptions.RootDeniedException;

public class NormalShell extends Shell {
    protected NormalShell() throws IOException, TimeoutException, RootDeniedException {
        super(false);
    }
}
