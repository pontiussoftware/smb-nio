package ch.pontius.nio.smb.watch;

import ch.pontius.nio.smb.SMBFileAttributeView;
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

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

/**
 * @author Jörg Frommann
 */
public class StandardSMBPoller extends AbstractSMBPoller {

    private static final Logger LOGGER = LoggerFactory.getLogger(StandardSMBPoller.class);

    public static final long DEFAULT_POLL_INTERVALL_MILLIS = 30_000;

    private final Map<Path, FileTime> lastModified = new HashMap<>();
    private final Map<Path, Set<Path>> knownDirContent = new HashMap<>();

    public StandardSMBPoller() {
        super(DEFAULT_POLL_INTERVALL_MILLIS);
    }

    public StandardSMBPoller(long pollIntervalMillis) {
        super(pollIntervalMillis);
    }

    @Override
    protected SMBWatchKey doRegister(Path path, Set<? extends WatchEvent.Kind<?>> kinds, WatchEvent.Modifier... modifiers) {
        final SMBWatchKey key = super.doRegister(path, kinds, modifiers);
        registerPathAttributes(path);
        return key;
    }

    private void registerPathAttributes(Path path) {
        try {
            lastModified.put(path, lastModifiedTime(path));
            if (Files.isDirectory(path)) {
                final Set<Path> dirContent = knownDirContent.computeIfAbsent(path, p -> new HashSet<>());
                Files.list(path).forEach(dirContent::add);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to register attributes of path: " + path, e);
        }
    }

    @Override
    protected void poll() {
        final List<Event> events = new ArrayList<>();

        for (final Map.Entry<Path, SMBWatchKey> entry : registry.entrySet()) {
            final Path path = entry.getKey();
            final SMBWatchKey key = entry.getValue();

            try {
                if (Files.exists(path)) {
                    if (isModified(path)) {
                        if (isKnownDirectory(path)) {
                            final Set<Path> cachedDirContent = knownDirContent.get(path);
                            final Set<Path> actualDirContent = Files.list(path).collect(Collectors.toSet());
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

        signalEvents(events);
    }

    private boolean isModified(Path path) {
        final FileTime lastModifiedTime = lastModifiedTime(path);
        final boolean modified = lastModifiedTime.compareTo(lastModified.get(path)) > 0;
        lastModified.put(path, lastModifiedTime);
        return modified;
    }

    private boolean isKnownDirectory(Path path) {
        return knownDirContent.containsKey(path);
    }

    private FileTime lastModifiedTime(Path path) {
        try {
            final SMBFileAttributeView  attributeView = Files.getFileAttributeView(path, SMBFileAttributeView.class);
            return attributeView.readAttributes().lastModifiedTime();
        } catch (IOException e) {
            throw new RuntimeException("Failed to determine modification time of path: " + path, e);
        }
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
    protected void doCancel(SMBWatchKey key) {
        super.doCancel(key);
        if (key.isValid()) {
            lastModified.remove(key.watchable());
            knownDirContent.remove(key.watchable());
        }
    }

    private void unregisterPathAttributes(Path path) {
        lastModified.remove(path);
        knownDirContent.remove(path);
    }

    @Override
    protected void doClose() {
        super.doClose();
        lastModified.clear();
        knownDirContent.clear();
    }

    private enum EventType {
        DELETE(ENTRY_DELETE),
        CREATE(ENTRY_CREATE),
        MODIFY(ENTRY_MODIFY);

        final WatchEvent.Kind<Path> kind;

        EventType(WatchEvent.Kind<Path> kind) {
            this.kind = kind;
        }
    }

    private static class Event {
        final SMBWatchKey key;
        final EventType type;
        final Path path;

        public Event(SMBWatchKey key, EventType type, Path path) {
            this.key = key;
            this.type = type;
            this.path = path;
        }
    }
}
