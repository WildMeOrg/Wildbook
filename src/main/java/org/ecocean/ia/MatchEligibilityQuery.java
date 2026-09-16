package org.ecocean.ia;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.Query;

import org.ecocean.shepherd.core.Shepherd;

/**
 * Direct-SQL utility for the empty-match-prospects batch gate.
 * Returns the set of annotation IDs reachable from a collection of
 * media-asset IDs that pass ml-service match eligibility:
 * {@code matchAgainst=true}, {@code acmId IS NOT NULL}, and (when
 * the matchConfig provides them) have an EMBEDDING row for the
 * configured method/methodVersion.
 *
 * <p>Used by {@link MatchVisibilityGate} (Track 2 C10) to compute
 * the wait set Phase 2 hands to
 * {@link org.ecocean.OpenSearch#waitForAnnotationMatchableIds}.
 * The 4-table join (ANNOTATION + ANNOTATION_FEATURES +
 * MEDIAASSET_FEATURES + EMBEDDING) matches the same shape used at
 * {@code ImportTask.java:781} for repo precedent; direct SQL is
 * preferred over JDOQL here because the join goes through two
 * JDO-generated join tables plus a custom FK table.</p>
 *
 * <p>method/methodVersion match Annotation.getMatchQuery semantics
 * at Annotation.java:1205-1209: each predicate is added only when
 * the value is non-null. Both null skips the EMBEDDING join
 * entirely (legacy api_endpoint-only configs that can't derive a
 * method).</p>
 *
 * <p>(Empty-match-prospects design Track 2 C9.)</p>
 */
public final class MatchEligibilityQuery {

    private MatchEligibilityQuery() { }

    /**
     * Resolve the set of match-eligible annotation IDs whose media
     * asset is in {@code siblingMaIds}. The returned set preserves
     * insertion order from the underlying query (which uses
     * {@code SELECT DISTINCT}, so ordering is engine-dependent but
     * stable within a call).
     *
     * @param shep           open Shepherd. Caller manages the
     *                       transaction lifecycle; this method
     *                       neither begins nor commits.
     * @param siblingMaIds   integer MediaAsset IDs from
     *                       {@code topTask.getObjectMediaAssets()}.
     *                       An empty or null collection yields an
     *                       empty result without hitting the DB.
     * @param method         embedding method (e.g. "miewid-msv4.1");
     *                       null skips the predicate.
     * @param methodVersion  embedding method version (e.g. "4.1");
     *                       null skips the predicate.
     * @return distinct annotation IDs, never null.
     * @throws IOException if the underlying JDO/SQL query fails. The
     *         gate translates this to a DEFER outcome with a reason
     *         so an incomplete corpus can't silently green-light
     *         matching (Codex C9 review Major — avoids the v1
     *         "couldn't tell vs no work" ambiguity).
     */
    public static Set<String> findEligibleAnnotationIds(
        Shepherd shep,
        Collection<Integer> siblingMaIds,
        String method,
        String methodVersion)
    throws IOException {
        Set<String> ids = new LinkedHashSet<String>();
        if (siblingMaIds == null || siblingMaIds.isEmpty()) return ids;

        // Drop nulls / dedupe before building so the same normalized
        // input drives both production execution and test-side shape
        // verification through the shared buildSql.
        LinkedHashSet<Integer> normalized = new LinkedHashSet<Integer>();
        for (Integer id : siblingMaIds) {
            if (id != null) normalized.add(id);
        }
        if (normalized.isEmpty()) return ids;

        String sql = buildSql(normalized, method, methodVersion);

        Query q = null;
        try {
            q = shep.getPM().newQuery("javax.jdo.query.SQL", sql);
            @SuppressWarnings("rawtypes")
            List rows = (List) q.execute();
            if (rows != null) {
                for (Object row : rows) {
                    // SELECT-of-one returns the scalar directly, not an
                    // Object[]. Defensive cast on both shapes.
                    String annId = scalarOrFirstColumn(row);
                    if (annId != null) ids.add(annId);
                }
            }
        } catch (Exception ex) {
            // Don't return a quietly-empty set on failure — that would
            // look indistinguishable from "no eligible siblings" and
            // silently green-light matching against an incomplete
            // corpus. Propagate so the gate can DEFER with a reason.
            throw new IOException(
                "MatchEligibilityQuery.findEligibleAnnotationIds() failed: " +
                ex.getMessage(), ex);
        } finally {
            if (q != null) q.closeAll();
        }
        return ids;
    }

    /**
     * Some JDO query backends return single-column rows as raw
     * scalars, others wrap them in a 1-element {@code Object[]}.
     * Handle both shapes defensively.
     */
    private static String scalarOrFirstColumn(Object row) {
        if (row == null) return null;
        if (row instanceof String) return (String) row;
        if (row instanceof Object[]) {
            Object[] arr = (Object[]) row;
            if (arr.length == 0) return null;
            Object v = arr[0];
            return (v == null) ? null : v.toString();
        }
        return row.toString();
    }

    /**
     * Escape a SQL string literal for inline interpolation. Method
     * and methodVersion come from admin-controlled IA.json, so SQL
     * injection isn't a typical risk, but this lets the helper hold
     * up if a future call site sources these from less-trusted input.
     */
    private static String sqlEscape(String s) {
        return s.replace("'", "''");
    }

    /**
     * Shared SQL builder. Called by {@link #findEligibleAnnotationIds}
     * in production AND by the unit tests, so test assertions on the
     * generated SQL match the SQL the database actually executes
     * (Codex C9 review Minor follow-up).
     *
     * <p>Package-visible.</p>
     */
    static String buildSql(Collection<Integer> siblingMaIds, String method,
        String methodVersion) {
        if (siblingMaIds == null) siblingMaIds = new ArrayList<Integer>();
        StringBuilder inList = new StringBuilder();
        int n = 0;
        for (Integer id : siblingMaIds) {
            if (id == null) continue;
            if (n > 0) inList.append(",");
            inList.append(id.intValue());
            n++;
        }
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT DISTINCT a.\"ID\" ");
        sql.append("FROM \"ANNOTATION\" a ");
        sql.append("JOIN \"ANNOTATION_FEATURES\" af ON af.\"ID_OID\" = a.\"ID\" ");
        sql.append("JOIN \"MEDIAASSET_FEATURES\" mf ON mf.\"ID_EID\" = af.\"ID_EID\" ");
        boolean joinEmbedding = method != null || methodVersion != null;
        if (joinEmbedding) {
            sql.append("JOIN \"EMBEDDING\" e ON e.\"ANNOTATION_ID\" = a.\"ID\" ");
        }
        sql.append("WHERE mf.\"ID_OID\" IN (").append(inList).append(") ");
        sql.append("AND a.\"MATCHAGAINST\" = true ");
        sql.append("AND a.\"ACMID\" IS NOT NULL ");
        if (method != null) {
            sql.append("AND e.\"METHOD\" = '").append(sqlEscape(method)).append("' ");
        }
        if (methodVersion != null) {
            sql.append("AND e.\"METHODVERSION\" = '")
                .append(sqlEscape(methodVersion)).append("' ");
        }
        return sql.toString();
    }
}
