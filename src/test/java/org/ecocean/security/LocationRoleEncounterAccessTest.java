package org.ecocean.security;

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

import java.security.Principal;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

/**
 * Location-based roles reach the live access checks the React app and the API use (issue
 * #1549) and the request-based check individuals.jsp uses (issue #1244), with the legacy
 * classic-page check switched to the same lineage rule.
 */
class LocationRoleEncounterAccessTest {
    private JSONObject previousTree;
    private Shepherd myShepherd;
    private User bob;
    private Encounter enc;

    @BeforeEach void setUp() {
        previousTree = LocationRoleTestTree.inject();
        myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        bob = new User("bob", null, null); // real user: not admin (mock Shepherd returns no roles)
        enc = new Encounter();
        enc.setSubmitterID("owner");
        enc.setLocationID("Komodo");
    }

    @AfterEach void tearDown() {
        LocationRoleTestTree.restore(previousTree);
    }

    /** bob holds a role that is in the lineage set iff the set contains heldRole. */
    @SuppressWarnings("unchecked")
    private void bobHoldsRole(final String heldRole) {
        when(myShepherd.doesUserHaveAnyRole(eq("bob"), any(Collection.class), eq("context0")))
            .thenAnswer(inv -> ((Collection<String>)inv.getArgument(1)).contains(heldRole));
    }

    private static MockedStatic<Collaboration> noCollaborations() {
        MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS);
        mc.when(() -> Collaboration.collaborationBetweenUsers(anyString(), anyString(),
            anyString())).thenReturn(null);
        return mc;
    }

    // ---- Encounter.canUserView / canUserEdit (API + React paths) ----

    @Test void canUserView_grantedByAncestorLocationRole() {
        bobHoldsRole("Indonesia");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertTrue(enc.canUserView(bob, myShepherd),
                "a role at Indonesia covers an encounter at Komodo");
        }
    }

    @Test void canUserView_deniedWhenOnlyAChildNodeRoleIsHeld() {
        enc.setLocationID("Flores Sea");
        bobHoldsRole("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertFalse(enc.canUserView(bob, myShepherd),
                "a Komodo role must not grant the parent Flores Sea encounter");
        }
    }

    @Test void canUserView_deniedWithoutAnyRoleOrCollaboration() {
        bobHoldsRole("Pakistan");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertFalse(enc.canUserView(bob, myShepherd));
        }
    }

    @Test void canUserEdit_grantedByLocationRole() {
        bobHoldsRole("Flores Sea");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertTrue(enc.canUserEdit(bob, myShepherd), "location roles grant edit, not only view");
        }
    }

    @Test void canUserEdit_deniedWithoutRoleOrEditCollaboration() {
        bobHoldsRole("Pakistan");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertFalse(enc.canUserEdit(bob, myShepherd));
        }
    }

    @Test void canUserAccessWithShepherd_ownerNeverConsultsRoles() {
        enc.setSubmitterID("bob");
        assertTrue(enc.canUserAccess(bob, myShepherd));
        verify(myShepherd, never()).doesUserHaveAnyRole(anyString(), any(Collection.class),
            anyString());
    }

    @Test void canUserAccessWithShepherd_roleThenCollaboration() {
        bobHoldsRole("Komodo");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertTrue(enc.canUserAccess(bob, myShepherd));
        }
        bobHoldsRole("Pakistan");
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            assertFalse(enc.canUserAccess(bob, myShepherd));
        }
    }

    // ---- Collaboration.canUserAccessEncounter (request path: individuals.jsp, #1244) ----

    @Test void requestPath_grantedByLineageRole() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isUserInRole(anyString())).thenReturn(false);
        when(request.isUserInRole("Indonesia")).thenReturn(true);
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canUserAccessOwnedObject(anyString(),
                any(HttpServletRequest.class))).thenReturn(false);
            assertTrue(Collaboration.canUserAccessEncounter(enc, request));
        }
    }

    @Test void requestPath_deniedWithoutLineageRole() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.isUserInRole(anyString())).thenReturn(false);
        when(request.isUserInRole("PAKISTAN - North")).thenReturn(true);
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canUserAccessOwnedObject(anyString(),
                any(HttpServletRequest.class))).thenReturn(false);
            assertFalse(Collaboration.canUserAccessEncounter(enc, request));
        }
    }

    // ---- Collaboration.canUserAccessEncounter (context + username path: exports, Task) ----

    @Test @SuppressWarnings("unchecked")
    void contextUsernamePath_grantedByRoleWithAShortLivedShepherdThatIsClosed() {
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.doesUserHaveAnyRole(eq("bob"), any(Collection.class), eq("context0")))
                        .thenReturn(true);
                })) {
            assertTrue(Collaboration.canUserAccessEncounter(enc, "context0", "bob"));
            assertTrue(shepherds.constructed().size() >= 1, "a Shepherd is opened for the role check");
            Shepherd roleShepherd = shepherds.constructed().get(0);
            verify(roleShepherd).beginDBTransaction();
            verify(roleShepherd).rollbackAndClose();
        }
    }

    @Test @SuppressWarnings("unchecked")
    void contextUsernamePath_closesTheRoleShepherdEvenWhenDenied() {
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.doesUserHaveAnyRole(anyString(), any(Collection.class), anyString()))
                        .thenReturn(false);
                })) {
            assertFalse(Collaboration.canUserAccessEncounter(enc, "context0", "bob"));
            verify(shepherds.constructed().get(0)).rollbackAndClose();
        }
    }

    // ---- ServletUtilities.isUserAuthorizedForEncounter (classic pages) ----

    private HttpServletRequest classicRequestFor(String username) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        Principal principal = mock(Principal.class);
        when(principal.getName()).thenReturn(username);
        when(request.getUserPrincipal()).thenReturn(principal);
        when(request.getRemoteUser()).thenReturn(username);
        when(request.isUserInRole(anyString())).thenReturn(false);
        return request;
    }

    @Test void classicCheck_grantedByAncestorRole() {
        HttpServletRequest request = classicRequestFor("bob");
        when(request.isUserInRole("Indonesia")).thenReturn(true);
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canEditEncounter(any(Encounter.class),
                any(HttpServletRequest.class))).thenReturn(false);
            assertTrue(ServletUtilities.isUserAuthorizedForEncounter(enc, request, myShepherd),
                "classic pages follow the same lineage rule");
        }
    }

    @Test void classicCheck_deniedForChildRoleOnParentEncounter() {
        enc.setLocationID("Flores Sea");
        HttpServletRequest request = classicRequestFor("bob");
        when(request.isUserInRole("Komodo")).thenReturn(true);
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canEditEncounter(any(Encounter.class),
                any(HttpServletRequest.class))).thenReturn(false);
            assertFalse(ServletUtilities.isUserAuthorizedForEncounter(enc, request, myShepherd));
        }
    }

    @Test void classicCheck_systemRoleNameCollisionGrantsNothing() {
        enc.setLocationID("researcher");
        HttpServletRequest request = classicRequestFor("bob");
        when(request.isUserInRole("researcher")).thenReturn(true);
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canEditEncounter(any(Encounter.class),
                any(HttpServletRequest.class))).thenReturn(false);
            assertFalse(ServletUtilities.isUserAuthorizedForEncounter(enc, request, myShepherd),
                "holding the researcher role must not unlock an encounter located at 'researcher'");
        }
    }
}
