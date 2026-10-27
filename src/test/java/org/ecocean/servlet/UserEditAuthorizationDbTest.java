package org.ecocean.servlet;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;
import java.util.UUID;
import org.ecocean.Organization;
import org.ecocean.Role;
import org.ecocean.User;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Org admins may edit only unprivileged users who share a direct organization with them, and may
 * add users only to organizations they belong to themselves. Site admins are not restricted.
 */
@Testcontainers
class UserEditAuthorizationDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    private Shepherd sh;
    private String suffix;
    private Organization orgA, orgB;
    private User admin, requester, t1, t2, t3, t4;

    @BeforeEach void setUp() {
        TestPMFUtil.closePMF("context0");
        Properties properties = new Properties();
        properties.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        properties.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        properties.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        properties.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        properties.setProperty("datanucleus.schema.autoCreateAll", "true");
        sh = new Shepherd("context0", properties);
        suffix = "-" + UUID.randomUUID().toString().substring(0, 8);
        sh.beginDBTransaction();
        admin = user("site-admin");
        requester = user("org-admin-a");
        t1 = user("member-a");
        t2 = user("member-b");
        t3 = user("org-admin-a2");
        t4 = user("org-super-ab");
        orgA = new Organization("A");
        orgB = new Organization("B");
        sh.getPM().makePersistent(orgA);
        sh.getPM().makePersistent(orgB);
        assertTrue(sh.commitDBTransactionWithStatus(), "fixture rows committed");
        sh.beginDBTransaction();
        join(orgA, requester);
        join(orgA, t1);
        join(orgB, t2);
        join(orgA, t3);
        join(orgA, t4);
        join(orgB, t4);
        role(admin, "admin", "context0");
        role(requester, Organization.ROLE_MANAGER, "context0");
        role(t3, Organization.ROLE_MANAGER, "context0");
        role(t4, Organization.ROLE_ADMIN, "context0");
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
    }

    @AfterEach void tearDown() {
        try {
            sh.rollbackAndClose();
        } finally {
            TestPMFUtil.closePMF("context0");
        }
    }

    private User user(String username) {
        User u = new User(username + suffix + "@example.invalid", UUID.randomUUID().toString());
        u.setUsername(username + suffix);
        sh.getPM().makePersistent(u);
        return u;
    }

    // one membership per transaction, as UserCreate adds them
    private void join(Organization org, User u) {
        org.addMember(u);
        assertTrue(sh.commitDBTransactionWithStatus(), "membership committed");
        sh.beginDBTransaction();
    }

    private void role(User u, String rolename, String context) {
        Role r = new Role(u.getUsername(), rolename);
        r.setContext(context);
        sh.getPM().makePersistent(r);
    }

    @Test void siteAdminCanEditAnyone() {
        assertTrue(UserCreate.canEditExistingUser(sh, admin, true, t2));
    }

    @Test void orgAdminCanEditSharedOrgMember() {
        assertTrue(UserCreate.canEditExistingUser(sh, requester, false, t1));
    }

    @Test void orgAdminCanEditSelf() {
        assertTrue(UserCreate.canEditExistingUser(sh, requester, false, requester));
    }

    @Test void selfEditOfServiceAccountStillNeedsSiteAdmin() {
        User svc = user("svc");
        svc.setServiceOrganizationId(orgA.getId());
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        join(orgA, svc);
        assertFalse(UserCreate.canEditServiceAccount(false, svc), "service account edits need a site admin");
        assertTrue(UserCreate.canEditServiceAccount(true, svc), "site admin allowed");
    }

    @Test void selfMatchUsesAccountIdNotUsername() {
        User other = new User("other@example.invalid", UUID.randomUUID().toString());
        other.setUsername(t2.getUsername());
        assertFalse(UserCreate.canEditExistingUser(sh, other, false, t2),
            "a different account with the same username is not the same account");
    }

    @Test void orgAdminCannotEditOutsideOrg() {
        assertFalse(UserCreate.canEditExistingUser(sh, requester, false, t2));
    }

    @Test void orgAdminCannotEditPrivilegedTarget() {
        assertFalse(UserCreate.canEditExistingUser(sh, requester, false, t3), "orgAdmin target");
        assertFalse(UserCreate.canEditExistingUser(sh, requester, false, t4), "orgSuper target");
    }

    @Test void privilegedRoleInAnyContextBlocks() {
        role(t1, Organization.ROLE_MANAGER, "context1");
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        assertFalse(UserCreate.canEditExistingUser(sh, requester, false, t1));
    }

    @Test void orgAdminCannotEditApiSubmissionHolder() {
        role(t1, Role.API_SUBMISSION, "context0");
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        assertFalse(UserCreate.canEditExistingUser(sh, requester, false, t1),
            "a holder of a site-admin-managed role is edited by site admins only");
    }

    @Test void orgAdminHoldingApiSubmissionCanEditSelf() {
        role(requester, Role.API_SUBMISSION, "context0");
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        assertTrue(UserCreate.canEditExistingUser(sh, requester, false, requester));
    }

    // the role replacement UserCreate.doPost runs when a non-admin saves their own account
    @Test void selfSaveKeepsSiteAdminManagedRoles() {
        role(requester, Organization.ROLE_ADMIN, "context0");
        role(requester, "researcher", "context0");
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        java.util.List<Role> preexisting = sh.getAllRolesForUser(requester.getUsername());
        assertEquals(3, preexisting.size());
        UserCreate.preserveSiteAdminManagedRoles(preexisting, requester.getUsername(), false);
        sh.getPM().deletePersistentAll(preexisting);
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.beginDBTransaction();
        java.util.List<String> kept = new java.util.ArrayList<String>();
        for (Role r : sh.getAllRolesForUser(requester.getUsername())) kept.add(r.getRolename());
        assertTrue(kept.contains(Organization.ROLE_MANAGER), "orgAdmin kept");
        assertTrue(kept.contains(Organization.ROLE_ADMIN), "orgSuper kept");
        assertFalse(kept.contains("researcher"), "roles a non-admin may grant are replaced from the form");
    }

    @Test void missingRequesterIsRefusedUnlessSiteAdmin() {
        assertFalse(UserCreate.canEditExistingUser(sh, null, false, t1));
        assertFalse(UserCreate.canAddOrganizations(sh, null, false, Collections.singletonList(orgA)));
        assertTrue(UserCreate.canAddOrganizations(sh, null, false, Collections.emptyList()),
            "no additions requested");
    }

    @Test void orgAdminMayAddOnlyOwnOrgs() {
        assertTrue(UserCreate.canAddOrganizations(sh, requester, false, Arrays.asList(orgA)));
        assertFalse(UserCreate.canAddOrganizations(sh, requester, false, Arrays.asList(orgA, orgB)));
        assertTrue(UserCreate.canAddOrganizations(sh, admin, true, Arrays.asList(orgA, orgB)));
    }
}
