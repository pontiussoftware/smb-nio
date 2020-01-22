package ch.pontius.nio.smb;

import ch.pontius.nio.smb.watch.SmbPoller;
import ch.pontius.nio.smb.watch.SmbWatchService;
import ch.pontius.nio.smb.watch.StandardSmbPoller;
import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Configuration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.AbstractCIFSContext;
import jcifs.context.BaseContext;
import jcifs.context.CIFSContextCredentialWrapper;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthentication;
import jcifs.smb.SmbFile;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.AccessMode;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.NotDirectoryException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class acts as a service-provider class for SMB/CIFS based file systems. Internally, it uses jCIFS to provide
 * all the file access functionality.
 *
 * @author      Ralph Gasser
 * @since       1.0
 */
public final class SMBFileSystemProvider extends FileSystemProvider {

    /** Key for the domain property in the env map {@link SMBFileSystemProvider#constructAuthority(URI, Map, CIFSContext)}. */
    public static final String PROPERTY_KEY_DOMAIN = "domain";

    /** Key for the username property in the env map {@link SMBFileSystemProvider#constructAuthority(URI, Map, CIFSContext)}. */
    public static final String PROPERTY_KEY_USERNAME = "username";

    /** Key for the password property in the env map {@link SMBFileSystemProvider#constructAuthority(URI, Map, CIFSContext)}. */
    public static final String PROPERTY_KEY_PASSWORD  = "password";

    /** Key to enable a smb watchservice */
    public static final String PROPERTY_KEY_WATCHSERVICE_ENABLED = "smb.watchservice.enabled";

    /** Key for the smb watchservice poll interval in milliseconds */
    public static final String PROPERTY_KEY_WATCHSERVICE_POLL_INTERVALL = "smb.watchservice.pollInterval";

    /** Key prefix for jcifs properties */
    private static final String JCIFS_PROPERTY_KEY_PREFIX  = "jcifs.";

    private static SMBFileSystemProvider DEFAULT;

    /** Local cache of {@link SMBFileSystem} instances. */
    final Map<String ,SMBFileSystem> fileSystemCache;

    /** Default constructor for {@link SMBFileSystemProvider}. */
    public SMBFileSystemProvider() {
        this.fileSystemCache = new ConcurrentHashMap<>();
    }

    /**
     * Gets the default instance of the SMBFileSystemProvider
     * @return Default instance
     */
    public static synchronized SMBFileSystemProvider getDefault() {
        if (DEFAULT == null) {
            DEFAULT = new SMBFileSystemProvider();
        }
        return DEFAULT;
    }

    /**
     * Returns the default scheme for {@link SMBFileSystemProvider}.
     *
     * @return URI Scheme - 'smb'
     */
    @Override
    public String getScheme() {
        return SMBFileSystem.SMB_SCHEME;
    }

    /**
     * Creates a new {@link SMBFileSystem} instance for the provided URI. {@link SMBFileSystem} instances are cached based
     * on the authority part of the URI (i.e. URI's with the same authority share the same {@link SMBFileSystem} instance).
     *
     * Credentials for connecting with the SMB/CIFS server can be provided in several ways:
     *
     * <ol>
     *      <li>Encode in the URI, e.g. smb://WORKGROUP;admin:1234@192.168.1.10 </li>
     *      <li>Provide in the env Map. To do so, you have to set the keys 'workgroup', 'username' and 'password'. </li>
     *      <li>Provide in the jCIFS config. See jCIFS documentation for more information. </li>
     * </ol>
     *
     * The above options will be considered according to precedence. That is, if the credentials are encoded in the URI those provided in
     * the env map or the jCIFS config will be ignored.
     *
     * @param uri URI for which to create {@link SMBFileSystem}
     * @param env Map containing configuration parameters.
     * @return Newly created {@link SMBFileSystem} instance
     *
     * @throws FileSystemAlreadyExistsException If an instance of {@link SMBFileSystem} already exists for provided URI.
     * @throws IllegalArgumentException If provided URI is not an SMB URI.
     */
    @Override
    public SMBFileSystem newFileSystem(URI uri, Map<String, ?> env) {
        SmbPoller smbPoller = null;
        if (MapUtils.isNotEmpty(env)) {
            if (MapUtils.getBooleanValue(env, PROPERTY_KEY_WATCHSERVICE_ENABLED, false)) {
                Long pollInterval = MapUtils.getLong(env, PROPERTY_KEY_WATCHSERVICE_POLL_INTERVALL);
                smbPoller = (pollInterval != null) ? new StandardSmbPoller(pollInterval) : new StandardSmbPoller();
            }
        }
        return newFileSystem(uri, env, smbPoller);
    }

    /**
     * Creates a new {@link SMBFileSystem} instance for the provided URI. {@link SMBFileSystem} instances are cached based
     * on the authority part of the URI (i.e. URI's with the same authority share the same {@link SMBFileSystem} instance).
     *
     * Credentials for connecting with the SMB/CIFS server can be provided in several ways:
     *
     * <ol>
     *      <li>Encode in the URI, e.g. smb://WORKGROUP;admin:1234@192.168.1.10 </li>
     *      <li>Provide in the env Map. To do so, you have to set the keys 'workgroup', 'username' and 'password'. </li>
     *      <li>Provide in the jCIFS config. See jCIFS documentation for more information. </li>
     * </ol>
     *
     * The above options will be considered according to precedence. That is, if the credentials are encoded in the URI those provided in
     * the env map or the jCIFS config will be ignored.
     *
     * @param uri URI for which to create {@link SMBFileSystem}
     * @param env Map containing configuration parameters.
     * @param smbPoller {@link SmbPoller} to create {@link SmbWatchService} from
     * @return Newly created {@link SMBFileSystem} instance
     *
     * @throws FileSystemAlreadyExistsException If an instance of {@link SMBFileSystem} already exists for provided URI.
     * @throws IllegalArgumentException If provided URI is not an SMB URI.
     */
    public SMBFileSystem newFileSystem(URI uri, Map<String, ?> env, SmbPoller smbPoller) {
        if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");

        /* Constructs a canonical authority string, taking all possible ways to provide credentials into consideration. */
        try {
            CIFSContext context = createContext(env);
            final String authority = constructAuthority(uri, env, context);
            context = addCredential(context, authority);

            /* Tries to create a new SMBFileSystem. */
            if (this.fileSystemCache.containsKey(authority)) {
                throw new FileSystemAlreadyExistsException("Filesystem for the provided server 'smb://" + authority + "' does already exist.");
            }
            final SMBFileSystem system = new SMBFileSystem(this, authority, context, smbPoller);
            this.fileSystemCache.put(authority, system);
            return system;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Failed to URL encode the username and/or password in provided URI.", e);
        } catch (CIFSException e) {
            throw new IllegalArgumentException("Failed to create the CIFS context by the provided configuration parameters.", e);
        }
    }

    /**
     * Retrieves a {@link SMBFileSystem} instance for the provided URI from fileSystemCache or creates a new one if it is missing.
     * {@link SMBFileSystem} instances are cached based on the authority part of the URI (i.e. URI's with the same authority share the same
     * {@link SMBFileSystem} instance).
     *
     * @param uri URI for which to fetch {@link SMBFileSystem}
     * @param env Map containing configuration parameters.
     * @return {@link SMBFileSystem} instance
     *
     * @throws FileSystemNotFoundException If no instance of {@link SMBFileSystem} could be retrieved from fileSystemCache.
     * @throws IllegalArgumentException If provided URI is not an SMB URI.
     */
    public SMBFileSystem getOrCreateFileSystem(URI uri, Map<String, ?> env) {
        if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");

        try {
            final CIFSContext context = createContext(env);
            final String authority = constructAuthority(uri, env, context);
            return fileSystemCache.containsKey(authority) ? fileSystemCache.get(authority) : newFileSystem(uri, env);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Failed to URL encode the username and/or password in provided URI.", e);
        } catch (CIFSException e) {
            throw new IllegalArgumentException("Failed to create the CIFS context by the provided configuration parameters.", e);
        }
    }

    /**
     * Creates a {@link CIFSContext}.
     * @param env Map containing configuration parameters
     * @return {@link CIFSContext}
     * @throws CIFSException if creating the context fails
     */
    private CIFSContext createContext(Map<String, ?> env) throws CIFSException {
        boolean jcifsPropertiesProvided = false;
        if (MapUtils.isNotEmpty(env)) {
            for (final String key : env.keySet()) {
                if (key.startsWith(JCIFS_PROPERTY_KEY_PREFIX)) {
                    jcifsPropertiesProvided = true;
                    break;
                }
            }
        }
        return jcifsPropertiesProvided ? new BaseContext(new PropertyConfiguration(MapUtils.toProperties(env))) : SingletonContext.getInstance();
    }

    /**
     * Adds credentials to the give {@link CIFSContext}
     * @param context The {@link CIFSContext}
     * @param authority The authority part of the URI
     * @return {@link CIFSContext} with credentials
     */
    private CIFSContext addCredential(CIFSContext context, String authority) {
        if (StringUtils.isEmpty(authority)) {
            return context;
        }
        final String domainAndUser = StringUtils.substringBefore(authority, "@");
        final String userInfo = domainAndUser.contains(";") ? StringUtils.substringAfter(domainAndUser, ";") : domainAndUser;
        return (StringUtils.isNotEmpty(userInfo))
                ? new CIFSContextCredentialWrapper((AbstractCIFSContext) context, new NtlmPasswordAuthentication(context, userInfo))
                : context;
    }

    /**
     * Retrieves a {@link SMBFileSystem} instance for the provided URI from fileSystemCache and returns it. {@link SMBFileSystem} instances
     * are cached based on the authority part of the URI (i.e. URI's with the same authority share the same {@link SMBFileSystem} instance).
     *
     * @param uri URI for which to fetch {@link SMBFileSystem}
     * @return {@link SMBFileSystem} instance
     *
     * @throws FileSystemNotFoundException If no instance of {@link SMBFileSystem} could be retrieved from fileSystemCache.
     * @throws IllegalArgumentException If provided URI is not an SMB URI.
     */
    @Override
    public SMBFileSystem getFileSystem(URI uri) {
        if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");

        /* Constructs a canonical authority string, taking all possible ways to provide credentials into consideration. */
        try {
            final String authority = this.constructAuthority(uri, new HashMap<>(), null);

            /* Tries to fetch an existing SMBFileSystem. */
            if (this.fileSystemCache.containsKey(authority)) {
                return this.fileSystemCache.get(authority);
            } else {
                throw new FileSystemNotFoundException("No filesystem for the provided server 'smb://" + authority + "' could be found.");
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Failed to URL encode the username and/or password in provided URI.", e);
        }
    }

    /**
     * Converts the provided URI to an {@link SMBPath} instance and returns it. Automatically links the {@link SMBPath}
     * with the {@link SMBFileSystem} associated with its authority.
     *
     * @param uri The URI from which to create the {@link SMBPath}
     * @return Newly created {@link SMBPath}.
     * @throws IllegalArgumentException If URI is not an SMB URI.
     */
    @Override
    public SMBPath getPath(URI uri) {
        if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");

        /* Constructs a canonical authority string, taking all possible ways to provide credentials into consideration. */
        try {
            final String authority = this.constructAuthority(uri, new HashMap<>(), null);

            /* Lookup authority string to determine, whether a new SMBFileSystem is required. */
            if (this.fileSystemCache.containsKey(authority)) {
                return new SMBPath(this.getFileSystem(uri), uri);
            } else {
                return new SMBPath(this.newFileSystem(uri, new HashMap<>()), uri);
            }
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Failed to URL encode the username and/or password in provided URI.", e);
        }
    }

    /**
     * Creates and returns a new {@link SeekableSMBByteChannel} instance.
     *
     * @param path The {@link SMBPath} for which a byte channel should be opened.
     * @param options A set of {@link StandardOpenOption}s.
     * @param attrs An optional list of file attributes to set when creating the file.
     * @return An instance of {@link SeekableSMBByteChannel}.
     *
     * @throws IllegalArgumentException If provided path is not an {@link SMBPath} instance.
     * @throws IOException If an I/O error occurs
     * @throws UnsupportedOperationException If an unsupported open option is specified (DSYNC, SYNC or SPARSE)
     */
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        /* Convert path and instantiate SmbFile. */
        final SMBPath smbPath = SMBPath.fromPath(path);
        final SmbFile file = smbPath.getSmbFile();

        /* Determines how the SeekableByteChannel should be setup. */
        boolean write = false;
        boolean create = false;
        boolean create_new = false;
        boolean append = false;
        boolean truncate = false;

        for (OpenOption option : options) {
            if (option.equals(StandardOpenOption.WRITE)) {
                write = true;
            } else if (option.equals(StandardOpenOption.CREATE)) {
                create = true;
            } else if (option.equals(StandardOpenOption.CREATE_NEW)) {
                create_new = true;
            } else if (option.equals(StandardOpenOption.APPEND)) {
                append = true;
            } else if (option.equals(StandardOpenOption.TRUNCATE_EXISTING)) {
                truncate = true;
            } else if (option.equals(StandardOpenOption.DSYNC) || option.equals(StandardOpenOption.SYNC) || option.equals(StandardOpenOption.SPARSE) || option.equals(StandardOpenOption.DELETE_ON_CLOSE)) {
                throw new UnsupportedOperationException("SMBFileSystemProvider does not support the option options SYNC, DSYNC, SPARSE or DELETE_ON_CLOSE");
            }
        }

        /* Returns a new SeekableSMBByteChannel object. */
        return new SeekableSMBByteChannel(file, write, create, create_new, truncate, append);
    }

    /**
     * Creates and returns a new {@link SMBDirectoryStream} for the specified path.
     *
     * @param dir The {@link SMBPath} for which to create a new DirectoryStream.
     * @param filter An optional filter that should be applied to filter entries in the stream.
     * @return An instance of {@link SMBDirectoryStream}.
     *
     * @throws IllegalArgumentException If provided path is not an {@link SMBPath} instance.
     * @throws NotDirectoryException If provided {@link SMBPath} does not point to a directory
     * @throws IOException If an I/O error occurs
     */
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return new SMBDirectoryStream(SMBPath.fromPath(dir), filter);
    }

    /**
     * Creates a directory under the provided {@link SMBPath}
     *
     * @param dir {@link SMBPath} to folder that should be created.
     *
     * @throws IllegalArgumentException If provided path is not an {@link SMBPath} instance.
     * @throws IOException If creating the folder fails for some reason.
     */
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        SmbFile smbFile = SMBPath.fromPath(dir).getSmbFile();
        smbFile.mkdir();
    }

    /**
     * Deletes the file under the provided {@link SMBPath}
     *
     * @param path {@link SMBPath} to file that should be deleted.
     *
     * @throws IllegalArgumentException If provided path is not an {@link SMBPath} instance.
     * @throws IOException If deleting the file fails for some reason.
     */
    @Override
    public void delete(Path path) throws IOException {
        SmbFile smbFile = SMBPath.fromPath(path).getSmbFile();
        smbFile.delete();
    }

    /**
     * Copies the file under provided source {@link SMBPath} to the destination {@link SMBPath}.
     * CopyOptions are ignored!
     *
     * @param source Source {@link SMBPath}
     * @param target Destination {@link SMBPath}
     * @param options CopyOptions (no effect)
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws IOException If copying fails for some reason.
     */
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        SmbFile fromFile = SMBPath.fromPath(source).getSmbFile();
        SmbFile toFile = SMBPath.fromPath(target).getSmbFile();
        fromFile.copyTo(toFile);
    }

    /**
     * Moves the file under the provided source {@link SMBPath} to the destination {@link SMBPath}.
     * CopyOptions are ignored!
     *
     * @param source Source {@link SMBPath}
     * @param target Destination {@link SMBPath}
     * @param options CopyOptions (no effect)
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws IOException If moving fails for some reason.
     */
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        SmbFile fromFile = SMBPath.fromPath(source).getSmbFile();
        SmbFile toFile = SMBPath.fromPath(target).getSmbFile();
        fromFile.renameTo(toFile);
    }

    /**
     * Returns true, if the resources specified by the two {@link SMBPath} instance are the same.
     *
     * @param path1 First {@link SMBPath}
     * @param path2 Second {@link SMBPath}
     * @return True if the two paths point to the same resource.
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws IOException If moving fails for some reason.
     */
    @Override
    public boolean isSameFile(Path path1, Path path2) throws IOException {
        SmbFile smbFile1 = SMBPath.fromPath(path1).getSmbFile();
        SmbFile smbFile2 = SMBPath.fromPath(path2).getSmbFile();
        return smbFile1.equals(smbFile2);
    }

    /**
     * Returns true, if the resource specified by the provided {@link SMBPath} instance is hidden..
     *
     * @param path {@link SMBPath} that should be checked.
     * @return True if the resource under {@link SMBPath} is hidden.
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws IOException If moving fails for some reason.
     */
    @Override
    public boolean isHidden(Path path) throws IOException {
        SmbFile smbFile = SMBPath.fromPath(path).getSmbFile();
        return smbFile.isHidden();
    }

    /**
     * Checks access to file under the provided {@link SMBPath}.
     *
     * @param path {@link SMBPath} for which access should be checked.
     * @param modes AccessModes that should be checked. Onl yREAD and WRITE are supported.
     *
     * @throws NoSuchFileException If file or folder specified by {@link SMBPath} does not exist.
     * @throws AccessDeniedException If requested access cannot be provided for file or folder under {@link SMBPath}.
     * @throws IllegalArgumentException If provided path is not a {@link SMBPath} instance.
     * @throws IOException If checking accessfails for some reason.
     */
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        SmbFile smbFile = SMBPath.fromPath(path).getSmbFile();

        /* First check if file exists. */
        if (!smbFile.exists()) throw new NoSuchFileException("The specified SMB resource does not exist: " + path);

        /* Determin which attributes to check. */
        boolean checkRead = false;
        boolean checkWrite = false;
        for (AccessMode mode : modes) {
            if (mode.equals(AccessMode.READ)) checkRead = true;
            if (mode.equals(AccessMode.WRITE)) checkWrite = true;
        }

        /* Perform necessary checks. */
        if (checkRead && !smbFile.canRead())  throw new AccessDeniedException("The specified SMB resource is not readable: " + path);
        if (checkWrite && !smbFile.canWrite())  throw new AccessDeniedException("The specified SMB resource is not writable: " + path);
    }

    /**
     * Reads the file attributes view of the file under the provided {@link SMBPath} and returns it. LinkOption will be ignored as
     * the SMB filesystem does not support symlinks.
     *
     * @param path {@link SMBPath} for which attributes view should be created.
     * @param type Class of the attributes view. Must be either {@link BasicFileAttributeView} or {@link SMBFileAttributeView}
     * @param options LinkOptions; will be ignored.
     * @param <V> Type of the class that's being returned.
     * @return {@link SMBFileAttributeView}
     *
     * @throws IllegalArgumentException If provided paths is not a {@link SMBPath} instance.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <V extends FileAttributeView> V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        if (type == BasicFileAttributeView.class || type == SMBFileAttributeView.class) {
            return (V)(new SMBFileAttributeView(SMBPath.fromPath(path)));
        } else {
            return null;
        }
    }

    /**
     * Reads the file attributes of the file under the provided {@link SMBPath} and returns it.  LinkOption will be ignored as
     * the SMB filesystem does not support symlinks.
     *
     * @param path {@link SMBPath} for which attributes should be read.
     * @param type Class of the attribute. Must be either {@link BasicFileAttributes} or {@link SMBFileAttributes}
     * @param options LinkOptions; will be ignored.
     * @param <A> Type of the class that's being returned.
     * @return {@link SMBFileAttributes}
     *
     * @throws IllegalArgumentException If provided paths is not a {@link SMBPath} instance.
     * @throws IOException If reading attributes fails for some reason.
     */
    @Override
    @SuppressWarnings("unchecked")
    public <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        if (type == BasicFileAttributes.class || type == SMBFileAttributes.class) {
            return (A)(new SMBFileAttributes(SMBPath.fromPath(path).getSmbFile()));
        } else {
            return null;
        }
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) throws IOException {
        return null;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Setting file attributes is currently not supported by SMBFileSystemProvider.");
    }

    @Override
    public FileStore getFileStore(Path path) throws IOException {
        throw new UnsupportedOperationException("Access to FileStore is currently not supported by SMBFileSystemProvider.");
    }

    /**
     * This method is used internally to construct a canonical authority string based on the provided URI and the various ways
     * credentials can be provided. The following options are considered in preceding order:
     *
     * <ol>
     *      <li>Encoded in the URI, e.g. smb://WORKGROUP;admin:1234@192.168.1.10 </li>
     *      <li>In the env Map. To do so, you have to set the keys 'workgroup', 'username' and 'password'. </li>
     *      <li>In the jCIFS config. See jCIFS documentation for more information. </li>
     * </ol>
     *
     * @param uri The URI for which to construct an authority string.
     * @param env The env map. Can be empty.
     * @param context The {@link CIFSContext}
     * @return A canonical authority string.
     */
    private String constructAuthority(URI uri, Map<String, ?> env, CIFSContext context) throws UnsupportedEncodingException {
        /* The authority string. */
        String authority;

        /* Check if URI encodes credentials. Credentials are used in the following order: */
        if (uri.getAuthority().contains(SMBFileSystem.CREDENTIALS_SEPARATOR)) {
            authority = uri.getAuthority();
        } else {
            final StringBuilder builder = new StringBuilder();
            if (MapUtils.isNotEmpty(env)) {
                if (env.containsKey(PROPERTY_KEY_DOMAIN)) {
                    builder.append(env.get(PROPERTY_KEY_DOMAIN));
                    builder.append(";");
                }
                if (env.containsKey(PROPERTY_KEY_USERNAME)) {
                    builder.append(URLEncoder.encode(env.get(PROPERTY_KEY_USERNAME).toString(), "UTF-8"));
                    if (env.containsKey(PROPERTY_KEY_PASSWORD)) {
                        builder.append(":");
                        builder.append(URLEncoder.encode(env.get(PROPERTY_KEY_PASSWORD).toString(), "UTF-8"));
                    }
                }
            } else if (context != null) {
                final Configuration config = context.getConfig();
                if (config.getDefaultDomain() != null) {
                    builder.append(config.getDefaultDomain());
                    builder.append(";");
                }
                if (config.getDefaultUsername() != null) {
                    builder.append(URLEncoder.encode(config.getDefaultUsername(), "UTF-8"));
                    if (config.getDefaultPassword() != null) {
                        builder.append(":");
                        builder.append(URLEncoder.encode(config.getDefaultPassword(), "UTF-8"));
                    }
                }
            }

            if (builder.length() > 0) {
                builder.append(SMBFileSystem.CREDENTIALS_SEPARATOR).append(uri.getAuthority());
                authority = builder.toString();
            } else {
                authority = uri.getAuthority();
            }
        }

        return authority;
    }
}
