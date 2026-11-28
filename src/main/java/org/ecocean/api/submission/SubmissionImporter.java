package org.ecocean.api.submission;

import java.util.*;
import org.ecocean.*;
import org.ecocean.api.UploadedFiles;
import org.ecocean.api.bulk.*;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.submission.Submission;
import org.json.*;

/** Caller owns the transaction. No commit, derivative or indexing dispatch occurs here. */
public class SubmissionImporter {
    /** Only thrown before any media copy or domain persistence begins. */
    public static class PreImportRejection extends SubmissionException {
        public PreImportRejection(int status, String code, String message) { super(status, code, message); }
    }
    public JSONObject execute(Submission draft, String taskId, Shepherd sh, SubmissionFiles storage) throws Exception {
        boolean attributed = SubmissionAttribution.requested(draft) != null;
        if (attributed) definite(() -> SubmissionAttribution.serviceOrganization(sh, draft.getOwnerId()));
        User owner = sh.getUserByUUID(draft.getOwnerId());
        if (owner == null || owner.getUsername() == null || owner.getUsername().isBlank() || !SubmissionPolicy.enrolled(draft.getContext(), draft.getOwnerId()))
            throw new PreImportRejection(403, "ACCESS_DENIED", "Owner is no longer eligible");
        JSONObject approved = new JSONObject(draft.getValidationJson());
        // the rerun resolves the owner again; a change in between must still end definitely failed
        SubmissionValidator.Checked rerun = definite(() -> new SubmissionValidator().check(draft, sh, storage));
        JSONObject checked = rerun.report;
        if (!approved.getBoolean("valid") || approved.getLong("revision") != draft.getRevision() || !checked.getBoolean("valid") || !approved.getString("configDigest").equals(checked.getString("configDigest"))
                || !approved.getString("manifestDigest").equals(checked.getString("manifestDigest"))
                || !SubmissionJson.canonical(approved.getJSONArray("normalizedRows")).equals(SubmissionJson.canonical(checked.getJSONArray("normalizedRows"))))
            throw new PreImportRejection(409, "VALIDATION_STALE", "Input or configuration changed after validation");
        JSONObject approvedOwner = approved.optJSONObject("effectiveOwner"), checkedOwner = checked.optJSONObject("effectiveOwner");
        if (attributed && (approvedOwner == null || checkedOwner == null
                || !SubmissionJson.canonical(approvedOwner).equals(SubmissionJson.canonical(checkedOwner))))
            throw new PreImportRejection(409, "VALIDATION_STALE", "The named collector changed after validation");
        String submitter = attributed ? approvedOwner.getString("username") : owner.getUsername();
        ImportTask task = sh.getImportTask(taskId);
        if (task == null) throw new PreImportRejection(409, "INVALID_STATE", "Reserved import task missing");
        // one merged row per group (Encounter.groupKey) or ungrouped row, validated in memory by the rerun
        GroupedRows g = rerun.rows;
        JSONArray rows = checked.getJSONArray("normalizedRows"), files = new JSONArray(draft.getFilesJson());
        List<Map<String, Object>> merged = g.mergedRows(); Set<String> requiredFiles = new HashSet<>();
        // a submitter that cannot be loaded (missing, or the lookup itself fails) is rejected once, before any
        // media work, as the shared row rules did
        BulkValidator submitterValue;
        try {
            if (sh.getUser(submitter) == null) throw new PreImportRejection(422, "VALIDATION_INVALID", "Execution validation failed");
            submitterValue = new BulkValidator("Encounter.submitterID", submitter, sh);
        } catch (Exception ex) { throw new PreImportRejection(422, "VALIDATION_INVALID", "Execution validation failed"); }
        for (Map<String, Object> data : merged) {
            data.put("Encounter.submitterID", BulkValidator.rebound(submitterValue, "Encounter.submitterID"));
            for (Object value : data.values()) if (!(value instanceof BulkValidator))
                throw new PreImportRejection(422, "VALIDATION_INVALID", "Execution validation failed");
            for (String field : BulkImportUtil.findIndexedFieldNames(data.keySet(), "Encounter.mediaAsset")) // bare slots only (bulk-import boxes)
                if (field != null) requiredFiles.add(((BulkValidator)data.get(field)).getValueString());
        }
        Map<String, MediaAsset> media = new HashMap<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            if (requiredFiles.contains(file.getString("name")))
                media.put(file.getString("name"), UploadedFiles.makeMediaAsset(taskId, storage.path(file).toFile(), sh));
        }
        // a null asset would be skipped by the importer, so every required photo must have one
        for (String name : requiredFiles) if (media.get(name) == null) throw new IllegalStateException("Required media unavailable");
        Map<Integer, Encounter> resolved = new TreeMap<>(); // by merged row
        BulkImporter importer = new BulkImporter(taskId, merged, media, owner, sh, g.exactSlotMode(), g.groupedMergedIndexes())
            .deferSideEffects(resolved::put).newOccurrencesOnly();
        JSONObject imported = importer.createImport();
        JSONArray mapping = new JSONArray();
        for (int i = 0; i < rows.length(); i++) { // one receipt per source row, in draft order
            Encounter enc = resolved.get(g.mergedIndexOf(i));
            if (enc == null) throw new IllegalStateException("Missing row resolution");
            JSONObject fields = rows.getJSONObject(i).getJSONObject("fields");
            JSONArray mediaIds = new JSONArray(); // this row's own photos, in its slot order
            for (String field : BulkImportUtil.findIndexedFieldNames(fields.keySet(), "Encounter.mediaAsset"))
                if (field != null) mediaIds.put(media.get(fields.getString(field)).getIdInt());
            JSONArray individuals = new JSONArray(); if (enc.getIndividualID() != null) individuals.put(enc.getIndividualID());
            mapping.put(new JSONObject().put("clientRowId", rows.getJSONObject(i).getString("clientRowId"))
                .put("encounterIds", new JSONArray().put(enc.getId()))
                .put("occurrenceIds", new JSONArray().put(enc.getOccurrenceID())).put("individualIds", individuals)
                .put("mediaAssetIds", mediaIds));
        }
        task.setEncounters(importer.getEncounters()); task.setProcessingProgress(1.0D); task.setStatus(draft.requestsAi() ? "pending-detection" : "complete");
        sh.getPM().makePersistent(task);
        return new JSONObject().put("rows", mapping).put("records", imported);
    }
    /** An attribution refusal before any import work is a definite failure, never one needing reconciliation. */
    private static <T> T definite(java.util.concurrent.Callable<T> check) throws Exception {
        try {
            return check.call();
        } catch (SubmissionException ex) {
            if (ex instanceof PreImportRejection || !SubmissionAttribution.OWNER_NOT_ALLOWED.equals(ex.code)) throw ex;
            throw new PreImportRejection(ex.status, ex.code, ex.getMessage());
        }
    }
}
