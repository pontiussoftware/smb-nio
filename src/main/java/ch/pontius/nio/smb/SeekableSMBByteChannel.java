package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;
import jcifs.smb.SmbRandomAccessFile;

import java.io.IOException;

import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SeekableByteChannel;

public final class SeekableSMBByteChannel implements SeekableByteChannel {
    /** Internal {@link SmbRandomAccessFile} reference to write to {@link SmbFile}. */
    private final SmbRandomAccessFile random;

    /** Boolean indicating whether this instance of {@link SeekableSMBByteChannel} is open. */
    private volatile boolean open = true;

    /**
     * Constructor for {@link SeekableSMBByteChannel}
     *
     * @param file The {@link SmbFile} instance that should be opened.
     * @param write Flag that indicates, whether write access was requested.
     * @param append Flag that indicates, whether data should be appended.
     * @throws IOException
     */
    SeekableSMBByteChannel(SmbFile file, boolean write, boolean append) throws IOException {
        if (write) {
            file.setReadWrite();
            this.random = new SmbRandomAccessFile(file, "rw");
            if (append) this.random.seek(this.random.length()-1);
        } else {
            file.setReadOnly();
            this.random = new SmbRandomAccessFile(file, "r");
        }
    }
    /**
     * Reads the content from the {@link SmbRandomAccessFile} handled by the current instance of {@link SeekableSMBByteChannel} to
     * the provided {@link ByteBuffer}. The {@link ByteBuffer} is written from its current position to its end.
     *
     * @param dst {@link ByteBuffer} to which to write the data.
     * @return Number of bytes that were read.
     * @throws IOException If something goes wrong while reading to the file.
     */
    @Override
    public synchronized int read(ByteBuffer dst) throws IOException {
        if (!this.open) throw new ClosedChannelException();
        final int len = dst.limit() - dst.position();
        final byte[] buffer = new byte[len];
        final int read = this.random.read(buffer);
        if (read > 0) dst.put(buffer, 0, read);
        return read;
    }

    /**
     * Writes the content of the provided {@link ByteBuffer} into the {@link SmbRandomAccessFile} handled by the current
     * instance of {@link SeekableSMBByteChannel}. The {@link ByteBuffer} is read from its current position to it end.
     *
     * @param src {@link ByteBuffer} from which to read the data.
     * @return Number of bytes that were written.
     * @throws IOException If something goes wrong while writing to the file.
     */
    @Override
    public synchronized int write(ByteBuffer src) throws IOException {
        if (!this.open) throw new ClosedChannelException();
        final int len = src.limit() - src.position();
        final byte[] buffer = new byte[len];
        src.get(buffer);
        this.random.write(buffer);
        return len;
    }

    /**
     * Returns the position of the pointer into the {@link SmbRandomAccessFile} that is handled by the current instance of {@link SeekableSMBByteChannel}
     *
     * @return newPosition New position within the file.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized long position() throws IOException {
        if (!this.open) throw new ClosedChannelException();
        return this.random.getFilePointer();
    }

    /**
     * Returns the size of the file handled by the current instance of {@link SeekableSMBByteChannel}. The size
     * is given in number of bytes.
     *
     * @return size Size of the SMB file.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized long size() throws IOException {
        if (!this.open) throw new ClosedChannelException();
        return this.random.length();
    }

    /**
     * Tries to reposition the pointer into the {@link SmbRandomAccessFile} that is handled by the current instance of {@link SeekableSMBByteChannel}
     *
     * @param  newPosition New position within the file.
     * @return Current instance of {@link SeekableSMBByteChannel}.
     * @throws IOException If something goes wrong while trying to determine file size.
     */
    @Override
    public synchronized SeekableByteChannel position(long newPosition) throws IOException {
        if (!this.open) throw new ClosedChannelException();
        this.random.seek(newPosition);
        return this;
    }

    /**
     * Truncates the {@link SmbRandomAccessFile} by setting its length to the provided value.
     *
     * @param size New size of the file.
     * @return Current instance of {@link SeekableSMBByteChannel}.
     * @throws IOException If something goes wrong during truncation.
     */
    @Override
    public synchronized SeekableByteChannel truncate(long size) throws IOException {
        if (!this.open) throw new ClosedChannelException();
        this.random.setLength(size);
        return this;
    }

    /**
     * Determines whether the current {@link SeekableSMBByteChannel} is still opened.
     *
     * @return True if {@link SeekableSMBByteChannel} and false otherwise.
     */
    @Override
    public synchronized boolean isOpen() {
        return this.open;
    }

    /**
     * Closes the  current {@link SeekableSMBByteChannel}. After that, is is not possible to either read from or
     * write to the channel.
     *
     * @throws IOException If something goes wrong while closing the channel.
     */
    @Override
    public synchronized void close() throws IOException {
        if (!this.open) throw new ClosedChannelException();
        this.open = false;
    }
}
