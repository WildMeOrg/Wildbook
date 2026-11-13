package org.ecocean.ia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;

import org.ecocean.Encounter;
import org.ecocean.servlet.IAGateway;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Review of #1861 (UrbanFercec): a boxed import handed off through the submissions route with no
 * box left. The hand-off builds root -> request exactly as SubmissionProcessing.prepare does and
 * publishes the shared envelope; intake rejects the empty envelope on the request only. The
 * import summary must still finish, on real DataNucleus and Postgres.
 */
@Testcontainers
class ImportNoBoxCompletionDbTest extends IdentificationDbTestSupport {

    /** An import with supplied boxes, none left, and its root -> request hand-off persisted. */
    static String[] handedOffWithNoBox(boolean skipIdent) {
        Shepherd sh = open("fixture");
        try {
            ImportTask itask = new ImportTask();
            itask.setPassedParameters(new JSONObject().put(ImportTask.PARAM_ANNOTATIONS_IMPORTED, true));
            itask.setEncounters(new ArrayList<Encounter>());
            JSONObject parameters = new JSONObject().put("importTaskId", itask.getId()).put("skipIdent", skipIdent);
            Task parent = new Task();
            parent.setParameters(parameters);
            Task request = new Task();
            request.setParameters(new JSONObject(parameters.toString()));
            parent.addChild(request);
            itask.setIATask(parent);
            sh.getPM().makePersistent(parent);
            sh.getPM().makePersistent(request);
            sh.getPM().makePersistent(itask);
            JSONObject message = new JSONObject().put("taskParameters", parameters).put("taskId", request.getId())
                .put("v2", true).put("__context", CTX).put("__baseUrl", "https://base.test");
            JSONObject envelope = IAGateway.buildImportEnvelope(itask);
            for (String key : envelope.keySet()) message.put(key, envelope.get(key));
            sh.commitDBTransaction();
            return new String[] { itask.getId(), request.getId(), message.toString() };
        } finally {
            sh.closeDBTransaction();
        }
    }

    static JSONObject summary(String importId) {
        Shepherd sh = open("summary");
        try {
            return sh.getImportTask(importId).iaSummaryJson(sh);
        } finally {
            sh.rollbackAndClose();
        }
    }

    @Test void submissionHandOffWithNoBoxLeftFinishes() throws Exception {
        for (boolean skipIdent : new boolean[] { true, false }) {
            String[] ids = handedOffWithNoBox(skipIdent);
            JSONObject message = new JSONObject(ids[2]);
            assertEquals(IAGateway.ANNOTATION_MODE_EXTRACT, message.optString("annotationMode"), "a boxed envelope");
            assertEquals(0, message.getJSONArray("annotationIds").length(), "with no box in it");
            assertFalse(summary(ids[0]).optBoolean("pipelineComplete"), "not finished before intake");

            IA.handleRest(message);

            Shepherd check = open("check");
            try {
                assertEquals("error", Task.load(ids[1], check).getStoredStatus(), "intake ends the request");
            } finally {
                check.rollbackAndClose();
            }
            JSONObject sum = summary(ids[0]);
            assertTrue(sum.optBoolean("pipelineComplete"), "skipIdent=" + skipIdent + " " + sum);
            if (!skipIdent) assertEquals("complete", sum.optString("identificationStatus"));
        }
    }
}
