package ch.pontius.nio.smb;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.Config;
import jcifs.Configuration;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.context.SingletonContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class acts as a service-provider class for SMB/CIFS based file systems. Internally, it uses jCIFS to provide
 * all the file access functionality.
 *
 * @author      Ralph Gasser
 * @version     1.2.1
 * @since       1.0.0
 */
public final class SMBFileSystemProvider extends FileSystemProvider {
    /** Internal {@link Logger} instance used by {@link SMBFileSystemProvider}. */
    private final static Logger LOGGER = LoggerFactory.getLogger(SMBFileSystemProvider.class);

    /** Local cache of {@link SMBFileSystem} instances. */
    final Map<String, SMBFileSystem> fileSystemCache;

    /** Default constructor for {@link SMBFileSystemProvider}. */
    public SMBFileSystemProvider() {
        this.fileSystemCache = new ConcurrentHashMap<>();
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
        if (!uri.getScheme().equals(SMBFileSystem.SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");

        /* Constructs a canonical authority string, taking all possible ways to provide credentials into consideration. */
        try {
            final CIFSContext context = this.contextFromMap(env);
            final String authority = this.constructAuthority(uri, context);

            /* Tries to create a new SMBFileSystem. */
            if (this.fileSystemCache.containsKey(authority)) throw new FileSystemAlreadyExistsException("Filesystem for the provided server 'smb://" + authority + "' does already exist.");
            SMBFileSystem system = new SMBFileSystem(this, authority, context);
            this.fileSystemCache.put(authority, system);
            return system;
        } catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException("Failed to URL encode the username and/or password in provided URI.", e);
        }
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
            final CIFSContext context = this.contextFromMap(new HashMap<>());
            final String authority = this.constructAuthority(uri, context);

            /* Tries to fetch an existing SMBFileSystem. */
            if (this.fileSystemCache.containsKey(authority)) {
                return this.fileSystemCache.get(authority);
            } else {
                throw new FileSystemNotFoundException("No filesystem for the provided server 'smb://" + uri.getAuthority() + "' could be found.");
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
            final CIFSContext context = this.contextFromMap(new HashMap<>());
            final String authority = this.constructAuthority(uri, context);

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
     * @throws FileAlreadyExistsException
     *         if a directory could not otherwise be created because a file of
     *         that name already exists <i>(optional specific exception)</i>
     * @throws IOException If creating the folder fails for some reason.
     */
    @Override
    public void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        try (SmbFile smbFile = SMBPath.fromPath(dir).getSmbFile()) {
            smbFile.mkdir();
        } catch (SmbException e) {
            SMBExceptionUtil.rethrowAsNIOException(e, dir);
        }
    }

    /**
     * Deletes the file under the provided {@link SMBPath}
     *
     * @param path {@link SMBPath} to file that should be deleted.
     *
     * @throws IllegalArgumentException If provided path is not an {@link SMBPath} instance.
     * @throws NoSuchFileException
     *         if the file does not exist <i>(optional specific exception)</i>
     * @throws IOException If deleting the file fails for some reason.
     */
    @Override
    public void delete(Path path) throws IOException {
        try (SmbFile smbFile = SMBPath.fromPath(path).getSmbFile()) {
            smbFile.delete();
        } catch (SmbException e) {
            SMBExceptionUtil.rethrowAsNIOException(e, path);
        }
    }

    /**
     * Copies the file under provided source {@link SMBPath} to the destination {@link SMBPath}.
     * Some CopyOptions are ignored!
     *
     * @param source Source {@link SMBPath}
     * @param target Destination {@link SMBPath}
     * @param options CopyOptions
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws NoSuchFileException
     *         if the file does not exist <i>(optional specific exception)</i>
     * @throws FileAlreadyExistsException
     *         if the target file exists but cannot be replaced because the
     *         {@code REPLACE_EXISTING} option is not specified <i>(optional
     *         specific exception)</i>
     * @throws IOException If copying fails for some reason.
     */
    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = false;
        boolean copyAttributes = false;
        for (CopyOption opt : options) {
            if (opt == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
            } else if (opt == StandardCopyOption.COPY_ATTRIBUTES) {
                copyAttributes = true;
            }
        }

        if (copyAttributes) {
            LOGGER.debug("Setting file attributes is currently not supported by SMBFileSystemProvider.");
        }

        try (SmbFile fromFile = SMBPath.fromPath(source).getSmbFile();
                SmbFile toFile = SMBPath.fromPath(target).getSmbFile()) {
            if (!replaceExisting && toFile.exists()) {
                throw new FileAlreadyExistsException(toFile.toString(), null, "The specified SMB resource does already exist.");
            }
            fromFile.copyTo(toFile);
        } catch (SmbException e) {
            SMBExceptionUtil.rethrowAsNIOException(e, source, target);
        }
    }

    /**
     * Moves the file under the provided source {@link SMBPath} to the destination {@link SMBPath}.
     * Some CopyOptions are ignored!
     *
     * @param source Source {@link SMBPath}
     * @param target Destination {@link SMBPath}
     * @param options CopyOptions
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws NoSuchFileException
     *         if the file does not exist <i>(optional specific exception)</i>
     * @throws FileAlreadyExistsException
     *         if the target file exists but cannot be replaced because the
     *         {@code REPLACE_EXISTING} option is not specified <i>(optional
     *         specific exception)</i>
     * @throws IOException If moving fails for some reason.
     */
    @Override
    public void move(Path source, Path target, CopyOption... options) throws IOException {
        boolean replaceExisting = false;
        for (CopyOption opt : options) {
            if (opt == StandardCopyOption.REPLACE_EXISTING) {
                replaceExisting = true;
                break;
            }
        }

        try (SmbFile fromFile = SMBPath.fromPath(source).getSmbFile();
                SmbFile toFile = SMBPath.fromPath(target).getSmbFile()) {
            fromFile.renameTo(toFile, replaceExisting);
        } catch (SmbException e) {
            SMBExceptionUtil.rethrowAsNIOException(e, source, target);
        }
    }

    /**
     * Returns true, if the resources specified by the two {@link SMBPath} instance are the same.
     *
     * @param path1 First {@link SMBPath}
     * @param path2 Second {@link SMBPath}
     * @return True if the two paths point to the same resource.
     *
     * @throws IllegalArgumentException If provided paths are not {@link SMBPath} instances.
     * @throws IOException If an I/O error occurs.
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
     * @throws IOException If an I/O error occurs.
     */
    @Override
    public boolean isHidden(Path path) throws IOException {
        try (SmbFile smbFile = SMBPath.fromPath(path).getSmbFile()) {
            return smbFile.isHidden();
        } catch (SmbException e) {
            SMBExceptionUtil.rethrowAsNIOException(e, path);
            return false;
        }
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
     * @throws IOException If checking access fails for some reason.
     */
    @Override
    public void checkAccess(Path path, AccessMode... modes) throws IOException {
        SmbFile smbFile = SMBPath.fromPath(path).getSmbFile();

        /* First check if file exists. */
        if (!smbFile.exists()) throw new NoSuchFileException("The specified SMB resource does not exist.");

        /* Determin which attributes to check. */
        boolean checkRead = false;
        boolean checkWrite = false;
        for (AccessMode mode : modes) {
            if (mode.equals(AccessMode.READ)) checkRead = true;
            if (mode.equals(AccessMode.WRITE)) checkWrite = true;
        }

        /* Perform necessary checks. */
        if (checkRead && !smbFile.canRead())  throw new AccessDeniedException("The specified SMB resource is not readable.");
        if (checkWrite && !smbFile.canWrite())  throw new AccessDeniedException("The specified SMB resource is not writable.");
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
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        return null;
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new UnsupportedOperationException("Setting file attributes is currently not supported by SMBFileSystemProvider.");
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException("Access to FileStore is currently not supported by SMBFileSystemProvider.");
    }

    /**
     * Converts an environment map to a {@link CIFSContext}.
     *
     * @param env The environment map to convert.
     */
    private CIFSContext contextFromMap(Map<String, ?> env) {
        final Properties properties = new Properties();
        properties.putAll(System.getProperties());
        properties.putAll(env);
        try {
            CIFSContext context = new BaseContext(new PropertyConfiguration(properties));
            if (Config.getBoolean(properties, "smb-nio.useNtlmPasswordAuthenticator", false)) {
                Configuration config = context.getConfig();
                context = context.withCredentials(new NtlmPasswordAuthenticator(config.getDefaultDomain(), config.getDefaultUsername(), config.getDefaultPassword()));
            }
            return context;
        } catch (CIFSException e) {
            LOGGER.warn("There was a problem when parsing CIFS configuration from environment map. Falling back to default context. Message:" + e.getMessage());
            return SingletonContext.getInstance();
        }
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
     * @param context The {@link CIFSContext} used by this {@link SMBFileSystemProvider}. Can be null!
     * @return A canonical authority string.
     */
    private String constructAuthority(URI uri, CIFSContext context) throws UnsupportedEncodingException {
        /* The authority string. */
        final StringBuilder builder = new StringBuilder();

        /*
         * Check if URI encodes credentials. Credentials are used in the following order:
         */
        if (uri.getAuthority() != null && uri.getAuthority().contains(SMBFileSystem.CREDENTIALS_SEPARATOR)) {
            builder.append(uri.getAuthority());
        } else if (context != null) {
            if (context.getConfig().getDefaultDomain() != null) {
                builder.append(context.getConfig().getDefaultDomain());
                builder.append(";");
            }
            if (context.getConfig().getDefaultUsername() != null) {
                builder.append(URLEncoder.encode(context.getConfig().getDefaultUsername(), "UTF-8"));
                if (context.getConfig().getDefaultPassword() != null) {
                    builder.append(":");
                    builder.append(URLEncoder.encode(context.getConfig().getDefaultPassword(), "UTF-8"));
                }
            }

            if (uri.getAuthority() != null) {
                if (builder.length() > 0) {
                    builder.append(SMBFileSystem.CREDENTIALS_SEPARATOR).append(uri.getAuthority());
                } else {
                    builder.append(uri.getAuthority());
                }
            }
        }
        return builder.toString();
    }
}
