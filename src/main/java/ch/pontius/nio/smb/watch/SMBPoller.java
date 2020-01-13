package ch.pontius.nio.smb.watch;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;

public interface SMBPoller {

    void start();

    WatchKey register(Path path, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException;

    void cancel(SMBWatchKey key);

    void close() throws IOException;
}
