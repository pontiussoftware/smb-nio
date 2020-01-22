package com.github.jfrommann.nio.smb;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileAlreadyExistsException;

public final class SeekableSmbByteChannel implements SeekableByteChannel {
    /**
     * Internal {@link SmbRandomAccessFile} reference to write to {@link SmbFile}.
     */
    private final SmbRandomAccessFile random;

    /**
     * Boolean indicating whether this instance of {@link SeekableSmbByteChannel} is open.
     */
    private volatile boolean open = true;

    /**
     * Constructor for {@link SeekableSmbByteChannel}
     *
     * @param file       The {@link SmbFile} instance that should be opened.
     * @param write      Flag that indicates, whether write access is requested.
     * @param create     Flag that indicates, whether file should be created.
     * @param createNew  Flag that indicates, whether file should be created. If it is set to true, operation will fail if file exists!
     * @param truncate   Flag that indicates, whether file should be truncated to length 0 when being opened.
     * @param append     Flag that indicates, whether data should be appended.
     * @throws IOException If something goes wrong when accessing the file.
     */
    SeekableSmbByteChannel(SmbFile file, boolean write, boolean create, boolean createNew, boolean truncate, boolean append) throws IOException {

        /*  Tries to create a new file, if so specified. */
        if (create || createNew) {
            if (file.exists()) {
                if (createNew) {
                    throw new FileAlreadyExistsException("The specified file '" + file.getPath() + "' does already exist!");
                }
            } else {
                file.createNewFile();
            }
        }

        /* Opens the file with either read only or write access. */
        if (write) {
            this.random = new SmbRandomAccessFile(file, "rw");
            if (truncate) {
                this.random.setLength(0);
            }
            if (append) {
                this.random.seek(this.random.length());
            }
        } else {
            this.random = new SmbRandomAccessFile(file, "r");
        }
    }

    /**
     * Reads the content from the {@link SmbRandomAccessFile} handled by the current instance of {@link SeekableSmbByteChannel} to
     * the provided {@link ByteBuffer}. The {@link ByteBuffer} is written from its current position to its end.
     *
     * @param dst {@link ByteBuffer} to which to write the data.
     * @return Number of bytes that were read.
     * @throws IOException If something goes wrong while reading to the file.
     */
    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        final int len = dst.limit() - dst.position();
        final byte[] buffer = new byte[len];
        final int read = this.random.read(buffer);
        if (read > 0) {
            dst.put(buffer, 0, read);
        }
        return read;
    }

    /**
     * Writes the content of the provided {@link ByteBuffer} into the {@link SmbRandomAccessFile} handled by the current
     * instance of {@link SeekableSmbByteChannel}. The {@link ByteBuffer} is read from its current position to it end.
     *
     * @param src {@link ByteBuffer} from which to read the data.
     * @return Number of bytes that were written.
     * @throws IOException If something goes wrong while writing to the file.
     */
    @Override
    public synchronized int write(ByteBuffer src) throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        final int len = src.limit() - src.position();
        final byte[] buffer = new byte[len];
        src.get(buffer);
        this.random.write(buffer);
        return len;
    }

    /**
     * Returns the position of the pointer into the {@link SmbRandomAccessFile} that is handled by the current instance of {@link SeekableSmbByteChannel}
     *
     * @return newPosition New position within the file.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized long position() throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        return this.random.getFilePointer();
    }

    /**
     * Returns the size of the file handled by the current instance of {@link SeekableSmbByteChannel}. The size
     * is given in number of bytes.
     *
     * @return size Size of the SMB file.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized long size() throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        return this.random.length();
    }

    /**
     * Tries to reposition the pointer into the {@link SmbRandomAccessFile} that is handled by the current instance of {@link SeekableSmbByteChannel}
     *
     * @param newPosition New position within the file.
     * @return Current instance of {@link SeekableSmbByteChannel}.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized SeekableByteChannel position(long newPosition) throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        this.random.seek(newPosition);
        return this;
    }

    /**
     * Truncates the {@link SmbRandomAccessFile} by setting its length to the provided value.
     *
     * @param size New size of the file.
     * @return Current instance of {@link SeekableSmbByteChannel}.
     * @throws IOException If something goes wrong during truncation.
     */
    @Override
    public synchronized SeekableByteChannel truncate(long size) throws IOException {
        if (!this.open) {
            throw new ClosedChannelException();
        }
        this.random.setLength(size);
        return this;
    }

    /**
     * Determines whether the current {@link SeekableSmbByteChannel} is still opened.
     *
     * @return True if {@link SeekableSmbByteChannel} and false otherwise.
     */
    @Override
    public synchronized boolean isOpen() {
        return this.open;
    }

    /**
     * Closes the  current {@link SeekableSmbByteChannel}. After that, is is not possible to either read from or
     * write to the channel.
     *
     * @throws IOException If something goes wrong while closing the channel.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!this.open) {
            return;
        }
        this.open = false;
        this.random.close();
    }
}
