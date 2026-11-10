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
    static final String MARKER = "\"" + Task.PARAM_IMPORTED_BOX + "\":true";

    private ImportedBoxReplay() {}

    /** The resume messages of non-terminal imported-box children modified before the cutoff, oldest first. */
    static List<String> fetchStaleResumeMessages(Shepherd shep, long modifiedCutoff, int max) {
        List<String> messages = new ArrayList<String>();
        // the marker is a JSON fragment; DataNucleus supports String.indexOf in JDOQL (see MetricsBot)
        String jdoql = "SELECT FROM org.ecocean.ia.Task WHERE " +
            "(status == null || (status != 'completed' && status != 'error' && status != 'dropped-stale'))" +
            " && modified < " + modifiedCutoff +
            " && parameters.indexOf('" + MARKER + "') > -1";
        Query q = shep.getPM().newQuery(jdoql);
        try {
            q.setOrdering("modified ascending");
            q.setRange(0, Math.max(1, max));
            @SuppressWarnings("unchecked")
            List<Task> stale = (List<Task>)q.execute();
            if (stale != null) {
                for (Task t : stale) {
                    String msg = t.getQueueResumeMessage();
                    if (msg != null) messages.add(msg);
                }
            }
        } finally {
            q.closeAll();
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
