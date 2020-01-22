package com.github.jfrommann.nio.smb.watch;

import com.github.jfrommann.nio.smb.SmbFileAttributeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * Standard implementation of a SMB poller
 *
 * @author Jörg Frommann
 */
public class StandardSmbPoller extends AbstractSmbPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardSmbPoller.class);

    public static final long DEFAULT_POLL_INTERVALL_MILLIS = 30_000;

    final Map<Path, FileTime> modifiedTimes = new HashMap<>();
    final Map<Path, Set<Path>> knownDirContent = new HashMap<>();
    final PathAccessor pathAccessor;

    public StandardSmbPoller() {
        this(DEFAULT_POLL_INTERVALL_MILLIS);
    }

    public StandardSmbPoller(long pollIntervalMillis) {
        this(pollIntervalMillis, new StandardPathAccessor());
    }

    StandardSmbPoller(long pollIntervalMillis, PathAccessor pathAccessor) {
        super(pollIntervalMillis);
        this.pathAccessor = pathAccessor;
    }

    @Override
    protected SmbWatchKey doRegister(Path path, Set<? extends WatchEvent.Kind<?>> kinds, WatchEvent.Modifier... modifiers) {
        final SmbWatchKey key = super.doRegister(path, kinds, modifiers);
        registerPathAttributes(path);
        return key;
    }

    private void registerPathAttributes(Path path) {
        modifiedTimes.put(path, pathAccessor.lastModifiedTime(path));
        if (pathAccessor.isDirectory(path)) {
            final Set<Path> dirContent = knownDirContent.computeIfAbsent(path, p -> new HashSet<>());
            pathAccessor.list(path).forEach(dirContent::add);
        }
    }

    @Override
    protected void poll() {
        final List<Event> events = pollEvents();
        signalEvents(events);
    }

    List<Event> pollEvents() {
        final List<Event> events = new ArrayList<>();

        for (final Map.Entry<Path, SmbWatchKey> entry : registry.entrySet()) {
            final Path path = entry.getKey();
            final SmbWatchKey key = entry.getValue();

            try {
                if (pathAccessor.exists(path)) {
                    if (isModified(path)) {
                        if (isKnownDirectory(path)) {
                            final Set<Path> cachedDirContent = knownDirContent.get(path);
                            final Set<Path> actualDirContent = pathAccessor.list(path).collect(Collectors.toSet());
                            for (final Iterator<Path> it = cachedDirContent.iterator(); it.hasNext();) {
                                final Path sub = it.next();
                                if (!actualDirContent.contains(sub)) {
                                    if (!isKnownDirectory(sub)) {
                                        events.add(new Event(key, EventType.DELETE, sub));
                                    }
                                    it.remove();
                                }
                            }
                            actualDirContent.forEach(sub -> {
                                if (!cachedDirContent.contains(sub)) {
                                    events.add(new Event(key, EventType.CREATE, sub));
                                    cachedDirContent.add(sub);
                                }
                            });
                        } else {
                            events.add(new Event(key, EventType.MODIFY, path));
                        }
                    }
                } else {
                    events.add(new Event(key, EventType.DELETE, path));
                }
            } catch (Exception e) {
                LOGGER.error("Failed to process path: {}", path, e);
            }
        }

        return events;
    }

    boolean isModified(Path path) {
        final FileTime lastModifiedTime = pathAccessor.lastModifiedTime(path);
        final boolean modified = lastModifiedTime.compareTo(modifiedTimes.get(path)) > 0;
        modifiedTimes.put(path, lastModifiedTime);
        return modified;
    }

    private boolean isKnownDirectory(Path path) {
        return knownDirContent.containsKey(path);
    }

    private void signalEvents(List<Event> events) {
        events.stream().sorted(Comparator.comparingInt(e -> e.type.ordinal())).forEach(this::signalEvent);
    }

    private void signalEvent(Event event) {
        signalEvent(event.key, event.type.kind, event.path);
        if (event.type == EventType.DELETE) {
            unregisterPathAttributes(event.path);
        }
    }

    @Override
    protected void doCancel(SmbWatchKey key) {
        super.doCancel(key);
        if (key.isValid()) {
            modifiedTimes.remove(key.watchable());
            knownDirContent.remove(key.watchable());
        }
    }

    private void unregisterPathAttributes(Path path) {
        modifiedTimes.remove(path);
        knownDirContent.remove(path);
    }

    @Override
    protected void doClose() {
        super.doClose();
        modifiedTimes.clear();
        knownDirContent.clear();
    }

    /**
     * Internal event type
     */
    enum EventType {
        DELETE(ENTRY_DELETE),
        CREATE(ENTRY_CREATE),
        MODIFY(ENTRY_MODIFY);

        final WatchEvent.Kind<Path> kind;

        EventType(WatchEvent.Kind<Path> kind) {
            this.kind = kind;
        }
    }

    /**
     * Internal event class
     */
    static class Event {
        final SmbWatchKey key;
        final EventType type;
        final Path path;

        public Event(SmbWatchKey key, EventType type, Path path) {
            this.key = key;
            this.type = type;
            this.path = path;
        }
    }

    /**
     * Interface to access a path physically
     */
    interface PathAccessor {

        boolean exists(Path path);

        boolean isDirectory(Path path);

        Stream<Path> list(Path dir);

        FileTime lastModifiedTime(Path path);
    }

    /**
     * Standard implementation of physical path access
     */
    private static class StandardPathAccessor implements PathAccessor {

        @Override
        public boolean exists(Path path) {
            return Files.exists(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return Files.isDirectory(path);
        }

        @Override
        public Stream<Path> list(Path dir) {
            try {
                return Files.list(dir);
            } catch (IOException e) {
                throw new RuntimeException("Failed to list files of directory: " + dir, e);
            }
        }

        @Override
        public FileTime lastModifiedTime(Path path) {
            try {
                final SmbFileAttributeView attributeView = Files.getFileAttributeView(path, SmbFileAttributeView.class);
                return attributeView.readAttributes().lastModifiedTime();
            } catch (IOException e) {
                throw new RuntimeException("Failed to determine modification time of path: " + path, e);
            }
        }
    }
}
