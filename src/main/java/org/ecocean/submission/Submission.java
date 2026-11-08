package org.ecocean.submission;

import java.time.Instant;
import org.json.JSONArray;
import org.json.JSONObject;

/** Private intake envelope; no domain entities are created by draft operations. */
public class Submission {
    private String id;
    private String context;
    private String ownerId;
    private String createKeyHash;
    private String createHash;
    private String createJson;
    private String rowsJson = "[]";
    private String filesJson = "[]";
    private String validationJson;
    private String jobId;
    private String commitKeyHash;
    private String commitHash;
    private String acceptedJson;
    private String resultJson;
    private String derivatives = "pending";
    private String phase = "pending";
    private String errorCode;
    private String aiState;
    private Boolean boxed; // bulk-import boxes (spec 4.9): rows carry user-supplied boxes; NULL (older rows) reads as false
    private Long aiStartedAt;
    private long workStartedAt;
    private long derivativesStartedAt;
    private long completedAt;
    private String state = "draft";
    private long revision;
    private long createdAt;
    private long expiresAt;

    public Submission() {}
    public Submission(String id, String context, String ownerId, String createKeyHash,
        String createHash, String createJson, long createdAt, long expiresAt) {
        this.id = id; this.context = context; this.ownerId = ownerId;
        this.createKeyHash = createKeyHash; this.createHash = createHash;
        this.createJson = createJson; this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public String getProcessingMode() { JSONObject processing = new JSONObject(createJson).optJSONObject("processing"); return processing == null ? "import-only" : processing.optString("mode", "import-only"); }
    public boolean requestsIdentification() { return "detect-and-identify".equals(getProcessingMode()); }
    public boolean isBoxed() { return Boolean.TRUE.equals(boxed); }
    public void boxed(boolean value) { boxed = value; }
    /** AI work is handed off when identification is requested or boxes were supplied (boxes are always extracted, decision 8). */
    public boolean requestsAi() { return requestsIdentification() || isBoxed(); }
    public String getAiState() {
        if (!requestsAi()) return "skipped";
        if (aiState != null) return aiState;
        if ("failed".equals(state)) return "failed";
        if ("needs_reconciliation".equals(state)) return "unknown";
        return "pending";
    }
    public long getAiStartedAt() { return aiStartedAt == null ? 0 : aiStartedAt; }
    public void aiState(String value) { aiState = value; }
    public void claimAi(long now) { aiState = "dispatching"; aiStartedAt = now; }
    public JSONObject aiPhase() {
        if (requestsAi() && ("failed".equals(state) || "needs_reconciliation".equals(state)))
            return new JSONObject().put("state", "not_started")
                .put("code", "failed".equals(state) ? "IMPORT_FAILED" : "IMPORT_OUTCOME_UNCERTAIN")
                .put("message", "Detection and identification were not started because record import failed or requires reconciliation. Inspect the import outcome first.");
        if ("unknown".equals(getAiState()) || "failed".equals(getAiState()))
            return new JSONObject().put("state", getAiState()).put("code", "unknown".equals(getAiState()) ? "AI_HANDOFF_UNKNOWN" : "AI_HANDOFF_FAILED").put("message", "AI handoff requires operator inspection; never automatically resubmitted. Imported records remain available.");
        String message;
        if (!requestsAi()) message = "Explicit import-only mode";
        else if (!isBoxed()) message = "Detection and identification workflow handoff; dispatched does not mean completed. See import task for progress and match candidates.";
        else if (requestsIdentification()) message = "Extraction and identification workflow handoff; dispatched does not mean completed. See import task for progress and match candidates.";
        else message = "Extraction-only workflow handoff for the supplied boxes; identification is not run (import-only); dispatched does not mean completed. See import task for progress.";
        return new JSONObject().put("state", getAiState()).put("message", message);
    }
    /** Bulk-import boxes: a boxed draft never runs detection on its photos. */
    public JSONObject detectionPhase() {
        if (isBoxed()) return new JSONObject().put("state", "skipped").put("code", "BOXES_SUPPLIED").put("message", "Boxes were supplied; detection is not run on these photos");
        return aiPhase();
    }
    /** Bulk-import boxes: a boxed import-only draft extracts its boxes but does not identify; failure phases pass through untouched. */
    public JSONObject identificationPhase() {
        JSONObject phase = aiPhase();
        if (requestsIdentification() || !isBoxed() || phase.has("code")) return phase;
        String state = phase.optString("state");
        if (!"pending".equals(state) && !"dispatching".equals(state) && !"dispatched".equals(state)) return phase;
        return phase.put("code", "BOXES_EXTRACTED_ONLY");
    }
    public String getId() { return id; }
    public String getContext() { return context; }
    public String getOwnerId() { return ownerId; }
    public String getCreateHash() { return createHash; }
    public String getRowsJson() { return rowsJson; }
    public String getFilesJson() { return filesJson; }
    public String getValidationJson() { return validationJson; }
    public void setFiles(String files) { filesJson = files; validationJson = null; state = "draft"; revision++; }
    public void setValidation(String report, boolean valid) { validationJson = report; state = valid ? "validated" : "draft"; }
    public String getJobId() { return jobId; }
    public String getCommitKeyHash() { return commitKeyHash; }
    public String getCommitHash() { return commitHash; }
    public String getAcceptedJson() { return acceptedJson; }
    public String getResultJson() { return resultJson; }
    public String getDerivatives() { return derivatives; }
    public void derivatives(String state) { derivatives = state; if ("running".equals(state)) derivativesStartedAt = System.currentTimeMillis(); }
    public String getPhase() { return phase; }
    public long getWorkStartedAt() { return workStartedAt; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() {
        if ("VALIDATION_STALE".equals(errorCode))
            return "Approved rows were no longer valid at import, for example a sighting ID created in the meantime. Nothing was imported; validate the rows in a new submission.";
        return "Submission requires operator inspection";
    }
    public void queue(String job, String key, String hash, String accepted) {
        jobId = job; commitKeyHash = key; commitHash = hash; acceptedJson = accepted; state = "queued";
    }
    public void claim() { claim(System.currentTimeMillis()); }
    public void claim(long now) { state = "importing"; workStartedAt = now; }
    public void imported(String result) { imported(result, System.currentTimeMillis()); }
    public void imported(String result, long now) { resultJson = result; aiState = requestsAi() ? "pending" : "skipped"; state = "imported"; phase = "pending"; completedAt = now; }
    public long getCompletedAt() { return completedAt; }
    public void releaseFiles() { filesJson = "[]"; }
    public void fail(String code, boolean uncertain) { errorCode = code; state = uncertain ? "needs_reconciliation" : "failed"; if (!uncertain) completedAt = System.currentTimeMillis(); }
    public void phase(String value) { phase = value; }
    public String getState() { return state; }
    public long getRevision() { return revision; }
    public void replaceRows(String rows) { this.rowsJson = rows; this.validationJson = null; this.state = "draft"; this.revision++; }
    public void cancel() { this.state = "cancelled"; this.revision++; }
    public JSONObject json(boolean originalCreate) {
        JSONObject original = new JSONObject(createJson);
        JSONObject result = new JSONObject().put("id", id).put("contractVersion", "1")
            .put("revision", originalCreate ? 0 : revision)
            .put("state", originalCreate ? "draft" : effectiveState())
            .put("source", original.getJSONObject("source"))
            .put("processing", new JSONObject().put("mode", getProcessingMode()))
            .put("createdAt", Instant.ofEpochMilli(createdAt).toString())
            .put("expiresAt", Instant.ofEpochMilli(expiresAt).toString())
            .put("rowCount", originalCreate ? 0 : new JSONArray(rowsJson).length());
        if (!originalCreate && !"[]".equals(rowsJson)) result.put("rowsDigest", org.ecocean.api.submission.SubmissionJson.hash(rowsJson));
        if (!originalCreate && jobId != null) result.put("operationId", jobId).put("importTaskId", jobId)
            .put("indexing", new JSONObject().put("state", phase))
            .put("derivatives", new JSONObject().put("state", derivatives))
            .put("detection", detectionPhase())
            .put("identification", identificationPhase());
        if (!originalCreate && errorCode != null) result.put("errors", new JSONArray().put(new JSONObject().put("code", errorCode).put("message", getErrorMessage())));
        return result;
    }
    public String effectiveState() {
        return ("draft".equals(state) || "validated".equals(state)) && expiresAt <= System.currentTimeMillis() ? "expired" : state;
    }
}
