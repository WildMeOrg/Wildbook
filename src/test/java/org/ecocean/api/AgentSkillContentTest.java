package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AgentSkillContentTest {

    static final String[] JARGON = {
        "embedding", "vector", "cosine", "centroid", "cluster", "latent", "bcubed"
    };
    static final String[] ACL_FIELDS = {
        "publiclyReadable", "submitterUserId", "submitterUserIds", "viewUsers", "editUsers"
    };

    static String load(String classpathPath) {
        try (InputStream in = AgentSkillContentTest.class.getResourceAsStream(classpathPath)) {
            assertNotNull(in, "resource must exist on classpath: " + classpathPath);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return fail("could not read " + classpathPath + ": " + e);
        }
    }

    static void assertNoLeak(String md) {
        for (String acl : ACL_FIELDS)
            assertFalse(md.contains(acl), "must not expose internal ACL field name " + acl);
    }

    // Strips the "## How to do it" section (up to the next "## ") and the markdown jargon table,
    // leaving only the text the assistant may read aloud to a biologist.
    static String userFacingSections(String md) {
        String stripped = md.replaceAll("(?is)## How to do it.*?(?=\\n## |\\z)", "");
        // remove any markdown table rows (the jargon table's "Don't say" column lives here)
        stripped = stripped.replaceAll("(?m)^\\|.*\\|\\s*$", "");
        return stripped;
    }

    static void assertNoJargon(String userFacingText) {
        String lower = userFacingText.toLowerCase();
        for (String j : JARGON)
            assertFalse(lower.contains(j), "user-facing text must not contain jargon term: " + j);
    }

    @Test void index_is_plain_language_and_lists_the_four_tools() {
        String md = load("/agent-skills/index.md");
        assertFalse(md.isEmpty(), "index must be non-empty");
        for (String name : new String[] {
                "find-missed-matches", "find-misfiled-sightings",
                "how-good-is-our-matching", "review-id-problems" })
            assertTrue(md.contains(name), "index toolbox must list " + name);
        assertTrue(md.contains("api-reference"), "index must point to api-reference for API detail");
        assertTrue(md.toLowerCase().contains("read-only")
                || md.toLowerCase().contains("only suggest")
                || md.toLowerCase().contains("you make the changes"),
            "index must state the tools are read-only");
        assertTrue(md.toLowerCase().contains("api access") || md.toLowerCase().contains("token"),
            "index must explain getting a token");
        assertNoLeak(md);
        assertNoJargon(userFacingSections(md));
    }

    @Test void api_reference_has_auth_schema_and_paging_contract() {
        String md = load("/agent-skills/api-reference.md");
        assertFalse(md.isEmpty(), "api-reference must be non-empty");
        assertTrue(md.contains("Authorization: Bearer"), "documents bearer auth");
        assertTrue(md.contains("/api/v3/search/"), "documents search");
        assertTrue(md.contains("/api/v3/media/resolve"), "documents media resolve");
        assertTrue(md.contains("X-Wildbook-Total-Hits"), "documents the total-hits header");
        assertTrue(md.contains("10,000") || md.contains("10000"), "documents the 10k window ceiling");
        assertTrue(md.contains("from") && md.contains("size"), "documents from/size paging");
        assertTrue(md.contains("100"), "documents the media/resolve 100-ID cap");
        for (String idx : new String[] {"encounter", "individual", "annotation"})
            assertTrue(md.contains(idx), "names allowed index " + idx);
        assertTrue(md.contains("403"), "states denied indices return 403");
        assertNoLeak(md);
    }
}
