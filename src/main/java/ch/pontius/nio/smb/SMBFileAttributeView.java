package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;

import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public final class SMBFileAttributeView implements BasicFileAttributeView {

    /** {@link SmbFile} reference used by the current instance of {@link SMBFileAttributeView}. */
    private final SmbFile file;

    /**
     * Public default constructor.
     *
     * @param path {@link SMBPath} for which to create {@link SMBFileAttributeView}.
     */
    public SMBFileAttributeView(SMBPath path) {
        this(path.getSmbFile());
    }

    /**
     * Internal constructor.
     *
     * @param file {@link SmbFile} for which to create {@link SMBFileAttributeView}.
     */
    SMBFileAttributeView(SmbFile file) {
        this.file = file;
    }

    /**
     * Returns the name of {@link SMBFileAttributeView}, which is 'basic'.
     *
     * @return 'basic'
     */
    @Override
    public String name() {
        return "basic";
    }

    /**
     * Reads the {@link SmbFile}'s attributes and returns them in the form of an {@link SMBFileAttributes} instance.
     *
     * @return {@link SMBFileAttributes}
     * @throws IOException If reading the file's attributes fails.
     */
    @Override
    public BasicFileAttributes readAttributes() throws IOException {
        return new SMBFileAttributes(this.file);
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        this.file.setLastModified(lastModifiedTime.to(TimeUnit.MILLISECONDS));
        this.file.setCreateTime(createTime.to(TimeUnit.MILLISECONDS));
    }
}
