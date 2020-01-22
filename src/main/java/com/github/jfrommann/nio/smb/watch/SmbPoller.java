package com.github.jfrommann.nio.smb.watch;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;

/**
 * Interface for SMB file change polling.
 *
 * @author Jörg Frommann
 */
public interface SmbPoller {

    void start(SmbWatchService watcher);

    SmbWatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException;

    void cancel(SmbWatchKey key);

    void close() throws IOException;
}
