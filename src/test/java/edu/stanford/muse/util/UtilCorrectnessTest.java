package edu.stanford.muse.util;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Regression cases for defects in the shared helpers, each of which lost or mangled data. */
public class UtilCorrectnessTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void removeMetaTagKeepsBodyTextThatFollowsIt() {
        // The lazy "(.*?)/>" with DOTALL used to match from the <meta> to the next
        // self-closing tag anywhere later, deleting "Dear Bob, " along with it.
        String html = "<html><head><meta charset=\"utf-8\"></head>"
                + "<body>Dear Bob, <br/>the deal is off.</body></html>";

        String cleaned = Util.removeMetaTag(html);

        assertTrue("body text before the next self-closing tag must survive",
                cleaned.contains("Dear Bob, "));
        assertTrue(cleaned.contains("the deal is off."));
        assertTrue("the br must survive", cleaned.contains("<br/>"));
        assertTrue("the meta tag itself must go", !cleaned.toLowerCase().contains("<meta"));
    }

    @Test
    public void removeMetaTagRemovesSelfClosingAndPlainMetaTags() {
        assertTrue(!Util.removeMetaTag("<meta charset=\"utf-8\"/>body").toLowerCase().contains("<meta"));
        assertTrue(!Util.removeMetaTag("<META HTTP-EQUIV=\"x\">body").toLowerCase().contains("<meta"));
        assertEquals("body", Util.removeMetaTag("<meta charset=\"utf-8\"/>body"));
    }

    @Test
    public void escapeJsonEscapesWhatJsonRequires() {
        // "\\\t" in the old source was a backslash plus a literal tab, so the control
        // character survived into the output and quotes were never escaped at all.
        String escaped = Util.escapeJSON("Hello\tworld\nnext \"quoted\" back\\slash");

        assertTrue("tab must become the two-character escape", escaped.contains("\\t"));
        assertTrue("newline must become the two-character escape", escaped.contains("\\n"));
        assertTrue("quote must be escaped", escaped.contains("\\\""));
        assertTrue("backslash must be escaped", escaped.contains("\\\\"));
        assertTrue("no raw control characters may remain", escaped.indexOf('\t') < 0);
        assertTrue(escaped.indexOf('\n') < 0);
    }

    @Test
    public void escapeJsonEscapesTheBackslashBeforeIntroducingNewOnes() {
        assertEquals("\\\\t", Util.escapeJSON("\\t"));
    }

    @Test
    public void escapeJsonToleratesNull() {
        assertNull(Util.escapeJSON(null));
    }

    @Test
    public void splitIntoTwoKeepsTheCharacterBeforeTheDelimiter() {
        assertArrayEquals(new String[] {"alice", "example.com"},
                Util.splitIntoTwo("alice@example.com", '@'));
        assertArrayEquals(new String[] {"a", "b"}, Util.splitIntoTwo("a,b", ','));
    }

    @Test
    public void splitIntoTwoHandlesDelimiterAtEachEnd() {
        assertArrayEquals(new String[] {"", "b"}, Util.splitIntoTwo(",b", ','));
        assertArrayEquals(new String[] {"a", ""}, Util.splitIntoTwo("a,", ','));
        assertArrayEquals(new String[] {"abc", ""}, Util.splitIntoTwo("abc", ','));
    }

    @Test
    public void splitIntoTwoSplitsOnTheFirstDelimiterOnly() {
        assertArrayEquals(new String[] {"key", "a,b,c"}, Util.splitIntoTwo("key,a,b,c", ','));
    }

    @Test
    public void stripCommonPrefixHandlesAShorterLaterEntry() {
        // Used to throw StringIndexOutOfBoundsException because the loop bounded on the
        // running prefix but indexed the current string.
        List<String> paths = new ArrayList<>(Arrays.asList("/a/bbbb", "/a/b"));

        List<String> stripped = Util.stripCommonPrefix(paths);

        assertEquals(2, stripped.size());
    }

    @Test
    public void getBytesFromFileReadsASmallFile() throws Exception {
        java.io.File f = temporaryFolder.newFile("small.bin");
        byte[] written = {0, 1, 2, (byte) 0xC3, (byte) 0xA9};
        java.nio.file.Files.write(f.toPath(), written);

        assertArrayEquals(written, Util.getBytesFromFile(f.getAbsolutePath()));
    }
}
