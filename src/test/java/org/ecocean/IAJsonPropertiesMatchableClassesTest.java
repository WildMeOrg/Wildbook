package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * Bulk-import boxes PR B, task 2 (spec 4.1 / 4.6): the classes an annotation of a species can be
 * stored under (selectable keys plus every resolved _save_as target) and the subset with a
 * default identification option, which is what intake will match.
 */
class IAJsonPropertiesMatchableClassesTest {
    private static final Taxonomy A = new Taxonomy("Genus species");
    private static final Taxonomy B = new Taxonomy("Genus other");
    private static final Taxonomy C = new Taxonomy("Genus empty");
    private static final Taxonomy D = new Taxonomy("Genus plain");

    private static JSONObject idConf(JSONObject... opts) {
        JSONArray arr = new JSONArray();
        for (JSONObject o : opts) arr.put(o);
        return new JSONObject().put("_id_conf", arr);
    }

    private static JSONObject opt(Boolean dflt, String root) {
        JSONObject o = new JSONObject();
        if (dflt != null) o.put("default", dflt.booleanValue());
        if (root != null) o.put("pipeline_root", root);
        return o;
    }

    private static IAJsonProperties fixture() {
        JSONObject json = new JSONObject()
            .put("Genus", new JSONObject()
                .put("species", new JSONObject()
                    .put("body", idConf(opt(true, "vector")))
                    .put("fin", idConf(opt(false, "HotSpotter")))
                    .put("animal", new JSONObject().put("_save_as", "body"))
                    .put("body+tail", idConf(opt(true, null)))
                    .put("dorsal", "@Genus.species.animal")   // a link that does not end in ".dorsal": not selectable
                    .put("flank", "@Genus.library.flank")     // a selectable link whose target exists only through it
                    .put("common_name", "x")
                    .put("_subspecies", new JSONArray().put("sub"))
                    .put("sub", new JSONObject()))
                .put("library", new JSONObject()
                    .put("flank", new JSONObject().put("_save_as", "side")))
                .put("other", new JSONObject()
                    .put("_default", "@Genus.shared._default")
                    .put("body", idConf(opt(true, null)))
                    .put("animal", new JSONObject().put("_save_as", "body"))
                    .put("flipper", new JSONObject().put("_save_as", "@Genus.shared.alias")))   // a link in the _save_as leaf itself
                .put("shared", new JSONObject()
                    .put("_default", idConf(opt(true, null)).put("_save_as", "shape"))
                    .put("alias", "shape2"))
                .put("empty", new JSONObject()
                    .put("body", idConf())
                    .put("fin", idConf(opt(null, "HotSpotter")))
                    .put("tail", idConf(opt(false, null), opt(false, null)))
                    .put("head", new JSONObject()))
                .put("plain", new JSONObject()
                    .put("body", idConf(opt(true, null)))
                    .put("_default", idConf(opt(true, null)).put("_save_as", "shape"))));
        IAJsonProperties iac = IAJsonProperties.iaConfig();
        assertNotNull(iac, "IAJsonProperties.iaConfig() must not be null in tests");
        iac.setJson(json);
        return iac;
    }

    @Test void canonicalIsSelectableKeysPlusResolvedRedirectTargets() {
        // animal -> body; dorsal is not selectable and its target (body) is already present; flank is
        // selectable and its linked _save_as adds side, which exists nowhere else; common_name,
        // _subspecies and sub are excluded
        assertEquals(List.of("body", "body+tail", "fin", "flank", "side"), fixture().getCanonicalIAClasses(A));
    }

    @Test void matchableKeepsOnlyClassesWithADefaultOption() {
        IAJsonProperties iac = fixture();
        assertEquals(List.of("body", "body+tail"), iac.getMatchableIAClasses(A));
        assertNull(iac.identOpts(A, "dorsal"), "dorsal resolves to animal, which has no options, and A has no _default");
        assertNull(iac.identOpts(A, "side"), "side has no key and A has no _default");
    }

    @Test void defaultSaveAsThroughALinkAddsItsTargetWithoutRemovingExplicitClasses() {
        IAJsonProperties iac = fixture();
        // shape through the linked _default, shape2 through a link in flipper's _save_as leaf
        assertEquals(List.of("body", "shape", "shape2"), iac.getCanonicalIAClasses(B));
        assertEquals(List.of("body", "shape", "shape2"), iac.getMatchableIAClasses(B));   // both through the linked _default._id_conf
    }

    @Test void optionalLookupsAreQuietAndFollowLinks() {
        IAJsonProperties iac = fixture();
        assertEquals("shape2", iac.opt("Genus.other.flipper._save_as"));
        assertEquals("body", iac.opt("Genus.species.dorsal._save_as"));   // dorsal -> animal -> body
        assertNull(iac.opt("Genus.species.body._save_as"));
        assertNull(iac.opt("Genus.species.nope._id_conf"));
        assertNull(iac.opt("Nope.nope"));
    }

    @Test void explicitClassSurvivesADefaultSaveAs() {
        IAJsonProperties iac = fixture();
        assertEquals(List.of("body", "shape"), iac.getCanonicalIAClasses(D));
        assertEquals(List.of("body", "shape"), iac.getMatchableIAClasses(D));
    }

    @Test void absentEmptyAndAllFalseOptionsAreNotMatchable() {
        IAJsonProperties iac = fixture();
        assertEquals(List.of("body", "fin", "head", "tail"), iac.getCanonicalIAClasses(C));
        // fin: default absent counts as true; body: empty; tail: all false; head: no options, no _default
        assertEquals(List.of("fin"), iac.getMatchableIAClasses(C));
    }

    @Test void unknownSpeciesGivesEmptyLists() {
        IAJsonProperties iac = fixture();
        assertTrue(iac.getMatchableIAClasses(new Taxonomy("No such")).isEmpty());
        assertTrue(iac.getCanonicalIAClasses(null).isEmpty());
        assertTrue(iac.getMatchableIAClasses(null).isEmpty());
    }
}
