package org.ecocean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
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
 * archive/sql/organization_members_pk.sql on a table in the old shape (an IDX column, keyed on
 * (ORGANIZATION_ID, IDX), a duplicated membership, a row with no user): afterwards the table matches what DataNucleus
 * creates today, every membership survives once, re-running changes nothing, and members can be
 * added and removed again.
 */
@Testcontainers class OrganizationMembershipMigrationDbTest {
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

    private static void execute(String sql)
    throws Exception {
        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement()) {
            st.execute(sql);
        }
    }

    private static Set<String> query(String sql)
    throws Exception {
        Set<String> out = new TreeSet<String>();

        try (Connection c = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
        Statement st = c.createStatement();
        ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) out.add(rs.getString(1));
        }
        return out;
    }

    private static Set<String> set(String... s) {
        return new TreeSet<String>(Arrays.asList(s));
    }

    private static Set<String> rows()
    throws Exception {
        return query("SELECT o.\"NAME\" || ':' || u.\"USERNAME\" FROM \"ORGANIZATION_MEMBERS\" m " +
                "JOIN \"ORGANIZATION\" o ON o.\"ID\" = m.\"ORGANIZATION_ID\" " +
                "JOIN \"USERS\" u ON u.\"UUID\" = m.\"USER_ID\"");
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

    private static String q(String s) {
        return "'" + s + "'";
    }

    @Test void migratesTheOldShapeAndKeepsEveryMembership()
    throws Exception {
        Shepherd sh = shepherd();
        Organization x = new Organization("X"), y = new Organization("Y");
        User a = new User("a", "pw", "salt"), b = new User("b", "pw", "salt"),
            c = new User("c", "pw", "salt");

        for (Object o : new Object[] { x, y, a, b, c }) sh.getPM().makePersistent(o);
        String xid = x.getId(), yid = y.getId(), aid = a.getId(), bid = b.getId(), cid = c.getId();
        commit(sh);

        // the old shape, as found on staging: IDX column, key (ORGANIZATION_ID, IDX) named _pkey
        execute(
            "ALTER TABLE \"ORGANIZATION_MEMBERS\" DROP CONSTRAINT \"ORGANIZATION_MEMBERS_PK\";" +
            "ALTER TABLE \"ORGANIZATION_MEMBERS\" ADD COLUMN \"IDX\" INTEGER NOT NULL DEFAULT 0;" +
            "ALTER TABLE \"ORGANIZATION_MEMBERS\" ALTER COLUMN \"IDX\" DROP DEFAULT;" +
            "ALTER TABLE \"ORGANIZATION_MEMBERS\" ADD CONSTRAINT \"ORGANIZATION_MEMBERS_pkey\" " +
            "PRIMARY KEY (\"ORGANIZATION_ID\", \"IDX\");" +
            "ALTER TABLE \"ORGANIZATION_MEMBERS\" ALTER COLUMN \"USER_ID\" DROP NOT NULL;" +
            "INSERT INTO \"ORGANIZATION_MEMBERS\" (\"ORGANIZATION_ID\", \"USER_ID\", \"IDX\") VALUES "
            + "(" + q(xid) + "," + q(aid) + ",0), (" + q(xid) + "," + q(bid) + ",1), " + "(" +
            q(xid) + "," + q(aid) + ",2), (" + q(yid) + "," + q(aid) + ",1), (" + q(xid) +
            ",NULL,3)");

        sh = shepherd();
        assertEquals("ORGANIZATION_MEMBERS still has the IDX column",
            OrganizationMembershipSchema.problem(sh));
        commit(sh);

        String script = new String(Files.readAllBytes(Paths.get(
            "archive/sql/organization_members_pk.sql")), StandardCharsets.UTF_8);
        for (int run = 0; run < 2; run++) {
            execute(script);
            assertEquals(set("ORGANIZATION_ID", "USER_ID"),
                query(
                "SELECT column_name FROM information_schema.columns WHERE table_name = 'ORGANIZATION_MEMBERS'"));
            assertEquals(set(
                "ORGANIZATION_MEMBERS_PK PRIMARY KEY (\"ORGANIZATION_ID\", \"USER_ID\")"),
                query("SELECT conname || ' ' || pg_get_constraintdef(oid) FROM pg_constraint " +
                "WHERE conrelid = '\"ORGANIZATION_MEMBERS\"'::regclass AND contype = 'p'"));
            assertEquals(set("X:a", "X:b", "Y:a"), rows());
            assertEquals(set("3"), query("SELECT count(*) FROM \"ORGANIZATION_MEMBERS\"")); // null row gone
        }
        sh = shepherd();
        assertEquals(null, OrganizationMembershipSchema.problem(sh));
        commit(sh);

        // c's first organization is X: the add that used to collide with a at IDX 0
        sh = shepherd();
        sh.getPM().getPersistenceManagerFactory().getDataStoreCache().evictAll();
        Organization.load(xid, sh).addMember(sh.getUserByUUID(cid));
        commit(sh);
        sh = shepherd();
        Organization.load(xid, sh).removeMember(sh.getUserByUUID(bid));
        Organization.load(yid, sh).addMember(sh.getUserByUUID(bid));
        commit(sh);
        assertEquals(set("X:a", "X:c", "Y:a", "Y:b"), rows());
    }
}
