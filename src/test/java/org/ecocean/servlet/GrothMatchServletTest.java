package org.ecocean.servlet;

import org.ecocean.grid.EncounterLite;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GrothMatchServlet#isEligibleCandidate}, the WB-1791
 * candidate-eligibility gate that restores same-species filtering (and self /
 * spot-less skips) to the synchronous Groth/I3S match path.
 */
class GrothMatchServletTest {

    // Builds an EncounterLite via the public JSON factory, since genus /
    // specificEpithet / encounterNumber have no public setters and the backing
    // fields are package-private to org.ecocean.grid (inaccessible from here).
    private static EncounterLite makeEncounterLite(String encNumber, String genus,
        String specificEpithet, boolean leftSpots, boolean rightSpots) {
        JSONObject j = new JSONObject();
        j.put("encounterNumber", encNumber);
        if (genus != null) j.put("genus", genus);
        if (specificEpithet != null) j.put("specificEpithet", specificEpithet);
        if (leftSpots) {
            j.put("spotsX", new JSONArray(new double[] { 1.0, 2.0, 3.0, 4.0 }));
            j.put("spotsY", new JSONArray(new double[] { 1.0, 2.0, 3.0, 4.0 }));
        }
        if (rightSpots) {
            j.put("rightSpotsX", new JSONArray(new double[] { 5.0, 6.0, 7.0, 8.0 }));
            j.put("rightSpotsY", new JSONArray(new double[] { 5.0, 6.0, 7.0, 8.0 }));
        }
        return EncounterLite.fromJSONObject(j);
    }

    @Test
    void sameSpeciesWithSpotsAndDifferentNumberIsEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite candidate = makeEncounterLite("C1", "Rhincodon", "typus", true, false);
        assertTrue(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "same species + has left spots + different enc number should be eligible");
    }

    @Test
    void differentSpeciesIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite candidate = makeEncounterLite("C1", "Carcharodon", "carcharias", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "cross-species candidate must be excluded (WB-1791)");
    }

    @Test
    void sameSpeciesButDifferentGenusIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        // matching specificEpithet but different genus -> not a match
        EncounterLite candidate = makeEncounterLite("C1", "Carcharodon", "typus", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "genus must also match for species equality");
    }

    @Test
    void selfIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite candidate = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "Q1", false),
            "the query encounter must not match itself");
    }

    @Test
    void sameSpeciesNoSpotsOnScannedSideIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        // candidate has only RIGHT spots, but we are doing a LEFT scan
        EncounterLite candidate = makeEncounterLite("C1", "Rhincodon", "typus", false, true);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "candidate with no spots on the scanned (left) side must be excluded");
    }

    @Test
    void nullGenusCandidateIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite candidate = makeEncounterLite("C1", null, "typus", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "candidate with null genus must be excluded (doesSpeciesMatch null-safety)");
    }

    @Test
    void nullSpecificEpithetCandidateIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite candidate = makeEncounterLite("C1", "Rhincodon", null, true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "candidate with null specificEpithet must be excluded (doesSpeciesMatch null-safety)");
    }

    @Test
    void nullTaxonomyQueryIsNotEligible() {
        EncounterLite query = makeEncounterLite("Q1", null, null, true, false);
        EncounterLite candidate = makeEncounterLite("C1", "Rhincodon", "typus", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", false),
            "query with no taxonomy must not match anything (doesSpeciesMatch null-safety)");
    }

    @Test
    void rightScanPicksRightSpotSide() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", false, true);
        // candidate has both sides
        EncounterLite candidate = makeEncounterLite("C1", "Rhincodon", "typus", true, true);
        assertTrue(GrothMatchServlet.isEligibleCandidate(query, candidate, "Q1", "C1", true),
            "right scan should use the right spot side, which is present -> eligible");

        // candidate with only LEFT spots is ineligible for a right scan
        EncounterLite leftOnly = makeEncounterLite("C2", "Rhincodon", "typus", true, false);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, leftOnly, "Q1", "C2", true),
            "right scan must exclude a candidate that only has left spots");
    }

    @Test
    void leftScanPicksLeftSpotSide() {
        EncounterLite query = makeEncounterLite("Q1", "Rhincodon", "typus", true, false);
        EncounterLite leftOnly = makeEncounterLite("C1", "Rhincodon", "typus", true, false);
        assertTrue(GrothMatchServlet.isEligibleCandidate(query, leftOnly, "Q1", "C1", false),
            "left scan should use the left spot side, which is present -> eligible");

        // candidate with only RIGHT spots is ineligible for a left scan
        EncounterLite rightOnly = makeEncounterLite("C2", "Rhincodon", "typus", false, true);
        assertFalse(GrothMatchServlet.isEligibleCandidate(query, rightOnly, "Q1", "C2", false),
            "left scan must exclude a candidate that only has right spots");
    }
}
