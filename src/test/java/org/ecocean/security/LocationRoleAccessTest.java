package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Location-based roles: a Role named after a locationID node grants access to encounters at
 * that node and everything below it. These tests pin the lineage rules (issue #1549 / #1244).
 */
class LocationRoleAccessTest {
    private JSONObject previousDefaultTree;

    private static Set<String> set(String... names) {
        return new HashSet<String>(Arrays.asList(names));
    }

    // fixture tree: see LocationRoleTestTree
    @BeforeEach void injectTree() {
        previousDefaultTree = LocationRoleTestTree.inject();
    }

    @AfterEach void restoreTree() {
        LocationRoleTestTree.restore(previousDefaultTree);
    }

    // ---- roleNamesFor: lineage against the default tree ----

    @Test void leafIncludesItselfAndAncestors() {
        assertEquals(set("Komodo", "Flores Sea", "Indonesia"),
            LocationRoleAccess.roleNamesFor("Komodo"), "role at any ancestor covers the leaf");
    }

    @Test void childOfTopLevelNodeIncludesParent() {
        assertEquals(set("PAKISTAN - North", "Pakistan"),
            LocationRoleAccess.roleNamesFor("PAKISTAN - North"));
    }

    @Test void parentNodeIsNotCoveredByChildRoles() {
        assertEquals(set("Pakistan"), LocationRoleAccess.roleNamesFor("Pakistan"),
            "a child-node role must not grant the parent-node encounter");
    }

    @Test void siblingIsNotIncluded() {
        assertFalse(LocationRoleAccess.roleNamesFor("PAKISTAN - North").contains("PAKISTAN - South"));
    }

    @Test void ambiguousDuplicateIdFallsBackToExactOnly() {
        assertEquals(set("Twin"), LocationRoleAccess.roleNamesFor("Twin"),
            "an id under two parents must not inherit either parent");
    }

    @Test void blankIntermediateIdIsSkipped() {
        assertEquals(set("Orphan"), LocationRoleAccess.roleNamesFor("Orphan"));
    }

    @Test void idNotInTreeIsExactOnly() {
        assertEquals(set("Atlantis"), LocationRoleAccess.roleNamesFor("Atlantis"));
    }

    @Test void nullOrBlankLocationYieldsNothing() {
        assertTrue(LocationRoleAccess.roleNamesFor(null).isEmpty());
        assertTrue(LocationRoleAccess.roleNamesFor("").isEmpty());
        assertTrue(LocationRoleAccess.roleNamesFor("   ").isEmpty());
    }

    @Test void systemRoleNamesNeverCount() {
        assertEquals(set("Lab"), LocationRoleAccess.roleNamesFor("Lab"),
            "an ancestor named like a system role is dropped");
        assertTrue(LocationRoleAccess.roleNamesFor("researcher").isEmpty(),
            "a location named exactly like a system role grants nothing");
        assertTrue(LocationRoleAccess.roleNamesFor("admin").isEmpty());
        assertTrue(LocationRoleAccess.roleNamesFor("orgAdmin").isEmpty());
    }

    // ---- lineageFor: pure traversal edge cases ----

    @Test void identifiedRootIsPartOfTheLineage() {
        JSONObject tree = new JSONObject(
            "{\"id\":\"World\",\"locationID\":[{\"id\":\"X\",\"locationID\":[]}]}");
        assertEquals(Arrays.asList("World", "X"), LocationRoleAccess.lineageFor("X", tree));
        assertEquals(Arrays.asList("World"), LocationRoleAccess.lineageFor("World", tree));
    }

    @Test void nullTreeYieldsExactOnly() {
        assertEquals(Arrays.asList("X"), LocationRoleAccess.lineageFor("X", null));
    }

    @Test void malformedChildEntriesAreIgnored() {
        JSONObject tree = new JSONObject(
            "{\"locationID\":[\"not-an-object\", 7, {\"name\":\"no id here\",\"locationID\":["
            + "{\"id\":\"Deep\",\"locationID\":\"not-an-array\"}]}]}");
        assertEquals(Arrays.asList("Deep"), LocationRoleAccess.lineageFor("Deep", tree));
    }

    // ---- request path (Shiro) ----

    @Test void requestGrantedWhenAnyLineageRoleHeld() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isUserInRole(anyString())).thenReturn(false);
        when(request.isUserInRole("Indonesia")).thenReturn(true);
        assertTrue(LocationRoleAccess.requestHasLocationRole(request, "Komodo"));
    }

    @Test void requestDeniedWhenNoLineageRoleHeld() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isUserInRole(anyString())).thenReturn(false);
        when(request.isUserInRole("PAKISTAN - South")).thenReturn(true); // sibling
        when(request.isUserInRole("Komodo")).thenReturn(true); // child of a different branch
        assertFalse(LocationRoleAccess.requestHasLocationRole(request, "PAKISTAN - North"));
    }

    @Test void requestWithNullLocationNeverAsksShiro() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        assertFalse(LocationRoleAccess.requestHasLocationRole(request, null));
        assertFalse(LocationRoleAccess.requestHasLocationRole(null, "Komodo"));
        verify(request, never()).isUserInRole(anyString());
    }

    // ---- Shepherd path (DB) ----

    @Test @SuppressWarnings("unchecked")
    void userHasLocationRoleQueriesTheFullLineageInContext() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        when(myShepherd.doesUserHaveAnyRole(eq("bob"), any(Collection.class), eq("context0")))
            .thenReturn(true);
        assertTrue(LocationRoleAccess.userHasLocationRole("bob", "Komodo", myShepherd));
        ArgumentCaptor<Collection<String>> names = ArgumentCaptor.forClass(Collection.class);
        verify(myShepherd).doesUserHaveAnyRole(eq("bob"), names.capture(), eq("context0"));
        assertEquals(set("Komodo", "Flores Sea", "Indonesia"), new HashSet<String>(names.getValue()));
    }

    @Test void userHasLocationRoleShortCircuitsWithoutNames() {
        Shepherd myShepherd = mock(Shepherd.class);
        assertFalse(LocationRoleAccess.userHasLocationRole("bob", null, myShepherd));
        assertFalse(LocationRoleAccess.userHasLocationRole("bob", "researcher", myShepherd));
        assertFalse(LocationRoleAccess.userHasLocationRole(null, "Komodo", myShepherd));
        verifyNoInteractions(myShepherd);
    }

    @Test @SuppressWarnings("unchecked")
    void userIdsWithLocationRoleResolvesUsernamesToIdsAndSkipsUnresolvable() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        when(myShepherd.getUsernamesWithAnyRole(any(Collection.class), eq("context0")))
            .thenReturn(Arrays.asList("bob", "amy", "ghost"));
        User bob = mock(User.class);
        when(bob.getId()).thenReturn("u-bob");
        User amy = mock(User.class);
        when(amy.getId()).thenReturn(null); // no uuid -> cannot appear in viewUsers
        when(myShepherd.getUser("bob")).thenReturn(bob);
        when(myShepherd.getUser("amy")).thenReturn(amy);
        when(myShepherd.getUser("ghost")).thenReturn(null);
        assertEquals(set("u-bob"), LocationRoleAccess.userIdsWithLocationRole("Komodo", myShepherd));
    }

    @Test void userIdsWithLocationRoleIsEmptyWithoutNames() {
        Shepherd myShepherd = mock(Shepherd.class);
        assertTrue(LocationRoleAccess.userIdsWithLocationRole(null, myShepherd).isEmpty());
        assertTrue(LocationRoleAccess.userIdsWithLocationRole("admin", myShepherd).isEmpty());
        verifyNoInteractions(myShepherd);
    }

    // ---- permissions-pass helper (pure) ----

    @Test void viewUserIdsForLocationUnionsLineageRolesAndCachesLineage() {
        Map<String, Set<String>> roleNameToUserIds = new HashMap<String, Set<String>>();
        roleNameToUserIds.put("Indonesia", set("u1"));
        roleNameToUserIds.put("Komodo", set("u2", "u1"));
        roleNameToUserIds.put("Pakistan", set("u3"));
        Map<String, Set<String>> lineageCache = new HashMap<String, Set<String>>();

        assertEquals(set("u1", "u2"),
            LocationRoleAccess.viewUserIdsForLocation("Komodo", roleNameToUserIds, lineageCache));
        assertTrue(lineageCache.containsKey("Komodo"), "lineage is cached per locationID");
        assertEquals(set("u1"),
            LocationRoleAccess.viewUserIdsForLocation("Flores Sea", roleNameToUserIds, lineageCache));
        assertTrue(LocationRoleAccess.viewUserIdsForLocation("Atlantis", roleNameToUserIds,
            lineageCache).isEmpty());
        assertTrue(LocationRoleAccess.viewUserIdsForLocation(null, roleNameToUserIds,
            lineageCache).isEmpty());
    }

    @Test void viewUserIdsForLocationUsesCachedLineageWhenPresent() {
        Map<String, Set<String>> roleNameToUserIds = new HashMap<String, Set<String>>();
        roleNameToUserIds.put("Elsewhere", set("u9"));
        Map<String, Set<String>> lineageCache = new HashMap<String, Set<String>>();
        lineageCache.put("Komodo", set("Elsewhere")); // pre-seeded: the tree is not consulted
        assertEquals(set("u9"),
            LocationRoleAccess.viewUserIdsForLocation("Komodo", roleNameToUserIds, lineageCache));
    }
}
