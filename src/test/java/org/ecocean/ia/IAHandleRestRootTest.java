package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/** Spec §4.0: nothing is attached beneath an umbrella; handleRest substitutes a fresh root. */
class IAHandleRestRootTest {

    @Test void unknownTaskIdCreatesARootWithThatId() {
        Task root = IA.resolveRequestRoot(null, "req-1", false);
        assertEquals("req-1", root.getId());
        assertTrue(root.getParameters() == null || !root.getParameters().has("fastlane"));
    }

    @Test void fastlaneIsStampedOnTheRoot() {
        Task root = IA.resolveRequestRoot(null, "req-1", true);
        assertTrue(root.getParameters().getBoolean("fastlane"));
    }

    /** Review Focus 5: the hamburger-menu New Match reuses the media-asset-only detection root. */
    @Test void mediaAssetOnlyRootIsReused() {
        Task loaded = new Task("req-2");
        loaded.addObject(mock(MediaAsset.class));
        assertSame(loaded, IA.resolveRequestRoot(loaded, "req-2", false));
    }

    @Test void rootHoldingAnnotationsIsReplacedAndKeepsTheRequestParameters() {
        Task loaded = new Task("req-3");
        loaded.setParameters(new JSONObject()
            .put("matchingSetFilter", new JSONObject().put("locationIds", new JSONArray().put("X")))
            .put("matchingAlgorithms", new JSONArray().put(new JSONObject().put("default", true)))
            .put("userId", "u1").put(Task.PARAM_RUN_ID, "req-3").put(Task.PARAM_OPTION, true)
            .put("ibeis.identification", new JSONObject()));
        Annotation a = new Annotation();
        a.setId("A");
        loaded.addObject(a);
        Task root = IA.resolveRequestRoot(loaded, "req-3", true);
        assertNotSame(loaded, root);
        assertNotEquals("req-3", root.getId());
        assertFalse(root.hasObjectAnnotations());
        JSONObject p = root.getParameters();
        assertTrue(p.getBoolean("fastlane"));
        assertTrue(p.has("matchingSetFilter"), "the matching-set filter survives the substitution");
        assertTrue(p.has("matchingAlgorithms"));
        assertEquals("u1", p.getString("userId"));
        assertFalse(p.has(Task.PARAM_RUN_ID));
        assertFalse(p.has(Task.PARAM_OPTION));
        assertFalse(p.has("ibeis.identification"));
    }

    @Test void rootCarryingARunIdIsReplaced() {
        Task loaded = new Task("req-4");
        loaded.addParameter(Task.PARAM_RUN_ID, "req-4");   // an umbrella
        Task root = IA.resolveRequestRoot(loaded, "req-4", false);
        assertNotSame(loaded, root);
        assertTrue(root.getParameters() == null || !root.getParameters().has(Task.PARAM_RUN_ID));
        Task inherited = new Task("req-5");
        inherited.addParameter(Task.PARAM_RUN_ID, "someone-else");   // an option/leaf of a run
        assertNotSame(inherited, IA.resolveRequestRoot(inherited, "req-5", false));
    }
}
