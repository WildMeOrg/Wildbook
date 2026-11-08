package org.ecocean.api;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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

    @Test void apiReferenceDocumentsEveryVectorField() {
        String md = load("/agent-skills/api-reference.md");
        for (String field : org.ecocean.Embedding.VECTOR_FIELDS.values())
            assertTrue(md.contains("embeddings." + field),
                "api-reference must document the kNN vector field embeddings." + field);
        assertTrue(md.contains("encounterDateMillis"),
            "api-reference must document the denormalized sighting date on annotations");
    }

    @Test void apiReferenceKnnExampleFiltersModelInsideKnn() {
        String md = load("/agent-skills/api-reference.md");
        // the model term must sit in the knn clause's own filter (applied during the search), not
        // beside the knn where it would only drop hits after the k nearest were chosen
        int start = md.indexOf("```json", md.indexOf("**Nearest-neighbour (`knn`) search"));
        assertTrue(start >= 0, "api-reference must have a kNN example");
        start += "```json".length();
        String example = md.substring(start, md.indexOf("```", start))
            .replaceAll("/\\*.*?\\*/", "0.1"); // the vector placeholder is a comment
        org.json.JSONObject knn = new org.json.JSONObject(example).getJSONObject("query")
            .getJSONObject("bool").getJSONArray("must").getJSONObject(0)
            .getJSONObject("nested").getJSONObject("query").getJSONObject("knn")
            .getJSONObject("embeddings.vector");
        assertTrue(knn.getJSONObject("filter").toString().contains("embeddings.methodVersion"),
            "kNN example must filter methodVersion inside the knn clause");
    }

    @Test void serviceKeyFlowDocumented() {
        String api = load("/agent-skills/api-reference.md");
        for (String needle : new String[] {
                "wbsvc_", "POST /api/v3/auth/token", "keyExpiresAt", "expiresInSeconds",
                "\"invalid service key\"", "\"service key expired\"", "\"service key revoked\"",
                "\"service key unavailable; contact your Wildbook administrator\"" })
            assertTrue(api.contains(needle), "api-reference must contain " + needle);
        String submit = load("/agent-skills/submit-sightings.md");
        assertTrue(submit.contains("wbsvc_"), "submit-sightings must mention service keys");
        assertTrue(submit.contains("scope=submissions:write"), "submit-sightings must show the write scope");
        assertTrue(load("/agent-skills/index.md").contains("service key"),
            "index must point to service keys");
        assertNoLeak(api);
        assertNoLeak(submit);
        int start = api.indexOf("## Using an organization service key");
        assertTrue(start >= 0, "api-reference must have the service key section");
        int end = api.indexOf("\n## ", start + 5);
        String section = end < 0 ? api.substring(start) : api.substring(start, end);
        assertTrue(section.contains("15 minutes"),
            "service key section must say issued tokens keep working until they expire (at most 15 minutes)");
        assertNoJargon(end < 0 ? api.substring(start) : api.substring(start, end));
    }

    @Test void catalog_files_and_index_do_not_drift() {
        // (a) every mapped name resolves to a non-empty resource whose frontmatter name == the key
        for (Map.Entry<String, String> e : AgentSkill.SKILL_RESOURCES.entrySet()) {
            String md = load("/agent-skills/" + e.getValue());
            assertFalse(md.isEmpty(), e.getValue() + " must be non-empty");
            assertEquals(e.getValue(), e.getKey() + ".md",
                "map value must be <name>.md for key " + e.getKey());
            assertTrue(md.contains("name: " + e.getKey()),
                e.getValue() + " frontmatter name must equal its map key");
        }
        // (b) index.md links exactly the four analytical skills in its toolbox, plus api-reference
        String index = load("/agent-skills/index.md");
        String[] analytical = {
            "find-missed-matches", "find-misfiled-sightings",
            "how-good-is-our-matching", "review-id-problems" };
        for (String n : analytical)
            assertTrue(index.contains(n), "index toolbox must list " + n);
        assertTrue(index.contains("api-reference"), "index must reference api-reference");
        // (c) analytical keys remain those four alongside reference and intake skills
        assertTrue(AgentSkill.SKILL_RESOURCES.containsKey("inat-to-wildbook-import"),
            "the import-prep skill must be registered");
        java.util.Set<String> keys = new java.util.HashSet<>(AgentSkill.SKILL_RESOURCES.keySet());
        keys.remove("api-reference");
        keys.remove("inat-to-wildbook-import");
        keys.remove("submit-sightings");
        keys.remove("import-your-own-detections");
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList(analytical)), keys,
            "the analytical skills in the map must be exactly the four listed in the index");
    }

    @Test void submission_skill_examples_match_the_runtime_input_contract() {
        String md = load("/agent-skills/submit-sightings.md");
        assertEquals("submit-sightings.md", AgentSkill.SKILL_RESOURCES.get("submit-sightings"));
        assertTrue(load("/agent-skills/index.md").contains("/api/v3/agent-skill/submit-sightings"));
        for (String section : REQUIRED_SECTIONS) assertTrue(md.contains(section));
        assertNoLeak(md);
        java.util.regex.Matcher examples = java.util.regex.Pattern.compile("```json\\s*\\n(.*?)\\n```", java.util.regex.Pattern.DOTALL).matcher(md);
        assertTrue(examples.find());
        org.json.JSONObject create = org.ecocean.api.submission.SubmissionJson.create(new org.json.JSONObject(examples.group(1)));
        assertEquals("detect-and-identify", create.getJSONObject("processing").getString("mode"));
        assertTrue(examples.find());
        org.json.JSONArray rows = org.ecocean.api.submission.SubmissionJson.rows(new org.json.JSONObject(examples.group(1)));
        for (int i = 0; i < rows.length(); i++)
            for (String field : rows.getJSONObject(i).getJSONObject("fields").keySet())
                assertTrue(org.ecocean.api.submission.SubmissionValidator.supported(field), field);
        for (String field : org.ecocean.api.submission.SubmissionValidator.FIELDS)
            assertTrue(md.contains("`" + field + "`"), "document supported field " + field);
    }

    @Test void inat_to_wildbook_import_is_well_formed() {
        String md = load("/agent-skills/inat-to-wildbook-import.md");
        assertFalse(md.isEmpty(), "inat-to-wildbook-import must be non-empty");
        assertTrue(md.contains("name: inat-to-wildbook-import"),
            "frontmatter name must equal the file stem");
        for (String s : REQUIRED_SECTIONS)
            assertTrue(md.contains(s), "import skill must contain section " + s);
        assertTrue(md.contains("github.com/WildMeOrg/inat-download-recent-species-sightings"),
            "must point at the public repo");
        assertTrue(md.contains("v1.0.0"), "must pin to the released tag");
        assertTrue(md.contains("Encounter.otherCatalogNumbers"),
            "must name the dedup back-reference column");
        assertTrue(md.toLowerCase().contains("bulk import"),
            "must hand off to the Wildbook Bulk Import UI");
        assertNoLeak(md);
        assertNoJargon(userFacingSections(md));
        // it is an import-prep skill, so it must NOT be forced through the read-only template
        assertTrue(load("/agent-skills/index.md").contains("inat-to-wildbook-import"),
            "root index must list the import skill");
    }

    // ---- bulk-import boxes PR D (spec 4.7) ----
    static String section(String md, String heading) {
        int start = md.indexOf(heading);
        assertTrue(start >= 0, "missing section " + heading);
        int end = md.indexOf("\n## ", start + heading.length());
        int sub = md.indexOf("\n### ", start + heading.length());
        if (heading.startsWith("### ") && sub >= 0 && (end < 0 || sub < end)) end = sub;
        return end < 0 ? md.substring(start) : md.substring(start, end);
    }

    @Test void import_your_own_detections_is_registered_and_listed() {
        assertEquals("import-your-own-detections.md", AgentSkill.SKILL_RESOURCES.get("import-your-own-detections"));
        String index = load("/agent-skills/index.md");
        assertTrue(index.contains("/api/v3/agent-skill/import-your-own-detections"));
        assertTrue(index.contains("sending detector boxes directly uses"), "index must not promise that every import-prep tool needs no token");
    }

    @Test void import_your_own_detections_is_well_formed_and_plain() {
        String md = load("/agent-skills/import-your-own-detections.md");
        assertTrue(md.contains("name: import-your-own-detections"));
        for (String s : REQUIRED_SECTIONS) assertTrue(md.contains(s), "missing section " + s);
        assertNoLeak(md);
        String plain = md.replaceAll("(?is)## How to do it.*?(?=\\n## |\\z)", "");   // tables included, unlike userFacingSections
        assertNoJargon(plain);
        for (String term : new String[] { "annotation", "iaclass", "embedding" })
            assertFalse(plain.toLowerCase().contains(term), "user-facing text must not say " + term);
    }

    @Test void import_your_own_detections_gates_on_the_capability_check() {
        String step1 = section(load("/agent-skills/import-your-own-detections.md"), "### 1. Check that this Wildbook accepts animal boxes");
        assertTrue(step1.contains("rowFields.perMedia") && step1.contains("`bbox`") && step1.contains("`iaClass`"));
        assertTrue(step1.contains("bulkImportIndexableFields") && step1.contains("Encounter.mediaAsset#.bbox"));
        assertTrue(step1.contains("stop"), "a failed check must stop the work");
        // the capability keys the skill names are the ones the server publishes
        assertTrue(org.ecocean.api.bulk.BulkValidator.FIELD_NAMES_INDEXABLE.contains("Encounter.mediaAsset#.bbox"));
        assertTrue(org.ecocean.api.bulk.BulkValidator.BOX_SUFFIXES.containsAll(java.util.List.of("bbox", "iaClass")));
    }

    @Test void import_your_own_detections_reads_labels_and_viewpoints_from_site_settings() {
        String step2 = section(load("/agent-skills/import-your-own-detections.md"), "### 2. Read the site's labels");
        assertTrue(step2.contains("bulkImportMatchableClasses") && step2.contains("annotationViewpoint"));
        assertTrue(step2.contains("never guess"));
        String bullet = step2.substring(step2.indexOf("- `annotationViewpoint`"));
        bullet = bullet.substring(0, bullet.indexOf("\n\n") > 0 ? bullet.indexOf("\n\n") : bullet.length());
        java.util.regex.Matcher vp = java.util.regex.Pattern.compile("`([a-z]+)`").matcher(bullet);
        int n = 0;
        while (vp.find()) { assertTrue(org.ecocean.Annotation.isValidViewpoint(vp.group(1)), "example viewpoint " + vp.group(1)); n++; }
        assertTrue(n >= 3, "viewpoint examples");
    }

    @Test void import_your_own_detections_worked_yolo_example_is_right() {
        String step4 = section(load("/agent-skills/import-your-own-detections.md"), "### 4. Convert every box to `x,y,width,height`");
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(
            "photo (\\d+)×(\\d+), `cx=([0-9.]+) cy=([0-9.]+) w=([0-9.]+) h=([0-9.]+)`\\).*?The box is `([0-9,]+)`", java.util.regex.Pattern.DOTALL).matcher(step4);
        assertTrue(m.find(), "worked YOLO example");
        int W = Integer.parseInt(m.group(1)), H = Integer.parseInt(m.group(2));
        double cx = Double.parseDouble(m.group(3)), cy = Double.parseDouble(m.group(4)), w = Double.parseDouble(m.group(5)), h = Double.parseDouble(m.group(6));
        int left = Math.max(0, (int)Math.floor((cx - w / 2) * W)), top = Math.max(0, (int)Math.floor((cy - h / 2) * H));
        int right = Math.min(W, (int)Math.ceil((cx + w / 2) * W)), bottom = Math.min(H, (int)Math.ceil((cy + h / 2) * H));
        assertEquals(left + "," + top + "," + (right - left) + "," + (bottom - top), m.group(7));
        assertTrue(step4.contains("rounded down") && step4.contains("rounded up") && step4.contains("clamped"));
        assertTrue(step4.contains("EXIF") || section(load("/agent-skills/import-your-own-detections.md"), "### 3. Check every photo before converting").contains("EXIF orientation"));
    }

    @Test void import_your_own_detections_row_example_is_a_valid_submission() throws Exception {
        String step5 = section(load("/agent-skills/import-your-own-detections.md"), "### 5. Build the rows");
        java.util.regex.Matcher json = java.util.regex.Pattern.compile("```json\\s*\\n(.*?)\\n```", java.util.regex.Pattern.DOTALL).matcher(step5);
        assertTrue(json.find(), "a row example");
        org.json.JSONArray rows = new org.json.JSONArray();
        do {
            org.json.JSONArray more = org.ecocean.api.submission.SubmissionJson.rows(new org.json.JSONObject(json.group(1)));
            for (int i = 0; i < more.length(); i++) rows.put(more.get(i));
        } while (json.find());
        assertTrue(rows.length() >= 1);
        for (int i = 0; i < rows.length(); i++) {
            org.json.JSONObject f = rows.getJSONObject(i).getJSONObject("fields");
            for (String field : f.keySet()) assertTrue(org.ecocean.api.submission.SubmissionValidator.supported(field), field);
            for (String required : new String[] { "Encounter.genus", "Encounter.specificEpithet", "Encounter.year", "Encounter.locationID", "Encounter.mediaAsset0" })
                assertTrue(f.has(required), "example row needs " + required);
            org.ecocean.api.bulk.BulkValidator.parseBbox(f.getString("Encounter.mediaAsset0.bbox"), "Encounter.mediaAsset0.bbox");
            assertTrue(f.has("Encounter.mediaAsset0.iaClass"), "a box needs its label");
            assertTrue(Double.isFinite(f.getDouble("Encounter.quality0")), "the score is a finite number on the photo's own slot number");
            assertTrue(org.ecocean.Annotation.isValidViewpoint(f.getString("Encounter.mediaAsset0.viewpoint")));
        }
        assertTrue(step5.contains("Every row that names that photo must carry a box"));
        assertTrue(step5.contains("identical box"));
    }

    @Test void import_your_own_detections_pins_the_risky_rules() {
        String md = load("/agent-skills/import-your-own-detections.md");
        String send = section(md, "### 6. Send them");
        assertTrue(send.contains("200 rows"));
        assertTrue(send.contains("import-only") && send.contains("prepare them for later matching"), "import-only still extracts");
        assertTrue(send.contains("ascending order") && send.contains("from slot 0") && send.contains("no empty photo cell")
            && send.contains("exact slot number"), "the page rules: ascending columns, no gaps, exact slots");
        assertFalse(send.contains("preview"), "a preview showing the columns must not lift the page rules");
        assertTrue(send.contains("only where the species is set up"), "import-only preparation is conditional");
        String after = section(md, "### 7. After sending");
        assertTrue(after.contains("BOXES_SUPPLIED") && after.contains("BOXES_EXTRACTED_ONLY"));
        assertTrue(after.contains("`pending`, `dispatching` or `dispatched`") && after.contains("does not by itself prove"),
            "BOXES_EXTRACTED_ONLY is qualified by state");
        assertTrue(after.contains("NO_EXTRACTION_CONFIG") && after.contains("Do not send"), "a config error is not a reason to re-import");
        String cautions = section(md, "## Cautions");
        assertTrue(cautions.contains("separate batch without box") && cautions.contains("imported untouched"),
            "decision 8: unboxed photos are untouched; detection needs a separate batch");
        assertTrue(cautions.contains("stored"), "the stored pixel grid");
        String report = section(md, "## How to report results");
        assertTrue(report.contains("Never say anything was imported") && report.contains("`imported`"));
        assertTrue(report.contains("Report\nmatching separately") || report.contains("Report matching separately"),
            "record creation and matching are reported separately");
        String photos = section(md, "### 3. Check every photo before converting");
        assertTrue(photos.contains("exactly as stored") && photos.contains("does not apply the EXIF orientation tag"), "stored grid, no transpose");
        String convert = section(md, "### 4. Convert every box to `x,y,width,height`");
        assertTrue(convert.contains("one-based and inclusive") && convert.contains("xmin − 1"), "VOC convention");
    }
}
