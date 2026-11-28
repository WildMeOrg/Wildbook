package org.ecocean;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import javax.jdo.datastore.JDOConnection;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Startup check that ORGANIZATION_MEMBERS has been migrated to one row per (organization, user).
 * DataNucleus creates the new shape on a fresh database but never alters an existing table, and on
 * an unmigrated one every membership insert fails (the old IDX column is NOT NULL). The fix is
 * archive/sql/organization_members_pk.sql.
 */
public final class OrganizationMembershipSchema {
    static final String SCRIPT = "archive/sql/organization_members_pk.sql";

    private OrganizationMembershipSchema() {}

    /** null when the table is missing (DataNucleus will create it) or already migrated. */
    public static String problem(Shepherd sh)
    throws SQLException {
        JDOConnection jc = sh.getPM().getDataStoreConnection();

        try {
            Connection c = (Connection)jc.getNativeConnection();
            // the table DataNucleus actually uses (first on the search_path), not same-named ones elsewhere
            String table = null;
            try (PreparedStatement st = c.prepareStatement(
                "SELECT to_regclass('\"ORGANIZATION_MEMBERS\"')::oid");
            ResultSet rs = st.executeQuery()) {
                if (rs.next()) table = rs.getString(1);
            }
            if (table == null) return null;
            if (count(c,
                "SELECT count(*) FROM pg_attribute WHERE attrelid = " + Long.parseLong(table) +
                " AND attname = 'IDX' AND NOT attisdropped") > 0)
                return "ORGANIZATION_MEMBERS still has the IDX column";
            String pk = null;
            try (PreparedStatement st = c.prepareStatement(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint " + "WHERE conrelid = " +
                Long.parseLong(table) + " AND contype = 'p'");
            ResultSet rs = st.executeQuery()) {
                if (rs.next()) pk = rs.getString(1);
            }
            if (!"PRIMARY KEY (\"ORGANIZATION_ID\", \"USER_ID\")".equals(pk))
                return "ORGANIZATION_MEMBERS primary key is " + pk +
                           ", not (ORGANIZATION_ID, USER_ID)";
            return null;
        } finally {
            jc.close();
        }
    }

    private static int count(Connection c, String sql)
    throws SQLException {
        try (PreparedStatement st = c.prepareStatement(sql);
        ResultSet rs = st.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    public static void check(String context) {
        Shepherd sh = new Shepherd(context);

        sh.setAction("OrganizationMembershipSchema.check");
        sh.beginDBTransaction();
        try {
            String problem = problem(sh);
            if (problem != null) {
                System.out.println("ERROR: " + problem +
                    "; adding organization members will fail until " + SCRIPT +
                    " is run (stop Wildbook first)");
            }
        } catch (Exception e) {
            System.out.println(
                "WARNING: OrganizationMembershipSchema.check could not inspect the schema: " + e);
        } finally {
            sh.rollbackAndClose();
        }
    }
}
