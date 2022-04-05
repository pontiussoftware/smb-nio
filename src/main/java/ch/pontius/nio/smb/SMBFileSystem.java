package ch.pontius.nio.smb;

import jcifs.CIFSContext;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.*;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * This class represents a single SMB server (filesystem). The authority part of the SMB URI creates a unique {@link SMBFileSystem}, that is,
 * if you connect to the same server with different credentials, it will results in two distinc {@link SMBFileSystem} instances. Furthermore,
 * different names for the same server will result in different {@link SMBFileSystem} instances too.
 *
 * The {@link SMBFileSystem} is the factory for several types of objects, like {@link SMBPath}, {@link SMBFileStore} etc.
 *
 * @author      Ralph Gasser
 * @version     1.1
 * @since       1.0
 */
public final class SMBFileSystem extends FileSystem {
    /** Default separator between path components. */
    static final String PATH_SEPARATOR = "/";

    /** Default separator between scheme and the rest of the path. The scheme directly preceeds the authority, which denotes the server and the credentials. */
    static final String SCHEME_SEPARATOR = "://";

    /** Default separator between the credentials and the server. Both are part of the authority, that follows directly after the scheme and its separator. */
    static final String CREDENTIALS_SEPARATOR = "@";

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

    /** The {@link CIFSContext} used by this {@link SMBFileSystem}. */
    private final CIFSContext context;

    /**
     * Constructor for {@link SMBFileSystem}.
     *
     * @param provider The {@link SMBFileSystemProvider} instance associated with this {@link SMBFileSystem}.
     * @param authority The identifier of the {@link SMBFileSystem}; usually defaults to the URI's authority part.
     */
    SMBFileSystem(SMBFileSystemProvider provider, String authority, CIFSContext context) {
        this.identifier = authority;
        this.provider = provider;
        this.context = context;
    }

    /**
     * Returns instance of {@link SMBFileSystemProvider} this {@link SMBFileSystem} belongs to.
     *
     * @return {@link SMBFileSystemProvider}
     */
    @Override
    public FileSystemProvider provider() {
        return this.provider;
    }

    /**
     * Removes the current instance of {@link SMBFileSystem} from the {@link SMBFileSystemProvider}'s cache. Calling this method will
     * not actually close any underlying resource.
     *
     * However, existing paths pointing to the current instance of {@link SMBFileSystem} will not be handled to the
     */
    @Override
    public void close() {
        this.provider.fileSystemCache.remove(this.identifier);
    }

    /**
     * Returns true, if the current {@link SMBFileSystem} is still known to the {@link SMBFileSystemProvider}.
     *
     * @return If current {@link SMBFileSystem} is still open.
     */
    @Override
    public boolean isOpen() {
        return this.provider.fileSystemCache.containsKey(this.identifier);
    }

    /**
     * Returns false because generally, {@link SMBFileSystem}'s are not considered to be read-only. However,
     * the concrete access permissions are specific to a file or resource.
     *
     * @return false
     */
    @Override
    public boolean isReadOnly() {
        if (!this.isOpen()) throw new ClosedFileSystemException();
        return false;
    }

    /**
     * Returns the default path sepeator, which is "/".
     *
     * @return "/"
     */
    @Override
    public String getSeparator() {
        return SMBFileSystem.PATH_SEPARATOR;
    }

    /**
     * Returns the root directories, i.e. the list of shares, provided by the current {@link SMBFileSystem}.
     *
     * @return List of shares for the current {@link SMBFileSystem}.
     */
    @Override
    public Iterable<Path> getRootDirectories() {
        if (!this.isOpen()) throw new ClosedFileSystemException();
        try {
            SmbFile file = new SmbFile(SMBFileSystem.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + this.identifier + "/", this.context);
            return Arrays.stream(file.list()).map(s -> (Path)(new SMBPath(this, "/" + s))).collect(Collectors.toList());
        } catch (MalformedURLException | SmbException e) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns the {@link SMBFileStore}s, i.e. the list of shares, provided by the current {@link SMBFileSystem}.
     *
     * @return List of {@link SMBFileStore}s for the current {@link SMBFileSystem}.
     */
    @Override
    public Iterable<FileStore> getFileStores() {
        if (!this.isOpen()) throw new ClosedFileSystemException();
        try {
            SmbFile file = new SmbFile(SMBFileSystem.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + this.identifier + "/", this.context);
            return Arrays.stream(file.list()).map(s -> (FileStore)(new SMBFileStore(this, s))).collect(Collectors.toList());
        } catch (MalformedURLException | SmbException e) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns a containing the names of the supported {@link FileAttributeView}s
     *
     * @return Set with the names of the supported {@link FileAttributeView}s.
     */
    @Override
    public Set<String> supportedFileAttributeViews() {
        if (!this.isOpen()) throw new ClosedFileSystemException();
        return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
    }

    /**
     * Constructs a new {@link SMBPath} by concatenating the provided path components. If the first path starts with
     * a '/' the newly constructed path will be an absolute path. If the last component ends with a '/' the path is treated
     * as a folder.
     *
     * @param first First path component.
     * @param more List of additional path components.
     * @return Constructed {@link SMBPath}.
     */
    @Override
    public Path getPath(String first, String... more) {
        if (!this.isOpen()) throw new ClosedFileSystemException();
        String[] components = Stream.concat(Arrays.stream(new String[] { first }), Arrays.stream(more))
                .toArray(String[]::new);
        boolean folder = components[components.length - 1].endsWith("/");
        final String path = SMBPathUtil.mergePath(
                components,
                0,
                components.length,
                first.startsWith("/"),
                folder
        );
        return new SMBPath(this, path);
    }

    /**
     * Returns a new {@link SMBPathMatcher} for the provided pattern.
     *
     * @param syntaxAndPattern The syntax or pattern that should be used to match paths against.
     * @return {@link SMBPathMatcher}
     */
    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return new SMBPathMatcher(syntaxAndPattern);
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
    public WatchService newWatchService() {
        throw new UnsupportedOperationException("The SMBFileSystem does not support WatchService.");
    }

    /**
     * Getter for the {@link CIFSContext} used by this {@link SMBFileSystem}. For internal use only.
     *
     * @return {@link CIFSContext}
     */
    CIFSContext getContext() {
        return this.context;
    }

    /**
     * Getter for the identifier of this {@link SMBFileSystem}.
     *
     * @return {@link SMBFileSystem}'s identifier, which acts as authority and name of the server.
     */
    String getName() {
        return this.identifier;
    }

    /**
     * Returns the fully qualified name to the server represented by the current instance of {@link SMBFileSystem}.
     *
     * @return FQN of the server represented by the current instance of {@link SMBFileSystem}.
     */
    String getFQN() {
        return SMBFileSystem.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + this.identifier;
    }
}
