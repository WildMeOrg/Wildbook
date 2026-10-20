package org.ecocean.api.submission;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.jdo.Query;
import javax.jdo.datastore.JDOConnection;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.JSONArray;
import org.json.JSONObject;

/** PostgreSQL-backed draft operations. Locks are transaction-scoped across JVMs. */
public class SubmissionStore {
    private final Supplier<Shepherd> shepherds;
    public SubmissionStore(String context) { this(() -> new Shepherd(context)); }
    public SubmissionStore(Supplier<Shepherd> shepherds) { this.shepherds = shepherds; }

    public JSONObject create(String context, String ownerId, String key, JSONObject input) {
        if (key == null || key.isEmpty() || key.length() > 128) throw new SubmissionException(400, "BAD_REQUEST", "Idempotency-Key required (maximum 128 characters)");
        JSONObject normalized = SubmissionJson.create(input);
        String keyHash = SubmissionJson.hash(new JSONArray().put(context).put(ownerId).put("create").put(key).toString());
        String hash = SubmissionJson.hash(SubmissionJson.canonical(normalized));
        Shepherd sh = open();
        try {
            // Per-owner lock makes admission limits and create retries atomic, even for distinct keys.
            lock(sh, "owner:" + context + ":" + ownerId);
            Submission existing = find(sh, "createKeyHash == :key", keyHash);
            if (existing != null) {
                if (!input.has("processing")) hash = SubmissionJson.hash(SubmissionJson.canonical(SubmissionJson.create(input, existing.getProcessingMode())));
                if (!existing.getCreateHash().equals(hash)) throw new SubmissionException(409, "IDEMPOTENCY_KEY_REUSED", "Key was used with different input");
                return existing.json(true);
            }
            Query<?> count = sh.getPM().newQuery(Submission.class,
                "context == :ctx && ownerId == :owner && (state == 'draft' || state == 'validated') && expiresAt > :now");
            try {
                count.setResult("count(this)");
                Number n = (Number)count.execute(context, ownerId, System.currentTimeMillis());
                if (n.longValue() >= 20) throw new SubmissionException(429, "LIMIT_EXCEEDED", "Maximum 20 active drafts per account");
            } finally { count.closeAll(); }
            Query<?> recent = sh.getPM().newQuery(Submission.class, "context == :ctx && ownerId == :owner && createdAt > :since");
            try {
                recent.setResult("count(this)");
                if (((Number)recent.execute(context, ownerId, System.currentTimeMillis() - 24 * 60 * 60 * 1000)).longValue() >= 20)
                    throw new SubmissionException(429, "LIMIT_EXCEEDED", "Maximum 20 new drafts per rolling 24 hours per account");
            } finally { recent.closeAll(); }
            long now = System.currentTimeMillis();
            Submission draft = new Submission(UUID.randomUUID().toString(), context, ownerId,
                keyHash, hash, SubmissionJson.canonical(normalized), now, now + SubmissionPolicy.DRAFT_TTL_MILLIS);
            sh.getPM().makePersistent(draft);
            JSONObject result = draft.json(true);
            commit(sh);
            return result;
        } finally { sh.rollbackAndClose(); }
    }

    public JSONObject get(String context, String ownerId, String id, boolean admin, boolean rows) {
        Shepherd sh = open();
        try {
            Submission draft = owned(sh, context, ownerId, id, admin);
            return rows ? new JSONObject().put("rows", new JSONArray(draft.getRowsJson())).put("revision", draft.getRevision())
                : draft.json(false);
        } finally { sh.rollbackAndClose(); }
    }

    public JSONObject replaceRows(String context, String ownerId, String id, boolean admin,
        long revision, JSONObject input) {
        JSONArray rows = SubmissionJson.rows(input);
        if (input.toString().getBytes(StandardCharsets.UTF_8).length > SubmissionPolicy.MAX_BODY_BYTES)
            throw new SubmissionException(413, "LIMIT_EXCEEDED", "Rows exceed body limit");
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id);
            Submission draft = owned(sh, context, ownerId, id, admin);
            editable(draft); checkRevision(draft, revision);
            draft.replaceRows(SubmissionJson.canonical(rows));
            JSONObject result = draft.json(false);
            commit(sh);
            return result;
        } finally { sh.rollbackAndClose(); }
    }

    public void cancel(String context, String ownerId, String id, boolean admin, long revision) {
        Shepherd sh = open();
        try {
            lock(sh, "submission:" + id);
            Submission draft = owned(sh, context, ownerId, id, admin);
            if ("cancelled".equals(draft.getState())) return;
            editable(draft); checkRevision(draft, revision);
            draft.cancel();
            commit(sh);
        } finally { sh.rollbackAndClose(); }
    }

    public JSONObject manifest(String context, String owner, String id, boolean admin) {
        Shepherd sh = open();
        try { Submission draft = owned(sh, context, owner, id, admin); readableFiles(draft); return manifest(draft); }
        finally { sh.rollbackAndClose(); }
    }
    private JSONObject manifest(Submission draft) {
        return new JSONObject().put("submissionId", draft.getId()).put("revision", draft.getRevision())
            .put("files", SubmissionFiles.publicFiles(new JSONArray(draft.getFilesJson())));
    }
    private void readableFiles(Submission draft) {
        if ("expired".equals(draft.effectiveState()) || "cancelled".equals(draft.effectiveState()))
            throw new SubmissionException(410, "GONE", "Staged files are no longer available");
    }
    @FunctionalInterface public interface Receiver { JSONObject receive(long maxFileBytes) throws Exception; }
    public JSONObject upload(String context, String owner, String id, boolean admin, long revision,
            SubmissionFiles storage, Receiver receiver) throws Exception {
        try (SubmissionResources slot = SubmissionResources.acquire(context + ":" + owner)) {
            return uploadReserved(context, owner, id, admin, revision, storage, receiver);
        }
    }
    private JSONObject uploadReserved(String context, String owner, String id, boolean admin, long revision,
            SubmissionFiles storage, Receiver receiver) throws Exception {
        Shepherd sh = open(); JSONObject received = null; boolean commitAttempted = false;
        try {
            tryLock(sh, "submission:" + id);
            Submission draft = owned(sh, context, owner, id, admin); editable(draft); checkRevision(draft, revision);
            JSONArray files = new JSONArray(draft.getFilesJson()); long bytes = 0;
            for (int i = 0; i < files.length(); i++) bytes += files.getJSONObject(i).getLong("sizeBytes");
            // The transaction lock reserves this draft's entire write slot before streaming.
            // A retry may reuse existing capacity; new content is checked against total below.
            received = receiver.receive(SubmissionFiles.maxFileBytes(context));
            for (int i = 0; i < files.length(); i++) {
                JSONObject prior = files.getJSONObject(i);
                if (!prior.getString("name").equalsIgnoreCase(received.getString("name"))) continue;
                if (prior.getString("name").equals(received.getString("name")) && prior.getString("sha256").equals(received.getString("sha256")))
                    return manifest(draft);
                throw new SubmissionException(409, "FILE_CONTENT_CONFLICT", "Filename already used by different content or case");
            }
            if (files.length() >= SubmissionFiles.MAX_FILES || bytes + received.getLong("sizeBytes") > SubmissionFiles.MAX_DRAFT_BYTES)
                throw new SubmissionException(413, "LIMIT_EXCEEDED", "Draft file count or byte limit exceeded");
            files.put(received); draft.setFiles(SubmissionJson.canonical(files));
            JSONObject result = manifest(draft); commitAttempted = true; commit(sh); return result;
        } finally {
            try { sh.rollbackAndClose(); }
            finally {
                // An uncertain commit retains the blob for reconciliation.
                if (received != null && !commitAttempted) try { storage.remove(received); }
                catch (java.io.IOException ex) { System.err.println("Submission temporary blob cleanup failed"); }
            }
        }
    }
    public JSONObject validate(String context, String owner, String id, boolean admin, long revision, SubmissionFiles storage) {
        try (SubmissionResources slot = SubmissionResources.acquire(context + ":" + owner)) {
            return validateReserved(context, owner, id, admin, revision, storage);
        }
    }
    private JSONObject validateReserved(String context, String owner, String id, boolean admin, long revision, SubmissionFiles storage) {
        Shepherd sh = open();
        try {
            tryLock(sh, "submission:" + id);
            Submission draft = owned(sh, context, owner, id, admin); editable(draft); checkRevision(draft, revision);
            JSONObject report = new SubmissionValidator().validate(draft, sh, storage);
            draft.setValidation(report.toString(), report.getBoolean("valid")); commit(sh); return report;
        } finally { sh.rollbackAndClose(); }
    }

    protected Shepherd open() {
        Shepherd sh = shepherds.get();
        try {
            sh.setAction("SubmissionStore"); sh.beginDBTransaction();
            if (!sh.isDBTransactionActive()) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Database transaction unavailable");
            return sh;
        } catch (RuntimeException ex) { sh.rollbackAndClose(); throw ex; }
    }
    protected void commit(Shepherd sh) {
        if (!sh.commitDBTransactionWithStatus()) throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Commit outcome unavailable; retry using the original operation key or reconcile the draft");
    }
    protected Submission owned(Shepherd sh, String context, String ownerId, String id, boolean admin) {
        Submission draft = find(sh, "id == :id", id);
        if (draft == null || !context.equals(draft.getContext()) || (!admin && !ownerId.equals(draft.getOwnerId())))
            throw new SubmissionException(404, "NOT_FOUND", "Submission not found");
        return draft;
    }
    protected Submission find(Shepherd sh, String filter, String value) {
        Query<?> query = sh.getPM().newQuery(Submission.class, filter);
        try {
            query.setIgnoreCache(true);
            List<?> results = (List<?>)query.execute(value);
            if (results.isEmpty()) return null;
            Submission draft = (Submission)results.get(0);
            sh.getPM().refresh(draft);
            return draft;
        } finally { query.closeAll(); }
    }
    protected void editable(Submission draft) {
        if (!"draft".equals(draft.effectiveState()) && !"validated".equals(draft.effectiveState()))
            throw new SubmissionException(409, "INVALID_STATE", "Submission is not editable");
    }
    protected void checkRevision(Submission draft, long revision) {
        if (draft.getRevision() != revision) throw new SubmissionException(412, "REVISION_STALE", "Fetch the current draft revision");
    }
    protected void tryLock(Shepherd sh, String value) { lock(sh, value, true); }
    protected void lock(Shepherd sh, String value) { lock(sh, value, false); }
    private void lock(Shepherd sh, String value, boolean immediate) {
        JDOConnection connection = sh.getPM().getDataStoreConnection();
        try {
            long key = Long.parseUnsignedLong(SubmissionJson.hash(value).substring(0, 16), 16);
            try (PreparedStatement statement = ((Connection)connection.getNativeConnection())
                    .prepareStatement(immediate ? "SELECT pg_try_advisory_xact_lock(?)" : "SELECT pg_advisory_xact_lock(?)")) {
                statement.setLong(1, key); statement.setQueryTimeout(10);
                try (java.sql.ResultSet result = statement.executeQuery()) {
                    if (immediate && (!result.next() || !result.getBoolean(1))) throw new SubmissionException(429, "LIMIT_EXCEEDED", "Draft busy; retry after five seconds");
                }
            }
        } catch (java.sql.SQLException ex) {
            throw new SubmissionException(503, "CAPABILITY_UNAVAILABLE", "Submission lock unavailable");
        } finally { connection.close(); }
    }
}
