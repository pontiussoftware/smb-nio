package com.github.jfrommann.nio.smb;


import jcifs.smb.SmbFile;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.NotDirectoryException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SmbDirectoryStream implements DirectoryStream<Path> {
    /**
     * Array containing the content of the directory handled by the current instance of {@link SmbDirectoryStream}. This array is eagerly populated upon construction.
     */
    private final ArrayList<Path> content;

    /**
     * Flag indicating whether the current instance of {@link SmbDirectoryStream} has been closed.
     */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /**
     * Flag indicating whether an iterator has already been returned by the current instance of {@link SmbDirectoryStream}.
     */
    private final AtomicBoolean iteratorReturned = new AtomicBoolean();

    /**
     * Public and internal constructor for {@link SmbDirectoryStream}.
     *
     * @param smbPath The {@link SmbPath} for which to open a directory stream.
     * @param filter  An optional filter predicate.
     * @throws NotDirectoryException If provided {@link SmbPath} does not point to a directory.
     * @throws IOException           If something goes wrong while reading the content of the directory.
     */
    public SmbDirectoryStream(SmbPath smbPath, java.nio.file.DirectoryStream.Filter<? super Path> filter) throws IOException {
        if (!smbPath.getSmbFile().isDirectory()) {
            throw new NotDirectoryException("The provided path '" + smbPath.toString() + "' is not a directory.");
        }
        this.content = new ArrayList<>();
        for (SmbFile name : smbPath.getSmbFile().listFiles()) {
            final Path path = smbPath.resolve(name.getName());
            if (filter == null || filter.accept(path)) {
                this.content.add(path);
            }
        }
    }

    /**
     * Returns an iterator for content of the directory handled by the current instance of {@link SmbDirectoryStream}.
     *
     * @return Iterator containing the content of the directory handled by the current instance of {@link SmbDirectoryStream}
     */
    @Override
    public Iterator<Path> iterator() {
        /* Make some checks. */
        if (this.closed.get()) {
            throw new IllegalStateException("The SMBDirectoryStream has been closed already.");
        }
        if (this.iteratorReturned.get()) {
            throw new IllegalStateException("The current instance of SMBDirectoryStream has already returned an iterator.");
        }

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
     * Closes the current instance of {@link SmbDirectoryStream}.
     */
    @Override
    public void close() {
        closed.set(true);
    }
}
