package org.ecocean.ia;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.ecocean.Annotation;
import org.ecocean.Embedding;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
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
        addToQueue(createJobData(ma, taxonomyString), null);
        return null;
    }

    public JSONObject initiateRequest(Annotation ann, String taxonomyString)
    throws IOException {
        return initiateRequest(ann, taxonomyString, null);
    }

    public JSONObject initiateRequest(Annotation ann, String taxonomyString, Task task)
    throws IOException {
        addToQueue(createJobData(ann, taxonomyString), task);
        return null;
    }

    public IAJsonProperties getIAConfig() {
        return iaConfig;
    }

    // there can be multiple configs (differing model_id)
    public List<JSONObject> getConfigs(String passedTxStr)
    throws IAException {
        IAJsonProperties iac = getIAConfig();

        if (iac == null) return null;
        if (passedTxStr == null) return null;
        String taxonomyString = passedTxStr.replaceAll(" ", "."); // need dots, not spaces
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

    public void addToQueue(JSONObject jobData, Task task)
    throws IOException {
        if (jobData == null) return;
        if (task != null) jobData.put("taskId", task.getId());
        IAGateway.addToDetectionQueue("context0", jobData.toString());
    }

    // i think we *must* pass taxonomyString here
    public JSONObject createJobData(MediaAsset ma, String taxonomyString) {
        JSONObject data = new JSONObject();

        data.put("MLService", true);
        data.put("taxonomyString", taxonomyString);

        JSONArray maIds = new JSONArray();
        maIds.put(ma.getIdInt());
        data.put("mediaAssetIds", maIds);
        return data;
    }

    public JSONObject createJobData(Annotation ann, String taxonomyString) {
        JSONObject data = new JSONObject();

        data.put("MLService", true);
        data.put("taxonomyString", taxonomyString);

        JSONArray annIds = new JSONArray();
        annIds.put(ann.getId());
        data.put("annotationIds", annIds);
        return data;
    }

    public void processQueueJob(JSONObject jobData) {
        System.out.println("#################################################### processing: " +
            jobData.toString(8));
        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.setAction("MLService.processQueueJob");
        myShepherd.beginDBTransaction();
        FeatureType.initAll(myShepherd);
        Task task = myShepherd.getTask(jobData.optString("taskId", null));
        JSONArray ids = jobData.optJSONArray("mediaAssetIds");
        try {
            // got some asset ids
            if (ids != null) {
                for (String maId : Util.jsonArrayToStringList(ids)) {
                    System.out.println("[DEBUG] MLService.processQueueJob() maId=" + maId + " [" +
                        task + "]");
                    send(myShepherd.getMediaAsset(maId), jobData.optString("taxonomyString", null),
                        myShepherd);
                }
                // maybe annot ids?
            } else {
                ids = jobData.optJSONArray("annotationIds");
                if (ids != null) {
                    for (String annId : Util.jsonArrayToStringList(ids)) {
                        System.out.println("[DEBUG] MLService.processQueueJob() annId=" + annId +
                            " [" + task + "]");
                        send(myShepherd.getAnnotation(annId),
                            jobData.optString("taxonomyString", null), myShepherd);
                    }
                }
            }
            if (task != null) task.setStatus("completed");
        } catch (IAException iaex) {
            System.out.println("MLService.processQueueJob() threw " + iaex + " with jobData=" +
                jobData);
            iaex.printStackTrace();
            if (task != null) task.setStatus("error");
            if (iaex.shouldRequeue()) requeueJob(jobData, iaex.shouldIncrement());
        } finally {
            // we end up here after *each* annotation, so we are "done" when all annotations have been processed
            boolean taskComplete = areAllEmbeddingsExtracted(task);
            if (taskComplete) task.setCompletionDateInMilliseconds();
            myShepherd.commitDBTransaction();
            if (taskComplete) {
                // now we are done we can fake a callback to initiate identification
                JSONObject fakeResp = new JSONObject();
                fakeResp.put("embeddingExtraction", true);
                // taskComplete is only true if we have *some* annots
                JSONObject annMap = new JSONObject();
                for (Annotation ann : task.getObjectAnnotations()) {
                    MediaAsset ma = ann.getMediaAsset();
                    if (ma == null) continue; // snh
                    if (!annMap.has(ma.getId())) annMap.put(ma.getId(), new JSONArray());
                    annMap.getJSONArray(ma.getId()).put(ann.getId());
                }
                fakeResp.put("annotationMap", annMap);
                JSONObject cbRes = IBEISIA.processCallback(task.getId(), fakeResp,
                    myShepherd.getContext(), null);
                System.out.println("[DEBUG] MLService.processQueueJob() [" + task +
                    " complete] cbRes=" + cbRes);
            }
            myShepherd.closeDBTransaction();
        }
    }

    // true if all annotations "are done" from (trying to) extract embeddings
    private boolean areAllEmbeddingsExtracted(Task task) {
        if (task == null) return false;
        List<Annotation> anns = task.getObjectAnnotations();
        // we return false here because there is no reason to send to ident in this case
        if (Util.collectionIsEmptyOrNull(anns)) return false;
        // we iterate over annotations and only return false if we find one explicitly still
        // in processing state. this means *any* other (complete, error, etc) get counted as "done"
        for (Annotation ann : anns) {
            if (IBEISIA.STATUS_PROCESSING_MLSERVICE.equals(ann.getIdentificationStatus()))
                return false;
        }
        System.out.println(
            "[DEBUG] MLService.areAllEmbeddingsExtracted() fell thru (aka true) on " + anns.size() +
            " annots for " + task);
        return true;
    }

    public void requeueJob(JSONObject jobData, boolean increment) {
        System.out.println("+++ MLService.requeueJob(): increment=" + increment + "; jobData=" +
            jobData);
        // this handles a bunch of messiness, including max retries etc
        IAGateway.requeueJob(jobData, increment);
    }

    public void send(MediaAsset ma, String taxonomyString, Shepherd myShepherd)
    throws IAException {
        if (ma == null) throw new IAException("null MediaAsset passed");
        for (JSONObject conf : getConfigs(taxonomyString)) {
            JSONObject payload = createPayload(ma, conf);
            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/predict/",
                payload);
            // got results, now we try to use them
            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
                "; RESPONSE => " + res);
            List<Annotation> anns = processMediaAssetResults(ma, res);
            System.out.println("MLService.send() created " + anns.size() + " anns on " + ma + ": " +
                anns);
            // FIXME persist anns using myShepherd
            // FIXME send along to ident????? (but using vectors!!!????!)
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

    public void send(Annotation ann, String taxonomyString, Shepherd myShepherd)
    throws IAException {
        if (ann == null) throw new IAException("null Annotation passed");
        for (JSONObject conf : getConfigs(taxonomyString)) {
            JSONObject payload = createPayload(ann, conf);
            JSONObject res = sendPayload(conf.optString("api_endpoint", null) + "/extract/",
                payload);
            // got results, now we try to use them
            JSONObject logRes = new JSONObject(res.toString());
            if (logRes.optJSONArray("embeddings") != null)
                logRes.put("embeddings",
                    "TRUNCATED [length=" + logRes.getJSONArray("embeddings").toString().length() +
                    "]");
            System.out.println("MLService.send() conf=" + conf + "; payload=" + payload +
                "; RESPONSE => " + logRes);
            processAnnotationResults(ann, res, myShepherd);
            System.out.println("MLService.send() process results on " + ann);
        }
    }

    // not sure what (if anything) we need to return here
    public void processAnnotationResults(Annotation ann, JSONObject res, Shepherd myShepherd)
    throws IAException {
        if (res == null) throw new IAException("empty results");
        if (ann == null) throw new IAException("null Annotation");
        ann.setIdentificationStatus(IBEISIA.STATUS_COMPLETE_MLSERVICE);
        // res has everything we sent (bbox, model_id, etc) plus "embeddings_shape"(?) and:
        JSONArray embs = res.optJSONArray("embeddings");
        if (embs == null) throw new IAException("results has no embeddings array: " + res);
        // in our case we should have one embedding in there
        if ((embs.length() < 1) || (embs.optJSONArray(0) == null))
            throw new IAException("results has no embeddings array[0]: " + res);
        JSONArray vecArr = embs.getJSONArray(0);
        String method = res.optString("model_id", null);
        String methodVersion = null;
        // kinda hack version splitting here but...
        if ((method != null) && method.contains("-")) {
            String[] parts = method.split("\\-");
            method = parts[0];
            methodVersion = parts[1];
        }
        Embedding emb = new Embedding(ann, method, methodVersion, vecArr);
        // maybe this is unwise? could 2 embeddings *from different methods* have same vectors? TODO
        Embedding exists = ann.findEmbeddingByVector(emb);
        if (exists != null) {
            System.out.println("[WARNING] MLService.processAnnotationResults(): skipping; " + ann +
                " already has: " + exists);
            return;
        }
        ann.addEmbedding(emb);
        // FIXME persist or whatever????
        System.out.println("[DEBUG] MLService.processAnnotationResults(): added " + emb + " to " +
            ann);
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
            // throws IOException, java.net.ProtocolException
            JSONObject res = RestClient.postJSON(url, payload, null);
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

    // this is to request detection find an annotation and (optionally) return embedding as well
    public JSONObject createPayload(MediaAsset ma, JSONObject config)
    throws IAException {
        if ((config == null) || (ma == null))
            throw new IAException("MLService.createPayload() configuration problem with ma=" + ma +
                    "; config=" + config);
        JSONObject payload = new JSONObject(config.toString());
        payload.remove("api_endpoint");
        payload.put("image_uri", ma.webURL());
        // FIXME add embedding boolean/args
        return payload;
    }

    // this only gets the embedding, from a given (manual or pre-existing) Annotation
    public JSONObject createPayload(Annotation ann, JSONObject config)
    throws IAException {
        if ((config == null) || (ann == null))
            throw new IAException("MLService.createPayload() configuration problem with ann=" +
                    ann + "; config=" + config);
        MediaAsset ma = ann.getMediaAsset();
        if (ma == null)
            throw new IAException("MLService.createPayload() no MediaAsset for ann=" + ann);
        JSONObject payload = new JSONObject(config.toString());
        payload.remove("api_endpoint");
        payload.put("image_uri", ma.webURL());
        payload.put("bbox", ann.getBbox());
        payload.put("theta", ann.getTheta());
        return payload;
    }
}
