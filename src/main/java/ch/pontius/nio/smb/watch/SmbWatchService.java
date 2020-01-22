package ch.pontius.nio.smb.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;

/**
 * SMB-specific implementation of a {@link WatchService}
 *
 * @author Jörg Frommann
 */
public class SmbWatchService implements WatchService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmbWatchService.class);

    private static final WatchKey CLOSE_KEY = new SmbWatchKey(null, null, null) {
        public boolean isValid() {
            return true;
        }

        public void cancel() {
        }
    };

    private final LinkedBlockingDeque<WatchKey> pendingKeys = new LinkedBlockingDeque<>();
    private final SmbPoller poller;

    private volatile boolean closed;
    private final Object closeLock = new Object();

    public SmbWatchService(SmbPoller poller) {
        this.poller = poller;
        poller.start(this);
    }

    public WatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
        return poller.register(path, kinds, modifiers);
    }

    void cancel(SmbWatchKey key) {
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
        LOGGER.debug("Poll: {} pendig keys", pendingKeys.size());
        checkOpen();
        final WatchKey key = pendingKeys.poll();
        checkKey(key);
        return key;
    }

    @Override
    public final WatchKey poll(long timeout, TimeUnit unit) throws InterruptedException {
        LOGGER.debug("Poll: {} pendig keys", pendingKeys.size());
        checkOpen();
        final WatchKey key = pendingKeys.poll(timeout, unit);
        checkKey(key);
        return key;
    }

    @Override
    public final WatchKey take() throws InterruptedException {
        LOGGER.debug("Take: {} pendig keys", pendingKeys.size());
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
