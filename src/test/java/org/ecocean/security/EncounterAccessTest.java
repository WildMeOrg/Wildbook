package org.ecocean.security;

import javax.servlet.http.HttpServletRequest;

import org.ecocean.Encounter;
import org.ecocean.MarkedIndividual;
import org.ecocean.Organization;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Tests for the consolidated encounter authorization logic in Collaboration.
 * Covers canUserAccessEncounter (read) and canUserModifyEncounter (write)
 * to verify the requireEdit flag correctly separates read vs write paths.
 */
class EncounterAccessTest {
    HttpServletRequest mockRequest;
    Shepherd mockShepherd;
    Principal mockPrincipal;

    @BeforeEach
    void setUp() {
        mockRequest = mock(HttpServletRequest.class);
        mockShepherd = mock(Shepherd.class);
        mockPrincipal = mock(Principal.class);
        when(mockPrincipal.getName()).thenReturn("test-user");
    }

    private void setAuthenticatedUser(String username) {
        when(mockPrincipal.getName()).thenReturn(username);
        when(mockRequest.getUserPrincipal()).thenReturn(mockPrincipal);
    }

    private void setUnauthenticated() {
        when(mockRequest.getUserPrincipal()).thenReturn(null);
    }

    private void setAdminRole(boolean isAdmin) {
        when(mockRequest.isUserInRole("admin")).thenReturn(isAdmin);
    }

    private void setOrgAdminRole(boolean isOrgAdmin) {
        when(mockRequest.isUserInRole("orgAdmin")).thenReturn(isOrgAdmin);
    }

    private void setLocationRole(String locationId, boolean hasRole) {
        when(mockRequest.isUserInRole(locationId)).thenReturn(hasRole);
    }

    // === null encounter ===

    @Test
    void nullEncounterReturnsFalseForBothPaths() {
        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            assertFalse(Collaboration.canUserAccessEncounter(null, mockRequest, mockShepherd),
                "read: null encounter should return false");
            assertFalse(Collaboration.canUserModifyEncounter(null, mockRequest, mockShepherd),
                "write: null encounter should return false");
        }
    }

    // === security disabled ===

    @Test
    void securityDisabledGrantsBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("some-owner");
        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(false);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: security disabled should grant access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: security disabled should grant access");
        }
    }

    // === anonymous/public owner ===

    @Test
    void anonymousOwnerGrantsReadButNotWrite() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("public");
        setUnauthenticated();

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: anonymous owner should grant access to unauthenticated user");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: anonymous owner should NOT grant modify to unauthenticated user");
        }
    }

    @Test
    void nullOwnerGrantsReadButNotWrite() {
        Encounter enc = new Encounter();
        // no submitterID set — owner is null
        setUnauthenticated();

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: null owner should grant access");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: null owner should NOT grant modify to unauthenticated user");
        }
    }

    // === unauthenticated user, private encounter ===

    @Test
    void unauthenticatedDeniedPrivateEncounter() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("some-owner");
        setUnauthenticated();

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertFalse(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: unauthenticated user denied private encounter");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: unauthenticated user denied private encounter");
        }
    }

    // === admin access ===

    @Test
    void adminGrantedBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("some-owner");
        setAuthenticatedUser("admin-user");
        setAdminRole(true);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: admin should have access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: admin should have modify access");
        }
    }

    // === owner match ===

    @Test
    void ownerGrantedBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("test-user");
        setAuthenticatedUser("test-user");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: owner should have access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: owner should have modify access");
        }
    }

    // === orgAdmin with shared org ===

    @Test
    void orgAdminWithSharedOrgGrantedBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        setAuthenticatedUser("org-admin-user");
        setOrgAdminRole(true);

        Organization sharedOrg = new Organization("Shared Org");
        User ownerUser = new User("enc-owner", null, null);
        List<Organization> ownerOrgs = new ArrayList<>();
        ownerOrgs.add(sharedOrg);
        ownerUser.setOrganizations(ownerOrgs);

        User requesterUser = new User("org-admin-user", null, null);
        List<Organization> requesterOrgs = new ArrayList<>();
        requesterOrgs.add(sharedOrg);
        requesterUser.setOrganizations(requesterOrgs);

        when(mockShepherd.getUser("enc-owner")).thenReturn(ownerUser);
        when(mockShepherd.getUser(mockRequest)).thenReturn(requesterUser);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: orgAdmin with shared org should have access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: orgAdmin with shared org should have modify access");
        }
    }

    @Test
    void orgAdminWithoutSharedOrgDenied() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        setAuthenticatedUser("org-admin-user");
        setOrgAdminRole(true);

        User ownerUser = new User("enc-owner", null, null);
        ownerUser.setOrganizations(new ArrayList<>());

        User requesterUser = new User("org-admin-user", null, null);
        requesterUser.setOrganizations(new ArrayList<>());

        when(mockShepherd.getUser("enc-owner")).thenReturn(ownerUser);
        when(mockShepherd.getUser(mockRequest)).thenReturn(requesterUser);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: orgAdmin without shared org should be denied");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: orgAdmin without shared org should be denied");
        }
    }

    // === locationID role ===

    @Test
    void locationRoleGrantsBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        enc.setLocationID("Kenya");
        setAuthenticatedUser("field-user");
        setLocationRole("Kenya", true);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: user with matching locationID role should have access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: user with matching locationID role should have modify access");
        }
    }

    @Test
    void nonMatchingLocationRoleDenied() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        enc.setLocationID("Kenya");
        setAuthenticatedUser("field-user");
        setLocationRole("Kenya", false);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: user without matching locationID role should be denied");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: user without matching locationID role should be denied");
        }
    }

    // === collaboration: approved vs edit ===

    @Test
    void approvedCollaborationGrantsReadButNotWrite() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        setAuthenticatedUser("collab-user");

        Collaboration collab = new Collaboration();
        collab.setState(Collaboration.STATE_APPROVED);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(collab);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: approved collaboration should grant read access");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: approved collaboration should NOT grant modify access");
        }
    }

    @Test
    void editCollaborationGrantsBothPaths() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        setAuthenticatedUser("collab-user");

        Collaboration collab = new Collaboration();
        collab.setState(Collaboration.STATE_EDIT_PRIV);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(collab);

            assertTrue(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: edit collaboration should grant read access");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: edit collaboration should grant modify access");
        }
    }

    @Test
    void noCollaborationDenied() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        setAuthenticatedUser("stranger");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: no collaboration should deny access");
            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: no collaboration should deny modify");
        }
    }

    // === WDP special case ===

    @Test
    void wdpExceptionOnlyInWritePath() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        enc.setGenus("Stenella");
        enc.setSpecificEpithet("frontalis");
        // no submitters = no researcher submitted
        setAuthenticatedUser("wdp");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserAccessEncounter(enc, mockRequest, mockShepherd),
                "read: WDP exception should NOT apply to read path");
            assertTrue(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: WDP exception should apply to write path for Stenella frontalis");
        }
    }

    @Test
    void wdpDeniedForOtherSpecies() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        enc.setGenus("Tursiops");
        enc.setSpecificEpithet("truncatus");
        setAuthenticatedUser("wdp");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserModifyEncounter(enc, mockRequest, mockShepherd),
                "write: WDP exception should not apply to non-Stenella frontalis");
        }
    }

    // === MarkedIndividual access ===

    @Test
    void individualAccessGrantedWhenOneEncounterAccessible() {
        MarkedIndividual individual = new MarkedIndividual();
        Encounter ownedEnc = new Encounter();
        ownedEnc.setSubmitterID("test-user");
        individual.addEncounter(ownedEnc);

        Encounter otherEnc = new Encounter();
        otherEnc.setSubmitterID("other-owner");
        individual.addEncounter(otherEnc);

        setAuthenticatedUser("test-user");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertTrue(Collaboration.canUserAccessMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual readable when user owns at least one encounter");
            assertTrue(Collaboration.canUserModifyMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual modifiable when user owns at least one encounter");
        }
    }

    @Test
    void individualDeniedWhenNoEncounterAccessible() {
        MarkedIndividual individual = new MarkedIndividual();
        Encounter otherEnc = new Encounter();
        otherEnc.setSubmitterID("other-owner");
        individual.addEncounter(otherEnc);

        setAuthenticatedUser("stranger");

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(null);

            assertFalse(Collaboration.canUserAccessMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual not readable when no encounters accessible");
            assertFalse(Collaboration.canUserModifyMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual not modifiable when no encounters accessible");
        }
    }

    @Test
    void individualWithNoEncountersAccessibleButNotModifiable() {
        MarkedIndividual individual = new MarkedIndividual();
        // no encounters

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual with no encounters should be readable");
            assertFalse(Collaboration.canUserModifyMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual with no encounters should NOT be modifiable");
        }
    }

    @Test
    void individualReadableViaApprovedCollabButNotModifiable() {
        MarkedIndividual individual = new MarkedIndividual();
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        individual.addEncounter(enc);

        setAuthenticatedUser("collab-user");

        Collaboration collab = new Collaboration();
        collab.setState(Collaboration.STATE_APPROVED);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);
            mockCollab.when(() -> Collaboration.collaborationBetweenUsers(any(String.class),
                any(String.class), any(String.class))).thenReturn(collab);

            assertTrue(Collaboration.canUserAccessMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual readable via approved collaboration");
            assertFalse(Collaboration.canUserModifyMarkedIndividual(individual, mockRequest,
                mockShepherd), "individual NOT modifiable via approved-only collaboration");
        }
    }

    // === locationID role on individual (the original bug) ===

    @Test
    void individualAccessibleViaLocationRoleOnEncounter() {
        MarkedIndividual individual = new MarkedIndividual();
        Encounter enc = new Encounter();
        enc.setSubmitterID("enc-owner");
        enc.setLocationID("Kenya");
        individual.addEncounter(enc);

        setAuthenticatedUser("field-user");
        setLocationRole("Kenya", true);

        try (MockedStatic<Collaboration> mockCollab = mockStatic(Collaboration.class,
                org.mockito.Answers.CALLS_REAL_METHODS);
             MockedStatic<ServletUtilities> mockServlet = mockStatic(ServletUtilities.class)) {
            mockServlet.when(() -> ServletUtilities.getContext(any())).thenReturn("context0");
            mockCollab.when(() -> Collaboration.securityEnabled("context0")).thenReturn(true);

            assertTrue(Collaboration.canUserAccessMarkedIndividual(individual, mockRequest,
                mockShepherd),
                "individual should be accessible when user has matching locationID role");
        }
    }
}
