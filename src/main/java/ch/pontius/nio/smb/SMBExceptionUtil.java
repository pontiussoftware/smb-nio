package ch.pontius.nio.smb;

import jcifs.smb.NtStatus;
import jcifs.smb.SmbException;

import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;

public final class SMBExceptionUtil {
    /**
     * Private constructor; this class cannot be instantiated.
     */
    private SMBExceptionUtil() {}

    private static IOException translateToNIOException(SmbException e, String file, String other) {
        switch (e.getNtStatus()) {
            case NtStatus.NT_STATUS_ACCESS_DENIED:
                return new AccessDeniedException(file, other, e.getMessage());
            case NtStatus.NT_STATUS_NO_SUCH_FILE:
            case NtStatus.NT_STATUS_OBJECT_NAME_NOT_FOUND:
            case NtStatus.NT_STATUS_OBJECT_PATH_NOT_FOUND:
                return new NoSuchFileException(file, other, e.getMessage());
            case NtStatus.NT_STATUS_OBJECT_NAME_COLLISION:
                return new FileAlreadyExistsException(file, other, e.getMessage());
            default:
                return e;
        }
    }

    /**
     * Tries to translate the {@link SmbException} to a specific nio exception and throws it.
     * If there is no specific case the provided exception is rethrown.
     */
    static void rethrowAsNIOException(SmbException e, Path file, Path other) throws IOException {
        String a = (file == null) ? null : file.toString();
        String b = (other == null) ? null : other.toString();
        IOException x = translateToNIOException(e, a, b);
        throw x;
    }

    /**
     * Tries to translate the {@link SmbException} to a specific nio exception and throws it.
     * If there is no specific case the provided exception is rethrown.
     */
    static void rethrowAsNIOException(SmbException e, Path file) throws IOException {
        rethrowAsNIOException(e, file, null);
    }
}
