package com.github.jfrommann.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.concurrent.TimeUnit;

public final class SmbFileAttributeView implements BasicFileAttributeView {

    /**
     * {@link SmbFile} reference used by the current instance of {@link SmbFileAttributeView}.
     */
    private final SmbPath path;

    /**
     * Public default constructor.
     *
     * @param path {@link SmbPath} for which to create {@link SmbFileAttributeView}.
     */
    public SmbFileAttributeView(SmbPath path) {
        this.path = path;
    }

    /**
     * Returns the name of {@link SmbFileAttributeView}, which is 'basic'.
     *
     * @return 'basic'
     */
    @Override
    public String name() {
        return "basic";
    }

    /**
     * Reads the {@link SmbFile}'s attributes and returns them in the form of an {@link SmbFileAttributes} instance.
     *
     * @return {@link SmbFileAttributes}
     * @throws IOException If reading the file's attributes fails.
     */
    @Override
    public BasicFileAttributes readAttributes() throws IOException {
        return new SmbFileAttributes(this.path.getSmbFile());
    }

    @Override
    public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        final SmbFile file = this.path.getSmbFile();
        file.setLastModified(lastModifiedTime.to(TimeUnit.MILLISECONDS));
        file.setCreateTime(createTime.to(TimeUnit.MILLISECONDS));
    }
}
