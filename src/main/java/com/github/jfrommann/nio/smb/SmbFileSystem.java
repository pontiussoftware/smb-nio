package com.github.jfrommann.nio.smb;

import com.github.jfrommann.nio.smb.watch.SmbPoller;
import com.github.jfrommann.nio.smb.watch.SmbWatchService;
import jcifs.CIFSContext;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.UserPrincipalLookupService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * This class represents a single SMB server (filesystem). The authority part of the SMB URI creates a unique {@link SmbFileSystem}, that is,
 * if you connect to the same server with different credentials, it will results in two distinc {@link SmbFileSystem} instances. Furthermore,
 * different names for the same server will result in different {@link SmbFileSystem} instances too.
 * <p>
 * The {@link SmbFileSystem} is the factory for several types of objects, like {@link SmbPath}, {@link SmbFileStore} etc.
 *
 * @author Ralph Gasser
 */
public final class SmbFileSystem extends FileSystem {
    /**
     * Default separator between path components.
     */
    static final String PATH_SEPARATOR = "/";

    /**
     * Default separator between scheme and the rest of the path. The scheme directly preceeds the authority, which denotes the server and the credentials.
     */
    static final String SCHEME_SEPARATOR = "://";

    /**
     * Default separator between the credentials and the server. Both are part of the authority, that follows directly after the scheme and its separator.
     */
    static final String CREDENTIALS_SEPARATOR = "@";

    /**
     * URI scheme supported by SMBFileSystemProvider.
     */
    static final String SMB_SCHEME = "smb";

    /**
     * Creates and initializes the set containing the supported FILE_ATTRIBUTE_VIEWS.
     */
    private static final Set<String> SUPPORTED_FILE_ATTRIBUTE_VIEWS = new HashSet<>();

    static {
        SUPPORTED_FILE_ATTRIBUTE_VIEWS.add("basic");
    }

    /**
     * The URI for which the {@link SmbFileSystem} was created.
     */
    private final String identifier;

    /**
     * The {@link SmbFileSystemProvider} instance this {@link SmbFileSystem} belongs to.
     */
    private final SmbFileSystemProvider provider;

    private final CIFSContext context;

    /**
     * Optional {@link SmbPoller} to create {@link SmbWatchService} from
     */
    private SmbPoller smbPoller;

    /**
     * Constructor for {@link SmbFileSystem}.
     *
     * @param provider  The {@link SmbFileSystemProvider} instance associated with this {@link SmbFileSystem}.
     * @param authority The identifier of the {@link SmbFileSystem}; usually defaults to the URI's authority part.
     */
    SmbFileSystem(SmbFileSystemProvider provider, String authority, CIFSContext context) {
        this.identifier = authority;
        this.provider = provider;
        this.context = context;
    }

    /**
     * Constructor for {@link SmbFileSystem}.
     *
     * @param provider  The {@link SmbFileSystemProvider} instance associated with this {@link SmbFileSystem}.
     * @param authority The identifier of the {@link SmbFileSystem}; usually defaults to the URI's authority part.
     * @param smbPoller Optional {@link SmbPoller} to create {@link SmbWatchService} from.
     */
    SmbFileSystem(SmbFileSystemProvider provider, String authority, CIFSContext context, SmbPoller smbPoller) {
        this(provider, authority, context);
        this.smbPoller = smbPoller;
    }

    /**
     * Returns instance of {@link SmbFileSystemProvider} this {@link SmbFileSystem} belongs to.
     *
     * @return {@link SmbFileSystemProvider}
     */
    @Override
    public SmbFileSystemProvider provider() {
        return provider;
    }

    /**
     * Returns the {@link CIFSContext}
     *
     * @return {@link CIFSContext}
     */
    public CIFSContext context() {
        return context;
    }

    /**
     * Removes the current instance of {@link SmbFileSystem} from the {@link SmbFileSystemProvider}'s cache. Calling this method will
     * not actually close any underlying resource.
     * <p>
     * However, existing paths pointing to the current instance of {@link SmbFileSystem} will not be handled to the
     */
    @Override
    public void close() {
        this.provider.fileSystemCache.remove(this.identifier);
    }

    /**
     * Returns true, if the current {@link SmbFileSystem} is still known to the {@link SmbFileSystemProvider}.
     *
     * @return If current {@link SmbFileSystem} is still open.
     */
    @Override
    public boolean isOpen() {
        return this.provider.fileSystemCache.containsKey(this.identifier);
    }

    /**
     * Returns false because generally, {@link SmbFileSystem}'s are not considered to be read-only. However,
     * the concrete access permissions are specific to a file or resource.
     *
     * @return false
     */
    @Override
    public boolean isReadOnly() {
        if (!this.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return false;
    }

    /**
     * Returns the default path sepeator, which is "/".
     *
     * @return "/"
     */
    @Override
    public String getSeparator() {
        return SmbFileSystem.PATH_SEPARATOR;
    }

    /**
     * Returns the root directories, i.e. the list of shares, provided by the current {@link SmbFileSystem}.
     *
     * @return List of shares for the current {@link SmbFileSystem}.
     */
    @Override
    public Iterable<Path> getRootDirectories() {
        if (!this.isOpen()) {
            throw new ClosedFileSystemException();
        }
        try {
            SmbFile file = new SmbFile(SmbFileSystem.SMB_SCHEME + SmbFileSystem.SCHEME_SEPARATOR + this.identifier + "/", context);
            return Arrays.stream(file.list()).map(s -> (Path) (new SmbPath(this, "/" + s))).collect(Collectors.toList());
        } catch (MalformedURLException | SmbException e) {
            return new ArrayList<>(0);
        }
    }

    /**
     * Returns the {@link SmbFileStore}s, i.e. the list of shares, provided by the current {@link SmbFileSystem}.
     *
     * @return List of {@link SmbFileStore}s for the current {@link SmbFileSystem}.
     */
    @Override
    public Iterable<FileStore> getFileStores() {
        if (!this.isOpen()) {
            throw new ClosedFileSystemException();
        }
        try {
            SmbFile file = new SmbFile(SmbFileSystem.SMB_SCHEME + SmbFileSystem.SCHEME_SEPARATOR + this.identifier + "/", context);
            return Arrays.stream(file.list()).map(s -> (FileStore) (new SmbFileStore(this, s))).collect(Collectors.toList());
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
        if (!this.isOpen()) {
            throw new ClosedFileSystemException();
        }
        return SUPPORTED_FILE_ATTRIBUTE_VIEWS;
    }

    /**
     * Constructs a new {@link SmbPath} by concatenating the provided path components. If the first path starts with
     * a '/' the newly constructed path will be an absolute path. If the last component ends with a '/' the path is treated
     * as a folder.
     *
     * @param first First path component.
     * @param more  List of additional path components.
     * @return Constructed {@link SmbPath}.
     */
    @Override
    public Path getPath(String first, String... more) {
        if (!this.isOpen()) {
            throw new ClosedFileSystemException();
        }
        final String[] components = new String[more.length + 1];
        components[0] = first;
        if (more.length > 0) {
            System.arraycopy(more, 0, components, 1, more.length);
        }
        final String path = SmbPathUtils.mergePath(components, 0, components.length, first.startsWith("/"), more[more.length - 1].endsWith("/"));
        return new SmbPath(this, path);
    }

    /**
     * Returns a new {@link SmbPathMatcher} for the provided pattern.
     *
     * @param syntaxAndPattern The syntax or pattern that should be used to match paths against.
     * @return {@link SmbPathMatcher}
     */
    @Override
    public PathMatcher getPathMatcher(String syntaxAndPattern) {
        return new SmbPathMatcher(syntaxAndPattern);
    }

    /**
     * {@link UserPrincipalLookupService} are not supported by the current version of {@link SmbFileSystem}.
     *
     * @throws UnsupportedOperationException Always
     */
    @Override
    public UserPrincipalLookupService getUserPrincipalLookupService() {
        throw new UnsupportedOperationException("The SMBFileSystem does not support UserPrincipalLookupServices.");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public WatchService newWatchService() throws IOException {
        if (smbPoller != null) {
            return new SmbWatchService(smbPoller);
        } else {
            throw new IOException("No SMBPoller instance registered, WatchService is not supported.");
        }
    }

    /**
     * Getter for the identifier of this {@link SmbFileSystem}.
     *
     * @return {@link SmbFileSystem}'s identifier, which acts as authority and name of the server.
     */
    String getName() {
        return this.identifier;
    }

    /**
     * Returns the fully qualified name to the server represented by the current instance of {@link SmbFileSystem}.
     *
     * @return FQN of the server represented by the current instance of {@link SmbFileSystem}.
     */
    String getFQN() {
        return SmbFileSystem.SMB_SCHEME + SmbFileSystem.SCHEME_SEPARATOR + this.identifier;
    }
}
