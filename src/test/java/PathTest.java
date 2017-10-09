import ch.pontius.nio.smb.SMBFileSystemProvider;
import ch.pontius.nio.smb.SMBPath;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PathTest {

    private static final String PATH_ROOT = "smb://test@192.168.1.105/";
    private static final String PATH_01 = "smb://test@192.168.1.105/home/rgasser/text.xls";
    private static final String PATH_02 = "smb://test@192.168.1.105/home/rgasser/";
    private static final String PATH_03 = "smb://test@192.168.1.105/home/rgasser/lala/text02.xls";
    private static final String PATH_04 = "smb://test@192.168.1.106/home/rgasser/text.xls";
    private static final String PATH_05 = "smb://test@192.168.1.106/home/rgasser/lala/text.xls";

    /**
     * Tests the definition of the {@link SMBPath}. I.e. whether paths are correctly parsed as
     * absolute / relative and file / folder.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testDefinition() throws URISyntaxException {
        final SMBFileSystemProvider provider = new SMBFileSystemProvider();
        final SMBPath path01 = provider.getPath(new URI(PATH_01));
        final SMBPath path02 = provider.getPath(new URI(PATH_02));
        final SMBPath path03 = provider.getPath(new URI(PATH_04));
        assertAll("SMBPath definition",
                () -> assertEquals(path01.isAbsolute(), true),
                () -> assertEquals(path02.isAbsolute(), true),
                () -> assertEquals(path03.isAbsolute(), true),
                () -> assertEquals(path01.isFolder(), false),
                () -> assertEquals(path02.isFolder(), true),
                () -> assertEquals(path03.isFolder(), false),
                () -> assertEquals(path01.getFileSystem(), path02.getFileSystem()),
                () -> assertNotEquals(path01.getFileSystem(), path03.getFileSystem()),
                () -> assertNotEquals(path02.getFileSystem(), path03.getFileSystem())
        );
    }

    /**
     * Tests the SMBPath.equals() method. Paths are considered equal if they belong to the same file system and
     * are composed of the same path components.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testEquals() throws URISyntaxException {
        final SMBFileSystemProvider provider = new SMBFileSystemProvider();
        final Path path01a = provider.getPath(new URI(PATH_01));
        final Path path01b = provider.getPath(new URI(PATH_01));
        final Path path02 = provider.getPath(new URI(PATH_02));
        final Path path03 = provider.getPath(new URI(PATH_03));

        assertAll("SMBPath.equals()",
                () -> assertEquals(path01a, path01b),
                () -> assertNotEquals(path01a, path02),
                () -> assertNotEquals(path01b, path02),
                () -> assertNotEquals(path01a, path03),
                () -> assertNotEquals(path01b, path03)
        );
    }

    /**
     * Tests the SMBPath.getParent() method.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testParent() throws URISyntaxException {
        final SMBFileSystemProvider provider = new SMBFileSystemProvider();
        final Path path01a = provider.getPath(new URI(PATH_01));
        final Path parent = path01a.getParent();
        final Path test = provider.getPath(new URI(PATH_02));
        final Path testNull = provider.getPath(new URI(PATH_ROOT));
        assertAll("SMBPath.getParent()",
                () -> assertEquals(parent, test),
                () -> assertNotEquals(path01a, test),
                () -> assertNotEquals(path01a, parent),
                () -> assertNull(testNull.getParent())
        );
    }

    /**
     * Tests the SMBPath.getFilename() method.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testFilename() throws URISyntaxException {
        final SMBFileSystemProvider provider = new SMBFileSystemProvider();
        final Path path01 = provider.getPath(new URI(PATH_01)).getFileName();
        final Path path02 = provider.getPath(new URI(PATH_02)).getFileName();
        assertAll("SMBPath.getFilename()",
                () -> assertEquals(path01.toString(), "text.xls"),
                () -> assertEquals(path02.toString(), "rgasser"),
                () -> assertEquals(path01.isAbsolute(), false),
                () -> assertEquals(path01.isAbsolute(), false)
        );
    }
}
