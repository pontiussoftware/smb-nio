package ch.pontius.nio.smb.watch;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 *
 * @author Jörg Frommann
 */
public class SMBWatchService implements WatchService {

    private static final WatchKey CLOSE_KEY = new SMBWatchKey(null, null, null) {
        public boolean isValid() {
            return true;
        }

        public void cancel() {
        }
    };

    private final LinkedBlockingDeque<WatchKey> pendingKeys = new LinkedBlockingDeque<>();
    private final SMBPoller poller;

    private volatile boolean closed;
    private final Object closeLock = new Object();

    public SMBWatchService(SMBPoller poller) {
        this.poller = poller;
        poller.start(this);
    }

    public WatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
        return poller.register(path, kinds, modifiers);
    }

    void cancel(SMBWatchKey key) {
        poller.cancel(key);
    }

    protected final void enqueueKey(WatchKey key) {
        pendingKeys.offer(key);
    }

    private void checkOpen() {
        if (closed) {
            throw new ClosedWatchServiceException();
        }
    }

    private void checkKey(WatchKey key) {
        if (key == CLOSE_KEY) {
            enqueueKey(key);
        }

        checkOpen();
    }

    @Override
    public final WatchKey poll() {
        checkOpen();
        final WatchKey key = pendingKeys.poll();
        checkKey(key);
        return key;
    }

    @Override
    public final WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        checkOpen();
        final WatchKey key = pendingKeys.poll(timeout, unit);
        checkKey(key);
        return key;
    }

    @Override
    public final WatchKey take() throws InterruptedException {
        checkOpen();
        final WatchKey key = pendingKeys.take();
        checkKey(key);
        return key;
    }

    @Override
    public final void close() throws IOException {
        synchronized(closeLock) {
            if (!closed) {
                closed = true;
                poller.close();
                pendingKeys.clear();
                pendingKeys.offer(CLOSE_KEY);
            }
        }
    }
}
