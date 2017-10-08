package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public final class SMBFileAttributes implements BasicFileAttributes {
    /**  nteger value encoding the resource attributes. */
    private final int attributes;

    /** Unix timestamp of the creation date. */
    private final long created;

    /** Unix timestamp of the modification date. */
    private final long modified;

    /** Content length of the file. */
    private final long length;

    /** Unique code used to identify the file. */
    private final int code;

    /**
     * Public default constructor for {@link SMBFileAttributes}.
     *
     * @param path {@link SMBPath} for which to create {@link SMBFileAttributeView}.
     *
     * @throws IOException
     */
    public SMBFileAttributes(SMBPath path) throws IOException {
        this(path.getSmbFile());
    }

    /**
     * Internal constructor for {@link SMBFileAttributes}.
     *
     * @param file {@link SmbFile} for which to create the attributes.
     */
    SMBFileAttributes(SmbFile file) throws IOException {
        this.attributes = file.getAttributes();
        this.created = file.createTime();
        this.modified = file.lastModified();
        this.length = file.length();
        this.code = file.hashCode();
    }

    @Override
    public FileTime lastModifiedTime() {
        return FileTime.from(this.modified, TimeUnit.MILLISECONDS);
    }

    @Override
    public FileTime lastAccessTime() {
        return FileTime.from(0L, TimeUnit.MILLISECONDS);
    }

    @Override
    public FileTime creationTime() {
        return FileTime.from(this.created, TimeUnit.MILLISECONDS);
    }

    @Override
    public boolean isRegularFile() {
        return (this.attributes & SmbFile.ATTR_DIRECTORY) == 0;
    }

    @Override
    public boolean isDirectory() {
        return (this.attributes & SmbFile.ATTR_DIRECTORY) != 0;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public boolean isOther() {
        return false;
    }

    @Override
    public long size() {
        return this.length;
    }

    @Override
    public Object fileKey() {
        return this.code;
    }
}
