package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

public final class SMBPath implements Path {
    /** */
    private final SMBFileSystem fileSystem;

    /** */
    private final URI uri;

    /** */
    private final SmbFile smbFile;

    /** An optional path to which the current path is relative to. */
    private final Optional<String> relativeTo;

    /**
     * Convenience method used to check and cast a {@link Path} to an {@link SMBPath} instance.
     *
     * @param path {@link Path} that should be checked and cast.
     * @return {@link SMBPath}
     * @throws IllegalArgumentException If path is not an instance of {@link SMBPath}.
     */
    static SMBPath fromPath(Path path) {
        if (path instanceof SMBPath) {
            return (SMBPath)path;
        } else {
            throw new IllegalArgumentException("The provided path '" + path.toString() + "' is not an SMB path.");
        }
    }

    /**
     *
     * @param system
     * @param file
     * @return
     * @throws URISyntaxException
     */
    static SMBPath fromFile(SMBFileSystem system, SmbFile file) throws URISyntaxException {
        final URL url = file.getURL();
        final URI uri = new URI(url.getProtocol(), url.getAuthority(), url.getPath(), null);
        return new SMBPath(system, uri);
    }

    /**
     * Constructor for {@link SMBPath}. Creates a new, absolute path to a SMB resource from the provided URI.
     *
     * @param fileSystem {@link SMBFileSystem} object this {@link SMBPath} is associated with.
     * @param
     */
    SMBPath(SMBFileSystem fileSystem, URI uri) {
        if (!uri.getScheme().equals(SMBFileSystemProvider.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI does not point to an SMB resource.");

        this.uri = uri;
        this.fileSystem = fileSystem;
        this.relativeTo = Optional.empty();

        try {
            this.smbFile = new SmbFile(this.uri.getScheme() + SMBFileSystem.SCHEME_SEPARATOR + this.uri.getAuthority(), this.uri.getPath());
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("The provided URI '" + this.uri + "' seems to be malformed.");
        }
    }

    /**
     * Constructor for {@link SMBPath}. Creates a new, absolute path to a SMB resource from the provided host and path string.
     *
     * @param fileSystem {@link SMBFileSystem} object this {@link SMBPath} is associated with.
     * @param authority
     * @param path
     */
    private SMBPath(SMBFileSystem fileSystem, String authority, String path) {
        this.fileSystem = fileSystem;
        this.relativeTo = Optional.empty();

        try {
            this.uri = new URI(SMBFileSystemProvider.SMB_SCHEME, authority, path, null, null);
            this.smbFile = new SmbFile(this.uri.getScheme() + SMBFileSystem.SCHEME_SEPARATOR + this.uri.getAuthority(), this.uri.getPath());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("The provided authority or path seems to be malformed.");
        }
    }

    /**
     *
     * @param fileSystem
     * @param authority
     * @param relativeTo
     * @param path
     */
    private SMBPath(SMBFileSystem fileSystem, String authority, String relativeTo, String path) {
        /* Append path separator if missing. */
        if (relativeTo != null && !relativeTo.endsWith(SMBFileSystem.PATH_SEPARATOR)) {
            relativeTo += SMBFileSystem.PATH_SEPARATOR;
        }

        this.fileSystem = fileSystem;
        this.relativeTo = Optional.ofNullable(relativeTo);

        /* Construct. */
        try {
            this.uri = new URI(SMBFileSystemProvider.SMB_SCHEME, authority, this.relativeTo.map(s -> s + path).orElse(path), null, null);
            this.smbFile = new SmbFile(this.uri.getScheme() + SMBFileSystem.SCHEME_SEPARATOR + this.uri.getAuthority(), this.uri.getPath());
        } catch (MalformedURLException | URISyntaxException e) {
            throw new IllegalArgumentException("The provided authority or path seems to be malformed.");
        }
    }

    /**
     * Getter for {@link SMBFileSystem} this {@link SMBPath} belongs to.
     *
     * @return {@link SMBFileSystem}
     */
    @Override
    public final FileSystem getFileSystem() {
        return this.fileSystem;
    }

    /**
     * {@link SMBPath} is always absolute!
     *
     * @return true
     */
    @Override
    public final boolean isAbsolute() {
        return !this.relativeTo.isPresent();
    }

    @Override
    public Path getRoot() {
        return relativeTo.map(s -> new SMBPath(this.fileSystem, this.uri.getAuthority(), s)).orElse(new SMBPath(this.fileSystem, this.uri.getAuthority(), "/"));
    }

    @Override
    public Path getFileName() {
        String[] split = SMBPathUtil.splitPath(this.uri.getPath());
        String relativeTo = SMBPathUtil.mergePath(split, 0, split.length-2, true, true);
        return new SMBPath(this.fileSystem, this.uri.getAuthority(), relativeTo, split[split.length-1]);
    }

    @Override
    public Path getParent() {
        String[] split = SMBPathUtil.splitPath(this.uri.getPath());
        if (split.length > 1) {
            String reduced = SMBPathUtil.mergePath(split, 0, split.length-2, true, true);
            return new SMBPath(this.fileSystem, this.uri.getAuthority(), null, reduced);
        } else {
            return null;
        }
    }

    @Override
    public int getNameCount() {
        String[] split = SMBPathUtil.splitPath(this.uri.getPath());
        return split.length;
    }

    @Override
    public Path getName(int index) {
        String[] split = SMBPathUtil.splitPath(this.uri.getPath());
        String reduced = SMBPathUtil.mergePath(split, 0, index, true, true);
        return new SMBPath(this.fileSystem, this.uri.getAuthority(), null, reduced);
    }

    @Override
    public Path subpath(int beginIndex, int endIndex) {
        throw new UnsupportedOperationException("It is not possible to create a sub path from an SMBPath.");
    }

    @Override
    public boolean startsWith(Path other) {
        return this.startsWith(other.toString());
    }

    @Override
    public boolean startsWith(String other) {
        return this.uri.getPath().startsWith(other);
    }

    @Override
    public boolean endsWith(Path other) {
        return this.endsWith(other.toString());
    }

    @Override
    public boolean endsWith(String other) {
        return this.uri.getPath().endsWith(other);
    }

    @Override
    public Path normalize() {
        return null;
    }


    /**
     *
     * @param other
     * @return
     *
     * @throws IllegalArgumentException If other path is not an instance of {@link SMBPath}.
     */
    @Override
    public Path resolve(Path other) {
        /* Check if other path is actuall an SMBPath. */
        if (!(other instanceof SMBPath)) throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path.");

        /* Check if current path is a folder (Important!). */
        if (!this.isFolder())  throw new IllegalArgumentException("The current path appears to be a file. You cannot resolve another path against a file path. Either add a trailing '/' to indicate that this is a folder or use resolveSibling() instead.");

        /* If other is absolute, return other else resolve. */
        if (other.isAbsolute()) {
            return other;
        } else {
            return new SMBPath(this.fileSystem, this.uri.resolve(((SMBPath) other).uri));
        }
    }

    /**
     *
     * @param other
     * @return
     */
    @Override
    public Path resolve(String other) {
        /* Check if current path is a folder (Important!). */
        if (!this.isFolder())  throw new IllegalArgumentException("The current path appears to be a file. You cannot resolve another path against a file path. Either add a trailing '/' to indicate that this is a folder or use resolveSibling() instead.");

        /* Resolve and return new path. */
        return new SMBPath(this.fileSystem, this.uri.resolve(other));
    }

    /**
     *
     * @param other
     * @return
     *
     * @throws IllegalArgumentException If other path is not an instance of {@link SMBPath}.
     */
    @Override
    public Path resolveSibling(Path other) {
        if (!(other instanceof SMBPath)) throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path.");
        if (other.isAbsolute()) {
            return other;
        } else {
            return this.getParent().resolve(other);
        }
    }

    /**
     *
     * @param other
     * @return
     */
    @Override
    public Path resolveSibling(String other) {
        return this.getParent().resolve(other);
    }

    @Override
    public Path relativize(Path other) {
        return null;
    }

    @Override
    public URI toUri() {
        return this.uri;
    }

    /**
     *
     * @return
     */
    @Override
    public Path toAbsolutePath() {
        return new SMBPath(this.fileSystem, this.uri);
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Symbolic links are currently not supported by SMB paths.");
    }

    @Override
    public File toFile() {
        return new File(this.smbFile.getUncPath());
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] events, WatchEvent.Modifier... modifiers) throws IOException {
        throw new UnsupportedOperationException("FileWatchers are currently not supported by SMB paths.");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... events) throws IOException {
        throw new UnsupportedOperationException("FileWatchers are currently not supported by SMB paths.");
    }

    @Override
    public Iterator<Path> iterator() {
        final List<Path> list = new ArrayList<>();
        final String[] split = SMBPathUtil.splitPath(this.uri.getPath());
        for (int i = 0; i<split.length;i++) {
            String path = SMBPathUtil.mergePath(split, 0, i,true,true);
            list.add(new SMBPath(this.fileSystem, this.uri.getAuthority(), null, path));
        }
        return list.iterator();
    }


    @Override
    public int compareTo(Path other) {
        return 0;
    }


    /**
     * Default toString() method.
     *
     * @return
     */
    public String toString() {
        return relativeTo.map(s -> this.uri.getPath().replaceFirst(s, "")).orElse(this.uri.getPath());
    }

    /**
     * Getter for {@link SmbFile} associated with this {@link SMBPath}
     *
     * @return {@link SmbFile}
     */
    SmbFile getSmbFile() {
        return this.smbFile;
    }


    /**
     * Checks whether the current {@link SMBPath} is a folder.
     *
     * @return True if current {@link SMBPath} is a folder.
     */
    private boolean isFolder() {
        return this.uri.getPath().endsWith("/");
    }
}
