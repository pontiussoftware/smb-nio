package ch.pontius.nio.smb;


import java.io.IOException;

import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SMBDirectoryStream implements DirectoryStream<Path> {
    /** Array containing the content of the directory handled by the current instance of {@link SMBDirectoryStream}. This array is eagerly populated upon construction. */
    private final ArrayList<Path> content;

    /** Flag indicating whether the current instance of {@link SMBDirectoryStream} has been closed. */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Flag indicating whether an iterator has already been returned by the current instance of {@link SMBDirectoryStream}. */
    private final AtomicBoolean iteratorReturned = new AtomicBoolean();

    /**
     * Public and internal constructor for {@link SMBDirectoryStream}.
     *
     * @param smbPath The {@link SMBPath} for which to open a directory stream.
     * @param filter An optional filter predicate.
     *
     * @throws NotDirectoryException If provided {@link SMBPath} does not point to a directory.
     * @throws IOException If something goes wrong while reading the content of the directory.
     */
    public SMBDirectoryStream(SMBPath smbPath, java.nio.file.DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!smbPath.getSmbFile().isDirectory()) throw new NotDirectoryException("The provided path '" + smbPath.toString() + "' is not a directory.");
        this.content = new ArrayList<>();
        for (String name : smbPath.getSmbFile().list()) {
            final Path path = smbPath.resolve(name);
            if (filter == null || filter.accept(path)) {
                this.content.add(path);
            }
        }
    }

    /**
     * Returns an iterator for content of the directory handled by the current instance of {@link SMBDirectoryStream}.
     *
     * @return Iterator containing the content of the directory handled by the current instance of {@link SMBDirectoryStream}
     */
    @Override
    public Iterator<Path> iterator() {
        /* Make some checks. */
        if (this.closed.get()) throw new IllegalStateException("The SMBDirectoryStream has been closed already.");
        if (this.iteratorReturned.get()) throw new IllegalStateException("The current instance of SMBDirectoryStream has already returned an iterator.");

        /* If call to smbFile.list() returned something; create iterator. Otherwise returne empty iterator. */
        if (this.content != null) {
            /* Set iterator returned flag. */
            this.iteratorReturned.compareAndSet(false, true);

            /* Return the iterator. */
            return this.content.iterator();
        } else {
            return (new ArrayList<Path>(0)).iterator();
        }
    }

    /**
     * Closes the current instance of {@link SMBDirectoryStream}.
     */
    @Override
    public void close() {
        closed.set(true);
    }
}
