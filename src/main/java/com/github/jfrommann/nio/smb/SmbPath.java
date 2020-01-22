package com.github.jfrommann.nio.smb;

import com.github.jfrommann.nio.smb.watch.SmbWatchService;
import com.sun.nio.file.ExtendedWatchEventModifier;
import jcifs.smb.SmbFile;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.FileSystem;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;


public final class SmbPath implements Path {
    /**
     * Reference to the {@link SmbFileSystem}.
     */
    private final SmbFileSystem fileSystem;

    /**
     * A list of path components that make up the current implementation of {@link SmbPath}.
     */
    private String[] components;

    /**
     * Flag indicating whether the current instance of {@link SmbPath} is an absolute path.
     */
    private final boolean absolute;

    /**
     * Flag indicating whether the current instance of {@link SmbPath} points to a folder.
     */
    private final boolean folder;

    /**
     * Convenience method used to check and cast a {@link Path} to an {@link SmbPath} instance.
     *
     * @param path {@link Path} that should be checked and cast.
     * @return {@link SmbPath}
     * @throws IllegalArgumentException If path is not an instance of {@link SmbPath}.
     */
    static SmbPath fromPath(Path path) {
        if (path instanceof SmbPath) {
            return (SmbPath) path;
        } else {
            throw new IllegalArgumentException("The provided path '" + path.toString() + "' is not an SMB path.");
        }
    }

    /**
     * Constructor for {@link SmbPath}. Creates a new, absolute path to a SMB resource from the provided URI.
     *
     * @param fileSystem {@link SmbFileSystem} object this {@link SmbPath} is associated with.
     * @param uri        The URI pointing to the desired file or folder on the SMB file system.
     */
    SmbPath(SmbFileSystem fileSystem, URI uri) {
        if (!uri.getScheme().equals(SmbFileSystem.SMB_SCHEME)) {
            throw new IllegalArgumentException("The provided URI does not point to an SMB resource.");
        }

        this.folder = SmbPathUtils.isFolder(uri.getPath());
        this.absolute = SmbPathUtils.isAbsolutePath(uri.getPath());
        this.components = SmbPathUtils.splitPath(uri.getPath());
        this.fileSystem = fileSystem;

    }

    /**
     * Constructor for {@link SmbPath}. Constructs a new path to a SMB resource from the provided host and path string.
     *
     * @param fileSystem {@link SmbFileSystem} object this {@link SmbPath} is associated with.
     * @param path       The path. It can either be relative or absolute.
     */
    SmbPath(SmbFileSystem fileSystem, String path) {
        /* Make sure that path is absolute. */
        this.absolute = SmbPathUtils.isAbsolutePath(path);
        this.folder = SmbPathUtils.isFolder(path);
        this.components = SmbPathUtils.splitPath(path);
        this.fileSystem = fileSystem;
    }

    /**
     * Getter for {@link SmbFileSystem} this {@link SmbPath} belongs to.
     *
     * @return {@link SmbFileSystem}
     */
    @Override
    public final FileSystem getFileSystem() {
        return this.fileSystem;
    }

    /**
     * {@link SmbPath} is always absolute!
     *
     * @return true
     */
    @Override
    public final boolean isAbsolute() {
        return this.absolute;
    }

    /**
     * Returns the root component of this {@link SmbPath} or null, if the path is relative.
     *
     * @return Root component of this {@link SmbPath}
     */
    @Override
    public Path getRoot() {
        if (this.absolute) {
            return new SmbPath(this.fileSystem, "/");
        } else {
            return null;
        }
    }

    /**
     * Returns a new, relative {@link SmbPath} instance that just contains the last path component of the current {@link SmbPath}'s path.
     *
     * @return {@link SmbPath} for the file name.
     */
    @Override
    public Path getFileName() {
        return new SmbPath(this.fileSystem, this.components[this.components.length - 1]);
    }

    /**
     * Returns a new {@link SmbPath} that points to the parent of the current {@link SmbPath}. If the current
     * {@link SmbPath} instance does not have a parent, this method returns null.
     *
     * @return Parent {@link SmbPath}.
     */
    @Override
    public Path getParent() {
        if (this.components.length > 1) {
            String reduced = SmbPathUtils.mergePath(this.components, 0, this.components.length - 1, this.absolute, true);
            return new SmbPath(this.fileSystem, reduced);
        } else {
            return null;
        }
    }

    /**
     * Returns the number of path components in the current {@link SmbPath}'s path.
     *
     * @return Number of path components.
     */
    @Override
    public int getNameCount() {
        return this.components.length;
    }

    /**
     * Returns a name element of this {@link SmbPath} as a new {@link SmbPath} object.
     * <p>
     * The index parameter is the index of the name element to return. The element that is closest to the root in the directory hierarchy has index 0.
     * The element that is farthest from the root has index count-1.
     *
     * @param index The index of the element
     * @return The name element.
     */
    @Override
    public Path getName(int index) {
        if (index < 0 || index >= this.components.length) {
            throw new IllegalArgumentException("The provided index is out of bounds.");
        }
        String reduced = SmbPathUtils.mergePath(this.components, index, index, false, index == this.components.length - 1 && this.folder);
        return new SmbPath(this.fileSystem, reduced);
    }

    /**
     * Returns a relative Path that is a subsequence of the name elements of this path.
     *
     * @param beginIndex The index of the first element, inclusive
     * @param endIndex   The index of the last element, exclusive
     * @return The resulting subpath.
     * @throws IllegalArgumentException If beginIndex is negative, or greater than or equal to the number of elements. If endIndex is less than or equal to beginIndex, or larger than the number of elements.
     */
    @Override
    public Path subpath(int beginIndex, int endIndex) {
        if (beginIndex < 0 || endIndex >= this.components.length) {
            throw new IllegalArgumentException("The provided indices are out of bounds.");
        }
        if (beginIndex > endIndex) {
            throw new IllegalArgumentException("The beginIndex must be smaller than the endIndex.");
        }
        String reduced = SmbPathUtils.mergePath(this.components, beginIndex, endIndex, false, endIndex == this.components.length - 1 && this.folder);
        return new SmbPath(this.fileSystem, reduced);
    }

    /**
     * Tests if this path starts with the given path. If the two paths belong to a different {@link FileSystem} then this
     * method always returns false. Otherwise, a string comparison is performed.
     *
     * @param other The given path
     * @return True if this path starts with the given path; otherwise false
     */
    @Override
    public boolean startsWith(Path other) {
        return other.getFileSystem() == this.fileSystem && this.startsWith(other.toString());
    }

    /**
     * Tests if this path starts with the provided string. The path separators will be taken into account.
     *
     * @param other The given path
     * @return True if this path starts with the given string; otherwise false
     */
    @Override
    public boolean startsWith(String other) {
        String path = SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
        return path.startsWith(other);
    }

    /**
     * Tests if this path ends with the given path. If the two paths belong to a different {@link FileSystem} then this
     * method always returns false. Otherwise, a string comparison is performed.
     *
     * @param other The given path
     * @return True if this path starts with the given path; otherwise false
     */
    @Override
    public boolean endsWith(Path other) {
        return other.getFileSystem() == this.fileSystem && this.endsWith(other.toString());
    }

    /**
     * Tests if this path ends with the provided string. The path separators will be taken into account.
     *
     * @param other The given path
     * @return True if this path starts with the given string; otherwise false
     */
    @Override
    public boolean endsWith(String other) {
        String path = SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
        return path.endsWith(other);
    }

    /**
     * Returns a path that is this path with redundant name elements, like "." or ".." eliminated.
     *
     * @return Normalized {@link SmbPath}.
     */
    @Override
    public Path normalize() {
        final ArrayList<String> normalized = new ArrayList<>();
        for (String component : this.components) {
            if (component.equals(".")) {
                continue;
            } else if (component.equals("..") && normalized.size() > 1) {
                normalized.remove(normalized.size() - 1);
            } else if (component.equals("..") && normalized.size() > 0) {
                continue;
            } else {
                normalized.add(component);
            }
        }
        String path = SmbPathUtils.mergePath(normalized.toArray(new String[normalized.size()]), 0, normalized.size(), this.absolute, this.folder);
        return new SmbPath(this.fileSystem, path);
    }

    /**
     * Resolve the given path against this {@link SmbPath}.
     * <p>
     * If the other parameter is an absolute path then this method trivially returns other. If other is an empty path then this method trivially returns this path.
     *
     * @param other The path to resolve against this path
     * @return The resulting {@link SmbPath}
     * @throws IllegalArgumentException If other path is not a {@link SmbPath} OR does not belong to the same {@link SmbFileSystem} OR if this path points to a file.
     */
    @Override
    public Path resolve(Path other) {
        /* Check if other path is on the same filesystem. */
        if (!(other instanceof SmbPath)) {
            throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path.");
        }
        if (((SmbPath) other).fileSystem != this.fileSystem) {
            throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path on the same file system.");
        }

        /* Check if current path is a folder (Important!). */
        if (!this.isFolder()) {
            throw new IllegalArgumentException(
                    "The current path appears to be a file. You cannot resolve another path against a file path. Either add a trailing '/' to indicate that this is a folder or use resolveSibling() instead.");
        }

        /* If other is absolute, return other else resolve. */
        if (other.isAbsolute()) {
            return other;
        } else {
            String[] components = new String[other.getNameCount() + this.getNameCount()];
            System.arraycopy(this.components, 0, components, 0, this.getNameCount());
            System.arraycopy(((SmbPath) other).components, 0, components, this.getNameCount(), other.getNameCount());
            String path = SmbPathUtils.mergePath(components, 0, components.length, this.absolute, ((SmbPath) other).folder);
            return new SmbPath(this.fileSystem, path);
        }
    }

    /**
     * Resolve the given path string against this {@link SmbPath}.
     * <p>
     * If the other parameter is an absolute path then this method trivially returns other. If other is an empty path then this method trivially returns this path.
     *
     * @param other The path to resolve against this path
     * @return The resulting {@link SmbPath}
     * @throws IllegalArgumentException If other path is not a {@link SmbPath} OR does not belong to the same {@link SmbFileSystem} OR if this path points to a file.
     */
    @Override
    public Path resolve(String other) {
        /* Check if current path is a folder (Important!). */
        if (!this.isFolder()) {
            throw new IllegalArgumentException(
                    "The current path appears to be a file. You cannot resolve another path against a file path. Either add a trailing '/' to indicate that this is a folder or use resolveSibling() instead.");
        }

        if (SmbPathUtils.isAbsolutePath(other)) {
            return new SmbPath(this.fileSystem, other);
        } else {
            String[] split = SmbPathUtils.splitPath(other);
            String[] components = new String[split.length + this.components.length];
            System.arraycopy(this.components, 0, components, 0, this.components.length);
            System.arraycopy(split, 0, components, this.components.length, split.length);
            String path = SmbPathUtils.mergePath(components, 0, components.length, this.absolute, SmbPathUtils.isFolder(other));
            return new SmbPath(this.fileSystem, path);
        }
    }

    /**
     * Resolve the given path against this {@link SmbPath}'s parent.
     * <p>
     * If the other parameter is an absolute path then this method trivially returns other. If other is an empty path then this method trivially returns this path.
     *
     * @param other The path to resolve against this path
     * @return The resulting {@link SmbPath}
     * @throws IllegalArgumentException If other path is not a {@link SmbPath} OR does not belong to the same {@link SmbFileSystem}.
     */
    @Override
    public Path resolveSibling(Path other) {
        /* Check if other path is on the same filesystem. */
        if (!(other instanceof SmbPath)) {
            throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path.");
        }
        if (((SmbPath) other).fileSystem != this.fileSystem) {
            throw new IllegalArgumentException("You can only resolve an SMB path against another SMB path on the same file system.");
        }

        if (other.isAbsolute()) {
            return other;
        } else {
            String[] components = new String[other.getNameCount() + this.getNameCount() - 1];
            System.arraycopy(this.components, 0, components, 0, this.components.length - 1);
            System.arraycopy(((SmbPath) other).components, 0, components, this.components.length - 1, ((SmbPath) other).components.length);
            String path = SmbPathUtils.mergePath(components, 0, components.length, this.absolute, ((SmbPath) other).folder);
            return new SmbPath(this.fileSystem, path);
        }
    }

    /**
     * Resolve the given path string against this {@link SmbPath}.
     * <p>
     * If the other parameter is an absolute path then this method trivially returns other. If other is an empty path then this method trivially returns this path.
     *
     * @param other The path to resolve against this path
     * @return The resulting {@link SmbPath}
     * @throws IllegalArgumentException If other path is not a {@link SmbPath} OR does not belong to the same {@link SmbFileSystem} OR if this path points to a file.
     */
    @Override
    public Path resolveSibling(String other) {
        /* Check if current path is a folder (Important!). */
        if (!this.isFolder()) {
            throw new IllegalArgumentException(
                    "The current path appears to be a file. You cannot resolve another path against a file path. Either add a trailing '/' to indicate that this is a folder or use resolveSibling() instead.");
        }

        if (SmbPathUtils.isAbsolutePath(other)) {
            return new SmbPath(this.fileSystem, other);
        } else {
            String[] split = SmbPathUtils.splitPath(other);
            String[] components = new String[split.length + this.components.length - 1];
            System.arraycopy(this.components, 0, components, 0, this.components.length - 1);
            System.arraycopy(split, 0, components, this.components.length - 1, split.length);
            String path = SmbPathUtils.mergePath(components, 0, components.length, this.absolute, SmbPathUtils.isFolder(other));
            return new SmbPath(this.fileSystem, path);
        }
    }

    /**
     * Constructs a relative path between this path and a given path.
     *
     * @param other The other path.
     * @throws IllegalArgumentException If other path is not a {@link SmbPath} OR does not belong to the same {@link SmbFileSystem} OR if this path points to a file.
     */
    @Override
    public Path relativize(Path other) {
        /* Check type. */
        if (!(other instanceof SmbPath)) {
            throw new IllegalArgumentException("The provided path is not a SMBPath.");
        }
        SmbPath target = (SmbPath) other;

        /* Check if both paths are of the same type. */
        if (this.fileSystem != target.fileSystem) {
            throw new IllegalArgumentException("The two paths do not belong to the same filesystem.");
        }
        if (this.absolute != target.absolute) {
            throw new IllegalArgumentException("The two paths are of a different type (absolute vs. relative).");
        }

        /* Construct the relative path. */
        boolean common = true;
        int lastIndex = 0;
        final List<String> newPath = new ArrayList<>();
        for (int i = 0; i < this.components.length; i++) {
            if (common) {
                if (i < target.components.length) {
                    if (this.components[i].equals(target.components[i])) {
                        lastIndex++;
                    } else {
                        common = false;
                        newPath.add("..");
                    }
                } else {
                    newPath.add("..");
                    common = false;
                }
            } else {
                newPath.add("..");
            }
        }

        if (lastIndex < target.components.length) {
            newPath.addAll(Arrays.asList(target.components).subList(lastIndex, target.components.length));
        }

        /* Create new relative path. */
        String[] array = new String[newPath.size()];
        String path = SmbPathUtils.mergePath(newPath.toArray(array), 0, newPath.size(), false, target.folder);
        return new SmbPath(this.fileSystem, path);
    }

    /**
     * Returns a URI to represent this {@link SmbPath}.
     *
     * @return The URI representing this path
     */
    @Override
    public URI toUri() {
        String path = SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
        try {
            return new URI(SmbFileSystem.SMB_SCHEME, this.fileSystem.getName(), path, null, null);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("The current instance of SMBPath '" + this.toString() + "' cannot be transformed to a valid URI.");
        }
    }

    /**
     * If this {@link SmbPath} is absolute then the method returns this. Otherwise, the given path is resolved against the top-level directory.
     *
     * @return Absolute {@link SmbPath}
     */
    @Override
    public Path toAbsolutePath() {
        if (this.isAbsolute()) {
            return this;
        } else {
            return new SmbPath(this.fileSystem, "/").resolve(this);
        }
    }

    /**
     * Returns an iterator over the name elements of this {@link SmbPath}.
     * <p>
     * The first element returned by the iterator represents the name element that is closest to the root in the directory hierarchy, the second element is the next closest,
     * and so on. The last element returned is the name of the file or directory denoted by this path. The root component, if present, is not returned by the iterator.
     *
     * @return An iterator over the name elements of this path.
     */
    @Override
    public Iterator<Path> iterator() {
        final List<Path> elements = new ArrayList<>(this.components.length);
        for (int i = 0; i < this.components.length - 1; i++) {
            elements.add(new SmbPath(this.fileSystem, this.components[i] + SmbFileSystem.PATH_SEPARATOR));
        }
        elements.add(new SmbPath(this.fileSystem, this.components[this.components.length - 1] + (this.folder ? SmbFileSystem.PATH_SEPARATOR : "")));
        return elements.iterator();
    }

    /**
     * Compares two abstract paths lexicographically. This method does not access the file system and neither file is required to exist.
     *
     * @param other The path compared to this {@link SmbPath}.
     * @return Zero if the argument is equal to this path, a value less than zero if this path is lexicographically less than the argument, or a value greater than zero if this path is lexicographically greater than the argument
     */
    @Override
    public int compareTo(Path other) {
        /* Check if other path is on the same filesystem. */
        if (!(other instanceof SmbPath)) {
            throw new IllegalArgumentException("You can only compare an SMB path against another SMB path.");
        }
        if (((SmbPath) other).fileSystem != this.fileSystem) {
            throw new IllegalArgumentException("You can only compare an SMB path against another SMB path on the same file system.");
        }

        final String thisPath = SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
        final String[] otherComponents = ((SmbPath) other).components;
        final String thatPath = SmbPathUtils.mergePath(otherComponents, 0, otherComponents.length, other.isAbsolute(), ((SmbPath) other).folder);
        return thisPath.compareTo(thatPath);
    }

    /**
     * Default toString() method.
     *
     * @return String representation of {@link SmbPath}.
     */
    public String toString() {
        return SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
    }

    /**
     * Creates a new {@link SmbFile} associated with this {@link SmbPath} and returns it.
     *
     * @return {@link SmbFile}
     */
    SmbFile getSmbFile() throws IOException {
        if (!this.fileSystem.isOpen()) {
            throw new ClosedFileSystemException();
        }
        String path = SmbPathUtils.mergePath(this.components, 0, this.components.length, this.absolute, this.folder);
        return new SmbFile(this.fileSystem.getFQN() + SmbFileSystem.PATH_SEPARATOR + SmbFileSystem.PATH_SEPARATOR + path, fileSystem.context());
    }

    /**
     * Tests this path for equality with the given object. If the given object is not a {@link SmbPath}, or is a {@link SmbPath} associated with a
     * different {@link SmbFileSystem}, then this method returns false.
     *
     * @param other The object to which this object is to be compared
     * @return If, and only if, the given object is a {@link SmbPath} that is identical to this {@link SmbPath}.
     */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SmbPath)) {
            return false;
        }
        if (((SmbPath) other).fileSystem != this.fileSystem) {
            return false;
        }
        return Arrays.equals(this.components, ((SmbPath) other).components);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(fileSystem.getName()).append(components).toHashCode();
    }

    /**
     * Checks whether the current {@link SmbPath} is a folder.
     *
     * @return True if current {@link SmbPath} is a folder.
     */
    public boolean isFolder() {
        return this.folder;
    }

    @Override
    public File toFile() {
        throw new UnsupportedOperationException("It is not possible to construct a file from a SMB path.");
    }

    @Override
    public Path toRealPath(LinkOption... options) throws IOException {
        throw new UnsupportedOperationException("Symbolic links are currently not supported by SMB paths.");
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>[] kinds, WatchEvent.Modifier... modifiers) throws IOException {
        if (watcher instanceof SmbWatchService) {
            return ((SmbWatchService) watcher).register(this, kinds, modifiers);
        } else {
            throw new IOException("Unsupported type of WatchService: " + watcher);
        }
    }

    @Override
    public WatchKey register(WatchService watcher, WatchEvent.Kind<?>... kinds) throws IOException {
        return register(watcher, kinds, ExtendedWatchEventModifier.FILE_TREE);
    }
}
