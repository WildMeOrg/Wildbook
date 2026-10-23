package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.ecocean.security.PermissionsAudit.AclTuple;
import org.ecocean.security.PermissionsAudit.IndexedDoc;
import org.ecocean.security.PermissionsAudit.Snapshot;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The pure part of the permissions audit: what ACL tuple each index document is expected to
 * carry, computed from one database snapshot, and how an indexed document (doc values) is read
 * and compared. These rules must equal what the serializers write (Encounter.opensearchDocument-
 * Serializer, Encounter.opensearchAclFields, Annotation.writeAclFields,
 * MarkedIndividual.writeAclFields); ViewUsersParityDbTest proves that on a real database.
 */
class PermissionsAuditTupleTest {
    private JSONObject previousTree;

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject(); // Indonesia > {Flores Sea, Komodo}, Pakistan > ...
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    private static Set<String> set(String... s) {
        return new HashSet<String>(Arrays.asList(s));
    }

    /** A snapshot builder: users owner/bob/amy/pubuser, bob holds Indonesia, amy collaborates
     *  with owner, orgadmin administers owner's organization. */
    private static final class Fixture {
        Map<String, String[]> encounters = new HashMap<String, String[]>();
        Map<String, String> usernameToId = new HashMap<String, String>();
        Map<String, Set<String> > collabGrants = new HashMap<String, Set<String> >();
        Map<String, Set<String> > orgGrants = new HashMap<String, Set<String> >();
        Map<String, Set<String> > roleNameToUserIds = new HashMap<String, Set<String> >();
        Set<String> eligibleAnnotations = new HashSet<String>();
        Map<String, Set<String> > annotationLinks = new HashMap<String, Set<String> >();
        Set<String> individuals = new HashSet<String>();
        Map<String, Set<String> > individualMembers = new HashMap<String, Set<String> >();

        Fixture() {
            usernameToId.put("owner", "uuid-O");
            usernameToId.put("bob", "uuid-B");
            usernameToId.put("amy", "uuid-A");
            usernameToId.put("orgadmin", "uuid-G");
            usernameToId.put("public", "uuid-P"); // a real user literally named "public"
            collabGrants.put("owner", set("uuid-A"));
            orgGrants.put("uuid-O", set("uuid-G"));
            roleNameToUserIds.put("Indonesia", set("uuid-B"));
        }

        Fixture encounter(String id, String submitter, String locationID) {
            encounters.put(id, new String[] { submitter, locationID });
            return this;
        }

        Fixture annotation(String id, String... parents) {
            eligibleAnnotations.add(id);
            annotationLinks.put(id, set(parents));
            return this;
        }

        Fixture individual(String id, String... members) {
            individuals.add(id);
            individualMembers.put(id, set(members));
            return this;
        }

        Snapshot snapshot() {
            return new Snapshot(encounters, usernameToId, collabGrants, orgGrants, roleNameToUserIds,
                eligibleAnnotations, annotationLinks, individuals, individualMembers);
        }
    }

    // ---- encounter documents ----

    @Test void privateEncounterCarriesItsOwnerAndTheThreeGrantSourcesMinusTheOwner() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").snapshot();
        AclTuple t = s.expectedEncounter("e1");
        assertNotNull(t);
        assertFalse(t.publiclyReadable);
        assertEquals(set("uuid-O"), t.owners);
        assertEquals(set("uuid-B", "uuid-A", "uuid-G"), t.viewers, "Indonesia covers Komodo; collab; orgAdmin");
    }

    @Test void publicEncounterKeepsAResolvedOwnerButGrantsNobody() {
        // the encounter serializer resolves and writes submitterUserId even when the owner is
        // anonymous by name, so a user literally named "public" shows up as the owner
        Snapshot s = new Fixture().encounter("e1", "public", "Komodo").snapshot();
        AclTuple t = s.expectedEncounter("e1");
        assertTrue(t.publiclyReadable);
        assertEquals(set("uuid-P"), t.owners);
        assertTrue(t.viewers.isEmpty());
    }

    @Test void anonymousEncounterWithoutAUserRowIsPublicWithNoOwner() {
        Snapshot s = new Fixture().encounter("e1", "N/A", "Komodo").snapshot();
        AclTuple t = s.expectedEncounter("e1");
        assertTrue(t.publiclyReadable);
        assertTrue(t.owners.isEmpty());
        assertTrue(t.viewers.isEmpty());
    }

    @Test void unresolvableOwnerGetsLocationGrantsOnly() {
        Snapshot s = new Fixture().encounter("e1", "ghost", "Komodo").snapshot();
        s = new Fixture().encounter("e1", "ghost", "Komodo").snapshot();
        AclTuple t = s.expectedEncounter("e1");
        assertFalse(t.publiclyReadable);
        assertTrue(t.owners.isEmpty(), "no owner id: the document's owner field must be cleared");
        assertEquals(set("uuid-B"), t.viewers, "owner-dependent grants fail closed");
    }

    @Test void ownerHoldingACoveringRoleIsNeverItsOwnViewer() {
        Fixture f = new Fixture().encounter("e1", "bob", "Komodo");
        AclTuple t = f.snapshot().expectedEncounter("e1");
        assertEquals(set("uuid-B"), t.owners);
        assertFalse(t.viewers.contains("uuid-B"));
    }

    @Test void paddedSubmitterResolvesLikeGetUserButCollaborationsMatchRaw() {
        Fixture f = new Fixture().encounter("e1", " owner ", "Atlantis");
        AclTuple t = f.snapshot().expectedEncounter("e1");
        assertEquals(set("uuid-O"), t.owners, "getUser trims the lookup key");
        assertEquals(set("uuid-G"), t.viewers, "orgAdmins key on the resolved id; the collaboration keyed on the raw string does not match");
    }

    @Test void unknownEncounterHasNoExpectation() {
        assertNull(new Fixture().snapshot().expectedEncounter("nope"));
    }

    // ---- annotation documents ----

    @Test void annotationWithOnePrivateParentCopiesTheParentTuple() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").annotation("a1", "e1").snapshot();
        AclTuple t = s.expectedAnnotation("a1");
        assertFalse(t.publiclyReadable);
        assertEquals(set("uuid-O"), t.owners);
        assertEquals(set("uuid-B", "uuid-A", "uuid-G"), t.viewers);
    }

    @Test void annotationWithOnePublicParentOmitsTheOwner() {
        // Encounter.opensearchAclFields omits submitterUserId for a public encounter
        Snapshot s = new Fixture().encounter("e1", "public", "Komodo").annotation("a1", "e1").snapshot();
        AclTuple t = s.expectedAnnotation("a1");
        assertTrue(t.publiclyReadable);
        assertTrue(t.owners.isEmpty());
        assertTrue(t.viewers.isEmpty());
    }

    @Test void annotationWithTwoParentsIsClosed() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").encounter("e2", "bob", "Komodo")
            .annotation("a1", "e1", "e2").snapshot();
        assertEquals(AclTuple.DENY, s.expectedAnnotation("a1"));
        assertEquals(2, s.parentsOf("a1").size());
    }

    @Test void eligibleOrphanAnnotationIsClosed() {
        Snapshot s = new Fixture().annotation("a1").snapshot();
        assertEquals(AclTuple.DENY, s.expectedAnnotation("a1"));
        assertTrue(s.parentsOf("a1").isEmpty());
    }

    @Test void danglingParentLinkIsNotAParent() {
        // a join row naming an encounter that no longer exists: findAllByAnnotation ignores it
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").annotation("a1", "e1", "gone").snapshot();
        assertEquals(set("e1"), s.parentsOf("a1"));
        assertEquals(set("uuid-O"), s.expectedAnnotation("a1").owners);
    }

    @Test void ineligibleAnnotationHasNoExpectation() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").snapshot();
        assertNull(s.expectedAnnotation("not-indexed"), "the content reconciler removes it; the audit leaves it alone");
    }

    // ---- individual documents ----

    @Test void individualUnionsItsPrivateMembersAndIsPublicIfAnyMemberIs() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").encounter("e2", "public", "Komodo")
            .encounter("e3", "bob", "Atlantis").individual("i1", "e1", "e2", "e3").snapshot();
        AclTuple t = s.expectedIndividual("i1");
        assertTrue(t.publiclyReadable, "e2 is public");
        assertEquals(set("uuid-O", "uuid-B"), t.owners, "private members' owners; the public member contributes none");
        assertEquals(set("uuid-B", "uuid-A", "uuid-G"), t.viewers,
            "bob stays a viewer through e1 although bob owns e3: the union is over per-encounter sets");
    }

    @Test void individualWithNoMembersIsPublic() {
        Snapshot s = new Fixture().individual("i1").snapshot();
        AclTuple t = s.expectedIndividual("i1");
        assertTrue(t.publiclyReadable);
        assertTrue(t.owners.isEmpty());
        assertTrue(t.viewers.isEmpty());
    }

    @Test void danglingMembershipIsIgnored() {
        Snapshot s = new Fixture().encounter("e1", "owner", "Komodo").individual("i1", "e1", "gone").snapshot();
        assertEquals(set("e1"), s.membersOf("i1"));
    }

    @Test void unknownIndividualHasNoExpectation() {
        assertNull(new Fixture().snapshot().expectedIndividual("nope"));
    }

    // ---- reading a doc-values hit ----

    private static JSONObject hit(String id, Long seqNo, Long primaryTerm, JSONObject fields) {
        JSONObject h = new JSONObject().put("_id", id);
        if (seqNo != null) h.put("_seq_no", seqNo.longValue());
        if (primaryTerm != null) h.put("_primary_term", primaryTerm.longValue());
        if (fields != null) h.put("fields", fields);
        return h;
    }

    @Test void hitParsesScalarsFromSingleElementArraysAndMissingFieldsAsEmpty() {
        JSONObject fields = new JSONObject()
            .put("publiclyReadable", new JSONArray().put(false))
            .put("submitterUserIds", new JSONArray().put("uuid-O"))
            .put("viewUsers", new JSONArray().put("uuid-B").put("uuid-A"))
            .put("encounterId", new JSONArray().put("e1"));
        IndexedDoc d = PermissionsAudit.parseHit(hit("a1", 7L, 2L, fields), "submitterUserIds");
        assertNull(d.error);
        assertEquals("a1", d.id);
        assertEquals(7L, d.seqNo);
        assertEquals(2L, d.primaryTerm);
        assertEquals(Boolean.FALSE, d.publiclyReadable);
        assertEquals(set("uuid-O"), d.owners);
        assertEquals(set("uuid-B", "uuid-A"), d.viewers);
        assertEquals(Arrays.asList("e1"), d.encounterId);

        IndexedDoc bare = PermissionsAudit.parseHit(hit("e9", 1L, 1L, new JSONObject()), "submitterUserId");
        assertNull(bare.error);
        assertNull(bare.publiclyReadable, "absent flag is unknown, never assumed");
        assertTrue(bare.owners.isEmpty());
        assertTrue(bare.viewers.isEmpty());
        assertTrue(bare.encounterId.isEmpty());
        assertTrue(bare.encounterIds.isEmpty());
    }

    @Test void hitWithoutConcurrencyMetadataOrWithMalformedValuesIsAnError() {
        JSONObject ok = new JSONObject().put("publiclyReadable", new JSONArray().put(true));
        assertNotNull(PermissionsAudit.parseHit(hit("x", null, 1L, ok), "submitterUserId").error, "no _seq_no");
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, null, ok), "submitterUserId").error, "no _primary_term");
        JSONObject badFlag = new JSONObject().put("publiclyReadable", new JSONArray().put("yes"));
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, 1L, badFlag), "submitterUserId").error, "non-boolean flag");
        JSONObject twoFlags = new JSONObject().put("publiclyReadable", new JSONArray().put(true).put(false));
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, 1L, twoFlags), "submitterUserId").error, "two values for a scalar");
        JSONObject badViewer = new JSONObject().put("viewUsers", new JSONArray().put(42));
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, 1L, badViewer), "submitterUserId").error, "non-string viewer");
    }

    @Test void indexedDocMatchesTupleOnlyWhenEveryFieldAgreesAsSets() {
        JSONObject fields = new JSONObject()
            .put("publiclyReadable", new JSONArray().put(false))
            .put("submitterUserId", new JSONArray().put("uuid-O"))
            .put("viewUsers", new JSONArray().put("uuid-A").put("uuid-B"));
        IndexedDoc d = PermissionsAudit.parseHit(hit("e1", 1L, 1L, fields), "submitterUserId");
        assertTrue(PermissionsAudit.matches(d, new AclTuple(false, set("uuid-O"), set("uuid-B", "uuid-A"))));
        assertFalse(PermissionsAudit.matches(d, new AclTuple(false, set("uuid-O"), set("uuid-B"))), "viewer missing");
        assertFalse(PermissionsAudit.matches(d, new AclTuple(false, set(), set("uuid-B", "uuid-A"))), "stale owner");
        assertFalse(PermissionsAudit.matches(d, new AclTuple(true, set("uuid-O"), set("uuid-B", "uuid-A"))), "flag");
        IndexedDoc noFlag = PermissionsAudit.parseHit(hit("e1", 1L, 1L, new JSONObject()), "submitterUserId");
        assertFalse(PermissionsAudit.matches(noFlag, new AclTuple(false, set(), set())), "an unknown flag never matches");
    }

    // ---- validating a scroll page ----

    private static JSONObject page(boolean timedOut, int failedShards, boolean withHits, boolean withScrollId) {
        JSONObject p = new JSONObject().put("timed_out", timedOut)
            .put("_shards", new JSONObject().put("total", 1).put("successful", 1 - failedShards).put("failed", failedShards));
        if (withHits) p.put("hits", new JSONObject().put("hits", new JSONArray()));
        if (withScrollId) p.put("_scroll_id", "abc");
        return p;
    }

    @Test void pageIsRejectedWhenTimedOutOrShardsFailedOrMalformed() {
        assertNull(PermissionsAudit.validatePage(page(false, 0, true, true)));
        assertNotNull(PermissionsAudit.validatePage(page(true, 0, true, true)), "timed_out");
        assertNotNull(PermissionsAudit.validatePage(page(false, 1, true, true)), "shard failure");
        assertNotNull(PermissionsAudit.validatePage(page(false, 0, false, true)), "no hits envelope");
        assertNotNull(PermissionsAudit.validatePage(page(false, 0, true, false)), "no scroll id");
        assertNotNull(PermissionsAudit.validatePage(null), "no page");
    }

    @Test void malformedFieldShapesAreErrorsNotAbsence() {
        JSONObject scalarViewers = new JSONObject().put("viewUsers", "uuid-B");
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, 1L, scalarViewers), "submitterUserId").error, "scalar viewers");
        JSONObject scalarOwners = new JSONObject().put("submitterUserIds", 5);
        assertNotNull(PermissionsAudit.parseHit(hit("x", 1L, 1L, scalarOwners), "submitterUserIds").error, "numeric owners");
        JSONObject h = hit("x", 1L, 1L, null).put("fields", "not-an-object");
        assertNotNull(PermissionsAudit.parseHit(h, "submitterUserId").error, "fields that are not an object");
    }

    @Test void pageWithAnUnusableScrollIdIsRejected() {
        JSONObject empty = page(false, 0, true, false).put("_scroll_id", "");
        assertNotNull(PermissionsAudit.validatePage(empty), "empty scroll id");
        JSONObject numeric = page(false, 0, true, false).put("_scroll_id", 42);
        assertNotNull(PermissionsAudit.validatePage(numeric), "non-string scroll id");
    }

    @Test void mappingWithANullValueIsRejected() {
        Map<String, JSONObject> m = new HashMap<String, JSONObject>();
        m.put("publiclyReadable", new JSONObject().put("type", "boolean"));
        m.put("viewUsers", new JSONObject().put("type", "keyword"));
        m.put("submitterUserId", new JSONObject().put("type", "keyword").put("null_value", "uuid-X"));
        assertNotNull(PermissionsAudit.checkMapping(m, Arrays.asList("publiclyReadable", "submitterUserId", "viewUsers")),
            "a JSON null written to this field would index the null_value");
        m.put("submitterUserId", new JSONObject().put("type", "keyword"));
        assertNull(PermissionsAudit.checkMapping(m, Arrays.asList("publiclyReadable", "submitterUserId", "viewUsers")));
    }

    @Test void denyTupleAndEqualityAreSetBased() {
        assertTrue(AclTuple.DENY.isDeny());
        assertFalse(new AclTuple(true, set(), set()).isDeny());
        assertEquals(new AclTuple(false, set("a", "b"), set("c")), new AclTuple(false, set("b", "a"), set("c")));
        assertEquals(new AclTuple(false, set("a", "b"), set("c")).hashCode(),
            new AclTuple(false, set("b", "a"), set("c")).hashCode());
    }
}
