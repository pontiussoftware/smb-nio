package ch.pontius.nio.smb.watch;

import ch.pontius.nio.smb.SMBFileAttributeView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.FileTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    protected void poll() {
        for (final Map.Entry<Path, SMBWatchKey> entry : registry.entrySet()) {
            final Path path = entry.getKey();
            final SMBWatchKey key = entry.getValue();

            try {
                if (Files.exists(path)) {
                    final SMBFileAttributeView  attributeView = Files.getFileAttributeView(path, SMBFileAttributeView.class);
                    final FileTime lastModifiedTime = attributeView.readAttributes().lastModifiedTime();

                    if (lastModifiedTime.compareTo(lastModified.computeIfAbsent(path, p -> lastModifiedTime)) > 0) {
                        if (Files.isDirectory(path)) {
                            final Set<Path> dirContent = knownDirContent.computeIfAbsent(path, p -> new HashSet<>());
                            Files.list(path).forEach(sub -> {
                                if (!dirContent.contains(sub)) {
                                    signalEvent(key, StandardWatchEventKinds.ENTRY_CREATE, sub);
                                    dirContent.add(sub);
                                }
                            });
                        } else {
                            signalEvent(key, StandardWatchEventKinds.ENTRY_MODIFY, path);
                        }
                    }
                    lastModified.put(path, lastModifiedTime);
                } else {
                    signalEvent(key, StandardWatchEventKinds.ENTRY_DELETE, path);
                    lastModified.remove(path);
                    knownDirContent.remove(path);
                }
            } catch (IOException e) {
                LOGGER.error("Failed to process path: {}", path, e);
            }
        }
    }

    private void signalEvent(SMBWatchKey key, WatchEvent.Kind<?> kind, Path path) {
        if (key.kinds().contains(kind)) {
            key.signalEvent(kind, path);
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

    @Override
    protected void doClose() {
        super.doClose();
        lastModified.clear();
        knownDirContent.clear();
    }
}
