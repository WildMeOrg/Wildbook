package org.ecocean.ia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.IAJsonProperties;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.RestClient;
import org.ecocean.servlet.IAGateway;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Util;

import java.io.IOException;

// https://github.com/WildMeOrg/ml-service

public class MLService {
    private IAJsonProperties iaConfig = null;

    public MLService() {
        iaConfig = IAJsonProperties.iaConfig();
    }

    public JSONObject initiateRequest(MediaAsset ma, String taxonomyString)
    throws IOException {
        addToQueue(createJobData(ma, taxonomyString));
        return null;
    }

    public IAJsonProperties getIAConfig() {
        return iaConfig;
    }

    // there can be multiple configs (differing model_id)
    public List<JSONObject> getConfigs(String taxonomyString)
    throws IAException {
        IAJsonProperties iac = getIAConfig();

        if (iac == null) return null;
        JSONObject txConf = (JSONObject)iac.get(taxonomyString);
        if (txConf == null)
            throw new IAException(
                      "MLService.getConfigs() configuration problem with taxonomyString=" +
                      taxonomyString);
        JSONArray confs = txConf.optJSONArray("_mlservice_conf");
        if (confs == null)
            throw new IAException(
                      "MLService.getConfigs() configuration problem with taxonomyString=" +
                      taxonomyString + "; txConf=" + txConf);
        List<JSONObject> configs = new ArrayList<JSONObject>();
        for (int i = 0; i < confs.length(); i++) {
            JSONObject jc = confs.optJSONObject(i);
            if (jc != null) configs.add(jc);
        }
        return configs;
    }

    public void addToQueue(JSONObject jobData)
    throws IOException {
        if (jobData == null) return;
        IAGateway.addToDetectionQueue("context0", jobData.toString());
    }

    // i think we *must* pass taxonomyString here
    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
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
        data.put("taxonomyString", taxonomyString);

        JSONArray maIds = new JSONArray();
        maIds.put(ma.getIdInt());
        data.put("mediaAssetIds", maIds);
        return data;
    }

    public void processQueueJob(JSONObject jobData) {
        System.out.println("#################################################### processing: " +
            jobData.toString(8));
        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.setAction("MLService.processQueueJob");
        myShepherd.beginDBTransaction();
        FeatureType.initAll(myShepherd);
        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
        try {
            if (ids != null) {
                for (String maId : Util.jsonArrayToStringList(ids)) {
                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null));
                }
            } else {
                ids = jobData.optJSONArray("annotationIds");
                if (ids != null) {
                    for (String annId : Util.jsonArrayToStringList(ids)) {
                        send(myShepherd.getAnnotation(annId));
                    }
                }
            }
        } catch (IAException iaex) {
            System.out.println("processQueueJob() threw " + iaex + " with jobData=" + jobData);
            iaex.printStackTrace();
            if (iaex.shouldRequeue()) requeueJob(jobData, iaex.shouldIncrement());
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    public void requeueJob(JSONObject jobData, boolean increment) {
        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
            jobData);
        // this handles a bunch of messiness, including max retries etc
        IAGateway.requeueJob(jobData, increment);
    }

    public void send(MediaAsset ma, String taxonomyString)
    throws IAException {
        if (ma == null) throw new IAException("null MediaAsset passed");
        for (JSONObject conf : getConfigs(taxonomyString)) {
            JSONObject payload = createPayload(ma, conf);
            JSONObject res = sendPayload(conf.optString("api_endpoint", null), payload);
            // got results, now we try to use them
            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
                "; RESPONSE => " + res);
            List<Annotation> anns = processMediaAssetResults(ma, res);
            System.out.println("MLService.send() anns=" + anns);
        }
    }

    public List<Annotation> processMediaAssetResults(MediaAsset ma, JSONObject res)
    throws IAException {
        if (res == null) throw new IAException("empty results");
        if (!res.optBoolean("success", false))
            throw new IAException("results success=false: " + res);
        JSONArray bboxes = res.optJSONArray("bboxes");
        if (bboxes == null) throw new IAException("null bboxes in results: " + res);
        List<Annotation> anns = new ArrayList<Annotation>();
        if (bboxes.length() < 1) return anns;
        // TODO do we ever care about scores?
        List<Double> scores = Util.jsonArrayToDoubleList(res.optJSONArray("scores"));
        if ((scores == null) || (scores.size() != bboxes.length()))
            throw new IAException("scores size does not match bboxes: " + res);
        List<Double> thetas = Util.jsonArrayToDoubleList(res.optJSONArray("thetas"));
        if ((thetas == null) || (thetas.size() != bboxes.length()))
            throw new IAException("thetas size does not match bboxes: " + res);
        List<String> classNames = Util.jsonArrayToStringList(res.optJSONArray("class_names"));
        if ((classNames == null) || (classNames.size() != bboxes.length()))
            throw new IAException("class_names size does not match bboxes: " + res);
        // FIXME wtf happened to viewpoint??? :)
        // iterate over bboxes and make annots
        for (int i = 0; i < bboxes.length(); i++) {
            List<Double> xywh = Util.jsonArrayToDoubleList(bboxes.optJSONArray(i));
            if (xywh == null) throw new IAException("error parsing bbox[" + i + "] (null): " + res);
            if (xywh.size() != 4)
                throw new IAException("error parsing bbox[" + i + "] (size): " + res);
            Annotation ann = createAnnotation(xywh, thetas.get(i), classNames.get(i), null);
            Annotation exists = ma.findAnnotation(ann, true);
            if (exists != null) { // i guess we just skip this and do not create???
                System.out.println("[WARNING] MLService.processMediaAssetResults() skipping i=" +
                    i + " (res=" + res + ") due to existing matching " + exists);
                continue;
            }
            ma.addFeature(ann.getFeature());
            anns.add(ann);
        }
        ma.setDetectionStatus("complete");
        return anns;
    }

    private Annotation createAnnotation(List<Double> bbox, Double theta, String iaClass,
        String viewpoint)
    throws IAException {
        if ((bbox == null) || (bbox.size() != 4))
            throw new IAException("createAnnotation() bad bbox");
        if ((bbox.get(2) < 1.0d) || (bbox.get(3) < 1.0d))
            throw new IAException("createAnnotation() bad bbox width/height");
        JSONObject fparams = new JSONObject();
        fparams.put("x", bbox.get(0));
        fparams.put("y", bbox.get(1));
        fparams.put("width", bbox.get(2));
        fparams.put("height", bbox.get(3));
        fparams.put("theta", ((theta == null) ? 0.0d : theta));
        fparams.put("viewpoint", viewpoint);
        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
        Annotation ann = new Annotation(null, ft, iaClass);
        ann.setViewpoint(viewpoint);
        return ann;
    }

    public void send(Annotation ann)
    throws IAException {
        throw new IAException("NOT YET IMPLEMENTED");
    }

    private JSONObject sendPayload(String endpoint, JSONObject payload)
    throws IAException {
        if (endpoint == null) throw new IAException("null api_endpoint");
        URL url = null;
        try {
            url = new URL(endpoint);
        } catch (MalformedURLException urlEx) {
            throw new IAException("api_endpoint url error: " + urlEx);
        }
        try {
            // throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
            JSONObject res = RestClient.post(url, payload);
            return res;
        } catch (Exception ex) {
            System.out.println("sendPayload(" + url + ") threw " + ex);
            ex.printStackTrace();
            String msg = ex.getMessage();
            if (msg.contains("Connection refused")) {
                throw new IAException("Connection refused", true, true);
            } else if (msg.contains("Read timed out")) {
                throw new IAException("time out", true); // no increment
            } else if (msg.contains("HTTP error code : 500")) {
                throw new IAException("500 error", true, true);
            } else if (msg.contains("HTTP error code : 502")) {
                throw new IAException("502 error", true); // we requeue, but dont increment this?
            }
            // default behavior is to retry, but with increment
            throw new IAException("unhandled exception [will requeue, incremented] on POST: " + ex,
                    true, true);
        }
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

    // this is to request detection find an annotation and (optionally) return embedding as well
    public JSONObject createPayload(MediaAsset ma, JSONObject config)
    throws IAException {
        if ((config == null) || (ma == null))
            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
                    "; config=" + config);
        JSONObject payload = new JSONObject(config.toString());
        payload.remove("api_endpoint");
        payload.put("image_url", ma.webURL());
        // FIXME add embedding boolean/args
        return payload;
    }

    // this only gets the embedding, from a given (manual or pre-existing) Annotation
    public JSONObject createPayload(Annotation ann, JSONObject config)
    throws IAException {
        JSONObject payload = createPayload(ann.getMediaAsset(), config);

        // TODO ann stuff
        return payload;
    }
}
