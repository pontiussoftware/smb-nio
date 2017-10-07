package ch.pontius.nio.smb;

import jcifs.smb.SmbException;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SMBDirectoryStream implements DirectoryStream<Path> {

    /** */
    private final SMBPath path;

    /** */
    private final SMBFileSystemProvider provider;

    /** */
    private final java.nio.file.DirectoryStream.Filter<? super Path> filter;

    /** Flag indicating whether the current instance of {@link SMBDirectoryStream} has been closed. */
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Flag indicating whether an iterator has already been returned by the current instance of {@link SMBDirectoryStream}. */
    private final AtomicBoolean iteratorReturned = new AtomicBoolean();


    /**
     * Public and internal constructor for {@link SMBDirectoryStream}.
     *
     * @param smbPath The {@link SMBPath} for which to open a directory stream.
     * @param filter An optional filter predicate.
     */
    public SMBDirectoryStream(SMBPath smbPath, java.nio.file.DirectoryStream.Filter<? super Path> filter) {
        if (smbPath.getFileSystem().provider() instanceof SMBFileSystemProvider) {
            this.filter = filter;
            this.path = smbPath;
            this.provider = (SMBFileSystemProvider)smbPath.getFileSystem().provider();
        } else {
            throw new IllegalArgumentException("The provided SMBPath is not associated with a valid SMBFileSystemProvider instance.");
        }

    }

    /**
     *
     * @return
     */
    @Override
    public Iterator<Path> iterator() {
        /* Make some checks. */
        if (this.closed.get()) throw new IllegalStateException("The SMBDirectoryStream has been closed already.");
        if (this.iteratorReturned.get()) throw new IllegalStateException("The current instance of SMBDirectoryStream has already returned an iterator.");

        /* Try to read list of content. */
        String[] content ;
        try {
            content = this.path.getSmbFile().list();
        } catch (SmbException e) {
            content = null;
        }

        /* If call to smbFile.list() returned something; create iterator. Otherwise returne empty iterator. */
        if (content != null) {
            final List<Path> paths = new ArrayList<>(content.length);
            for (String name : content) {
                final Path path = this.path.resolve(name);
                try {
                    if (this.filter == null || this.filter.accept(path)) {
                        paths.add(path);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            /* Set iterator returned flag. */
            this.iteratorReturned.compareAndSet(false, true);

            /* Return the iterator. */
            return paths.iterator();
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
