package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileStoreAttributeView;

/**
 * This class represents a single SMB share on a specific {@link SMBFileSystem}. It provides access to basic attributes of that share.
 *
 * @author      Ralph Gasser
 * @version     1.0
 * @since       1.0
 */
public final class SMBFileStore extends FileStore {
    /** The {@link SMBFileSystem} this {@link SMBFileStore} belongs to. */
    private final SMBFileSystem fileSystem;

    /** The name of the share identified by this {@link SMBFileStore}. */
    private final String share;

    /**
     * Constructor for {@link SMBFileStore}.
     *
     * @param fileSystem The {@link SMBFileSystem} this instance of {@link SMBFileStore}.
     * @param share The name of the share identified by the current instance of {@link SMBFileStore}.
     */
    SMBFileStore(SMBFileSystem fileSystem, String share) {
        this.fileSystem = fileSystem;
        this.share = share;
    }

    /**
     * Returns the full name of this {@link SMBFileStore}, which includes the FQN of the {@link SMBFileSystem}
     * and the name of the associated share.
     *
     * @return Full name of {@link SMBFileStore}
     */
    @Override
    public String name() {
        return this.fileSystem.getFQN() + SMBFileSystem.PATH_SEPARATOR + this.share;
    }

    /**
     * Returns the type of the {@link SMBFileStore}, which is "share".
     *
     * @return "share"
     */
    @Override
    public String type() {
        return "share";
    }

    /**
     * Returns false because generally, {@link SMBFileStore}'s are not considered to be read-only. However,
     * the concrete access permissions are specific to a file or resource.
     *
     * @return false
     */
    @Override
    public boolean isReadOnly() {
        return false;
    }

    /**
     * Returns the total capacity of the share represented by this {@link SMBFileStore} instance.
     *
     * @return  Total capacity of the share represented by this {@link SMBFileStore} instance
     * @throws IOException If total capacity cannot be determined.
     */
    @Override
    public long getTotalSpace() throws IOException {
        if (!this.fileSystem.isOpen()) throw new ClosedFileSystemException();
        return new SmbFile(this.name(), fileSystem.context()).length();
    }

    /**
     * Returns the number of bytes that are currently available on the share represented by this {@link SMBFileStore} instance. The
     * value returned by this method is always the same as {@link SMBFileStore#getUnallocatedSpace()}
     *
     * @return Number of bytes currently available.
     * @throws IOException If usable space cannot be determined.
     */
    @Override
    public long getUsableSpace() throws IOException {
        if (!this.fileSystem.isOpen()) throw new ClosedFileSystemException();
        return new SmbFile(this.name(), fileSystem.context()).getDiskFreeSpace();
    }

    /**
     * Returns the number of bytes that are currently available on the share represented by this {@link SMBFileStore} instance. The
     * value returned by this method is always the same as {@link SMBFileStore#getUsableSpace()}
     *
     * @return Number of bytes currently available.
     * @throws IOException If usable space cannot be determined.
     */
    @Override
    public long getUnallocatedSpace() throws IOException {
        if (!this.fileSystem.isOpen()) throw new ClosedFileSystemException();
        return new SmbFile(this.name(), fileSystem.context()).getDiskFreeSpace();
    }

    /**
     * Checks whether or not this {@link SMBFileStore} supports the file attributes identified by the given file attribute view.
     *
     * @param type The type of the {@link FileAttributeView} for which support should be verified.
     * @return True if {@link FileAttributeView} is supported, false otherwise.
     */
    @Override
    public boolean supportsFileAttributeView(Class<? extends FileAttributeView> type) {
        return (type.equals(BasicFileAttributeView.class) || type.equals(SMBFileAttributeView.class));
    }

    /**
     * Checks whether or not this {@link SMBFileStore} supports the file attributes identified by the given file attribute view.
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
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SMBFileStore that = (SMBFileStore) o;

        return fileSystem.equals(that.fileSystem) && share.equals(that.share);
    }

    @Override
    public int hashCode() {
        int result = fileSystem.hashCode();
        result = 31 * result + share.hashCode();
        return result;
    }
}
