package alexander.martinz.libs.execution;

import java.io.Closeable;
import java.net.Socket;

public class IoUtils {
    private static final String TAG = IoUtils.class.getSimpleName();

    public static void closeQuietly(final Object o) {
        if (o instanceof Socket) {
            try {
                ((Socket) o).close();
            } catch (Exception ignored) { }
        } else if (o instanceof Closeable) {
            try {
                ((Closeable) o).close();
            } catch (Exception ignored) { }
        }
    }
}
