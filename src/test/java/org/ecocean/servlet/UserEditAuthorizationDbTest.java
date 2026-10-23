package org.ecocean.servlet;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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

    private static boolean membershipKeyFixed = false;

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
        fixMembershipKey();
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

    /**
     * Schema auto-creation keys ORGANIZATION_MEMBERS on (ORGANIZATION_ID, IDX), but IDX is the
     * position in the owning User.organizations list, so a second member of one organization
     * collides. Key it on the owning side, as the list mapping implies.
     */
    private static synchronized void fixMembershipKey() {
        if (membershipKeyFixed) return;
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
            Statement st = c.createStatement()) {
            st.execute("ALTER TABLE \"ORGANIZATION_MEMBERS\" DROP CONSTRAINT \"ORGANIZATION_MEMBERS_PK\"");
            st.execute("ALTER TABLE \"ORGANIZATION_MEMBERS\" ADD PRIMARY KEY (\"USER_ID\", \"IDX\")");
            membershipKeyFixed = true;
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not re-key ORGANIZATION_MEMBERS", e);
        }
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
