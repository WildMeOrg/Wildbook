package org.ecocean.servlet;

import org.datanucleus.api.jdo.JDOPersistenceManager;

import javax.jdo.PersistenceManager;
import javax.jdo.datastore.JDOConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SqlHelper {
    public static List<Object[]> executeRawSql(PersistenceManager pm, String sql) {
        JDOConnection jdoConn = null;
        Connection conn = null;
        PreparedStatement ps = null;
        ResultSet rs = null;
        try {
            // 1) Obtain JDOConnection and unwrap JDBC Connection
            jdoConn = ((JDOPersistenceManager) pm).getDataStoreConnection();
            conn = (Connection) jdoConn.getNativeConnection();

            // 2) Prepare your raw SQL (no ? placeholders)
            ps = conn.prepareStatement(sql);


            String sqlTrimmed = sql.trim().toLowerCase();
            if (sqlTrimmed.startsWith("select")) {
                // 3) Execute directly
                rs = ps.executeQuery();

                // 4) Read results
                int colCount = rs.getMetaData().getColumnCount();
                List<Object[]> results = new ArrayList<>();
                while (rs.next()) {
                    Object[] row = new Object[colCount];
                    for (int c = 1; c <= colCount; c++) {
                        row[c - 1] = rs.getObject(c);
                    }
                    results.add(row);
                }
                return results;
            } else {
                // 3) Execute directly
                ps.executeUpdate();
                return new ArrayList<>(); // No results for non-selects
            }

        } catch (Exception e) {
            throw new RuntimeException("Error executing raw SQL", e);
        } finally {
            // 5) Clean up JDBC resources
            try {
                if (rs != null) rs.close();
            } catch (Exception ignored) {
            }

            try {
                if (ps != null) ps.close();
            } catch (Exception ignored) {
            }

            // 6) Close JDOConnection (returns JDBC Connection to the pool)
            try {
                if (jdoConn != null) jdoConn.close();
            } catch (Exception ignored) {
            }
        }
    }
}
