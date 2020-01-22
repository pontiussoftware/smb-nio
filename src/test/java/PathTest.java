import com.github.jfrommann.nio.smb.SmbFileSystemProvider;
import com.github.jfrommann.nio.smb.SmbPath;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

public class PathTest {

    private static final String PATH_ROOT = "smb://test@192.168.1.105/";
    private static final String PATH_01 = "smb://test@192.168.1.105/home/rgasser/text.xls";
    private static final String PATH_02 = "smb://test@192.168.1.105/home/rgasser/";
    private static final String PATH_03 = "smb://test@192.168.1.105/home/rgasser/lala/text02.xls";
    private static final String PATH_04 = "smb://test@192.168.1.106/home/rgasser/text.xls";
    private static final String PATH_05 = "smb://test@192.168.1.106/home/rgasser/";


    /**
     * Test paths for the relativize unit tests.
     *
     * IN: Test paths.
     * OUT: Expected output.
     */
    private static final String PATH_REL_IN_01 = "smb://test@192.168.1.106/a/b/c";
    private static final String PATH_REL_IN_02 = "smb://test@192.168.1.106/a/b/c/d/e/f";
    private static final String PATH_REL_IN_03 = "smb://test@192.168.1.106/x/y/z";

    private static final String PATH_REL_OUT_01 = "d/e/f";
    private static final String PATH_REL_OUT_02 = "../../..";
    private static final String PATH_REL_OUT_03 = "";
    private static final String PATH_REL_OUT_04 = "../../../x/y/z";
    private static final String PATH_REL_OUT_05 = "../../../../../../x/y/z";
    private static final String PATH_REL_OUT_06 = "../../../a/b/c";

    /**
     * Tests the definition of the {@link SmbPath}. I.e. whether paths are correctly parsed as
     * absolute / relative and file / folder.
     *
     * @throws URISyntaxException
     */
    @Test
    public void testDefinition() throws URISyntaxException {
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final SmbPath path01 = provider.getPath(new URI(PATH_01));
        final SmbPath path02 = provider.getPath(new URI(PATH_02));
        final SmbPath path03 = provider.getPath(new URI(PATH_04));
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
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
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
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
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
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final Path path01 = provider.getPath(new URI(PATH_01)).getFileName();
        final Path path02 = provider.getPath(new URI(PATH_02)).getFileName();
        assertAll("SMBPath.getFilename()",
                () -> assertEquals(path01.toString(), "text.xls"),
                () -> assertEquals(path02.toString(), "rgasser"),
                () -> assertEquals(path01.isAbsolute(), false),
                () -> assertEquals(path01.isAbsolute(), false)
        );
    }

    /**
     * Tests the SMBPath.relativize() method on absolute paths.
     *
     * @throws URISyntaxException If one of the paths is wrong.
     */
    @Test
    public void testRelativizeOnAbsolute() throws URISyntaxException {
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final Path path01 = provider.getPath(new URI(PATH_REL_IN_01));
        final Path path02 = provider.getPath(new URI(PATH_REL_IN_02));
        final Path path03 = provider.getPath(new URI(PATH_REL_IN_03));
        assertAll("SMBPath.testRelativizeOnAbsolute()",
                () -> assertEquals(path01.relativize(path02).toString(), PATH_REL_OUT_01),
                () -> assertEquals(path02.relativize(path01).toString(), PATH_REL_OUT_02),
                () -> assertEquals(path02.relativize(path02).toString(), PATH_REL_OUT_03),
                () -> assertEquals(path01.relativize(path01).toString(), PATH_REL_OUT_03),
                () -> assertEquals(path01.relativize(path03).toString(), PATH_REL_OUT_04),
                () -> assertEquals(path02.relativize(path03).toString(), PATH_REL_OUT_05),
                () -> assertEquals(path03.relativize(path01).toString(), PATH_REL_OUT_06),
                () -> assertFalse(path01.relativize(path02).isAbsolute()),
                () -> assertFalse(path02.relativize(path01).isAbsolute()),
                () -> assertFalse(path02.relativize(path02).isAbsolute()),
                () -> assertFalse(path01.relativize(path01).isAbsolute()),
                () -> assertFalse(path01.relativize(path03).isAbsolute()),
                () -> assertFalse(path02.relativize(path03).isAbsolute()),
                () -> assertFalse(path03.relativize(path01).isAbsolute())
        );
    }

    @Test
    public void testCompareTo() throws Exception {
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final Path leftPath = provider.getPath(new URI(PATH_01));
        final Path rightPath = provider.getPath(new URI(PATH_02));
        assertTrue(leftPath.compareTo(rightPath) > 0);
        assertTrue(rightPath.compareTo(leftPath) < 0);
        assertEquals(0, leftPath.compareTo(leftPath));
    }

    @Test
    public void testCompareToDifferentFileSystem() throws Exception {
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final Path leftPath = provider.getPath(new URI(PATH_01));
        final Path rightPath = provider.getPath(new URI(PATH_05));
        assertThrows(IllegalArgumentException.class, () -> leftPath.compareTo(rightPath));
    }

    @Test
    public void testCompareToDifferentProtocol() throws Exception {
        final SmbFileSystemProvider provider = SmbFileSystemProvider.getDefault();
        final Path leftPath = provider.getPath(new URI(PATH_01));
        final Path rightPath = Paths.get("/tmp/test.txt");
        assertThrows(IllegalArgumentException.class, () -> leftPath.compareTo(rightPath));
    }
}
