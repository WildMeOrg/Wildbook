package org.ecocean;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Exercises Project's encounter-membership methods after the List -> Set change.
// These operate purely on the in-memory collection, so no datastore is required.
class ProjectTest {
    private static Encounter enc(final String id) {
        Encounter e = new Encounter();
        e.setId(id); // sets catalogNumber (= getId)
        return e;
    }

    @Test void addAndContainsByCatalogNumber() {
        Project p = new Project("PREFIX");
        Encounter a = enc("enc-1");
        p.addEncounter(a);
        assertEquals(1, p.numEncounters(), "one encounter after add");
        assertTrue(p.containsEncounter(a), "contains the added encounter");
        // a distinct instance with the same id is the same member (Base.equals by id)
        assertTrue(p.containsEncounter(enc("enc-1")), "membership is by catalogNumber, not identity");
        assertFalse(p.containsEncounter(enc("other")), "non-member returns false");
    }

    @Test void addIsIdempotentBySameId() {
        Project p = new Project("PREFIX");
        p.addEncounter(enc("dup"));
        p.addEncounter(enc("dup")); // different instance, same id
        assertEquals(1, p.numEncounters(), "duplicate id is not added twice");
    }

    @Test void addRejectsNullId() {
        Project p = new Project("PREFIX");
        p.addEncounter(enc(null));
        p.addEncounter(null);
        assertEquals(0, p.numEncounters(), "null-id and null encounters are refused");
    }

    @Test void removeByCatalogNumber() {
        Project p = new Project("PREFIX");
        p.addEncounter(enc("gone"));
        p.addEncounter(enc("stays"));
        p.removeEncounter(enc("gone")); // distinct instance, same id
        assertEquals(1, p.numEncounters(), "removed by id");
        assertFalse(p.containsEncounter(enc("gone")), "removed member is gone");
        assertTrue(p.containsEncounter(enc("stays")), "other member remains");
    }

    @Test void getEncountersReturnsDetachedCopy() {
        Project p = new Project("PREFIX");
        p.addEncounter(enc("a"));
        List<Encounter> snapshot = p.getEncounters();
        assertNotNull(snapshot);
        assertEquals(1, snapshot.size());
        // mutating the returned list must not affect the project's membership
        snapshot.add(enc("sneaky"));
        snapshot.clear();
        assertEquals(1, p.numEncounters(), "getEncounters() returns a copy, not the live set");
    }

    @Test void addEncountersBatchDedups() {
        Project p = new Project("PREFIX");
        List<Encounter> batch = new ArrayList<>();
        batch.add(enc("x"));
        batch.add(enc("y"));
        batch.add(enc("x")); // dup id
        p.addEncounters(batch);
        assertEquals(2, p.numEncounters(), "batch add dedups by id");
    }
}
