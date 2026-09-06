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
    void contextUsernamePath_closesTheRoleShepherdBeforeTheCollaborationLookup() {
        final java.util.concurrent.atomic.AtomicBoolean closedBeforeCollab =
            new java.util.concurrent.atomic.AtomicBoolean(false);
        final java.util.List<Shepherd> opened = new java.util.ArrayList<Shepherd>();
        // register the stub BEFORE the construction mock is active: with CALLS_REAL_METHODS the
        // stubbing call itself runs the real collaborationBetweenUsers once, and that must not
        // become one of the "constructed" Shepherds we assert on
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.collaborationBetweenUsers(anyString(), anyString(),
                anyString())).thenAnswer(inv -> {
                    // the collaboration lookup runs only after the role Shepherd has been closed
                    closedBeforeCollab.set(!opened.isEmpty() &&
                        org.mockito.Mockito.mockingDetails(opened.get(0)).getInvocations().stream()
                        .anyMatch(i -> "rollbackAndClose".equals(i.getMethod().getName())));
                    return null;
                });
            try (MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    opened.add(mock);
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.doesUserHaveAnyRole(anyString(), any(Collection.class), anyString()))
                        .thenReturn(false);
                })) {
                assertFalse(Collaboration.canUserAccessEncounter(enc, "context0", "bob"));
                assertEquals(1, opened.size(), "only the role check opens a Shepherd on this path");
                verify(opened.get(0)).rollbackAndClose();
                assertTrue(closedBeforeCollab.get(),
                    "the role-check Shepherd must be closed before canCollaborate opens its own");
            }
        }
    }

    @Test @SuppressWarnings("unchecked")
    void contextUsernamePath_aThrowingRoleQueryDeniesAndStillCloses() {
        try (MockedStatic<Collaboration> mc = noCollaborations();
            MockedConstruction<Shepherd> shepherds = mockConstruction(Shepherd.class,
                (mock, ctx) -> {
                    when(mock.getContext()).thenReturn("context0");
                    when(mock.doesUserHaveAnyRole(anyString(), any(Collection.class), anyString()))
                        .thenThrow(new javax.jdo.JDOException("datastore down"));
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

    @Test void classicCheck_ownerAdminAndPublicStillAuthorized() {
        try (MockedStatic<Collaboration> mc = noCollaborations()) {
            mc.when(() -> Collaboration.canEditEncounter(any(Encounter.class),
                any(HttpServletRequest.class))).thenReturn(false);
            HttpServletRequest ownerReq = classicRequestFor("owner");
            assertTrue(ServletUtilities.isUserAuthorizedForEncounter(enc, ownerReq, myShepherd),
                "owner");
            HttpServletRequest adminReq = classicRequestFor("root");
            when(adminReq.isUserInRole("admin")).thenReturn(true);
            assertTrue(ServletUtilities.isUserAuthorizedForEncounter(enc, adminReq, myShepherd),
                "admin");
            Encounter publicEnc = new Encounter();
            publicEnc.setSubmitterID("public");
            publicEnc.setLocationID("Komodo");
            assertTrue(ServletUtilities.isUserAuthorizedForEncounter(publicEnc,
                classicRequestFor("bob"), myShepherd), "public encounter");
            assertFalse(ServletUtilities.isUserAuthorizedForEncounter(enc,
                classicRequestFor("bob"), myShepherd), "unrelated user without any role");
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
