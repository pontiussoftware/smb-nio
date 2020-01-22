package com.github.jfrommann.nio.smb.watch;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.WatchEvent;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static com.github.jfrommann.nio.smb.watch.StandardSmbPoller.EventType.CREATE;
import static com.github.jfrommann.nio.smb.watch.StandardSmbPoller.EventType.DELETE;
import static com.github.jfrommann.nio.smb.watch.StandardSmbPoller.EventType.MODIFY;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
public class StandardSmbPollerTest {

    @Mock
    private SmbWatchService watcher;

    @Test
    public void testRegister() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Path file = Paths.get(new URI("smb://server/share/dir/file.bin"));
        final Path subDir = Paths.get(new URI("smb://server/share/dir/subdir/"));
        final Map<Path, FileTime> modifiedTimes = modifiedTimes(dir, file, subDir);
        final Map<Path, List<Path>> dirContents = dirContents(dir, subDir, file);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(modifiedTimes, dirContents));
        poller.start(watcher);
        final SmbWatchKey watchKey = poller.register(dir, kinds());
        assertEquals(dir, watchKey.watchable());
        assertEquals(watchKey, poller.registry.get(dir));
        assertEquals(modifiedTimes.get(dir), poller.modifiedTimes.get(dir));
        assertArrayEquals(dirContents.get(dir).stream().sorted(Comparator.comparing(Path::toString)).toArray(),
                poller.knownDirContent.get(dir).stream().sorted(Comparator.comparing(Path::toString)).toArray());
    }

    @Test
    public void testCancel() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Map<Path, FileTime> modifiedTimes = modifiedTimes(dir);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(modifiedTimes, dirContents(dir)));
        poller.start(watcher);
        final SmbWatchKey watchKey = poller.register(dir, kinds());
        assertEquals(watchKey, poller.registry.get(dir));

        poller.cancel(watchKey);
        assertFalse(poller.registry.containsKey(dir));
        assertFalse(poller.modifiedTimes.containsKey(dir));
        assertFalse(poller.knownDirContent.containsKey(dir));
    }

    @Test
    public void testClose() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Map<Path, FileTime> modifiedTimes = modifiedTimes(dir);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(modifiedTimes, dirContents(dir)));
        poller.start(watcher);
        final SmbWatchKey watchKey = poller.register(dir, kinds());
        assertEquals(watchKey, poller.registry.get(dir));

        poller.close();
        assertTrue(poller.isShutdown());
        assertTrue(poller.registry.isEmpty());
        assertTrue(poller.modifiedTimes.isEmpty());
        assertTrue(poller.knownDirContent.isEmpty());
    }

    @Test
    public void testIsModified() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Map<Path, FileTime> modifiedTimes = modifiedTimes(dir);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(modifiedTimes, dirContents(dir)));
        poller.start(watcher);
        poller.register(dir, kinds());

        assertFalse(poller.isModified(dir));
        modifiedTimes.put(dir, FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
        assertTrue(poller.isModified(dir));
    }

    @Test
    public void testPollEventsOnDir() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Path file = Paths.get(new URI("smb://server/share/dir/file.bin"));
        final Path subDir = Paths.get(new URI("smb://server/share/dir/subdir/"));
        final Map<Path, FileTime> existingPathsTimes = modifiedTimes(dir, file, subDir);
        final Map<Path, List<Path>> dirContents = dirContents(dir, subDir, file);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(existingPathsTimes, dirContents));
        poller.start(watcher);
        final SmbWatchKey watchKey = poller.register(dir, kinds());

        List<StandardSmbPoller.Event> events = poller.pollEvents();
        assertEquals(0, events.size());

        existingPathsTimes.put(dir, FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
        dirContents.get(dir).remove(file);
        events = poller.pollEvents();
        assertEquals(1, events.size());
        StandardSmbPoller.Event event = events.get(0);
        assertEquals(watchKey, event.key);
        assertEquals(file, event.path);
        assertEquals(DELETE, event.type);

        existingPathsTimes.put(dir, FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
        dirContents.get(dir).add(file);
        events = poller.pollEvents();
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(watchKey, event.key);
        assertEquals(file, event.path);
        assertEquals(CREATE, event.type);
    }

    @Test
    public void testPollEventsOnFile() throws Exception {
        final Path dir = Paths.get(new URI("smb://server/share/dir/"));
        final Path file = Paths.get(new URI("smb://server/share/dir/file.bin"));
        final Path subDir = Paths.get(new URI("smb://server/share/dir/subdir/"));
        final Map<Path, FileTime> existingPathsTimes = modifiedTimes(dir, file, subDir);
        final Map<Path, List<Path>> dirContents = dirContents(dir, subDir, file);

        final StandardSmbPoller poller = new StandardSmbPoller(100L, new TestPathAccessor(existingPathsTimes, dirContents));
        poller.start(watcher);
        final SmbWatchKey watchKey = poller.register(file, kinds());

        List<StandardSmbPoller.Event> events = poller.pollEvents();
        assertEquals(0, events.size());

        existingPathsTimes.put(file, FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
        events = poller.pollEvents();
        assertEquals(1, events.size());
        StandardSmbPoller.Event event = events.get(0);
        assertEquals(watchKey, event.key);
        assertEquals(file, event.path);
        assertEquals(MODIFY, event.type);

        existingPathsTimes.remove(file);
        events = poller.pollEvents();
        assertEquals(1, events.size());
        event = events.get(0);
        assertEquals(watchKey, event.key);
        assertEquals(file, event.path);
        assertEquals(DELETE, event.type);
    }

    private Map<Path, FileTime> modifiedTimes(Path... paths) {
        final Map<Path, FileTime> modifiedTimes = new HashMap<>();
        for (final Path path : paths) {
            modifiedTimes.put(path, FileTime.from(System.currentTimeMillis(), TimeUnit.MILLISECONDS));
        }
        return modifiedTimes;
    }

    private Map<Path, List<Path>> dirContents(Path dir, Path... contents) {
        final Map<Path, List<Path>> dirContents = new HashMap<>();
        for (final Path content : contents) {
            dirContents.computeIfAbsent(dir, p -> new ArrayList<>()).add(content);
        }
        return dirContents;
    }

    private WatchEvent.Kind<?>[] kinds() {
        return new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
    }

    private static class TestPathAccessor implements StandardSmbPoller.PathAccessor {

        final Map<Path, FileTime> modifiedTimes;
        final Map<Path, List<Path>> dirContents;

        public TestPathAccessor(Map<Path, FileTime> modifiedTimes, Map<Path, List<Path>> dirContents) {
            this.modifiedTimes = modifiedTimes;
            this.dirContents = dirContents;
        }

        @Override
        public boolean exists(Path path) {
            return modifiedTimes.containsKey(path);
        }

        @Override
        public boolean isDirectory(Path path) {
            return dirContents.containsKey(path);
        }

        @Override
        public Stream<Path> list(Path dir) {
            return dirContents.get(dir).stream();
        }

        @Override
        public FileTime lastModifiedTime(Path path) {
            return modifiedTimes.get(path);
        }
    }
}
