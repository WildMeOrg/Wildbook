package org.ecocean.api.submission;

import java.util.*;
import java.util.function.Supplier;
import javax.jdo.Query;
import org.ecocean.*;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;

/** Durable queue, one installation-wide active writer, no automatic uncertain replay. */
public class SubmissionJobs extends SubmissionStore {
    public SubmissionJobs(String context) { super(context); }
    public SubmissionJobs(Supplier<Shepherd> shepherds) { super(shepherds); }
    public JSONObject enqueue(String context, String actor, String id, boolean admin, long revision, String key, JSONObject input) {
        SubmissionJson.keys(input, "validationId");
        String validation = SubmissionJson.requiredString(input, "validationId", 36);
        if (!Util.isUUID(validation) || key == null || key.isEmpty() || key.length() > 128)
            throw new SubmissionException(400, "BAD_REQUEST", "Valid validationId and Idempotency-Key required");
        String keyHash = SubmissionJson.hash(new JSONArray().put(context).put(actor).put(id).put(key).toString());
        String hash = SubmissionJson.hash(SubmissionJson.canonical(input) + ":" + revision);
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id);
            Submission draft = owned(sh, context, actor, id, admin);
            if (draft.getJobId() != null) {
                if (!keyHash.equals(draft.getCommitKeyHash())) throw new SubmissionException(409, "ALREADY_COMMITTED", "Submission already has an accepted execution");
                if (!hash.equals(draft.getCommitHash())) throw new SubmissionException(409, "IDEMPOTENCY_KEY_REUSED", "Commit key was used with different input");
                return new JSONObject(draft.getAcceptedJson());
            }
            if (!SubmissionPolicy.commitEnabled(context)) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Commit is disabled");
            editable(draft); checkRevision(draft, revision);
            if (draft.getValidationJson() != null) {
                JSONObject lastValidation = new JSONObject(draft.getValidationJson());
                if (validation.equals(lastValidation.optString("id")) && !lastValidation.optBoolean("valid", false))
                    throw new SubmissionException(422, "VALIDATION_INVALID", "Validation contains errors");
            }
            if (!"validated".equals(draft.getState()) || draft.getValidationJson() == null
                    || !validation.equals(new JSONObject(draft.getValidationJson()).getString("id")))
                throw new SubmissionException(409, "VALIDATION_STALE", "Validate the current revision before commit");
            JSONObject approved = new JSONObject(draft.getValidationJson());
            String configDigest = SubmissionJson.hash(SubmissionJson.canonical(SubmissionValidator.configuration(context)));
            if (!configDigest.equals(approved.optString("configDigest"))) throw new SubmissionException(409, "VALIDATION_STALE", "Configuration changed; validate again");
            lock(sh, "owner-jobs:" + context + ":" + draft.getOwnerId());
            Query<?> jobs = sh.getPM().newQuery(Submission.class,
                "context == :ctx && ownerId == :owner && (state == 'queued' || state == 'importing' || state == 'needs_reconciliation')");
            try {
                jobs.setResult("count(this)");
                if (((Number)jobs.execute(context, draft.getOwnerId())).longValue() > 0)
                    throw new SubmissionException(429, "LIMIT_EXCEEDED", "One active job per owner; reconcile existing work first");
            } finally { jobs.closeAll(); }
            User owner = sh.getUserByUUID(draft.getOwnerId());
            if (owner == null || !SubmissionPolicy.enrolled(context, draft.getOwnerId())) throw new SubmissionException(403, "ACCESS_DENIED", "Owner is not eligible");
            String job = UUID.randomUUID().toString();
            JSONObject accepted = new JSONObject().put("submissionId", id).put("operationId", job).put("importTaskId", job)
                .put("revision", revision).put("acceptedRevision", revision).put("state", "queued").put("statusUrl", "/api/v3/submissions/" + id);
            ImportTask task = new ImportTask(owner, job); task.setStatus("queued"); task.setProcessingProgress(0.0D);
            task.setPassedParameters(new JSONObject().put("submissionId", id).put("processing", draft.getProcessingMode()).put("skipDetection", !draft.requestsIdentification()).put("skipIdentification", !draft.requestsIdentification()));
            sh.getPM().makePersistent(task);
            draft.queue(job, keyHash, hash, accepted.toString()); commit(sh); return accepted;
        } finally { sh.rollbackAndClose(); }
    }
    public String claimNext(String context) {
        Shepherd sh = open();
        try {
            lock(sh, "submission-worker:" + context);
            Query<?> active = sh.getPM().newQuery(Submission.class, "context == :ctx && state == 'importing'");
            try { active.setResult("count(this)"); if (((Number)active.execute(context)).longValue() > 0) return null; }
            finally { active.closeAll(); }
            Query<?> query = sh.getPM().newQuery(Submission.class, "context == :ctx && state == 'queued'");
            try {
                query.setOrdering("createdAt ascending"); query.setRange(0, 1); query.setIgnoreCache(true);
                List<?> rows = (List<?>)query.execute(context); if (rows.isEmpty()) return null;
                Submission draft = (Submission)rows.get(0); sh.getPM().refresh(draft); draft.claim();
                String id = draft.getId(); commit(sh); return id;
            } finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    @FunctionalInterface public interface Execution { JSONObject run(Submission draft, Shepherd sh) throws Exception; }
    public void execute(String context, String id, Execution execution) {
        Shepherd sh = open(); boolean attempted = false; boolean started = false;
        try {
            lock(sh, "submission:" + id);
            Submission draft = find(sh, "id == :id", id);
            if (draft == null || !context.equals(draft.getContext()) || !"importing".equals(draft.getState())) return;
            started = true; JSONObject result = execution.run(draft, sh);
            draft.imported(result.toString()); attempted = true; commit(sh);
        } catch (Exception ex) {
            sh.rollbackAndClose(); sh = null;
            if (!started) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Execution claim unavailable; inspect status before retrying");
            // Re-read after rollback/acknowledgment failure. A durable imported row wins.
            fail(context, id, attempted ? "COMMIT_OUTCOME_UNCERTAIN" : "IMPORT_FAILED", attempted || !(ex instanceof SubmissionImporter.PreImportRejection));
        } finally { if (sh != null) sh.rollbackAndClose(); }
    }
    private void fail(String context, String id, String code, boolean uncertain) {
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft != null && context.equals(draft.getContext()) && "importing".equals(draft.getState())) {
                draft.fail(code, uncertain); ImportTask task = sh.getImportTask(draft.getJobId());
                if (task != null) task.setStatus(uncertain ? "needs_reconciliation" : "failed"); commit(sh);
            }
        } finally { sh.rollbackAndClose(); }
    }
    public JSONObject results(String context, String actor, String id, boolean admin, int offset, int limit) {
        if (offset < 0 || limit < 1 || limit > 200) throw new SubmissionException(400, "BAD_REQUEST", "Invalid result page");
        Shepherd sh = open();
        try {
            Submission draft = owned(sh, context, actor, id, admin);
            JSONArray all = draft.getResultJson() == null ? new JSONArray() : new JSONObject(draft.getResultJson()).getJSONArray("rows");
            User user = sh.getUserByUUID(actor);
            JSONArray rows = new JSONArray();
            for (int i = offset; i < Math.min(all.length(), (long)offset + limit); i++) {
                JSONObject row = all.getJSONObject(i); boolean visible = admin;
                if (!visible && user != null) {
                    visible = true; JSONArray ids = row.getJSONArray("encounterIds");
                    for (int n = 0; n < ids.length(); n++) {
                        Encounter enc = sh.getEncounter(ids.getString(n));
                        if (enc == null || !org.ecocean.security.Collaboration.canUserAccessEncounter(enc, user.getUsername(), sh)) { visible = false; break; }
                    }
                }
                if (visible) rows.put(row);
            }
            JSONObject result = new JSONObject().put("submissionId", id).put("state", draft.effectiveState()).put("rows", rows)
                .put("indexing", new JSONObject().put("state", draft.getPhase()).put("message", "unknown means dispatched; completion is not acknowledged"))
                .put("derivatives", new JSONObject().put("state", draft.getDerivatives()))
                .put("detection", draft.aiPhase()).put("identification", draft.aiPhase())
                .put("errors", draft.getErrorCode() == null ? new JSONArray() : new JSONArray().put(new JSONObject().put("code", draft.getErrorCode()).put("message", "Operator inspection required")));
            if ((long)offset + limit < all.length()) result.put("nextCursor", String.valueOf(offset + limit));
            if (draft.getJobId() != null) result.put("links", new JSONObject().put("importTask", "/react/bulk-import-task?id=" + draft.getJobId()));
            return result;
        } finally { sh.rollbackAndClose(); }
    }
    public List<String> pendingPostprocessing(String context) {
        Shepherd sh = open();
        try {
            Query<?> query = sh.getPM().newQuery(Submission.class, "context == :ctx && state == 'imported' && (derivatives == 'pending' || (derivatives == 'complete' && phase == 'pending'))");
            try {
                query.setResult("id"); query.setOrdering("createdAt ascending"); query.setRange(0, 10);
                return new ArrayList<>((List<String>)query.execute(context));
            } finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    public List<String> replayBatch(String context, long startup, String after) {
        Shepherd sh = open();
        try {
            Query<?> query = sh.getPM().newQuery(Submission.class,
                "context == :ctx && state == 'imported' && derivatives == 'complete' && phase == 'unknown' && createdAt <= :startup && id > :after");
            try {
                query.setResult("id"); query.setOrdering("id ascending"); query.setRange(0, 5);
                return new ArrayList<>((List<String>)query.execute(context, startup, after));
            } finally { query.closeAll(); }
        } finally { sh.rollbackAndClose(); }
    }
    public boolean postprocess(String context, String id) throws Exception {
        boolean generate = false;
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft == null || !context.equals(draft.getContext()) || !"imported".equals(draft.getState())) return false;
            if ("pending".equals(draft.getDerivatives())) { draft.derivatives("running"); commit(sh); generate = true; }
            else if (!"complete".equals(draft.getDerivatives())) return false;
        } finally { sh.rollbackAndClose(); }
        if (generate) {
            sh = open();
            try {
                lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
                if (!"running".equals(draft.getDerivatives())) return false;
                JSONArray ids = new JSONObject(draft.getResultJson()).getJSONObject("records").getJSONArray("mediaAssets");
                for (int i = 0; i < ids.length(); i++) {
                    org.ecocean.media.MediaAsset parent = sh.getMediaAsset(String.valueOf(ids.getInt(i)));
                    if (parent == null || parent.getStore() == null) throw new IllegalStateException("Missing imported media");
                    for (String type : parent.getStore().standardChildTypes()) {
                        org.ecocean.media.MediaAsset child = parent.updateChild(type);
                        if (child == null) throw new IllegalStateException("Derivative unavailable");
                        child.setSkipAutoIndexing(true); sh.getPM().makePersistent(child);
                    }
                }
                draft.derivatives("complete"); commit(sh);
            } finally { sh.rollbackAndClose(); }
        }
        // Queue only committed objects. The durable intent remains replayable after process restart.
        sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (!"complete".equals(draft.getDerivatives())) return false;
            IndexingManager indexing = IndexingManagerFactory.getIndexingManager();
            if (indexing == null) throw new IllegalStateException("Indexing unavailable");
            JSONObject records = new JSONObject(draft.getResultJson()).getJSONObject("records");
            JSONArray encounters = records.getJSONArray("encounters");
            for (int i = 0; i < encounters.length(); i++) {
                Encounter enc = sh.getEncounter(encounters.getString(i));
                if (enc == null) continue;
                indexing.addIndexingQueueEntry(enc, false);
                if (enc.getAnnotations() != null) for (Annotation ann : enc.getAnnotations()) indexing.addIndexingQueueEntry(ann, false);
            }
            JSONArray sightings = records.getJSONArray("sightings");
            for (int i = 0; i < sightings.length(); i++) {
                Occurrence occ = sh.getOccurrence(sightings.getString(i)); if (occ != null) indexing.addIndexingQueueEntry(occ, false);
            }
            draft.phase("unknown"); commit(sh); return true; // dispatched; no completion acknowledgment API exists
        } finally { sh.rollbackAndClose(); }
    }
    /** A stale claim is held, never rerun. Taking its lock fences a delayed worker. */
    public void reconcileStaleClaims(String context) {
        Shepherd sh = open();
        try {
            Query<?> query = sh.getPM().newQuery(Submission.class,
                "context == :ctx && ((state == 'importing' && workStartedAt < :cutoff) || (state == 'imported' && derivatives == 'running' && derivativesStartedAt < :cutoff))");
            List<String> ids;
            try { query.setResult("id"); query.setRange(0, 20); ids = new ArrayList<>((List<String>)query.execute(context, System.currentTimeMillis() - 60 * 60 * 1000)); }
            finally { query.closeAll(); }
            for (String id : ids) {
                try { tryLock(sh, "submission:" + id); }
                catch (SubmissionException busy) { continue; }
                Submission draft = find(sh, "id == :id", id);
                if ("importing".equals(draft.getState())) {
                    draft.fail("INTERRUPTED_EXECUTION", true);
                    ImportTask task = sh.getImportTask(draft.getJobId()); if (task != null) task.setStatus("needs_reconciliation");
                }
                else if ("imported".equals(draft.getState()) && "running".equals(draft.getDerivatives())) draft.derivatives("unknown");
            }
            commit(sh);
        } finally { sh.rollbackAndClose(); }
    }

    public void holdFailedIndexing(String context, String id) {
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id); Submission draft = find(sh, "id == :id", id);
            if (draft != null && context.equals(draft.getContext()) && "imported".equals(draft.getState()) && "complete".equals(draft.getDerivatives())) {
                draft.phase("failed"); commit(sh);
            }
        } finally { sh.rollbackAndClose(); }
    }
    public void cleanup(String context, SubmissionFiles storage) throws java.io.IOException {
        Set<String> retained = new HashSet<>(); long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10);
        String after = "";
        while (true) {
            Shepherd sh = open(); List<Object[]> page;
            try {
                Query<?> query = sh.getPM().newQuery(Submission.class, "context == :ctx && filesJson != '[]' && id > :after");
                try {
                    query.setResult("id, state, expiresAt, filesJson, completedAt"); query.setOrdering("id ascending"); query.setRange(0, 100);
                    page = new ArrayList<>((List<Object[]>)query.execute(context, after));
                } finally { query.closeAll(); }
            } finally { sh.rollbackAndClose(); }
            for (Object[] row : page) {
                if (System.nanoTime() > deadline) { System.err.println("Submission cleanup paused: inventory deadline exceeded; released references remain saved"); return; }
                after = (String)row[0]; String state = (String)row[1]; String files = (String)row[3];
                long completed = ((Number)row[4]).longValue();
                if ("cancelled".equals(state) || (("draft".equals(state) || "validated".equals(state)) && ((Number)row[2]).longValue() <= System.currentTimeMillis())
                        || (("imported".equals(state) || "failed".equals(state)) && completed > 0 && completed < System.currentTimeMillis() - SubmissionPolicy.DRAFT_TTL_MILLIS))
                    files = cleanupReference(context, (String)row[0], files);
                JSONArray manifest = new JSONArray(files);
                for (int i = 0; i < manifest.length(); i++) retained.add(manifest.getJSONObject(i).getString("blob"));
            }
            if (page.size() < 100) { storage.cleanup(retained); return; }
        }
    }
    /** One short transaction per candidate; released references are never scanned again. */
    private String cleanupReference(String context, String id, String fallback) {
        Shepherd sh = open();
        try {
            try { tryLock(sh, "submission:" + id); } catch (SubmissionException busy) { return fallback; }
            Submission draft = find(sh, "id == :id", id);
            if (draft == null || !context.equals(draft.getContext())) return fallback;
            boolean completed = ("imported".equals(draft.getState()) || "failed".equals(draft.getState()))
                && draft.getCompletedAt() > 0 && draft.getCompletedAt() < System.currentTimeMillis() - SubmissionPolicy.DRAFT_TTL_MILLIS;
            if ("cancelled".equals(draft.effectiveState()) || "expired".equals(draft.effectiveState()) || completed) {
                draft.releaseFiles(); commit(sh); return "[]";
            }
            return draft.getFilesJson();
        } finally { sh.rollbackAndClose(); }
    }
}
