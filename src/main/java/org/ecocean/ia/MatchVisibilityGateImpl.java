package org.ecocean.ia;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.jdo.Query;

import org.ecocean.OpenSearch;
import org.ecocean.Util;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.MediaAsset;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;

/**
 * Production {@link MatchVisibilityGate}. Phase 1 + 2 run under
 * Shepherd; Shepherd closes before Phase 3 (per c11 Phase A/B/C
 * pattern). All state needed by Phase 3 is captured as scalars.
 *
 * <p>(Empty-match-prospects design Track 2 C10.)</p>
 */
public final class MatchVisibilityGateImpl implements MatchVisibilityGate {

    /**
     * Visibility-poll timeout per phase (caller-id wait + sibling-id
     * wait each get this budget). Matches the visibility-wait
     * budget used by the c7 deferred-match path elsewhere.
     */
    static final long VISIBILITY_TIMEOUT_MS = 15L * 1000L;

    // MediaAsset.detectionStatus values considered terminal — sibling
    // is done contributing (success or otherwise), don't wait. The set
    // mirrors IBEISIA constants at IBEISIA.java:73-82 (Codex round-3 OQ #3).
    private static final Set<String> TERMINAL_MA_STATUSES =
        new HashSet<String>(Arrays.asList(
            IBEISIA.STATUS_COMPLETE,
            IBEISIA.STATUS_COMPLETE_MLSERVICE,
            IBEISIA.STATUS_PENDING,
            IBEISIA.STATUS_PENDING_SPECIES,
            IBEISIA.STATUS_ERROR,
            IBEISIA.STATUS_DROPPED_STALE));

    // Task.status values considered terminal — child task is done
    // contributing. Note "completed" (Task) vs "complete" (MA);
    // see Task.statusInEndState() at Task.java:85 (Codex round-4 Major).
    private static final Set<String> TERMINAL_TASK_STATUSES =
        new HashSet<String>(Arrays.asList(
            "completed",
            "error",
            "dropped-stale"));

    // MA detection statuses considered "produced annotations" — only
    // these contribute to Phase 2's eligibility set. Other terminal
    // statuses (ERROR, DROPPED_STALE, PENDING_SPECIES) contributed
    // nothing.
    private static final Set<String> CONTRIBUTING_MA_STATUSES =
        new HashSet<String>(Arrays.asList(
            IBEISIA.STATUS_COMPLETE,
            IBEISIA.STATUS_COMPLETE_MLSERVICE));

    private final String context;
    private final OpenSearch openSearch;

    public MatchVisibilityGateImpl(String context) {
        this(context, new OpenSearch());
    }

    /** Package-visible: tests inject a mocked OpenSearch. */
    MatchVisibilityGateImpl(String context, OpenSearch openSearch) {
        this.context = context;
        this.openSearch = openSearch;
    }

    @Override
    public GateOutcome gateForBatch(
        Collection<String> callerAnnotationIds,
        String childTaskId,
        JSONObject matchConfig,
        int attempt,
        Long firstDeferredAt) {

        long deferStart = (firstDeferredAt == null)
            ? System.currentTimeMillis()
            : firstDeferredAt.longValue();
        if (System.currentTimeMillis() - deferStart > MAX_DEFER_AGE_MILLIS) {
            return GateOutcome.giveUp(attempt, deferStart,
                "exceeded MAX_DEFER_AGE_MILLIS=" + MAX_DEFER_AGE_MILLIS);
        }

        // Normalize caller ids: drop nulls + dedupe.
        Set<String> normalizedCaller = new LinkedHashSet<String>();
        if (callerAnnotationIds != null) {
            for (String id : callerAnnotationIds) {
                if (id != null) normalizedCaller.add(id);
            }
        }

        // Derive method/methodVersion from matchConfig using the same
        // fallback chain as Embedding.findMatchProspects
        // (Embedding.java:349-355): _id_conf.method/version first,
        // then MLService.getMethodValues for legacy api_endpoint
        // configs.
        //
        // Whatever falls out of the chain is what we use, including
        // blank strings. The downstream helpers (C8
        // waitForAnnotationMatchableIds, C9 MatchEligibilityQuery, and
        // Annotation.getMatchQuery itself at Annotation.java:1205-1209)
        // are strict-when-non-null; normalizing blank to null here
        // would silently broaden the gate's wait predicate vs the
        // matcher's strict match, causing the gate to declare READY
        // for docs the matcher then rejects (Codex round-1 C10
        // Major).
        String method = (matchConfig == null) ? null
            : matchConfig.optString("method", null);
        String methodVersion = (matchConfig == null) ? null
            : matchConfig.optString("version", null);
        if (!Util.stringExists(method) && matchConfig != null) {
            String[] mv = MLService.getMethodValues(matchConfig);
            method = (mv == null) ? null : mv[0];
            methodVersion = (mv == null) ? null : mv[1];
        }

        // ---- Phase 1 + 2: under Shepherd ---------------------------
        Phase12Result phase12;
        try {
            phase12 = loadPhase12(childTaskId, method, methodVersion,
                normalizedCaller);
        } catch (IOException ex) {
            // SQL failure during eligibility resolution — DEFER with
            // reason rather than silently proceed against an unknown
            // sibling set (Codex C9 Major).
            return GateOutcome.defer(attempt, deferStart,
                "Phase2 SQL failed: " + ex.getMessage());
        }
        if (phase12.deferReason != null) {
            return GateOutcome.defer(attempt, deferStart, phase12.deferReason);
        }

        // ---- Phase 3: visibility, no Shepherd held ----------------
        // Wait for caller IDs first using the weaker _id predicate.
        // If a caller annotation is visible by _id but lacks
        // matchAgainst/acmId/embedding metadata, that's a different
        // problem (no candidates returned); we don't want to block
        // the gate on it.
        try {
            if (!normalizedCaller.isEmpty() &&
                !openSearch.waitForVisibility("annotation", normalizedCaller,
                    VISIBILITY_TIMEOUT_MS)) {
                return GateOutcome.defer(attempt, deferStart,
                    "caller IDs not yet visible in OS");
            }
        } catch (IOException ex) {
            return GateOutcome.defer(attempt, deferStart,
                "caller visibility poll IOException: " + ex.getMessage());
        }

        // Sibling-only set: drop caller IDs from the eligibility set
        // since they're already waited for above with the weaker
        // predicate. Then wait on the remainder with the full
        // matchable predicate.
        Set<String> siblingsOnly = new LinkedHashSet<String>(phase12.eligibleIds);
        siblingsOnly.removeAll(normalizedCaller);
        if (!siblingsOnly.isEmpty()) {
            try {
                if (!openSearch.waitForAnnotationMatchableIds(siblingsOnly,
                    method, methodVersion, VISIBILITY_TIMEOUT_MS)) {
                    return GateOutcome.defer(attempt, deferStart,
                        "sibling IDs not yet matchable in OS");
                }
            } catch (IOException ex) {
                return GateOutcome.defer(attempt, deferStart,
                    "sibling matchable-visibility poll IOException: " +
                    ex.getMessage());
            }
        }

        return GateOutcome.ready(attempt, deferStart);
    }

    /**
     * Phase 1 + 2 result carrier. Either {@code deferReason} is
     * non-null (Phase 1 said wait) or {@code eligibleIds} is the
     * Phase 2 eligibility set.
     */
    static final class Phase12Result {
        final String deferReason;
        final Set<String> eligibleIds;

        private Phase12Result(String deferReason, Set<String> eligibleIds) {
            this.deferReason = deferReason;
            this.eligibleIds = (eligibleIds == null)
                ? new LinkedHashSet<String>() : eligibleIds;
        }

        static Phase12Result defer(String reason) {
            return new Phase12Result(reason, null);
        }

        static Phase12Result ready(Set<String> eligibleIds) {
            return new Phase12Result(null, eligibleIds);
        }
    }

    /**
     * Run Phase 1 + 2 under Shepherd. The transaction closes before
     * we return; the result holds only detached scalars so callers
     * can do network IO without holding the connection.
     */
    Phase12Result loadPhase12(String childTaskId, String method,
        String methodVersion, Set<String> normalizedCaller)
    throws IOException {
        Shepherd shep = new Shepherd(context);
        shep.setAction("MatchVisibilityGate.gateForBatch.phase12." + childTaskId);
        try {
            shep.beginDBTransaction();
            Task child = (childTaskId == null) ? null
                : Task.load(childTaskId, shep);
            if (child == null) {
                // No child task to walk up from. Degrade to caller-only
                // visibility (Codex round-4 OQ #3: log WARN, not silent).
                System.out.println(
                    "WARN: MatchVisibilityGate gating with no child task; childTaskId=" +
                    childTaskId);
                shep.commitDBTransaction();
                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
            }
            Task topTask = child.getParent();
            if (topTask == null) {
                System.out.println(
                    "WARN: MatchVisibilityGate gating with no parent topTask; childTaskId=" +
                    childTaskId);
                shep.commitDBTransaction();
                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
            }
            List<MediaAsset> siblingMas = topTask.getObjectMediaAssets();
            if (siblingMas == null || siblingMas.isEmpty()) {
                shep.commitDBTransaction();
                return Phase12Result.ready(new LinkedHashSet<String>(normalizedCaller));
            }
            // Phase 1: sibling terminal-state check via single native
            // SQL query. Replaces the previous JDO graph fan-out
            // (topTask.getChildren() + per-child
            // getObjectMediaAssets() + per-sibling status access),
            // which on bulk imports cost ~10-20 SELECTs per gate
            // cycle × hundreds of defer cycles × 200+ annotations.
            // The native SQL faithfully models findChildTaskForSibling
            // (single-MA child rule: HAVING count == 1) and
            // isSiblingTerminal (terminal iff MA detectionStatus is
            // in TERMINAL_MA_STATUSES OR child task status is in
            // TERMINAL_TASK_STATUSES) so the gate semantics are
            // unchanged.
            String deferReason = firstNonTerminalSiblingReason(shep,
                topTask.getId());
            if (deferReason != null) {
                shep.commitDBTransaction();
                return Phase12Result.defer(deferReason);
            }
            // Phase 2: eligibility resolution. Only siblings whose MAs
            // actually contributed annotations (complete/complete-mlservice)
            // need to be in the eligibility set.
            List<Integer> contributingMaIds = new ArrayList<Integer>();
            for (MediaAsset siblingMa : siblingMas) {
                if (siblingMa == null) continue;
                if (CONTRIBUTING_MA_STATUSES.contains(siblingMa.getDetectionStatus())) {
                    contributingMaIds.add(Integer.valueOf(siblingMa.getIdInt()));
                }
            }
            Set<String> eligibleIds = MatchEligibilityQuery
                .findEligibleAnnotationIds(shep, contributingMaIds, method,
                    methodVersion);
            // Always include caller IDs in the wait set; the
            // eligibility filter may have excluded them (e.g.,
            // matchAgainst=false), but we must not block the caller's
            // own match on its own visibility (Codex round-4 Major).
            eligibleIds.addAll(normalizedCaller);
            shep.commitDBTransaction();
            return Phase12Result.ready(eligibleIds);
        } catch (IOException ex) {
            shep.rollbackDBTransaction();
            throw ex;
        } catch (Exception ex) {
            shep.rollbackDBTransaction();
            throw new IOException(
                "MatchVisibilityGate Phase 1+2 failed: " + ex.getMessage(), ex);
        } finally {
            shep.closeDBTransaction();
        }
    }

    /**
     * Find the per-MA child task by iterating
     * {@code topTask.getChildren()} directly (Codex round-3 Major:
     * NOT {@code Task.getTasksFor(ma)}, which returns any task
     * containing the MA, including the topTask itself or old
     * unrelated tasks).
     *
     * <p>Match by {@code .equals()} (Codex round-4 Major:
     * {@code MediaAsset.getId()} returns String, not int).</p>
     */
    static Task findChildTaskForSibling(List<Task> children, MediaAsset siblingMa) {
        if (children == null || siblingMa == null) return null;
        String maId = siblingMa.getId();
        if (maId == null) return null;
        for (Task child : children) {
            List<MediaAsset> mas = child.getObjectMediaAssets();
            if (mas != null && mas.size() == 1 &&
                maId.equals(mas.get(0).getId())) {
                return child;
            }
        }
        return null;
    }

    /**
     * A sibling is treated as terminal when EITHER its MediaAsset
     * detectionStatus OR its child Task's stored status is in the
     * terminal set. Several failure paths in MlServiceProcessor
     * mark the child Task error WITHOUT advancing
     * MediaAsset.detectionStatus, so MA-only evaluation misses
     * real terminal states (Codex round-2 Blocker/Major).
     */
    static boolean isSiblingTerminal(String maStatus, String childTaskStatus) {
        if (maStatus != null && TERMINAL_MA_STATUSES.contains(maStatus)) return true;
        if (childTaskStatus != null &&
            TERMINAL_TASK_STATUSES.contains(childTaskStatus)) return true;
        return false;
    }

    /**
     * Native-SQL implementation of the Phase 1 sibling-terminal
     * check. Single round-trip per gate cycle replaces the JDO
     * graph fan-out described in the caller.
     *
     * Returns null when all siblings of {@code topTaskId} are
     * terminal (gate may proceed). Returns a defer-reason string
     * matching the legacy log format
     * ({@code "sibling MA X non-terminal (ma=Y, task=Z)"}) when at
     * least one sibling is non-terminal.
     *
     * <p>The CTE {@code single_ma_children} models
     * {@link #findChildTaskForSibling}: only children of topTask
     * with EXACTLY one row in their {@code TASK_OBJECTMEDIAASSETS}
     * count, and that MA must be the sibling MA. This excludes
     * match tasks and any other non-detection children of topTask
     * (Codex Phase-1-SQL design review Blocker — preserved
     * exactly).</p>
     *
     * <p>Terminal status values are bound as positional parameters
     * sourced from {@link #TERMINAL_MA_STATUSES} and
     * {@link #TERMINAL_TASK_STATUSES} — no string-concat — so the
     * SQL stays in sync if those constants change (Codex Phase-1-
     * SQL Major).</p>
     *
     * <p><b>Multi-match edge case</b>: under healthy data the
     * model guarantees at most one single-MA detection child per
     * sibling MA. If malformed data ever produces multiple such
     * children, the SQL is existential — ANY matching child whose
     * status is non-terminal causes a defer. The Java
     * implementation returned the first child via
     * {@code topTask.getChildren()} iteration order, so behavior
     * could differ on bad data. The SQL is the safer direction
     * (over-defer, never under-defer) (Codex Phase-1-SQL Minor).</p>
     */
    static String firstNonTerminalSiblingReason(Shepherd shep, String topTaskId) {
        List<String> maStatuses = new ArrayList<String>(TERMINAL_MA_STATUSES);
        List<String> taskStatuses = new ArrayList<String>(TERMINAL_TASK_STATUSES);
        String sql = buildSiblingTerminalCheckSql(maStatuses.size(), taskStatuses.size());
        // Bind order (positional, matching the SQL):
        //   1. topTaskId — CTE WHERE
        //   2. topTaskId — main WHERE
        //   3..N MA terminal statuses
        //   N+1..M Task terminal statuses
        Object[] params = new Object[2 + maStatuses.size() + taskStatuses.size()];
        int p = 0;
        params[p++] = topTaskId;
        params[p++] = topTaskId;
        for (String s : maStatuses) params[p++] = s;
        for (String s : taskStatuses) params[p++] = s;

        Query q = null;
        try {
            q = shep.getPM().newQuery("javax.jdo.query.SQL", sql);
            @SuppressWarnings("rawtypes")
            List rows = (List) q.executeWithArray(params);
            if (rows == null || rows.isEmpty()) return null;
            Object first = rows.get(0);
            // Native SQL returning >1 column gives Object[]; single
            // column gives a scalar. We always select 3 columns.
            Object[] row = (first instanceof Object[]) ? (Object[]) first
                : new Object[] { first, null, null };
            return "sibling MA " + row[0] +
                " non-terminal (ma=" + row[1] + ", task=" + row[2] + ")";
        } finally {
            if (q != null) q.closeAll();
        }
    }

    /**
     * Builds the parameterized SQL string for
     * {@link #firstNonTerminalSiblingReason}. Placeholders for the
     * terminal-status IN-lists are generated from the actual size
     * of the constant sets at call time so the SQL stays in sync
     * with {@link #TERMINAL_MA_STATUSES} /
     * {@link #TERMINAL_TASK_STATUSES}.
     */
    static String buildSiblingTerminalCheckSql(int numMaStatuses, int numTaskStatuses) {
        // Guard against empty IN-lists: "NOT IN ()" would be a SQL
        // syntax error. The constant sets are non-empty by design,
        // but a future refactor that empties one would otherwise
        // fail at runtime, not build (Codex Phase-1-SQL Nit).
        if (numMaStatuses <= 0 || numTaskStatuses <= 0) {
            throw new IllegalArgumentException(
                "buildSiblingTerminalCheckSql requires non-empty status sets: ma=" +
                numMaStatuses + " task=" + numTaskStatuses);
        }
        StringBuilder maPh = new StringBuilder();
        for (int i = 0; i < numMaStatuses; i++) {
            if (i > 0) maPh.append(",");
            maPh.append("?");
        }
        StringBuilder taskPh = new StringBuilder();
        for (int i = 0; i < numTaskStatuses; i++) {
            if (i > 0) taskPh.append(",");
            taskPh.append("?");
        }
        // PostgreSQL-targeted SQL. Wildbook ships against PostgreSQL
        // 13 only; tests use Testcontainers with a real PostgreSQL.
        // If a non-Postgres backend is ever introduced, this query
        // needs portable rewriting (Codex Phase-1-SQL Major).
        //
        // The single-MA child filter is expressed as an inline
        // subquery, NOT a CTE. DataNucleus' native-SQL gateway
        // rejects statements that don't start with "SELECT" (it
        // refuses "WITH ..." outright with
        // "You have specified an SQL statement (..) that doesnt
        // start with SELECT. This is invalid.") — observed in a
        // bulk-import run as a 100% gate-defer cascade. The
        // subquery form is semantically identical and starts with
        // SELECT (Codex Phase-1-SQL hotfix).
        return "SELECT topOma.\"ID_EID\" AS ma_id," +
            "       siblingMa.\"DETECTIONSTATUS\" AS ma_status," +
            "       childTask.\"STATUS\" AS child_status " +
            "FROM \"TASK_OBJECTMEDIAASSETS\" topOma " +
            "JOIN \"MEDIAASSET\" siblingMa" +
            "  ON siblingMa.\"ID\" = topOma.\"ID_EID\" " +
            "LEFT JOIN (" +
            "  SELECT tc.\"ID_EID\" AS child_id," +
            "         max(childOma.\"ID_EID\") AS sole_ma_id" +
            "  FROM \"TASK_CHILDREN\" tc" +
            "  JOIN \"TASK_OBJECTMEDIAASSETS\" childOma" +
            "    ON childOma.\"ID_OID\" = tc.\"ID_EID\"" +
            "  WHERE tc.\"ID_OID\" = ?" +
            "  GROUP BY tc.\"ID_EID\"" +
            "  HAVING count(*) = 1" +
            ") smc" +
            "  ON smc.sole_ma_id = topOma.\"ID_EID\" " +
            "LEFT JOIN \"TASK\" childTask" +
            "  ON childTask.\"ID\" = smc.child_id " +
            "WHERE topOma.\"ID_OID\" = ? " +
            "  AND (siblingMa.\"DETECTIONSTATUS\" IS NULL" +
            "       OR siblingMa.\"DETECTIONSTATUS\" NOT IN (" + maPh + ")) " +
            "  AND (childTask.\"STATUS\" IS NULL" +
            "       OR childTask.\"STATUS\" NOT IN (" + taskPh + ")) " +
            "LIMIT 1";
    }
}
