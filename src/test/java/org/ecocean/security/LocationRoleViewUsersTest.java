package org.ecocean.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

/**
 * Encounter.computeViewUsers (the OpenSearch viewUsers writer behind the encounter, annotation
 * and individual serializers) must list everyone holding a covering location role, so search
 * results stop showing "access: none" to users the live checks admit (#1549).
 */
class LocationRoleViewUsersTest {
    private JSONObject previousTree;
    private Shepherd myShepherd;
    private Encounter enc;
    private User owner;

    private static User user(String username, String id) {
        User u = mock(User.class);
        when(u.getUsername()).thenReturn(username);
        when(u.getId()).thenReturn(id);
        return u;
    }

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        // build the mocks first: stubbing a fresh mock inside thenReturn(...) is unfinished stubbing
        owner = user("owner", "owner-uuid");
        User bob = user("bob", "uuid-B");
        User amy = user("amy", "uuid-A");
        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("bob")).thenReturn(bob);
        when(myShepherd.getUser("amy")).thenReturn(amy);
        enc = new Encounter();
        enc.setSubmitterID("owner");
        enc.setLocationID("Komodo");
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    @SuppressWarnings("unchecked")
    private void roleHolders(String... usernames) {
        when(myShepherd.getUsernamesWithAnyRole(any(Collection.class), eq("context0")))
            .thenReturn(Arrays.asList(usernames));
    }

    private static MockedStatic<Collaboration> securityOnNoCollabs(Shepherd sh) {
        MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
        mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
        mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(sh), anyString()))
            .thenReturn(new ArrayList<Collaboration>());
        return mc;
    }

    @Test void locationRoleHoldersAreViewers() {
        roleHolders("bob");
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            List<String> ids = enc.computeViewUsers(myShepherd);
            assertEquals(Arrays.asList("uuid-B"), ids);
        }
    }

    @Test @SuppressWarnings("unchecked")
    void lineageNamesAreQueried() {
        roleHolders();
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            enc.computeViewUsers(myShepherd);
        }
        org.mockito.ArgumentCaptor<Collection<String>> names =
            org.mockito.ArgumentCaptor.forClass(Collection.class);
        verify(myShepherd).getUsernamesWithAnyRole(names.capture(), eq("context0"));
        assertEquals(new HashSet<String>(Arrays.asList("Komodo", "Flores Sea", "Indonesia")),
            new HashSet<String>(names.getValue()));
    }

    @Test void ownerHoldingTheRoleIsNotListedAsAViewer() {
        roleHolders("owner", "bob");
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            List<String> ids = enc.computeViewUsers(myShepherd);
            assertEquals(Arrays.asList("uuid-B"), ids,
                "the owner is granted via submitterUserId, never listed in viewUsers");
        }
    }

    @Test void roleGrantSurvivesAnUnresolvableOwner() {
        enc.setSubmitterID("ghost-user");
        when(myShepherd.getUser("ghost-user")).thenReturn(null);
        roleHolders("bob");
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            List<String> ids = enc.computeViewUsers(myShepherd);
            assertEquals(Arrays.asList("uuid-B"), ids,
                "location roles do not depend on the owner; only collaboration grants fail closed");
        }
    }

    @Test void roleHolderWhoIsAlsoACollaboratorIsListedOnce() {
        roleHolders("bob");
        Collaboration approved = new Collaboration("owner", "bob");
        approved.setState(Collaboration.STATE_APPROVED);
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
                .thenReturn(Arrays.asList(approved));
            List<String> ids = enc.computeViewUsers(myShepherd);
            assertEquals(Arrays.asList("uuid-B"), ids);
        }
    }

    @Test @SuppressWarnings("unchecked")
    void noLocationIdNeverQueriesRoles() {
        enc.setLocationID(null);
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            assertTrue(enc.computeViewUsers(myShepherd).isEmpty());
        }
        verify(myShepherd, never()).getUsernamesWithAnyRole(any(Collection.class), anyString());
    }

    @Test @SuppressWarnings("unchecked")
    void publicEncounterStaysEmptyWithoutQueryingRoles() {
        enc.setSubmitterID("public");
        try (MockedStatic<Collaboration> mc = securityOnNoCollabs(myShepherd)) {
            assertTrue(enc.computeViewUsers(myShepherd).isEmpty());
        }
        verify(myShepherd, never()).getUsernamesWithAnyRole(any(Collection.class), anyString());
        assertFalse(enc.computeViewUsers(myShepherd).contains("uuid-B"));
    }
}
