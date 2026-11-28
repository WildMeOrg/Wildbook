package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.shepherd.core.TestPMFUtil;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Organization membership on a schema DataNucleus creates from scratch. Memberships used to be an
 * indexed list whose position came from each user's list while the table's key was
 * (ORGANIZATION_ID, IDX), so a second user joining an organization as their first one collided
 * with the first member; every way of adding members failed. Membership is now one row per
 * (organization, user) with no position.
 */
@Testcontainers class OrganizationMembershipDbTest {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
        "postgres:15-alpine")
            .withDatabaseName("wildbook_test").withUsername("wildbook").withPassword("wildbook");

    private static Properties props;

    @BeforeAll static void setUp() {
        TestPMFUtil.closePMF("context0");
        props = new Properties();
        props.setProperty("datanucleus.ConnectionUserName", postgres.getUsername());
        props.setProperty("datanucleus.ConnectionPassword", postgres.getPassword());
        props.setProperty("datanucleus.ConnectionDriverName", postgres.getDriverClassName());
        props.setProperty("datanucleus.ConnectionURL", postgres.getJdbcUrl());
        props.setProperty("datanucleus.schema.autoCreateTables", "true");
    }

    @AfterAll static void tearDown() {
        TestPMFUtil.closePMF("context0");
    }

    private static Connection jdbc()
    throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword());
    }

    private static Set<String> query(String sql)
    throws Exception {
        Set<String> out = new TreeSet<String>();

        try (Connection c = jdbc();
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private static Shepherd shepherd() {
        Shepherd sh = new Shepherd("context0", props);

        sh.beginDBTransaction();
        return sh;
    }

    private static void commit(Shepherd sh) {
        assertTrue(sh.commitDBTransactionWithStatus());
        sh.closeDBTransaction();
    }

    private static String user(Shepherd sh, String name) {
        User u = new User(name, "pw", "salt");

        sh.getPM().makePersistent(u);
        return u.getId();
    }

    private static String org(Shepherd sh, String name) {
        Organization o = new Organization(name);

        sh.getPM().makePersistent(o);
        return o.getId();
    }

    private static Set<String> rows()
    throws Exception {
        return query("SELECT o.\"NAME\" || ':' || u.\"USERNAME\" FROM \"ORGANIZATION_MEMBERS\" m " +
                "JOIN \"ORGANIZATION\" o ON o.\"ID\" = m.\"ORGANIZATION_ID\" " +
                "JOIN \"USERS\" u ON u.\"UUID\" = m.\"USER_ID\"");
    }

    private static Set<String> set(String... s) {
        return new TreeSet<String>(Arrays.asList(s));
    }

    private static Set<String> xy()
    throws Exception {
        Set<String> out = rows("X:");

        out.addAll(rows("Y:"));
        return out;
    }

    @Test void membershipAddsRemovesAndReloads()
    throws Exception {
        Shepherd sh = shepherd();
        String x = org(sh, "X"), y = org(sh, "Y");
        String a = user(sh, "a"), b = user(sh, "b"), c = user(sh, "c"), d = user(sh, "d"),
            e = user(sh, "e");

        commit(sh);

        // no position column; keyed on the pair, which the migration script reproduces on old schemas
        assertEquals(set("ORGANIZATION_ID", "USER_ID"),
            query(
            "SELECT column_name FROM information_schema.columns WHERE table_name = 'ORGANIZATION_MEMBERS'"));
        assertEquals(set("ORGANIZATION_MEMBERS_PK PRIMARY KEY (\"ORGANIZATION_ID\", \"USER_ID\")"),
            query("SELECT conname || ' ' || pg_get_constraintdef(oid) FROM pg_constraint " +
            "WHERE conrelid = '\"ORGANIZATION_MEMBERS\"'::regclass AND contype = 'p'"));
        sh = shepherd();
        assertEquals(null, OrganizationMembershipSchema.problem(sh)); // the startup check accepts it
        commit(sh);
        // an unmigrated same-named table in another schema is not the one DataNucleus uses
        try (Connection conn = jdbc();
        Statement st = conn.createStatement()) {
            st.execute("CREATE SCHEMA other; CREATE TABLE other.\"ORGANIZATION_MEMBERS\" " +
                "(\"ORGANIZATION_ID\" varchar, \"USER_ID\" varchar, \"IDX\" integer NOT NULL, " +
                "PRIMARY KEY (\"ORGANIZATION_ID\", \"IDX\"))");
        }
        sh = shepherd();
        assertEquals(null, OrganizationMembershipSchema.problem(sh));
        commit(sh);
        // each add in its own transaction, as /UserCreate does; every one is each user's first org
        for (String uid : new String[] { a, b, c }) {
            sh = shepherd();
            Organization ox = Organization.load(x, sh);
            ox.addMember(sh.getUserByUUID(uid));
            commit(sh);
        }
        // d joins Y first, then X (the other side of the old collision)
        sh = shepherd();
        Organization.load(y, sh).addMember(sh.getUserByUUID(d));
        commit(sh);
        sh = shepherd();
        List<User> bulk = new ArrayList<User>();
        bulk.add(sh.getUserByUUID(d));
        bulk.add(sh.getUserByUUID(e));
        Organization.load(x, sh).addMembers(bulk); // as /OrganizationEdit addUsers
        commit(sh);
        // the user-side entry point, and a repeat add, are no-ops for existing memberships
        sh = shepherd();
        sh.getUserByUUID(a).addOrganization(Organization.load(x, sh));
        Organization.load(x, sh).addMember(sh.getUserByUUID(b));
        commit(sh);
        assertEquals(set("X:a", "X:b", "X:c", "X:d", "X:e", "Y:d"), xy());

        // reload in a fresh persistence manager with an empty level-2 cache
        sh = shepherd();
        sh.getPM().getPersistenceManagerFactory().getDataStoreCache().evictAll();
        Set<String> names = new HashSet<String>();
        for (User u : Organization.load(x, sh).getMembers()) names.add(u.getUsername());
        assertEquals(new HashSet<String>(Arrays.asList("a", "b", "c", "d", "e")), names);
        Set<String> dOrgs = new HashSet<String>();
        for (Organization o : sh.getUserByUUID(d).getOrganizations()) dOrgs.add(o.getName());
        assertEquals(new HashSet<String>(Arrays.asList("X", "Y")), dOrgs);
        commit(sh);

        // remove, then re-add the same member
        sh = shepherd();
        Organization.load(x, sh).removeMember(sh.getUserByUUID(b));
        commit(sh);
        assertEquals(set("X:a", "X:c", "X:d", "X:e", "Y:d"), xy());
        sh = shepherd();
        Organization.load(x, sh).addMember(sh.getUserByUUID(b));
        commit(sh);
        assertEquals(set("X:a", "X:b", "X:c", "X:d", "X:e", "Y:d"), xy());
    }

    private static Set<String> rows(String orgPrefix)
    throws Exception {
        Set<String> out = new TreeSet<String>();

        for (String r : rows()) if (r.startsWith(orgPrefix)) out.add(r);
        return out;
    }

    private static Set<String> names(List<?> list) {
        Set<String> out = new TreeSet<String>();

        if (list == null) return out;
        for (Object o : list)
            out.add(o instanceof User ? ((User)o).getUsername() : ((Organization)o).getName());
        return out;
    }

    private static Shepherd reloaded() {
        Shepherd sh = shepherd();

        sh.getPM().getPersistenceManagerFactory().getDataStoreCache().evictAll();
        return sh;
    }

    @Test void userSideAddAndRemove()
    throws Exception {
        Shepherd sh = shepherd();
        String o = org(sh, "U1"), u = user(sh, "u1");

        commit(sh);
        sh = shepherd();
        sh.getUserByUUID(u).addOrganization(Organization.load(o, sh)); // u1's first organization
        commit(sh);
        assertEquals(set("U1:u1"), rows("U1"));
        sh = reloaded();
        assertEquals(set("u1"), names(Organization.load(o, sh).getMembers()));
        assertEquals(set("U1"), names(sh.getUserByUUID(u).getOrganizations()));
        sh.getUserByUUID(u).removeOrganization(Organization.load(o, sh));
        commit(sh);
        assertEquals(set(), rows("U1"));
        sh = reloaded();
        assertEquals(set(), names(Organization.load(o, sh).getMembers()));
        assertEquals(set(), names(sh.getUserByUUID(u).getOrganizations()));
        commit(sh);
    }

    @Test void settersReplaceAndClearBothSides()
    throws Exception {
        Shepherd sh = shepherd();
        String o = org(sh, "S1"), s1 = user(sh, "s1"), s2 = user(sh, "s2"), s3 = user(sh, "s3");

        commit(sh);
        sh = shepherd();
        Organization org = Organization.load(o, sh);
        org.setMembers(new ArrayList<User>(Arrays.asList(sh.getUserByUUID(s1),
            sh.getUserByUUID(s2))));
        assertEquals(set("S1"), names(sh.getUserByUUID(s1).getOrganizations()));
        commit(sh);
        assertEquals(set("S1:s1", "S1:s2"), rows("S1"));

        sh = shepherd();
        org = Organization.load(o, sh);
        org.setMembers(new ArrayList<User>(Arrays.asList(sh.getUserByUUID(s2),
            sh.getUserByUUID(s3))));
        assertEquals(set(), names(sh.getUserByUUID(s1).getOrganizations())); // dropped side unlinked too
        assertEquals(set("S1"), names(sh.getUserByUUID(s3).getOrganizations()));
        commit(sh);
        assertEquals(set("S1:s2", "S1:s3"), rows("S1"));

        sh = reloaded();
        sh.getUserByUUID(s3).setOrganizations(new ArrayList<Organization>());
        assertEquals(set("s2"), names(Organization.load(o, sh).getMembers()));
        commit(sh);
        assertEquals(set("S1:s2"), rows("S1"));

        sh = shepherd();
        Organization.load(o, sh).setMembers(null);
        assertEquals(set(), names(sh.getUserByUUID(s2).getOrganizations()));
        commit(sh);
        assertEquals(set(), rows("S1"));
    }

    @Test void gettersReturnSortedCopies()
    throws Exception {
        Shepherd sh = shepherd();
        String o = org(sh, "P1"), pb = user(sh, "pB"), pa = user(sh, "pa");

        commit(sh);
        sh = shepherd();
        Organization org = Organization.load(o, sh);
        org.addMember(sh.getUserByUUID(pb));
        org.addMember(sh.getUserByUUID(pa));
        List<User> members = org.getMembers();
        assertEquals(Arrays.asList("pa", "pB"),
            Arrays.asList(members.get(0).getUsername(), members.get(1).getUsername()));
        members.clear(); // a copy: membership is unchanged
        sh.getUserByUUID(pa).getOrganizations().clear();
        assertTrue(org.hasMember(sh.getUserByUUID(pa)) && org.hasMember(sh.getUserByUUID(pb)));
        commit(sh);
        assertEquals(set("P1:pB", "P1:pa"), rows("P1"));
    }

    @Test void membershipQueryAfterReload()
    throws Exception {
        Shepherd sh = shepherd();
        String q1 = org(sh, "Q1"), q2 = org(sh, "Q2"), q3 = org(sh, "Q3"), u = user(sh, "q");

        commit(sh);
        sh = shepherd();
        Organization.load(q2, sh).addMember(sh.getUserByUUID(u));
        Organization.load(q1, sh).addMember(sh.getUserByUUID(u));
        commit(sh);
        sh = reloaded();
        // JDOQL members.contains(user), as the encounter/occurrence/individual searches use
        assertEquals(set("Q1", "Q2"), names(sh.getAllOrganizationsForUser(sh.getUserByUUID(u))));
        commit(sh);
    }

    @Test void nameKeyComesFromTheOldestKeyedOrganization()
    throws Exception {
        Shepherd sh = shepherd();
        Organization old = new Organization("N-old");

        old.setIndividualNameKey("OLDKEY");
        sh.getPM().makePersistent(old);
        Thread.sleep(5);
        Organization unkeyed = new Organization("N-none");
        sh.getPM().makePersistent(unkeyed);
        Thread.sleep(5);
        Organization young = new Organization("A-young"); // sorts first by name
        young.setIndividualNameKey("YOUNGKEY");
        sh.getPM().makePersistent(young);
        String oid = old.getId(), yid = young.getId(), nid = unkeyed.getId(), u = user(sh, "n");
        commit(sh);
        sh = shepherd();
        Organization.load(yid, sh).addMember(sh.getUserByUUID(u));
        Organization.load(nid, sh).addMember(sh.getUserByUUID(u));
        Organization.load(oid, sh).addMember(sh.getUserByUUID(u));
        assertEquals("OLDKEY", sh.getUserByUUID(u).getIndividualNameKey());
        Organization.load(oid, sh).setName("ZZZ"); // renaming does not change the choice
        commit(sh);
        sh = reloaded();
        assertEquals("OLDKEY", sh.getUserByUUID(u).getIndividualNameKey());
        commit(sh);
    }
}
