package ch.pontius.nio.smb.watch;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Code ported from sun.nio.fs.AbstractWatchKey
 *
 * @author Jörg Frommann
 */
public class SMBWatchKey implements WatchKey {

    private enum State {
        READY,
        SIGNALLED
    }

    static final int MAX_EVENT_LIST_SIZE = 512;
    static final SMBWatchKey.Event<Object> OVERFLOW_EVENT;

    private final SMBWatchService watcher;
    private final Path dir;
    private SMBWatchKey.State state;
    private List<WatchEvent<?>> events;

    private Map<Object, WatchEvent<?>> lastModifyEvents;

    static {
        OVERFLOW_EVENT = new SMBWatchKey.Event<>(StandardWatchEventKinds.OVERFLOW, null);
    }

    SMBWatchKey(Path path, SMBWatchService watcher) {
        this.dir = path;
        this.watcher = watcher;
        this.state = SMBWatchKey.State.READY;
        this.events = new ArrayList<>();
        this.lastModifyEvents = new HashMap<>();
    }

    @Override
    public Path watchable() {
        return this.dir;
    }

    @Override
    public final List<WatchEvent<?>> pollEvents() {
        synchronized(this) {
            final List<WatchEvent<?>> currentEvents = events;
            events = new ArrayList<>();
            lastModifyEvents.clear();
            return currentEvents;
        }
    }

    @Override
    public final boolean reset() {
        synchronized(this) {
            if (state == SMBWatchKey.State.SIGNALLED && isValid()) {
                if (events.isEmpty()) {
                    state = SMBWatchKey.State.READY;
                } else {
                    watcher.enqueueKey(this);
                }
            }

            return isValid();
        }
    }

    @Override
    public boolean isValid() {
        throw new RuntimeException("Operation isValid not supported!"); // TODO!
    }

    @Override
    public void cancel() {
        if (isValid()) {
            watcher.cancel(this);
        }
    }

    // ToDO: Call
    public void signalEvent(WatchEvent.Kind<?> kind, Object key) {
        boolean modify = kind == StandardWatchEventKinds.ENTRY_MODIFY;

        synchronized(this) {
            final int size = events.size();
            if (size > 0) {
                final WatchEvent<?> event = events.get(size - 1);
                if (event.kind() == StandardWatchEventKinds.OVERFLOW || kind == event.kind() && Objects.equals(key, event.context())) {
                    ((SMBWatchKey.Event<?>) event).increment();
                    return;
                }

                if (!lastModifyEvents.isEmpty()) {
                    if (modify) {
                        final WatchEvent<?> modifyEvent = lastModifyEvents.get(key);
                        if (modifyEvent != null) {
                            assert modifyEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY;
                            ((SMBWatchKey.Event<?>) modifyEvent).increment();
                            return;
                        }
                    } else {
                        lastModifyEvents.remove(key);
                    }
                }

                if (size >= MAX_EVENT_LIST_SIZE) {
                    kind = StandardWatchEventKinds.OVERFLOW;
                    modify = false;
                    key = null;
                }
            }

            final SMBWatchKey.Event<?> event = new SMBWatchKey.Event(kind, key);
            if (modify) {
                lastModifyEvents.put(key, event);
            } else if (kind == StandardWatchEventKinds.OVERFLOW) {
                events.clear();
                lastModifyEvents.clear();
            }

            events.add(event);
            signal();
        }
    }

    private void signal() {
        synchronized(this) {
            if (state == SMBWatchKey.State.READY) {
                state = SMBWatchKey.State.SIGNALLED;
                watcher.enqueueKey(this);
            }
        }
    }

    private static class Event<T> implements WatchEvent<T> {
        private final Kind<T> kind;
        private final T context;

        private int count;

        Event(Kind<T> kind, T context) {
            this.kind = kind;
            this.context = context;
            this.count = 1;
        }

        public Kind<T> kind() {
            return kind;
        }

        public T context() {
            return context;
        }

        public int count() {
            return count;
        }

        public void increment() {
            ++count;
        }
    }
}
