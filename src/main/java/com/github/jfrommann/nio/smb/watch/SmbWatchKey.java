package com.github.jfrommann.nio.smb.watch;

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * SMB-specific implementation of a {@link WatchKey}
 *
 * @author Jörg Frommann
 */
public class SmbWatchKey implements WatchKey {

    private enum State {
        READY, SIGNALLED
    }

    static final int MAX_EVENT_LIST_SIZE = 512;
    static final SmbWatchKey.Event<Object> OVERFLOW_EVENT;

    final Path path;
    final SmbWatchService watcher;
    final Set<? extends WatchEvent.Kind<?>> kinds;

    private SmbWatchKey.State state;
    private List<WatchEvent<?>> events;

    private Map<Path, WatchEvent<?>> lastModifyEvents;

    static {
        OVERFLOW_EVENT = new SmbWatchKey.Event<>(StandardWatchEventKinds.OVERFLOW, null);
    }

    SmbWatchKey(Path path, SmbWatchService watcher, Set<? extends WatchEvent.Kind<?>> kinds) {
        this.path = path;
        this.watcher = watcher;
        this.kinds = kinds;
        state = SmbWatchKey.State.READY;
        events = new ArrayList<>();
        lastModifyEvents = new HashMap<>();
    }

    @Override
    public Path watchable() {
        return this.path;
    }

    public Set<? extends WatchEvent.Kind<?>> kinds() {
        return kinds;
    }

    @Override
    public final List<WatchEvent<?>> pollEvents() {
        synchronized (this) {
            final List<WatchEvent<?>> currentEvents = events;
            events = new ArrayList<>();
            lastModifyEvents.clear();
            return currentEvents;
        }
    }

    @Override
    public final boolean reset() {
        synchronized (this) {
            if (state == SmbWatchKey.State.SIGNALLED && isValid()) {
                if (events.isEmpty()) {
                    state = SmbWatchKey.State.READY;
                } else {
                    watcher.enqueueKey(this);
                }
            }

            return isValid();
        }
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public void cancel() {
        if (isValid()) {
            watcher.cancel(this);
        }
    }

    public void signalEvent(WatchEvent.Kind<?> kind, Path path) {
        boolean modify = kind == StandardWatchEventKinds.ENTRY_MODIFY;

        synchronized (this) {
            final int size = events.size();
            if (size > 0) {
                final WatchEvent<?> event = events.get(size - 1);
                if (event.kind() == StandardWatchEventKinds.OVERFLOW || kind == event.kind() && Objects.equals(path, event.context())) {
                    ((SmbWatchKey.Event<?>) event).increment();
                    return;
                }

                if (!lastModifyEvents.isEmpty()) {
                    if (modify) {
                        final WatchEvent<?> modifyEvent = lastModifyEvents.get(path);
                        if (modifyEvent != null) {
                            assert modifyEvent.kind() == StandardWatchEventKinds.ENTRY_MODIFY;
                            ((SmbWatchKey.Event<?>) modifyEvent).increment();
                            return;
                        }
                    } else {
                        lastModifyEvents.remove(path);
                    }
                }

                if (size >= MAX_EVENT_LIST_SIZE) {
                    kind = StandardWatchEventKinds.OVERFLOW;
                    modify = false;
                    path = null;
                }
            }

            final SmbWatchKey.Event<?> event = new SmbWatchKey.Event(kind, path);
            if (modify) {
                lastModifyEvents.put(path, event);
            } else if (kind == StandardWatchEventKinds.OVERFLOW) {
                events.clear();
                lastModifyEvents.clear();
            }

            events.add(event);
            signal();
        }
    }

    private void signal() {
        synchronized (this) {
            if (state == SmbWatchKey.State.READY) {
                state = SmbWatchKey.State.SIGNALLED;
                watcher.enqueueKey(this);
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final SmbWatchKey that = (SmbWatchKey) o;
        return new EqualsBuilder()
                .append(watcher, that.watcher)
                .append(path, that.path)
                .append(state, that.state)
                .append(events, that.events)
                .append(lastModifyEvents, that.lastModifyEvents)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE) //--
                .append("path", path) //--
                .append("kinds", kinds) //--
                .append("state", state) //--
                .toString();
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

        @Override
        public String toString() {
            return new ToStringBuilder(this) //--
                    .append("kind", kind) //--
                    .append("context", context) //--
                    .append("count", count) //--
                    .toString();
        }
    }
}
