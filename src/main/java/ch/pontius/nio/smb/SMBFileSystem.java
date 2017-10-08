package ch.pontius.nio.smb;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SMBFileSystem extends FileSystem {
    /** Default separator between path components. */
    static final String PATH_SEPARATOR = "/";

    /** Default separator between scheme and the rest of the path. */
    static final String SCHEME_SEPARATOR = "://";

    /** URI scheme supported by SMBFileSystemProvider. */
    static final String SMB_SCHEME = "smb";

    /** Creates and initializes the set containing the supported FILE_ATTRIBUTE_VIEWS. */
    private static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = new HashSet<>();
    static {
        SUPPORTED_FILE_ATTRIBUTE_VIEWS.add("basic");
    }

    /** The URI for which the {@link SMBFileSystem} was created. */
    private final String identifier;

    /** The {@link SMBFileSystemProvider} instance this {@link SMBFileSystem} belongs to. */
    private final SMBFileSystemProvider provider;

    /**
     * Constructor for {@link SMBFileSystem}.
     *
     * @param identifier The identifier of the {@link SMBFileSystem}; usually defaults to the URI's authority part.
     * @param provider The {@link SMBFileSystemProvider} instance associated with this {@link SMBFileSystem}.
     */
    public SMBFileSystem(String identifier, SMBFileSystemProvider provider, Map<String,?> env) {
        this.identifier = identifier;
        this.provider = provider;
    }


    @Override
    public FileSystemProvider provider() {
        return this.provider;
    }

    @Override
    public void close() throws IOException {
        /* Has no effect as FileSystem cannot be closed. */
    }

    @Override
    public boolean isOpen() {
        return true;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public String getSeparator() {
        return SMBFileSystem.PATH_SEPARATOR;
    }

    @Override
    public Iterable<Path> getRootDirectories() {
        return null;
    }

    @Override
    public Iterable<FileStore> getFileStores() {
        return null;
    }

    @Override
    public Set<String> supportedFileAttributeViews() {
        return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
    }

    @Override
    public Path getPath(String first, String... more) {
        final StringBuilder buffer = new StringBuilder(SMBFileSystem.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + this.identifier);
        buffer.append(first);
        for (String component : more) {
            buffer.append(SMBFileSystem.PATH_SEPARATOR);
            buffer.append(component);
        }

        try {
            final URI uri = new URI(buffer.toString());
            return new SMBPath(this, uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("The provided path components to not make up a valid SMB path.");
        }
    }

    /**
     *
     * @param syntaxAndPattern
     * @return
     */
    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return null;
    }

    /**
     * {@link UserPrincipalLookupService} are not supported by the current version of {@link SMBFileSystem}.
     *
     * @throws UnsupportedOperationException Always
     */
    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("The SMBFileSystem does not support UserPrincipalLookupServices.");
    }

    /**
     * {@link WatchService} are not supported by the current version of {@link SMBFileSystem}.
     *
     * @throws UnsupportedOperationException Always
     */
    @Override
    public WatchService newWatchService() throws IOException {
        throw new UnsupportedOperationException("The SMBFileSystem does not support WatchService.");
    }
}
