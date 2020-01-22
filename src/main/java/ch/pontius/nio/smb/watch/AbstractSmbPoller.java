package ch.pontius.nio.smb.watch;

import org.apache.commons.collections4.BidiMap;
import org.apache.commons.collections4.bidimap.DualHashBidiMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * Abstract base implementation of a SMB poller. This class basically manages the registration and concurrency aspects of the file change polling.
 *
 * @author Jörg Frommann
 */
public abstract class AbstractSmbPoller implements SmbPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSmbPoller.class);

    private enum RequestType {
        REGISTER,
        CANCEL,
        CLOSE
    }

    private final LinkedList<AbstractSmbPoller.Request> requests = new LinkedList<>();
    private final long pollIntervalMillis;

    private SmbWatchService watcher;
    private boolean shutdown;

    protected final BidiMap<Path, SmbWatchKey> registry = new DualHashBidiMap<>();

    public AbstractSmbPoller(long pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public void start(SmbWatchService watcher) {
        this.watcher = watcher;

        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                final Thread thread = new Thread(() -> process());
                thread.setDaemon(true);
                thread.start();
                return null;
            }
        });
    }

    private void process() {
        LOGGER.info("SMB poller started: poll interval = {} ms", pollIntervalMillis);

        while(true) {
            try {
                final boolean shouldStop = processRequests();
                if (shouldStop) {
                    LOGGER.debug("SMB poller stop requested");
                    break;
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process requests", e);
            }

            try {
                poll();
            } catch (Exception e) {
                LOGGER.error("Poll failed", e);
            }

            try {
                Thread.sleep(pollIntervalMillis);
            } catch (InterruptedException e) {
                // ignored
            }
        }
        LOGGER.info("SMB poller stopped");
    }

    protected abstract void poll();

    @Override
    public SmbWatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
        if (path == null) {
            throw new NullPointerException("Path is null");
        } else {
            final Set<WatchEvent.Kind<?>> filteredKinds = new HashSet<>(kinds.length);

            for (final WatchEvent.Kind<?> kind : kinds) {
                if (kind != StandardWatchEventKinds.ENTRY_CREATE && kind != StandardWatchEventKinds.ENTRY_MODIFY
                        && kind != StandardWatchEventKinds.ENTRY_DELETE) {
                    if (kind != StandardWatchEventKinds.OVERFLOW) {
                        if (kind == null) {
                            throw new NullPointerException("An element in event set is null");
                        }

                        throw new UnsupportedOperationException(kind.name());
                    }
                } else {
                    filteredKinds.add(kind);
                }
            }

            if (filteredKinds.isEmpty()) {
                throw new IllegalArgumentException("No events to register");
            } else {
                return (SmbWatchKey) invoke(AbstractSmbPoller.RequestType.REGISTER, path, filteredKinds, modifiers);
            }
        }
    }

    protected SmbWatchKey doRegister(Path path, Set<? extends WatchEvent.Kind<?>> kinds, WatchEvent.Modifier... modifiers) {
        LOGGER.debug("Register: {} - {}", path, kinds);
        final SmbWatchKey key = new SmbWatchKey(path, watcher, kinds);
        // Modifiers are dismissed at the moment.
        registry.put(path, key);
        return key;
    }

    public void cancel(SmbWatchKey key) {
        try {
            invoke(AbstractSmbPoller.RequestType.CANCEL, key);
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    protected void doCancel(SmbWatchKey key) {
        if (key.isValid()) {
            LOGGER.debug("Cancel: {} - {}", key.path, key.kinds);
            registry.removeValue(key);
        }
    }

    @Override
    public void close() throws IOException {
        invoke(AbstractSmbPoller.RequestType.CLOSE);
    }

    protected void doClose() {
        registry.clear();
        requests.clear();
        LOGGER.info("SMB poller closed");
    }

    private Object invoke(AbstractSmbPoller.RequestType type, Object... params) throws IOException {
        final AbstractSmbPoller.Request request = new AbstractSmbPoller.Request(type, params);
        synchronized(requests) {
            if (shutdown) {
                throw new ClosedWatchServiceException();
            }

            requests.add(request);
        }

        final Object result = request.awaitResult();
        if (result instanceof RuntimeException) {
            throw (RuntimeException) result;
        } else if (result instanceof IOException) {
            throw (IOException) result;
        } else {
            return result;
        }
    }

    protected boolean processRequests() {
        AbstractSmbPoller.Request request;
        synchronized(this.requests) {
            while((request = requests.poll()) != null) {
                if (shutdown) {
                    request.release(new ClosedWatchServiceException());
                }

                Object[] params;
                switch(request.type()) {
                    case REGISTER:
                        params = request.parameters();
                        final Path path = (Path) params[0];
                        final Set<? extends WatchEvent.Kind<?>> kinds = (Set<? extends WatchEvent.Kind<?>>) params[1];
                        final WatchEvent.Modifier[] modifiers = (WatchEvent.Modifier[]) params[2];
                        request.release(doRegister(path, kinds, modifiers));
                        break;
                    case CANCEL:
                        params = request.parameters();
                        final SmbWatchKey key = (SmbWatchKey) params[0];
                        doCancel(key);
                        request.release(null);
                        break;
                    case CLOSE:
                        doClose();
                        request.release(null);
                        shutdown = true;
                        break;
                    default:
                        request.release(new IOException("Invalid request type: " + request.type()));
                }
            }
        }

        return shutdown;
    }

    protected void signalEvent(SmbWatchKey key, WatchEvent.Kind<?> kind, Path path) {
        if (key.kinds().contains(kind)) {
            LOGGER.debug("Signal event: {} - {} - {}", key, kind, path);
            key.signalEvent(kind, path);
        }
    }

    public boolean isShutdown() {
        return shutdown;
    }

    private static class Request {

        private final AbstractSmbPoller.RequestType type;
        private final Object[] params;
        private boolean completed;

        private Object result;

        Request(AbstractSmbPoller.RequestType type, Object... params) {
            this.type = type;
            this.params = params;
        }

        AbstractSmbPoller.RequestType type() {
            return type;
        }

        Object[] parameters() {
            return params;
        }

        void release(Object result) {
            synchronized(this) {
                completed = true;
                this.result = result;
                notifyAll();
            }
        }

        Object awaitResult() {
            boolean interrupted = false;
            synchronized(this) {
                while(!completed) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        interrupted = true;
                    }
                }

                if (interrupted) {
                    Thread.currentThread().interrupt();
                }

                return result;
            }
        }
    }
}
