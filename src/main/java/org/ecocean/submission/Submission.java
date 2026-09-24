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
    public void imported(String result, long now) { resultJson = result; state = "imported"; phase = "pending"; completedAt = now; }
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
            .put("processing", original.getJSONObject("processing"))
            .put("createdAt", Instant.ofEpochMilli(createdAt).toString())
            .put("expiresAt", Instant.ofEpochMilli(expiresAt).toString())
            .put("rowCount", originalCreate ? 0 : new JSONArray(rowsJson).length());
        if (!originalCreate && !"[]".equals(rowsJson)) result.put("rowsDigest", org.ecocean.api.submission.SubmissionJson.hash(rowsJson));
        if (!originalCreate && jobId != null) result.put("operationId", jobId).put("importTaskId", jobId)
            .put("indexing", new JSONObject().put("state", phase))
            .put("derivatives", new JSONObject().put("state", derivatives))
            .put("detection", new JSONObject().put("state", "skipped"))
            .put("identification", new JSONObject().put("state", "skipped"));
        if (!originalCreate && errorCode != null) result.put("errors", new JSONArray().put(new JSONObject().put("code", errorCode).put("message", "Submission requires operator inspection")));
        return result;
    }
    public String effectiveState() {
        return ("draft".equals(state) || "validated".equals(state)) && expiresAt <= System.currentTimeMillis() ? "expired" : state;
    }
}
