package alexander.martinz.libs.execution;

import java.io.IOException;
import java.util.concurrent.TimeoutException;

import alexander.martinz.libs.execution.exceptions.RootDeniedException;

public class RootShell extends Shell {
    protected RootShell() throws IOException, TimeoutException, RootDeniedException {
        super(true);
    }
}
