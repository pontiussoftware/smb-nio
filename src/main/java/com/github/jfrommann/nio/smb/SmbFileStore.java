package com.github.jfrommann.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * This class represents a single SMB share on a specific {@link SmbFileSystem}. It provides access to basic attributes of that share.
 *
 * @author Ralph Gasser
 */
public final class SmbFileStore extends FileStore {
    /**
     * The {@link SmbFileSystem} this {@link SmbFileStore} belongs to.
     */
    private final SmbFileSystem fileSystem;

    /**
     * The name of the share identified by this {@link SmbFileStore}.
     */
    private final String share;

    /**
     * Constructor for {@link SmbFileStore}.
     *
     * @param fileSystem The {@link SmbFileSystem} this instance of {@link SmbFileStore}.
     * @param share      The name of the share identified by the current instance of {@link SmbFileStore}.
     */
    SmbFileStore(SmbFileSystem fileSystem, String share) {
        this.fileSystem = fileSystem;
        this.share = share;
    }

    /**
     * Returns the full name of this {@link SmbFileStore}, which includes the FQN of the {@link SmbFileSystem}
     * and the name of the associated share.
     *
     * @return Full name of {@link SmbFileStore}
     */
    @Override
    public String name() {
        return this.fileSystem.getFQN() + SmbFileSystem.PATH_SEPARATOR + this.share;
    }

    /**
     * Returns the type of the {@link SmbFileStore}, which is "share".
     *
     * @return "share"
     */
    @Override
    public String type() {
        return "share";
    }

    /**
     * Returns false because generally, {@link SmbFileStore}'s are not considered to be read-only. However,
     * the concrete access permissions are specific to a file or resource.
     *
     * @return false
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Returns the total capacity of the share represented by this {@link SmbFileStore} instance.
     *
     * @return Total capacity of the share represented by this {@link SmbFileStore} instance
     * @throws IOException If total capacity cannot be determined.
     */
    @Override
    public long getTotalSpace() throws IOException {
        if (!this.fileSystem.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return new SmbFile(this.name(), fileSystem.context()).length();
    }

    /**
     * Returns the number of bytes that are currently available on the share represented by this {@link SmbFileStore} instance. The
     * value returned by this method is always the same as {@link SmbFileStore#getUnallocatedSpace()}
     *
     * @return Number of bytes currently available.
     * @throws IOException If usable space cannot be determined.
     */
    @Override
    public long getUsableSpace() throws IOException {
        if (!this.fileSystem.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return new SmbFile(this.name(), fileSystem.context()).getDiskFreeSpace();
    }

    /**
     * Returns the number of bytes that are currently available on the share represented by this {@link SmbFileStore} instance. The
     * value returned by this method is always the same as {@link SmbFileStore#getUsableSpace()}
     *
     * @return Number of bytes currently available.
     * @throws IOException If usable space cannot be determined.
     */
    @Override
    public long getUnallocatedSpace() throws IOException {
        if (!this.fileSystem.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return new SmbFile(this.name(), fileSystem.context()).getDiskFreeSpace();
    }

    /**
     * Checks whether or not this {@link SmbFileStore} supports the file attributes identified by the given file attribute view.
     *
     * @param type The type of the {@link FileAttributeView} for which support should be verified.
     * @return True if {@link FileAttributeView} is supported, false otherwise.
     */
    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return (type.equals(BasicFileAttributeView.class) || type.equals(SmbFileAttributeView.class));
    }

    /**
     * Checks whether or not this {@link SmbFileStore} supports the file attributes identified by the given file attribute view.
     *
     * @param name Name of the {@link FileAttributeView} for which support should be verified.
     * @return True if {@link FileAttributeView} is supported, false otherwise.
     */
    @Override
    public boolean supportsFileAttributeView(String name) {
        return name.equals("basic");
    }

    /**
     * Always returns null as {@link FileStoreAttributeView} are currently not supported.
     *
     * @param type The Class object corresponding to the attribute view
     * @return null
     */
    @Override
    public <V extends FileStoreAttributeView> V getFileStoreAttributeView(Class<V> type) {
        return null;
    }

    /**
     * Always throws an {@link UnsupportedOperationException} as {@link FileStoreAttributeView} are currently not supported.
     *
     * @throws UnsupportedOperationException Always
     */
    @Override
    public Object getAttribute(String attribute) throws IOException {
        throw new UnsupportedOperationException("File store attribute views are not supported for the current implementation of SMBFileStore.");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        SmbFileStore that = (SmbFileStore) o;

        return fileSystem.equals(that.fileSystem) && share.equals(that.share);
    }

    @Override
    public int hashCode() {
        int result = fileSystem.hashCode();
        result = 31 * result + share.hashCode();
        return result;
    }
}
