package edu.stanford.muse;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertTrue;

/**
 * The discovery edition is the public-facing one, and the only thing separating it from the
 * full edition is the hand-maintained include list in pom-discovery.xml. Nothing checked that
 * list against reality, so it had drifted: it named error.jsp, which does not exist anywhere
 * in the tree.
 *
 * This turns "someone has to remember" into a failing build.
 */
public class DiscoveryPackagingTest {
    private static final Pattern INCLUDE = Pattern.compile("<include>([^<]+)</include>");

    @Test
    public void everyJspInTheDiscoveryAllowlistExists() throws Exception {
        String pom = new String(Files.readAllBytes(Paths.get("pom-discovery.xml")), StandardCharsets.UTF_8);

        List<String> named = new ArrayList<>();
        Matcher m = INCLUDE.matcher(pom);
        while (m.find()) {
            String entry = m.group(1);
            if (entry.endsWith(".jsp") || entry.endsWith(".jspf"))
                named.add(entry);
        }

        assertTrue("expected the discovery allowlist to name some JSPs", named.size() > 0);

        List<String> missing = new ArrayList<>();
        for (String entry : named)
            if (!new File("WebContent", entry).exists())
                missing.add(entry);

        assertTrue("pom-discovery.xml packages files that do not exist, so the discovery WAR is "
                + "built from a stale list: " + missing, missing.isEmpty());
    }
}
