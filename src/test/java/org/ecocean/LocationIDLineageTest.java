package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.Collections;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

/**
 * LocationID.lineageFor(): the root-to-node path through a locationID tree. Callers (currently
 * LocationRoleAccess, for issue #1549) rely on it returning a single path or nothing useful --
 * never a union of branches, which is how the older getIDForChildAndParents() behaves.
 */
class LocationIDLineageTest {
    @Test void identifiedRootIsPartOfTheLineage() {
        JSONObject tree = new JSONObject(
            "{\"id\":\"World\",\"locationID\":[{\"id\":\"X\",\"locationID\":[]}]}");

        assertEquals(Arrays.asList("World", "X"), LocationID.lineageFor("X", tree));
        assertEquals(Arrays.asList("World"), LocationID.lineageFor("World", tree));
    }

    @Test void nestedLeafCarriesEveryAncestorInOrder() {
        JSONObject tree = new JSONObject(
            "{\"locationID\":[{\"id\":\"Indonesia\",\"locationID\":[{\"id\":\"Flores Sea\","
            + "\"locationID\":[{\"id\":\"Komodo\"}]}]}]}");

        assertEquals(Arrays.asList("Indonesia", "Flores Sea", "Komodo"),
            LocationID.lineageFor("Komodo", tree), "root-to-node order, node last");
    }

    @Test void duplicatedIdFallsBackToExactOnly() {
        JSONObject tree = new JSONObject(
            "{\"locationID\":[{\"id\":\"A\",\"locationID\":[{\"id\":\"Twin\"}]},"
            + "{\"id\":\"B\",\"locationID\":[{\"id\":\"Twin\"}]}]}");

        assertEquals(Arrays.asList("Twin"), LocationID.lineageFor("Twin", tree),
            "an id under two parents must not inherit the ancestors of either");
    }

    @Test void blankIntermediateIdIsSkipped() {
        JSONObject tree = new JSONObject(
            "{\"locationID\":[{\"id\":\"  \",\"locationID\":[{\"id\":\"Orphan\"}]}]}");

        assertEquals(Arrays.asList("Orphan"), LocationID.lineageFor("Orphan", tree));
    }

    @Test void idNotInTreeIsExactOnly() {
        JSONObject tree = new JSONObject("{\"locationID\":[{\"id\":\"X\"}]}");

        assertEquals(Arrays.asList("Atlantis"), LocationID.lineageFor("Atlantis", tree));
    }

    @Test void nullTreeYieldsExactOnly() {
        assertEquals(Arrays.asList("X"), LocationID.lineageFor("X", null));
    }

    @Test void malformedChildEntriesAreIgnored() {
        JSONObject tree = new JSONObject(
            "{\"locationID\":[\"not-an-object\", 7, {\"name\":\"no id here\",\"locationID\":["
            + "{\"id\":\"Deep\",\"locationID\":\"not-an-array\"}]}]}");

        assertEquals(Arrays.asList("Deep"), LocationID.lineageFor("Deep", tree));
    }

    @Test void blankLocationIdYieldsNothing() {
        JSONObject tree = new JSONObject("{\"locationID\":[{\"id\":\"X\"}]}");

        assertEquals(Collections.emptyList(), LocationID.lineageFor(null, tree));
        assertEquals(Collections.emptyList(), LocationID.lineageFor("", tree));
        assertEquals(Collections.emptyList(), LocationID.lineageFor("   ", tree));
    }
}
