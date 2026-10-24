package org.ecocean.api.submission;

import java.util.*;
import java.util.function.Supplier;
import javax.jdo.Query;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.queue.FileQueue;
import org.ecocean.queue.Queue;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;

/** Post-commit handoff to the existing detection/identification pipeline. Never republishes uncertain work. */
public class SubmissionProcessing extends SubmissionStore {
    public SubmissionProcessing(String context) { super(context); }
    public SubmissionProcessing(Supplier<Shepherd> shepherds) { super(shepherds); }
    @FunctionalInterface public interface Preparation { JSONObject prepare(Submission draft, Shepherd sh) throws Exception; }
    @FunctionalInterface public interface Publisher { void publish(String context, String message) throws Exception; }
    public List<String> pending(String context) {
        Shepherd sh = open();
        try {
            Query<?> query = sh.getPM().newQuery(Submission.class,
                "context == :ctx && state == 'imported' && derivatives == 'complete' && aiState == 'pending'");
            try { query.setResult("id"); query.setOrdering("createdAt ascending"); query.setRange(0, 10);
                return new ArrayList<>((List<String>)query.execute(context)); }
            finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    public void dispatch(String context, String id) throws Exception {
        java.util.concurrent.atomic.AtomicReference<FileQueue> queue = new java.util.concurrent.atomic.AtomicReference<>();
        dispatch(context, id, (draft, sh) -> {
            Queue selected = IAGateway.getDetectionQueue(context);
            if (!(selected instanceof FileQueue)) throw new IllegalStateException("Checked detection queue unavailable");
            queue.set((FileQueue)selected);
            return prepare(draft, sh);
        }, (ctx, message) -> queue.get().publishChecked(message));
    }
    public void dispatch(String context, String id, Preparation preparation, Publisher publisher) throws Exception {
        JSONObject message = null; boolean commitAttempted = false; boolean preparing = false; long claim = 0;
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft == null || !context.equals(draft.getContext()) || !"imported".equals(draft.getState())
                    || !draft.requestsIdentification() || !"complete".equals(draft.getDerivatives()) || !"pending".equals(draft.getAiState())) return;
            preparing = true; message = preparation.prepare(draft, sh);
            claim = System.currentTimeMillis(); draft.claimAi(claim); commitAttempted = true; commit(sh);
        } catch (Exception ex) {
            sh.rollbackAndClose(); sh = null;
            if (preparing) settle(context, id, commitAttempted ? "dispatching" : "pending", commitAttempted ? "unknown" : "failed");
            throw ex;
        } finally { if (sh != null) sh.rollbackAndClose(); }
        // Tasks and resume message are now durable. An ambiguous publish is held for operator reconciliation.
        sh = open(); boolean publishing = false;
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft == null || !context.equals(draft.getContext()) || !"dispatching".equals(draft.getAiState()) || draft.getAiStartedAt() != claim) return;
            publishing = true; publisher.publish(context, message.toString());
            draft.aiState("dispatched"); commit(sh);
        } catch (Exception ex) {
            sh.rollbackAndClose(); sh = null;
            if (publishing) settle(context, id, "dispatching", "unknown");
            throw ex;
        } finally { if (sh != null) sh.rollbackAndClose(); }
    }
    private void settle(String context, String id, String expected, String next) {
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft != null && context.equals(draft.getContext()) && "imported".equals(draft.getState()) && expected.equals(draft.getAiState())) {
                draft.aiState(next); updateTaskFailure(draft, sh); commit(sh);
            }
        } finally { sh.rollbackAndClose(); }
    }
    private void updateTaskFailure(Submission draft, Shepherd sh) {
        if (!"failed".equals(draft.getAiState()) && !"unknown".equals(draft.getAiState())) return;
        ImportTask task = sh.getImportTask(draft.getJobId());
        if (task != null) {
            task.setStatus("unknown".equals(draft.getAiState()) ? "needs_reconciliation" : "failed");
            task.addLog("Submissions AI handoff " + draft.getAiState() + "; imported records retained; operator inspection required");
        }
    }
    JSONObject prepare(Submission draft, Shepherd sh) throws Exception {
        String base = IA.getBaseURL(draft.getContext());
        if (base == null || base.isBlank()) throw new IllegalStateException("IA callback base URL unavailable");
        ImportTask imported = sh.getImportTask(draft.getJobId());
        if (imported == null) throw new IllegalStateException("Missing import task");
        if (imported.getIATask() != null) throw new IllegalStateException("IA task already exists; inspect before dispatch");
        JSONArray media = new JSONObject(draft.getResultJson()).getJSONObject("records").getJSONArray("mediaAssets");
        if (media.isEmpty()) throw new IllegalStateException("No imported media");
        JSONArray mediaIds = new JSONArray();
        for (int i = 0; i < media.length(); i++) mediaIds.put(String.valueOf(media.getInt(i)));
        JSONObject parameters = new JSONObject().put("importTaskId", draft.getJobId()).put("skipIdent", false);
        Task parent = new Task(); parent.setParameters(parameters); sh.getPM().makePersistent(parent);
        Task child = new Task(); child.setParameters(new JSONObject(parameters.toString())); sh.getPM().makePersistent(child);
        parent.addChild(child); imported.setIATask(parent); imported.setStatus("processing-detection");
        JSONObject message = new JSONObject().put("taskParameters", parameters).put("taskId", child.getId())
            .put("mediaAssetIds", mediaIds).put("v2", true)
            .put("__context", draft.getContext()).put("__baseUrl", base).put("__handleBulkImport", System.currentTimeMillis());
        child.setQueueResumeMessage(message.toString());
        sh.getPM().makePersistent(imported);
        return message;
    }
    public void reconcile(String context) {
        long cutoff = System.currentTimeMillis() - 60 * 60 * 1000;
        Shepherd sh = open();
        try {
            Query<?> query = sh.getPM().newQuery(Submission.class,
                "context == :ctx && state == 'imported' && ((aiState == 'dispatching' && aiStartedAt < :cutoff) || (aiState == 'pending' && derivatives == 'unknown'))");
            List<String> ids;
            try { query.setResult("id"); query.setRange(0, 20); ids = new ArrayList<>((List<String>)query.execute(context, cutoff)); }
            finally { query.closeAll(); }
            for (String id : ids) {
                try { tryLock(sh, "submission:" + id); } catch (SubmissionException busy) { continue; }
                Submission draft = find(sh, "id == :id", id);
                if (draft == null || !context.equals(draft.getContext()) || !"imported".equals(draft.getState())) continue;
                if ("dispatching".equals(draft.getAiState()) && draft.getAiStartedAt() < cutoff) draft.aiState("unknown");
                else if ("pending".equals(draft.getAiState()) && "unknown".equals(draft.getDerivatives())) draft.aiState("failed");
                updateTaskFailure(draft, sh);
            }
            commit(sh);
        } finally { sh.rollbackAndClose(); }
    }
}
