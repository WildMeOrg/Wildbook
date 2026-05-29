package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.ecocean.security.Collaboration;
import org.ecocean.shepherd.core.Shepherd;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class ComputeViewUsersTest {

    // User has no public setId; use the single-arg uuid constructor (User.java:118)
    // then set the username. getId() returns that uuid (User.java:173).
    private User user(String username, String id) {
        User u = new User(id);     // constructor sets uuid = id
        u.setUsername(username);
        return u;
    }

    @Test void publicEncounter_yieldsEmpty() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("public"); // User.isUsernameAnonymous => publiclyReadable
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        // security disabled => isPubliclyReadable() returns true (short-circuits)
        List<String> ids = enc.computeViewUsers(myShepherd);
        assertTrue(ids.isEmpty(), "public encounter must have no viewUsers");
    }

    @Test void invalidOwner_yieldsEmpty() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("ghost-user");
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        when(myShepherd.getUser("ghost-user")).thenReturn(null); // unmappable owner
        // security disabled => isPubliclyReadable() returns true; but let's also test
        // with security enabled so the invalid-owner guard is exercised
        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            List<String> ids = enc.computeViewUsers(myShepherd);
            assertTrue(ids.isEmpty(), "invalid-owner encounter must fail closed to []");
        }
    }

    @Test void persistedApprovedAndEdit_added_othersExcluded() {
        Encounter enc = new Encounter();
        enc.setSubmitterID("owner");
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");
        User owner = user("owner", "owner-uuid");
        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("approvedU")).thenReturn(user("approvedU", "uuid-A"));
        when(myShepherd.getUser("editU")).thenReturn(user("editU", "uuid-E"));
        when(myShepherd.getUser("rejectedU")).thenReturn(user("rejectedU", "uuid-R"));
        when(myShepherd.getUser("pendingU")).thenReturn(user("pendingU", "uuid-P"));
        // owner is not an orgAdmin and belongs to no orgs
        when(myShepherd.getAllOrganizationsForUser(owner)).thenReturn(new ArrayList<Organization>());

        Collaboration cApproved = new Collaboration("owner", "approvedU"); cApproved.setState(Collaboration.STATE_APPROVED);
        Collaboration cEdit = new Collaboration("owner", "editU"); cEdit.setState(Collaboration.STATE_EDIT_PRIV);
        Collaboration cRejected = new Collaboration("owner", "rejectedU"); cRejected.setState(Collaboration.STATE_REJECTED);
        Collaboration cPending = new Collaboration("owner", "pendingU"); cPending.setState(Collaboration.STATE_EDIT_PENDING_PRIV);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
              .thenReturn(Arrays.asList(cApproved, cEdit, cRejected, cPending));
            List<String> ids = enc.computeViewUsers(myShepherd);
            Set<String> set = new java.util.HashSet<String>(ids);
            assertTrue(set.contains("uuid-A"), "approved collaborator must be a viewer");
            assertTrue(set.contains("uuid-E"), "edit collaborator must be a viewer");
            assertTrue(!set.contains("uuid-R"), "rejected collaborator must NOT be a viewer (Defect 2)");
            assertTrue(!set.contains("uuid-P"), "pending collaborator must NOT be a viewer (Defect 2)");
            assertEquals(2, set.size());
        }
    }

    @Test void orgAdmin_oneWay_adminSeesMemberNotInverse() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");

        User member = user("member", "member-uuid");
        User admin  = user("admin",  "admin-uuid");

        Organization testOrg = new Organization("testOrg");
        testOrg.addMember(member);
        testOrg.addMember(admin);

        when(myShepherd.getUser("member")).thenReturn(member);
        when(myShepherd.getUser("admin")).thenReturn(admin);
        when(myShepherd.getAllOrganizationsForUser(member)).thenReturn(Arrays.asList(testOrg));
        when(myShepherd.getAllOrganizationsForUser(admin)).thenReturn(Arrays.asList(testOrg));
        when(myShepherd.doesUserHaveRole(eq("admin"),  eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(true);
        when(myShepherd.doesUserHaveRole(eq("member"), eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(false);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            mc.when(() -> Collaboration.persistedCollaborationsForUser(any(Shepherd.class), anyString()))
              .thenReturn(new ArrayList<Collaboration>());

            // encounter owned by "member" → admin should see it
            Encounter encMember = new Encounter();
            encMember.setSubmitterID("member");
            List<String> ids1 = encMember.computeViewUsers(myShepherd);
            assertTrue(ids1.contains("admin-uuid"), "orgAdmin must see member's encounter");

            // encounter owned by "admin" → member must NOT see it (one-way)
            Encounter encAdmin = new Encounter();
            encAdmin.setSubmitterID("admin");
            List<String> ids2 = encAdmin.computeViewUsers(myShepherd);
            assertTrue(!ids2.contains("member-uuid"), "member must NOT see admin's encounter (one-way)");
        }
    }

    @Test void orgAdmin_multiOrg_seesMembersOfAllOrgs() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");

        User m1    = user("m1",    "m1-uuid");
        User m2    = user("m2",    "m2-uuid");
        User m3    = user("m3",    "m3-uuid");
        User admin = user("admin", "admin-uuid");

        Organization orgA = new Organization("orgA");
        orgA.addMember(m1);
        orgA.addMember(admin);

        Organization orgB = new Organization("orgB");
        orgB.addMember(m2);
        orgB.addMember(admin);

        when(myShepherd.getUser("m1")).thenReturn(m1);
        when(myShepherd.getUser("m2")).thenReturn(m2);
        when(myShepherd.getUser("m3")).thenReturn(m3);
        when(myShepherd.getUser("admin")).thenReturn(admin);
        when(myShepherd.getAllOrganizationsForUser(m1)).thenReturn(Arrays.asList(orgA));
        when(myShepherd.getAllOrganizationsForUser(m2)).thenReturn(Arrays.asList(orgB));
        // m3 belongs to both orgs so admin appears via two org paths (dedupe check)
        when(myShepherd.getAllOrganizationsForUser(m3)).thenReturn(Arrays.asList(orgA, orgB));
        when(myShepherd.doesUserHaveRole(eq("admin"), eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(true);
        when(myShepherd.doesUserHaveRole(eq("m1"),    eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(false);
        when(myShepherd.doesUserHaveRole(eq("m2"),    eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(false);
        when(myShepherd.doesUserHaveRole(eq("m3"),    eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(false);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            mc.when(() -> Collaboration.persistedCollaborationsForUser(any(Shepherd.class), anyString()))
              .thenReturn(new ArrayList<Collaboration>());

            // orgA path: encounter owned by m1 → admin must be a viewer
            Encounter encM1 = new Encounter();
            encM1.setSubmitterID("m1");
            List<String> idsM1 = encM1.computeViewUsers(myShepherd);
            assertTrue(idsM1.contains("admin-uuid"), "admin in same org must see m1's encounter (orgA path)");

            // orgB path: encounter owned by m2 → admin must be a viewer
            Encounter encM2 = new Encounter();
            encM2.setSubmitterID("m2");
            List<String> idsM2 = encM2.computeViewUsers(myShepherd);
            assertTrue(idsM2.contains("admin-uuid"), "admin must see m2's encounter via orgB path");

            // dedupe: m3 is in both orgA and orgB; admin must appear exactly once
            Encounter encM3 = new Encounter();
            encM3.setSubmitterID("m3");
            List<String> idsM3 = encM3.computeViewUsers(myShepherd);
            long adminCount = idsM3.stream().filter("admin-uuid"::equals).count();
            assertEquals(1L, adminCount, "admin-uuid must appear exactly once (no duplicate via two orgs)");
        }
    }

    @Test void orgAdminOwner_withRealCollabs_doesNotInvert() {
        Shepherd myShepherd = mock(Shepherd.class);
        when(myShepherd.getContext()).thenReturn("context0");

        User owner  = user("owner",  "owner-uuid");
        User member = user("member", "member-uuid");
        User friend = user("friend", "friend-uuid");

        Organization testOrg = new Organization("testOrg");
        testOrg.addMember(owner);
        testOrg.addMember(member);

        when(myShepherd.getUser("owner")).thenReturn(owner);
        when(myShepherd.getUser("member")).thenReturn(member);
        when(myShepherd.getUser("friend")).thenReturn(friend);
        when(myShepherd.getAllOrganizationsForUser(owner)).thenReturn(Arrays.asList(testOrg));
        when(myShepherd.doesUserHaveRole(eq("owner"),  eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(true);
        when(myShepherd.doesUserHaveRole(eq("member"), eq(Organization.ROLE_MANAGER), eq("context0"))).thenReturn(false);

        Collaboration cFriend = new Collaboration("owner", "friend");
        cFriend.setState(Collaboration.STATE_APPROVED);

        try (MockedStatic<Collaboration> mc = mockStatic(Collaboration.class, Answers.CALLS_REAL_METHODS)) {
            mc.when(() -> Collaboration.securityEnabled(anyString())).thenReturn(true);
            mc.when(() -> Collaboration.persistedCollaborationsForUser(eq(myShepherd), eq("owner")))
              .thenReturn(Arrays.asList(cFriend));

            Encounter enc = new Encounter();
            enc.setSubmitterID("owner");
            List<String> ids = enc.computeViewUsers(myShepherd);
            Set<String> set = new java.util.HashSet<String>(ids);
            assertTrue(set.contains("friend-uuid"), "approved friend must see owner's encounter");
            assertTrue(!set.contains("member-uuid"), "org member must NOT see orgAdmin owner's encounter");
            assertEquals(1, set.size());
        }
    }
}
