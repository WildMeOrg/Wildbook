package org.ecocean.ia;

import java.util.ArrayList;
import java.util.List;

import javax.jdo.Query;

import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;

/**
 * Bulk-import boxes PR A (spec 4.4), crash recovery at parity with the ml-service detection
 * path: once per startup, every imported-box extraction child that is still non-terminal after
 * the stale threshold has its queue message republished. The FileQueue acknowledges a job before
 * processing it, so a JVM crash mid-extraction leaves the child with its job consumed; the
 * republished job either extracts, or finds the embedding already stored and goes on to match.
 *
 * <p>Duplicate-tolerant by design: a child whose job is merely still queued is republished too.
 * No pending-job check is attempted; task age alone cannot establish one.</p>
 */
public final class ImportedBoxReplay {
    public static final int MAX_PER_RUN = 500;
    // bound on candidate rows examined per run, so rows the text match catches but that are not
    // replayable (no resume message, or the marker is not the top-level flag) cannot stall startup
    static final int MAX_SCANNED = 20 * MAX_PER_RUN;
    static final String MARKER = "\"" + Task.PARAM_IMPORTED_BOX + "\":true";

    private ImportedBoxReplay() {}

    /**
     * The resume messages of non-terminal imported-box children modified before the cutoff, oldest
     * first, at most {@code max}. The text match is only a prefilter, so candidates are read in
     * pages and only real imported-box children with a resume message count toward {@code max}.
     */
    static List<String> fetchStaleResumeMessages(Shepherd shep, long modifiedCutoff, int max) {
        return fetchStaleResumeMessages(shep, modifiedCutoff, max, MAX_SCANNED);
    }

    static List<String> fetchStaleResumeMessages(Shepherd shep, long modifiedCutoff, int max,
        int maxScanned) {
        List<String> messages = new ArrayList<String>();
        int limit = Math.max(1, max);
        // the marker is a JSON fragment; DataNucleus supports String.indexOf in JDOQL (see MetricsBot)
        String jdoql = "SELECT FROM org.ecocean.ia.Task WHERE " +
            "(status == null || (status != 'completed' && status != 'error' && status != 'dropped-stale'))" +
            " && modified < " + modifiedCutoff +
            " && parameters.indexOf('" + MARKER + "') > -1";
        long offset = 0;
        while ((messages.size() < limit) && (offset < maxScanned)) {
            int pageSize = (int)Math.min(limit, maxScanned - offset);   // never past the scan bound
            Query q = shep.getPM().newQuery(jdoql);
            int rows = 0;
            try {
                q.setOrdering("modified ascending, id ascending");   // stable across pages
                q.setRange(offset, offset + pageSize);
                @SuppressWarnings("unchecked")
                List<Task> page = (List<Task>)q.execute();
                if (page != null) {
                    for (Task t : page) {
                        rows++;
                        // the marker must be the real top-level flag, not text found anywhere
                        if (!Task.isImportedBoxTask(t)) continue;
                        String msg = t.getQueueResumeMessage();
                        if ((msg != null) && (messages.size() < limit)) messages.add(msg);
                    }
                }
            } finally {
                q.closeAll();
            }
            if (rows < pageSize) break;   // no further candidates
            offset += rows;
        }
        return messages;
    }

    /** Republishes every stale message through the lane classifier; returns how many were published. */
    public static int replay(String context, long modifiedCutoff) {
        List<String> messages;
        Shepherd shep = new Shepherd(context);
        shep.setAction("ImportedBoxReplay.replay");
        try {
            shep.beginDBTransaction();
            messages = fetchStaleResumeMessages(shep, modifiedCutoff, MAX_PER_RUN);
        } catch (Exception ex) {
            System.out.println("WARN: ImportedBoxReplay.replay() query failed: " + ex);
            return 0;
        } finally {
            shep.rollbackAndClose();
        }
        int published = 0;
        for (String msg : messages) {
            try {
                if (IAGateway.publishToLane(context, msg)) published++;
                else System.out.println("WARN: ImportedBoxReplay.replay() publish returned false for " + msg);
            } catch (Exception ex) {
                System.out.println("WARN: ImportedBoxReplay.replay() publish failed: " + ex);
            }
        }
        System.out.println("[INFO] ImportedBoxReplay: republished " + published + " of " + messages.size() +
            " stale imported-box jobs");
        return published;
    }
}
