package ch.pontius.nio.smb.watch;

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
 * Code ported from sun.nio.fs.AbstractPoller
 *
 * @author Jörg Frommann
 */
public abstract class AbstractSMBPoller implements SMBPoller {

    private enum RequestType {
        REGISTER,
        CANCEL,
        CLOSE
    }

    private final LinkedList<AbstractSMBPoller.Request> requestList = new LinkedList<>();
    private boolean shutdown = false;

    public void start() {
        AccessController.doPrivileged(new PrivilegedAction<Object>() {
            public Object run() {
                final Thread thread = new Thread(() -> poll());
                thread.setDaemon(true);
                thread.start();
                return null;
            }
        });
    }

    protected abstract void wakeup() throws IOException;

    protected abstract Object doRegister(Path path, Set<? extends WatchEvent.Kind<?>> kinds, WatchEvent.Modifier... modifiers);

    protected abstract void doCancel(WatchKey key);

    protected abstract void doClose();

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

    public void cancel(SMBWatchKey key) {
        try {
            invoke(AbstractSMBPoller.RequestType.CANCEL, key);
        } catch (IOException e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    @Override
    public void close() throws IOException {
        invoke(AbstractSMBPoller.RequestType.CLOSE);
    }

    private Object invoke(AbstractSMBPoller.RequestType type, Object... params) throws IOException {
        final AbstractSMBPoller.Request request = new AbstractSMBPoller.Request(type, params);
        synchronized(requestList) {
            if (shutdown) {
                throw new ClosedWatchServiceException();
            }

            requestList.add(request);
        }
        wakeup();

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
        synchronized(this.requestList) {
            while((request = requestList.poll()) != null) {
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
                        final WatchKey key = (WatchKey) params[0];
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

        return this.shutdown;
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
