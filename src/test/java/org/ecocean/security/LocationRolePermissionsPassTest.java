package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import org.ecocean.Encounter;
import org.ecocean.IndexingManager;
import org.ecocean.IndexingManagerFactory;
import org.ecocean.OpenSearch;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Encounter.opensearchIndexPermissions() is the viewUsers writer that runs after Role changes.
 * It must agree with computeViewUsers about location-based roles, and a failed role read must
 * abort the pass (leaving permissionsNeeded set) rather than silently revoke every location
 * grant in the index.
 *
 * Its child-refresh decision compares the computed set with what is currently indexed. A full
 * reindex now writes viewUsers itself (issue #1779), so an unchanged indexed value must NOT
 * trigger a reindex (that was the write/erase loop), while a changed one must.
 */
class LocationRolePermissionsPassTest {
    private JSONObject previousTree;
    private List<User> users;
    private Map<String, User> usersByName;
    private List<Role> roles;
    private List<Collaboration> collabs;
    private List<Organization> orgs;
    private List<String> orgAdminUsernames;
    private boolean collabReadFails;
    private List<Object[]> rows;
    private RuntimeException roleLoadFailure;
    private IndexingManager indexingManager;
    private Encounter staleEncounter; // returned by getEncounter(...) for the invalid-owner row
    private Map<String, Encounter> encounters; // id -> Encounter returned by getEncounter(id)
    private Map<String, JSONArray> indexed; // id -> currently indexed viewUsers (null = unknown)

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        users = new ArrayList<User>();
        usersByName = new HashMap<String, User>();
        roles = new ArrayList<Role>();
        collabs = new ArrayList<Collaboration>();
        orgs = new ArrayList<Organization>();
        orgAdminUsernames = new ArrayList<String>();
        collabReadFails = false;
        rows = new ArrayList<Object[]>();
        roleLoadFailure = null;
        indexingManager = mock(IndexingManager.class);
        staleEncounter = null;
        encounters = new HashMap<String, Encounter>();
        indexed = new HashMap<String, JSONArray>();
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    private void user(String username, String id) {
        User u = mock(User.class);
        when(u.getUsername()).thenReturn(username);
        when(u.getId()).thenReturn(id);
        users.add(u);
        usersByName.put(username, u);
    }

    private void collab(String u1, String u2, String state) {
        Collaboration c = new Collaboration(u1, u2);
        c.setState(state);
        collabs.add(c);
    }

    /** An organization whose members are the named fixture users (by stored username). */
    private void org(String... memberUsernames) {
        List<User> members = new ArrayList<User>();
        for (String m : memberUsernames) members.add(usersByName.get(m));
        Organization o = mock(Organization.class);
        when(o.getMembers()).thenReturn(members);
        orgs.add(o);
    }

    private void role(String username, String rolename) {
        Role r = new Role(username, rolename);
        r.setContext("context0");
        roles.add(r);
    }

    private void encounterRow(String id, String submitter, String locationID) {
        rows.add(new Object[] { id, submitter, locationID });
    }

    /** An encounter the pass can load (for the child-refresh enqueue), with auto-indexing on. */
    private Encounter loadable(String id) {
        Encounter enc = new Encounter();
        enc.setCatalogNumber(id);
        enc.setSkipAutoIndexing(false);
        encounters.put(id, enc);
        return enc;
    }

    private void indexedState(String id, String... viewUserIds) {
        indexed.put(id, new JSONArray(Arrays.asList(viewUserIds)));
    }

    /** Runs the pass with the fixture; returns viewUsers written per encounter id. */
    private Map<String, Set<String> > runPass() {
        final Map<String, Set<String> > written = new HashMap<String, Set<String> >();
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.getUsersWithUsername()).thenReturn(users);
                    when(mock.getAllCollaborations()).thenReturn(collabReadFails ? null : collabs);
                    when(mock.getAllOrganizations()).thenReturn(orgs);
                    when(mock.getUsernamesWithAnyRole(any(), eq("context0")))
                        .thenReturn(orgAdminUsernames);
                    if (roleLoadFailure != null) {
                        when(mock.getRolesInContext("context0")).thenThrow(roleLoadFailure);
                    } else {
                        when(mock.getRolesInContext("context0")).thenReturn(roles);
                    }
                    PersistenceManager pm = mock(PersistenceManager.class);
                    Query query = mock(Query.class);
                    when(query.execute()).thenReturn(rows);
                    when(pm.newQuery(eq("javax.jdo.query.SQL"), anyString())).thenReturn(query);
                    when(mock.getPM()).thenReturn(pm);
                    if (staleEncounter != null)
                        encounters.put(staleEncounter.getCatalogNumber(), staleEncounter);
                    when(mock.getEncounter(anyString())).thenAnswer(inv ->
                        encounters.get((String)inv.getArgument(0)));
                });
            MockedConstruction<OpenSearch> searches = mockConstruction(OpenSearch.class,
                (mock, ctx) -> {
                    when(mock.getIndexedViewUsers(anyString(), anyString())).thenAnswer(inv ->
                        indexed.get((String)inv.getArgument(1)));
                    // the pass reuses ONE updateData object across rows, so snapshot each
                    // document at call time instead of capturing the (mutated) reference
                    org.mockito.Mockito.doAnswer(inv -> {
                        JSONObject doc = inv.getArgument(2);
                        JSONArray vu = doc.optJSONArray("viewUsers");
                        Set<String> set = new HashSet<String>();
                        if (vu != null) for (int j = 0; j < vu.length(); j++) set.add(vu.getString(j));
                        written.put(inv.getArgument(1), set);
                        return null;
                    }).when(mock).indexUpdate(eq("encounter"), anyString(), any(JSONObject.class));
                });
            MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class);
            MockedStatic<OpenSearch> osStatic = mockStatic(OpenSearch.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(indexingManager);
            // enqueueAclReindex honors the global /tmp/skipAutoIndexing kill-switch; pin it off
            osStatic.when(OpenSearch::skipAutoIndexing).thenReturn(false);
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);

            boolean completed = Encounter.opensearchIndexPermissions();
            written.put("__completed", new HashSet<String>(Arrays.asList(String.valueOf(completed))));
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return written;
    }

    @Test void locationRoleHoldersLandInViewUsers() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        user("amy", "uuid-A");
        role("bob", "Indonesia"); // ancestor of Komodo
        role("amy", "Pakistan"); // unrelated branch
        encounterRow("enc-1", "owner", "Komodo");

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"),
            "bob via the Indonesia ancestor role; amy's Pakistan role does not cover Komodo");
    }

    @Test void ownerIsNeverListedInItsOwnViewUsers() {
        user("owner", "uuid-O");
        role("owner", "Komodo");
        encounterRow("enc-1", "owner", "Komodo");

        Map<String, Set<String> > written = runPass();
        assertTrue(written.containsKey("enc-1"), "viewUsers is always written, even when empty");
        assertTrue(written.get("enc-1").isEmpty());
    }

    @Test void systemRoleNamesAndUnknownUsersAreIgnored() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "researcher");
        role("nobody", "Indonesia"); // no matching user row -> no id to grant
        encounterRow("enc-1", "owner", "researcher");
        encounterRow("enc-2", "owner", "Komodo");

        Map<String, Set<String> > written = runPass();
        assertTrue(written.get("enc-1").isEmpty(), "researcher is a system role, not a location");
        assertTrue(written.get("enc-2").isEmpty());
    }

    @Test void encounterWithoutLocationGetsNoLocationGrants() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", null);

        Map<String, Set<String> > written = runPass();
        assertTrue(written.get("enc-1").isEmpty());
    }

    @Test void roleLoadFailureAbortsBeforeAnyIndexWrite() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", "Komodo");
        roleLoadFailure = new javax.jdo.JDOException("datastore down");

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("false")), written.get("__completed"),
            "the pass must report failure so permissionsNeeded stays set for a retry");
        assertFalse(written.containsKey("enc-1"),
            "no viewUsers write may happen when the roles could not be read");
    }

    @Test void differentEncountersGetTheirOwnViewerSets() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        user("amy", "uuid-A");
        role("bob", "Indonesia");
        role("amy", "Pakistan");
        encounterRow("enc-komodo", "owner", "Komodo");
        encounterRow("enc-pak", "owner", "PAKISTAN - North");
        encounterRow("enc-none", "owner", "Atlantis");

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-komodo"));
        assertEquals(new HashSet<String>(Arrays.asList("uuid-A")), written.get("enc-pak"));
        assertTrue(written.get("enc-none").isEmpty());
    }

    @Test void invalidOwnerRowIsHandedToTheFullReindexNotWrittenInline() {
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-ghost", "ghost-user", "Komodo"); // owner has no user row
        staleEncounter = new Encounter();
        staleEncounter.setCatalogNumber("enc-ghost");

        Map<String, Set<String> > written = runPass();
        assertFalse(written.containsKey("enc-ghost"),
            "the pass does not write viewUsers inline for an unresolvable owner");
        // the full reindex it enqueues serializes viewUsers via computeViewUsers, which grants
        // location roles independently of the owner (see LocationRoleViewUsersTest)
        verify(indexingManager).addIndexingQueueEntry(eq(staleEncounter), eq(false));
    }

    // ---- child refresh decision (the #1779 write/erase loop lived here) ----

    @Test void unchangedIndexedViewUsersEnqueuesNothing() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", "Komodo");
        loadable("enc-1");
        indexedState("enc-1", "uuid-B"); // a full reindex already wrote the same set

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"),
            "the pass still (re)writes the set");
        verify(indexingManager, never()).addIndexingQueueEntry(any(), anyBoolean());
    }

    @Test void changedIndexedViewUsersEnqueuesTheEncounterOnce() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", "Komodo");
        Encounter enc = loadable("enc-1");
        indexedState("enc-1"); // indexed [] -> bob is new

        runPass();
        verify(indexingManager, org.mockito.Mockito.times(1)).addIndexingQueueEntry(eq(enc), eq(false));
    }

    @Test void unknownIndexedStateEnqueuesNothing() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", "Komodo");
        loadable("enc-1");
        // no indexedState(...) -> null = unreadable/degraded: never storm

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"));
        verify(indexingManager, never()).addIndexingQueueEntry(any(), anyBoolean());
    }

    // ---- username resolution must match computeViewUsers (Shepherd.getUser trims) ----

    @Test void paddedRoleUsernameResolvesLikeGetUser() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role(" bob ", "Indonesia"); // stored with whitespace; getUser(" bob ") finds bob
        encounterRow("enc-1", "owner", "Komodo");

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"),
            "the serializer grants bob through getUser's trimmed lookup; so must the pass");
    }

    @Test void paddedSubmitterResolvesToItsOwner() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        role("owner", "Komodo");
        encounterRow("enc-1", " owner ", "Komodo");

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"),
            "a padded submitter is the owner (getUser trims): written inline, owner not listed");
        verify(indexingManager, never()).addIndexingQueueEntry(any(), anyBoolean());
    }

    // ---- collaborations and organizations, read the way computeViewUsers reads them ----

    @Test void approvedAndEditCollaboratorsAreGranted_pendingAndRejectedAreNot() {
        user("owner", "uuid-O");
        user("ann", "uuid-A");
        user("ed", "uuid-E");
        user("pen", "uuid-P");
        user("rej", "uuid-R");
        collab("owner", "ann", Collaboration.STATE_APPROVED);
        collab("owner", "ed", Collaboration.STATE_EDIT_PRIV);
        collab("owner", "pen", Collaboration.STATE_INITIALIZED);
        collab("rej", "owner", Collaboration.STATE_REJECTED);
        encounterRow("enc-1", "owner", null);
        encounterRow("enc-2", "ann", null); // mutual: owner sees ann's encounter too

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-A", "uuid-E")), written.get("enc-1"));
        assertEquals(new HashSet<String>(Arrays.asList("uuid-O")), written.get("enc-2"));
    }

    @Test void paddedCollaborationCounterpartResolvesLikeGetUser() {
        // computeViewUsers finds this row by the raw submitter string and resolves the
        // counterpart through getUser (trimmed); the pass must grant the same user
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        collab("owner", " bob ", Collaboration.STATE_APPROVED);
        encounterRow("enc-1", "owner", null);

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-B")), written.get("enc-1"));
    }

    @Test void selfCollaborationGrantsNothing() {
        user("owner", "uuid-O");
        collab("owner", "owner", Collaboration.STATE_APPROVED);
        encounterRow("enc-1", "owner", null);

        Map<String, Set<String> > written = runPass();
        assertTrue(written.get("enc-1").isEmpty());
    }

    @Test void orgAdminSeesMembersOfAllItsOrganizations_oneWay() {
        user("admin", "uuid-AD");
        user("m1", "uuid-1");
        user("m2", "uuid-2");
        user("other", "uuid-X");
        orgAdminUsernames.add("admin");
        org("admin", "m1");
        org("admin", "m2");
        org("other", "m1"); // no orgAdmin here: grants nothing
        encounterRow("enc-m1", "m1", null);
        encounterRow("enc-m2", "m2", null);
        encounterRow("enc-admin", "admin", null);

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-AD")), written.get("enc-m1"));
        assertEquals(new HashSet<String>(Arrays.asList("uuid-AD")), written.get("enc-m2"));
        assertTrue(written.get("enc-admin").isEmpty(), "members never see the orgAdmin's own");
    }

    @Test void paddedSubmitterStillGetsItsOrganizationGrants() {
        // computeViewUsers resolves the owner through getUser (trimmed) and then walks that
        // user's organizations; the pass keys organization grants by the resolved user id
        user("admin", "uuid-AD");
        user("member", "uuid-M");
        orgAdminUsernames.add("admin");
        org("admin", "member");
        encounterRow("enc-1", " member ", null);

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("uuid-AD")), written.get("enc-1"));
    }

    @Test void collaborationReadFailureAbortsBeforeAnyIndexWrite() {
        user("owner", "uuid-O");
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-1", "owner", "Komodo");
        collabReadFails = true; // Shepherd.getAllCollaborations swallows errors and returns null

        Map<String, Set<String> > written = runPass();
        assertEquals(new HashSet<String>(Arrays.asList("false")), written.get("__completed"));
        assertFalse(written.containsKey("enc-1"),
            "an unreadable collaboration table must not silently revoke every collaboration grant");
    }

    @Test void whitespaceOnlyOwnerIsAnonymous_neverWritten() {
        user("bob", "uuid-B");
        role("bob", "Indonesia");
        encounterRow("enc-ws", " ", "Komodo"); // User.isUsernameAnonymous trims; the SQL does not

        Map<String, Set<String> > written = runPass();
        assertFalse(written.containsKey("enc-ws"),
            "an anonymous-owner encounter is publiclyReadable: computeViewUsers yields [] and the pass must not write a viewer set for it");
        verify(indexingManager, never()).addIndexingQueueEntry(any(), anyBoolean());
    }
}
