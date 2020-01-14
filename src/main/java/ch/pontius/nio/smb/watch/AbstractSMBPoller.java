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
import java.nio.file.WatchKey;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 *
 * @author Jörg Frommann
 */
public abstract class AbstractSMBPoller implements SMBPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(AbstractSMBPoller.class);

    private enum RequestType {
        REGISTER,
        CANCEL,
        CLOSE
    }

    private final LinkedList<AbstractSMBPoller.Request> requests = new LinkedList<>();
    private final long pollIntervalMillis;

    private SMBWatchService watcher;
    private boolean shutdown;

    protected final BidiMap<Path, SMBWatchKey> registry = new DualHashBidiMap<>();

    public AbstractSMBPoller(long pollIntervalMillis) {
        this.pollIntervalMillis = pollIntervalMillis;
    }

    public void start(SMBWatchService watcher) {
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
        while(true) {
            try {
                final boolean shouldStop = processRequests();
                if (shouldStop) {
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
    }

    protected abstract void poll();

    @Override
    public WatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
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
                return (WatchKey) invoke(AbstractSMBPoller.RequestType.REGISTER, path, filteredKinds, modifiers);
            }
        }
    }

    private SMBWatchKey doRegister(Path path, Set<? extends WatchEvent.Kind<?>> kinds, WatchEvent.Modifier... modifiers) {
        final SMBWatchKey key = new SMBWatchKey(path, watcher, kinds);
        // Modifiers are dismissed at the moment.
        registry.put(path, key);
        return key;
    }

    public void cancel(SMBWatchKey key) {
        try {
            invoke(AbstractSMBPoller.RequestType.CANCEL, key);
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    protected void doCancel(SMBWatchKey key) {
        if (key.isValid()) {
            registry.removeValue(key);
        }
    }

    @Override
    public void close() throws IOException {
        invoke(AbstractSMBPoller.RequestType.CLOSE);
    }

    protected void doClose() {
        registry.clear();
        requests.clear();
    }

    private Object invoke(AbstractSMBPoller.RequestType type, Object... params) throws IOException {
        final AbstractSMBPoller.Request request = new AbstractSMBPoller.Request(type, params);
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
        AbstractSMBPoller.Request request;
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
                        final SMBWatchKey key = (SMBWatchKey) params[0];
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

    private static class Request {

        private final AbstractSMBPoller.RequestType type;
        private final Object[] params;
        private boolean completed;

        private Object result;

        Request(AbstractSMBPoller.RequestType type, Object... params) {
            this.type = type;
            this.params = params;
        }

        AbstractSMBPoller.RequestType type() {
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
