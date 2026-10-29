package edu.stanford.muse.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * The guard these cases describe used to be a string test for ".." + File.separator, which
 * passed on Windows for a "../" payload because File.separator is a backslash there while
 * the Win32 APIs accept forward slashes anyway.
 */
public class PathContainmentTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void plainFileNameResolvesInsideTheDirectory() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        Path resolved = Util.resolveWithinDirectory(base.getAbsolutePath(), "attachment.pdf");

        assertNotNull(resolved);
        assertEquals(base.toPath().resolve("attachment.pdf"), resolved);
    }

    @Test
    public void nestedPathResolvesInsideTheDirectory() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        Path resolved = Util.resolveWithinDirectory(base.getAbsolutePath(), "sub/attachment.pdf");

        assertNotNull(resolved);
        assertEquals(base.toPath().resolve("sub").resolve("attachment.pdf"), resolved);
    }

    @Test
    public void forwardSlashTraversalIsRejected() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        assertNull(Util.resolveWithinDirectory(base.getAbsolutePath(), "../../etc/passwd"));
    }

    @Test
    public void backslashTraversalIsRejectedOnWindows() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        Path resolved = Util.resolveWithinDirectory(base.getAbsolutePath(), "..\\..\\secret.txt");

        // On Windows a backslash is a separator and this escapes, so it must be rejected.
        // On POSIX a backslash is an ordinary filename character, so it stays inside.
        if (File.separatorChar == '\\')
            assertNull(resolved);
        else
            assertNotNull(resolved);
    }

    @Test
    public void traversalThatReturnsInsideIsAccepted() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        Path resolved = Util.resolveWithinDirectory(base.getAbsolutePath(), "sub/../attachment.pdf");

        assertNotNull(resolved);
        assertEquals(base.toPath().resolve("attachment.pdf"), resolved);
    }

    @Test
    public void siblingDirectoryWithTheSamePrefixIsRejected() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        temporaryFolder.newFolder("blobs-other");

        assertNull(Util.resolveWithinDirectory(base.getAbsolutePath(), "../blobs-other/secret.txt"));
    }

    @Test
    public void absolutePathIsRejected() throws Exception {
        File base = temporaryFolder.newFolder("blobs");
        File outside = temporaryFolder.newFile("outside.txt");

        assertNull(Util.resolveWithinDirectory(base.getAbsolutePath(), outside.getAbsolutePath()));
    }

    @Test
    public void nullAndEmptyInputsAreRejected() throws Exception {
        File base = temporaryFolder.newFolder("blobs");

        assertNull(Util.resolveWithinDirectory(base.getAbsolutePath(), null));
        assertNull(Util.resolveWithinDirectory(null, "attachment.pdf"));
        assertNull(Util.resolveWithinDirectory("", "attachment.pdf"));
    }
}
