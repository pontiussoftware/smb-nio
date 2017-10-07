package ch.pontius.nio.smb;

import jcifs.smb.SmbFile;

import java.io.IOException;
import java.net.URI;

import java.nio.channels.SeekableByteChannel;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.spi.FileSystemProvider;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class SMBFileSystemProvider extends FileSystemProvider {

    /** URI scheme supported by SMBFileSystemProvider. */
    static final String SMB_SCHEME = "smb";

    /** Local fileSystemCache of {@link SMBFileSystem} instances. */
    private final Map<String ,SMBFileSystem> fileSystemCache;

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
        return SMB_SCHEME;
    }

    /**
     * Creates a new {@link SMBFileSystem} instance for the provided URI. {@link SMBFileSystem} instances are cached based
     * on the authority part of the URI (i.e. URI's with the same authority share the same {@link SMBFileSystem} instance).
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
        if (!uri.getScheme().equals(SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");
        if (this.fileSystemCache.containsKey(uri.getAuthority())) throw new FileSystemAlreadyExistsException("Filesystem for the provided server 'smb://" + uri.getAuthority() + "' does already exist.");
        SMBFileSystem system = new SMBFileSystem(uri.getAuthority(), this, env);
        this.fileSystemCache.put(uri.getAuthority(), system);
        return system;
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
        if (!uri.getScheme().equals(SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");
        if (this.fileSystemCache.containsKey(uri.getAuthority())) {
            return this.fileSystemCache.get(uri.getAuthority());
        } else {
            throw new FileSystemNotFoundException("No filesystem for the provided server 'smb://" + uri.getAuthority() + "' could be found.");
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
        if (!uri.getScheme().equals(SMB_SCHEME)) throw new IllegalArgumentException("The provided URI is not an SMB URI.");
        if (this.fileSystemCache.containsKey(uri.getAuthority())) {
            return new SMBPath(this.getFileSystem(uri), uri);
        } else {
            return new SMBPath(this.newFileSystem(uri, new HashMap<>()), uri);
        }
    }

    /**
     *
     * @param path
     * @param options
     * @param attrs
     * @return
     * @throws IOException
     */
    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {

        /* Convert path and instantiate SmbFile. */
        final SMBPath smbPath = SMBPath.fromPath(path);
        final SmbFile file = smbPath.getSmbFile();

        /* Determines how the SeekableByteChannel should be setup. */
        boolean write = false;
        boolean create = false;
        boolean check = false;
        boolean append = false;

        for (OpenOption option : options) {
            if (option.equals(StandardOpenOption.WRITE)) {
                write = true;
            } else if (option.equals(StandardOpenOption.CREATE_NEW)) {
                create = true;
            } else if (option.equals(StandardOpenOption.CREATE)) {
                create = true;
                check = true;
            } else if (option.equals(StandardOpenOption.APPEND)) {
                append = true;
            }
        }

        /* Tries to create a new file, if so specified. */
        if (create) {
            if (file.exists()) {
                if (check) throw new FileAlreadyExistsException("The specified file '" + smbPath.toString() + "' does already exist!");
            } else {
                file.createNewFile();
            }
        }

        /* Returns a new SeekableSMBByteChannel object. */
        return new SeekableSMBByteChannel(file, write, append);
    }

    /**
     *
     * @param dir
     * @param filter
     * @return
     * @throws IOException
     */
    @Override
    public DirectoryStream<Path> newDirectoryStream(Path dir, DirectoryStream.Filter<? super Path> filter) throws IOException {
        return null;
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
            return (V)(new SMBFileAttributeView(SMBPath.fromPath(path).getSmbFile()));
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
}
