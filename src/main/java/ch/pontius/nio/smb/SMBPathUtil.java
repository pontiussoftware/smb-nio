package ch.pontius.nio.smb;

public final class SMBPathUtil {


    /**
     * Private constructor; this class cannot be instantiated.
     */
    private SMBPathUtil() {}


    /**
     *  Checks if provided path is an absolute path (i.e. starts with an /).
     *
     * @param path Path that should be checked.
     * @return True if provided path is a relative path and false otherwise.
     */
    public static boolean isAbsolutePath(String path) {
        return path.startsWith(SMBFileSystem.PATH_SEPARATOR);
    }

    /**
     *  Checks if provided path is a relative path (i.e. does not start with an /).
     *
     * @param path Path that should be checked.
     * @return True if provided path is a relative path and false otherwise.
     */
    public static boolean isRelativePath(String path) {
        return !path.startsWith(SMBFileSystem.PATH_SEPARATOR);
    }

    /**
     * Normalizes the provided authority for internal usage.
     *
     * All authorities used by these classes start with 'smb://' and have NO trailing '/'!
     *
     * @param authority Authority that should be normalized.
     * @return Normalized authority
     */
    public static String normalizeAuthority(String authority) {
         /* Prepend smb:// if not provided. */
        if (!authority.startsWith(SMBFileSystemProvider.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR)) {
            authority = (SMBFileSystemProvider.SMB_SCHEME + SMBFileSystem.SCHEME_SEPARATOR + authority);
        }

        /* Remove trailing / if any. */
        if (authority.endsWith(SMBFileSystem.PATH_SEPARATOR)) {
            authority = authority.substring(0, authority.length()-1);
        }

        return authority;
    }

    /**
     * Splits the provided path into path components.
     *
     * @param path Path that should be split.
     * @return Array of path components.
     */
    public static String[] splitPath(String path) {
        return path.split(SMBFileSystem.PATH_SEPARATOR);
    }

    /**
     * Splits the provided path into path components.
     *
     * @return Array of path components.
     */
    public static String mergePath(String[] components, boolean absolute, boolean folder) {
        StringBuilder builder = new StringBuilder();
        if (absolute) builder.append(SMBFileSystem.PATH_SEPARATOR);
        for (String component : components) {
            builder.append(component);
            builder.append(SMBFileSystem.PATH_SEPARATOR);
        }
        if (!folder) {
            return builder.substring(0, builder.length()-1);
        } else {
            return builder.toString();
        }
    }

    /**
     * Splits the provided path into path components.
     *
     * @return Array of path components.
     */
    public static String mergePath(String[] components, int start, int end, boolean absolute, boolean folder) {
        StringBuilder builder = new StringBuilder();
        if (absolute) builder.append(SMBFileSystem.PATH_SEPARATOR);
        for (int i = start; i<end; i++) {
            builder.append(components[i]);
            builder.append(SMBFileSystem.PATH_SEPARATOR);
        }
        if (!folder) {
            return builder.substring(0, builder.length()-1);
        } else {
            return builder.toString();
        }
    }
}
