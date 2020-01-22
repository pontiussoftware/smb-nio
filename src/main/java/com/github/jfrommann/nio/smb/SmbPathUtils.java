package com.github.jfrommann.nio.smb;

public final class SmbPathUtils {
    /**
     * Private constructor; this class cannot be instantiated.
     */
    private SmbPathUtils() {
    }

    /**
     * Checks if the provided path string points to a folder (i.e. ends with an /).
     *
     * @param path Path that should be checked.
     * @return True if provided path points to a folder and false otherwise.
     */
    public static boolean isFolder(String path) {
        return path.endsWith(SmbFileSystem.PATH_SEPARATOR);
    }

    /**
     * Checks if provided path string is an absolute path (i.e. starts with an /).
     *
     * @param path Path that should be checked.
     * @return True if provided path is a relative path and false otherwise.
     */
    public static boolean isAbsolutePath(String path) {
        return path.startsWith(SmbFileSystem.PATH_SEPARATOR);
    }

    /**
     * Checks if provided path string is a relative path (i.e. does not start with an /).
     *
     * @param path Path that should be checked.
     * @return True if provided path is a relative path and false otherwise.
     */
    public static boolean isRelativePath(String path) {
        return !path.startsWith(SmbFileSystem.PATH_SEPARATOR);
    }

    /**
     * Splits the provided path string into its path components.
     *
     * @param path Path string that should be split.
     * @return Array of path components.
     */
    public static String[] splitPath(String path) {
        String[] split = path.split(SmbFileSystem.PATH_SEPARATOR);
        if (split.length > 0 && split[0].equals("")) {
            String[] truncated = new String[split.length - 1];
            System.arraycopy(split, 1, truncated, 0, split.length - 1);
            return truncated;
        } else {
            return split;
        }
    }

    /**
     * Merges the provided path components into a single pat string.
     *
     * @param components Array of path components.
     * @param start      Index of the first item in the list that should be considered; inclusive.
     * @param end        Index of the last item in the list that should be considered; exclusive.
     * @param absolute   Boolean indicating whether resulting path should be treated as absolute path.
     * @param folder     Boolean indicating whether resulting path should point to a folder.
     * @return Resulting path string
     */
    public static String mergePath(String[] components, int start, int end, boolean absolute, boolean folder) {
        StringBuilder builder = new StringBuilder();
        if (absolute) {
            builder.append(SmbFileSystem.PATH_SEPARATOR);
        }
        for (int i = start; i < end; i++) {
            builder.append(components[i]);
            builder.append(SmbFileSystem.PATH_SEPARATOR);
        }
        if (!folder) {
            return builder.substring(0, Math.max(0, builder.length() - 1));
        } else {
            return builder.toString();
        }
    }
}
