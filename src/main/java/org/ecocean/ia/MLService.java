package org.ecocean.ia;

import org.json.JSONArray;
import org.json.JSONObject;

import org.ecocean.IAJsonProperties;
import org.ecocean.media.MediaAsset;
import org.ecocean.servlet.IAGateway;

import java.io.IOException;

// https://github.com/WildMeOrg/ml-service

public class MLService {
    private IAJsonProperties iaConfig = null;

    public MLService() {
        iaConfig = IAJsonProperties.iaConfig();
    }

    public JSONObject initiateRequest(MediaAsset ma)
    throws IOException {
        addToQueue(createJobData(ma));
        return null;
    }

    public void addToQueue(JSONObject jobData)
    throws IOException {
        if (jobData == null) return;
        IAGateway.addToDetectionQueue("context0", jobData.toString());
    }

    public JSONObject createJobData(MediaAsset ma) {
/* examples from IAGateway
        qjob.put("taskId", task.getId());
        qjob.put("mediaAssetIds", maIds);
        qjob.put("v2", true);
        qjob.put("__context", context);
        qjob.put("__baseUrl", baseUrl);
        qjob.put("__handleBulkImport", System.currentTimeMillis());
 */
        // TODO make this do the right thing
        JSONObject data = new JSONObject();

        data.put("MLService", true);

        JSONArray maIds = new JSONArray();
        maIds.put(ma.getIdInt());
        data.put("mediaAssetIds", maIds);
        return data;
    }

    public void processQueueJob(JSONObject jobData) {
        System.out.println("#################################################### processing: " +
            jobData.toString(8));
    }

/*
        if ((jobj.optJSONObject("detect") != null) && (jobj.optString("taskId", null) != null)) {
            JSONObject res = new JSONObject("{\"success\": false}");
            res.put("taskId", jobj.getString("taskId"));
            String context = jobj.optString("__context", "context0");
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("IAGateway.processQueueMessage.detect");
            myShepherd.beginDBTransaction();
            String baseUrl = jobj.optString("__baseUrl", null);
            try {
                JSONObject rtn = _doDetect(jobj, res, myShepherd, baseUrl);
                System.out.println(
                    "INFO: IAGateway.processQueueMessage() 'detect' successful --> " +
                    rtn.toString());
                if (!rtn.optBoolean("success", false)) {
                    requeueIncrement = true;
                    requeue = true;
                    myShepherd.rollbackDBTransaction();
                } else {
                    myShepherd.commitDBTransaction();
                }
            } catch (Exception ex) {
                System.out.println(
                    "ERROR: IAGateway.processQueueMessage() 'detect' threw exception: " +
                    ex.toString());
                if (ex.toString().contains("HTTP error code : 500")) {
                    requeueIncrement = true;
                    requeue = true;
                }
                // error - don't requeue
                else if (ex.toString().contains("HTTP error code : 502")) {
                    requeueIncrement = true;
                    requeue = true;
                }
                // error - don't requeue
                else if (ex.toString().contains("HTTP error code : 608")) {
                    requeue = false;
                } else {
                    requeueIncrement = true;
                    requeue = true;
                }
                myShepherd.rollbackDBTransaction();
            }
            myShepherd.closeDBTransaction();
 */
}
