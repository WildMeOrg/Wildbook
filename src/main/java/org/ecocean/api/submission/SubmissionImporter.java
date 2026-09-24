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
        User owner = sh.getUserByUUID(draft.getOwnerId());
        if (owner == null || owner.getUsername() == null || owner.getUsername().isBlank() || !SubmissionPolicy.enrolled(draft.getContext(), draft.getOwnerId()))
            throw new PreImportRejection(403, "ACCESS_DENIED", "Owner is no longer eligible");
        JSONObject approved = new JSONObject(draft.getValidationJson());
        JSONObject checked = new SubmissionValidator().validate(draft, sh, storage);
        if (!approved.getBoolean("valid") || approved.getLong("revision") != draft.getRevision() || !checked.getBoolean("valid") || !approved.getString("configDigest").equals(checked.getString("configDigest"))
                || !approved.getString("manifestDigest").equals(checked.getString("manifestDigest"))
                || !SubmissionJson.canonical(approved.getJSONArray("normalizedRows")).equals(SubmissionJson.canonical(checked.getJSONArray("normalizedRows"))))
            throw new PreImportRejection(409, "VALIDATION_STALE", "Input or configuration changed after validation");
        ImportTask task = sh.getImportTask(taskId);
        if (task == null) throw new PreImportRejection(409, "INVALID_STATE", "Reserved import task missing");
        JSONArray rows = checked.getJSONArray("normalizedRows"), files = new JSONArray(draft.getFilesJson());
        List<Map<String, Object>> validated = new ArrayList<>(); Set<String> requiredFiles = new HashSet<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject fields = new JSONObject(rows.getJSONObject(i).getJSONObject("fields").toString());
            fields.put("Encounter.submitterID", owner.getUsername());
            Map<String, Object> data = BulkImportUtil.validateRow(fields, sh);
            for (Object value : data.values()) if (!(value instanceof BulkValidator))
                throw new PreImportRejection(422, "VALIDATION_INVALID", "Execution validation failed");
            validated.add(data);
            for (String field : fields.keySet()) if (field.startsWith("Encounter.mediaAsset")) requiredFiles.add(fields.getString(field));
        }
        Map<String, MediaAsset> media = new HashMap<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject file = files.getJSONObject(i);
            if (requiredFiles.contains(file.getString("name")))
                media.put(file.getString("name"), UploadedFiles.makeMediaAsset(taskId, storage.path(file).toFile(), sh));
        }
        if (!media.keySet().equals(requiredFiles)) throw new IllegalStateException("Required media unavailable");
        Map<Integer, Encounter> resolved = new TreeMap<>();
        BulkImporter importer = new BulkImporter(taskId, validated, media, owner, sh).deferSideEffects(resolved::put);
        JSONObject imported = importer.createImport();
        JSONArray mapping = new JSONArray();
        for (int i = 0; i < rows.length(); i++) {
            Encounter enc = resolved.get(i);
            if (enc == null) throw new IllegalStateException("Missing row resolution");
            JSONArray mediaIds = new JSONArray(); for (MediaAsset ma : enc.getMedia()) mediaIds.put(ma.getIdInt());
            JSONArray individuals = new JSONArray(); if (enc.getIndividualID() != null) individuals.put(enc.getIndividualID());
            mapping.put(new JSONObject().put("clientRowId", rows.getJSONObject(i).getString("clientRowId"))
                .put("encounterIds", new JSONArray().put(enc.getId()))
                .put("occurrenceIds", new JSONArray().put(enc.getOccurrenceID())).put("individualIds", individuals)
                .put("mediaAssetIds", mediaIds));
        }
        task.setEncounters(importer.getEncounters()); task.setProcessingProgress(1.0D); task.setStatus("complete");
        sh.getPM().makePersistent(task);
        return new JSONObject().put("rows", mapping).put("records", imported);
    }
}
