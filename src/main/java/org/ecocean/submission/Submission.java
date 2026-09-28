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
    public String getAiState() {
        if (!requestsIdentification()) return "skipped";
        if (aiState != null) return aiState;
        if ("failed".equals(state)) return "failed";
        if ("needs_reconciliation".equals(state)) return "unknown";
        return "pending";
    }
    public long getAiStartedAt() { return aiStartedAt == null ? 0 : aiStartedAt; }
    public void aiState(String value) { aiState = value; }
    public void claimAi(long now) { aiState = "dispatching"; aiStartedAt = now; }
    public JSONObject aiPhase() {
        if (requestsIdentification() && ("failed".equals(state) || "needs_reconciliation".equals(state)))
            return new JSONObject().put("state", "not_started")
                .put("code", "failed".equals(state) ? "IMPORT_FAILED" : "IMPORT_OUTCOME_UNCERTAIN")
                .put("message", "Detection and identification were not started because record import failed or requires reconciliation. Inspect the import outcome first.");
        if ("unknown".equals(getAiState()) || "failed".equals(getAiState()))
            return new JSONObject().put("state", getAiState()).put("code", "unknown".equals(getAiState()) ? "AI_HANDOFF_UNKNOWN" : "AI_HANDOFF_FAILED").put("message", "AI handoff requires operator inspection; never automatically resubmitted. Imported records remain available.");
        return new JSONObject().put("state", getAiState()).put("message", requestsIdentification()
            ? "Detection and identification workflow handoff; dispatched does not mean completed. See import task for progress and match candidates."
            : "Explicit import-only mode");
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
    public void queue(String job, String key, String hash, String accepted) {
        jobId = job; commitKeyHash = key; commitHash = hash; acceptedJson = accepted; state = "queued";
    }
    public void claim() { claim(System.currentTimeMillis()); }
    public void claim(long now) { state = "importing"; workStartedAt = now; }
    public void imported(String result) { imported(result, System.currentTimeMillis()); }
    public void imported(String result, long now) { resultJson = result; aiState = requestsIdentification() ? "pending" : "skipped"; state = "imported"; phase = "pending"; completedAt = now; }
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
            .put("detection", aiPhase())
            .put("identification", aiPhase());
        if (!originalCreate && errorCode != null) result.put("errors", new JSONArray().put(new JSONObject().put("code", errorCode).put("message", "Submission requires operator inspection")));
        return result;
    }
    public String effectiveState() {
        return ("draft".equals(state) || "validated".equals(state)) && expiresAt <= System.currentTimeMillis() ? "expired" : state;
    }
}
