package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
 */
class LocationRolePermissionsPassTest {
    private JSONObject previousTree;
    private List<User> users;
    private List<Role> roles;
    private List<Object[]> rows;
    private RuntimeException roleLoadFailure;
    private IndexingManager indexingManager;
    private Encounter staleEncounter; // returned by getEncounter(...) for the invalid-owner row

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        users = new ArrayList<User>();
        roles = new ArrayList<Role>();
        rows = new ArrayList<Object[]>();
        roleLoadFailure = null;
        indexingManager = mock(IndexingManager.class);
        staleEncounter = null;
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    private void user(String username, String id) {
        User u = mock(User.class);
        when(u.getUsername()).thenReturn(username);
        when(u.getId()).thenReturn(id);
        users.add(u);
    }

    private void role(String username, String rolename) {
        Role r = new Role(username, rolename);
        r.setContext("context0");
        roles.add(r);
    }

    private void encounterRow(String id, String submitter, String locationID) {
        rows.add(new Object[] { id, submitter, locationID });
    }

    /** Runs the pass with the fixture; returns viewUsers written per encounter id. */
    private Map<String, Set<String> > runPass() {
        final Map<String, Set<String> > written = new HashMap<String, Set<String> >();
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.getUsersWithUsername()).thenReturn(users);
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
                    when(mock.getEncounter(anyString())).thenReturn(null);
                    if (staleEncounter != null)
                        when(mock.getEncounter(staleEncounter.getCatalogNumber())).thenReturn(staleEncounter);
                });
            MockedConstruction<OpenSearch> searches = mockConstruction(OpenSearch.class,
                (mock, ctx) -> {
                    when(mock.getIndexedViewUsers(anyString(), anyString())).thenReturn(null);
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
            MockedStatic<IndexingManagerFactory> factory = mockStatic(IndexingManagerFactory.class)) {
            factory.when(IndexingManagerFactory::getIndexingManager).thenReturn(indexingManager);
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            mc.when(() -> Collaboration.collaborationsForUser(any(Shepherd.class), anyString()))
                .thenReturn(new ArrayList<Collaboration>());

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
}
