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

    static final String[] REQUIRED_SECTIONS = {
        "## When to use this",
        "## What it does, in plain terms",
        "## What you'll need",
        "## How to do it",
        "## How to report results",
        "## Cautions"
    };

    // Validates the shared skill template: frontmatter name == file stem, all six sections present,
    // no ACL leak, no jargon outside "How to do it"/table, and the read-only worklist promise.
    static void assertSkillStructure(String stem) {
        String md = load("/agent-skills/" + stem + ".md");
        assertFalse(md.isEmpty(), stem + " must be non-empty");
        assertTrue(md.contains("name: " + stem),
            stem + " frontmatter name must equal the file stem");
        for (String s : REQUIRED_SECTIONS)
            assertTrue(md.contains(s), stem + " must contain section " + s);
        assertTrue(userFacingSections(md).toLowerCase().contains("read-only")
                || userFacingSections(md).toLowerCase().contains("only suggest")
                || userFacingSections(md).toLowerCase().contains("worklist")
                || userFacingSections(md).toLowerCase().contains("to-do"),
            stem + " must state it is read-only / produces a worklist");
        assertNoLeak(md);
        assertNoJargon(userFacingSections(md));
    }

    @Test void review_id_problems_is_well_formed() {
        assertSkillStructure("review-id-problems");
        String md = load("/agent-skills/review-id-problems.md");
        assertTrue(md.contains("/api/v3/media/resolve"), "uses media resolve to show photos");
        assertTrue(md.toLowerCase().contains("side by side") || md.toLowerCase().contains("side-by-side"),
            "presents photos side by side");
        assertTrue(md.toLowerCase().contains("to-do") || md.toLowerCase().contains("worklist")
                || md.toLowerCase().contains("export"),
            "produces an actionable to-do list");
    }

    @Test void how_good_is_our_matching_is_well_formed() {
        assertSkillStructure("how-good-is-our-matching");
        String md = load("/agent-skills/how-good-is-our-matching.md");
        assertTrue(md.contains("%") || md.toLowerCase().contains("percent"),
            "reports reliability as a plain percentage");
        assertTrue(md.toLowerCase().contains("same sighting")
                || md.toLowerCase().contains("same encounter"),
            "states the same-sighting exclusion that prevents inflated numbers");
    }

    @Test void find_misfiled_sightings_is_well_formed() {
        assertSkillStructure("find-misfiled-sightings");
        String md = load("/agent-skills/find-misfiled-sightings.md");
        assertTrue(md.contains("/api/v3/media/resolve"), "names media resolve for the identity join");
        assertTrue(md.toLowerCase().contains("exclude")
                && (md.toLowerCase().contains("same sighting")
                    || md.toLowerCase().contains("same encounter")
                    || md.toLowerCase().contains("itself")),
            "states the self / same-sighting exclusion rule");
    }

    @Test void find_missed_matches_is_well_formed() {
        assertSkillStructure("find-missed-matches");
        String md = load("/agent-skills/find-missed-matches.md");
        assertTrue(md.contains("/api/v3/search/annotation"), "names the annotation search");
        assertTrue(md.contains("/api/v3/media/resolve"), "names media resolve for the identity join");
        assertTrue(md.toLowerCase().contains("viewpoint") && md.toLowerCase().contains("methodversion"),
            "states the same-viewpoint + same-methodVersion rule");
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
