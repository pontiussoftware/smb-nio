package ch.pontius.nio.smb.watch;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

/**
 * @author Jörg Frommann
 */
public interface SMBPoller {

    void start(SMBWatchService watcher);

    WatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException;

    void cancel(SMBWatchKey key);

    void close() throws IOException;
}
