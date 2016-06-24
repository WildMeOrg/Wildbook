package org.ecocean.identity;

import org.ecocean.ImageAttributes;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.servlet.ServletUtilities;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URL;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import java.io.IOException;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.joda.time.DateTime;
import javax.servlet.http.HttpServletRequest;


public class IBEISIA {

    private static String SERVICE_NAME = "IBEISIA";

    private static HashMap<Integer,Boolean> alreadySentMA = new HashMap<Integer,Boolean>();
    private static HashMap<String,Boolean> alreadySentAnn = new HashMap<String,Boolean>();

    //public static JSONObject post(URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException {

    //a convenience way to send MediaAssets with no (i.e. with only the "trivial") Annotation
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return sendMediaAssets(mas, null);
    }

    //other is a HashMap of additional properties to build lists out of (e.g. Encounter ids and so on), that do not live in/on MediaAsset
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, HashMap<MediaAsset,HashMap<String,Object>> other) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddImages", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int ct = 0;

        //see: https://erotemic.github.io/ibeis/ibeis.web.html?highlight=add_images_json#ibeis.web.app.add_images_json
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_uuid_list", new ArrayList<JSONObject>());
        map.put("image_width_list", new ArrayList<Integer>());
        map.put("image_height_list", new ArrayList<Integer>());
        map.put("image_time_posix_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());

        for (MediaAsset ma : mas) {
            if (!needToSend(ma)) continue;
            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
            map.get("image_uri_list").add(mediaAssetToUri(ma));

            ImageAttributes iatt = ma.getImageAttributes();
            if (iatt == null) {
                map.get("image_width_list").add(0);
                map.get("image_height_list").add(0);
            } else {
                map.get("image_width_list").add((int) iatt.getWidth());
                map.get("image_height_list").add((int) iatt.getHeight());
            }

            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());

            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_time_posix_list").add(0);
            } else {
                map.get("image_time_posix_list").add((int)Math.floor(t.getMillis() / 1000));  //IBIES-IA wants seconds since epoch
            }
            markSent(ma);
            ct++;
        }

System.out.println("sendMediaAssets(): sending " + ct);
        if (ct < 1) return null;  //null for "none to send" ?  is this cool?
        return RestClient.post(url, new JSONObject(map));
    }



            //Annotation ann = new Annotation(ma, species);

    public static JSONObject sendAnnotations(ArrayList<Annotation> anns) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);

        int ct = 0;
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());

        for (Annotation ann : anns) {
            if (!needToSend(ann)) continue;
            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
            map.get("annot_species_list").add(ann.getSpecies());
            map.get("annot_bbox_list").add(ann.getBbox());
            markSent(ann);
            ct++;
        }

System.out.println("sendAnnotations(): sending " + ct);
        if (ct < 1) return null;

        //this should only be checking for missing images, i guess?
        boolean tryAgain = true;
        JSONObject res = null;
        while (tryAgain) {
            res = RestClient.post(url, new JSONObject(map));
            tryAgain = iaCheckMissing(res);
        }
        return res;
    }

    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, String baseUrl) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlStartIdentifyAnnotations", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
        URL url = new URL(u);

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("callback_url", baseUrl + "/IBEISIAGetJobStatus.jsp");
        ArrayList<JSONObject> qlist = new ArrayList<JSONObject>();
        ArrayList<JSONObject> tlist = new ArrayList<JSONObject>();

        for (Annotation ann : qanns) {
            qlist.add(toFancyUUID(ann.getUUID()));
        }
        for (Annotation ann : tanns) {
            tlist.add(toFancyUUID(ann.getUUID()));
        }
        map.put("qannot_uuid_list", qlist);
        map.put("dannot_uuid_list", tlist);

System.out.println("===================================== qlist & tlist =========================");
System.out.println(qlist + " callback=" + baseUrl + "/IBEISIAGetJobStatus.jsp");
System.out.println("tlist.size()=" + tlist.size());
        return RestClient.post(url, new JSONObject(map));
    }


    public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlStartDetectImages", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartDetectAnnotations is not set");
        URL url = new URL(u);

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("callback_url", baseUrl + "/IBEISIAGetJobStatus.jsp");
        ArrayList<JSONObject> malist = new ArrayList<JSONObject>();

        for (MediaAsset ma : mas) {
            malist.add(toFancyUUID(ma.getUUID()));
        }
        map.put("image_uuid_list", malist);

        return RestClient.post(url, new JSONObject(map));
    }


    public static JSONObject getJobStatus(String jobID) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobStatus", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobStatus is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    public static JSONObject getJobResult(String jobID) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobResult", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobResult is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }


    //null return means we are still waiting... JSONObject will have success property = true/false (and results or error)
    /*
       we get back an *array* like this:
             json_result: "[{"qaid": 492, "daid_list": [493], "score_list": [1.5081310272216797], "qauuid": {"__UUID__": "f6b27df2-5d81-4e62-b770-b56fe1dcf5c2"}, "dauuid_list": [{"__UUID__": "d88c974b-c746-49db-8178-e7b7414708cf"}]}]"
       there would be one element for each queried annotation (492 here)... but we are FOR NOW always only sending one.  we should TODO adapt for many-to-many eventually?
    */
    public static JSONObject getTaskResults(String taskID, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME, myShepherd);
        if ((logs == null) || (logs.size() < 1)) {
            rtn.put("success", false);
            rtn.put("error", "could not find any log of task ID = " + taskID);
            return rtn;
        }
        JSONObject last = logs.get(logs.size() - 1).getStatusJson();

// note: jobstatus == completed seems to be the thing we want
        if ("getJobStatus".equals(last.getString("_action")) && "unknown".equals(last.getJSONObject("_response").getJSONObject("response").getString("jobstatus"))) {
            rtn.put("success", false);
            rtn.put("details", last.get("_response"));
            rtn.put("error", "final log for task " + taskID + " was an unknown jobstatus, so results were not obtained");
            return rtn;
        }

        if (last.getString("_action").equals("getJobResult")) {
            if (last.getJSONObject("_response").getJSONObject("status").getBoolean("success") && "ok".equals(last.getJSONObject("_response").getJSONObject("response").getString("status"))) {
                rtn.put("success", true);
                rtn.put("_debug", last);

                JSONArray resOut = new JSONArray();
                JSONArray res = last.getJSONObject("_response").getJSONObject("response").getJSONArray("json_result"); //"should never" fail. HA!
                //JSONArray res = new JSONArray(last.getJSONObject("_response").getJSONObject("response").getString("json_result")); //"should never" fail. HA!

                for (int i = 0 ; i < res.length() ; i++) {
                    JSONObject el = new JSONObject();
                    el.put("score_list", res.getJSONObject(i).get("score_list"));
                    el.put("query_annot_uuid", fromFancyUUID(res.getJSONObject(i).getJSONObject("qauuid")));
                    JSONArray matches = new JSONArray();
                    JSONArray dlist = res.getJSONObject(i).getJSONArray("dauuid_list");
                    for (int d = 0 ; d < dlist.length() ; d++) {
                        matches.put(fromFancyUUID(dlist.getJSONObject(d)));
                    }
                    el.put("match_annot_list", matches);
                    resOut.put(el);
                }

                rtn.put("results", resOut);

            } else {
                rtn.put("error", "getJobResult for task " + taskID + " logged as either non successful or with a status not OK");
                rtn.put("details", last.get("_response"));
                rtn.put("success", false);
            }
            return rtn;
        }

        //TODO we could also do a comparison with when it was started to enable a failure due to timeout
        return null;  //if we fall through, it means we are still waiting ......
    }

    public static HashMap<String,Object> getTaskResultsAsHashMap(String taskID, Shepherd myShepherd) {
        JSONObject jres = getTaskResults(taskID, myShepherd);
        HashMap<String,Object> res = new HashMap<String,Object>();
        if (jres == null) {
            System.out.println("WARNING: getTaskResultsAsHashMap() had null results from getTaskResults(" + taskID + "); return empty HashMap");
            return res;
        }
        res.put("taskID", taskID);
        if (jres.has("success")) res.put("success", jres.get("success"));
        if (jres.has("error")) res.put("error", jres.get("error"));

        if (jres.has("results")) {
            HashMap<String,Object> rout = new HashMap<String,Object>();
            JSONArray r = jres.getJSONArray("results");
            for (int i = 0 ; i < r.length() ; i++) {
                if (r.getJSONObject(i).has("query_annot_uuid")) {
                    HashMap<String,Double> scores = new HashMap<String,Double>();
                    JSONArray m = r.getJSONObject(i).getJSONArray("match_annot_list");
                    JSONArray s = r.getJSONObject(i).getJSONArray("score_list");
                    for (int j = 0 ; j < m.length() ; j++) {
                        Encounter menc = Encounter.findByAnnotationId(m.getString(j), myShepherd);
                        scores.put(menc.getCatalogNumber(), s.getDouble(j));
                    }
                    Encounter enc = Encounter.findByAnnotationId(r.getJSONObject(i).getString("query_annot_uuid"), myShepherd);
                    rout.put(enc.getCatalogNumber(), scores);
                } 
            }
            res.put("results", rout);
        }

        return res;
    }


    public static boolean waitingOnTask(String taskID, Shepherd myShepherd) {
        JSONObject res = getTaskResults(taskID, myShepherd);
//System.out.println(" . . . . . . . . . . . . waitingOnTask(" + taskID + ") -> " + res);
        if (res == null) return true;
        return false;  //anything else means we are done (good or bad)
    }

/*
anyMethod failed with code=600
{"status": {"cache": -1, "message": "Missing image and/or annotation UUIDs (0, 1)", "code": 600, "success": false}, "response": {"missing_image_uuid_list": [], "missing_annot_uuid_list": [{"__UUID__": "523e8e9d-941c-4879-a5a6-aeafebf34f65"}]}}

########## iaCheckMissing res -> {"response":[],"status":{"message":"","cache":-1,"code":200,"success":true}}
===================================== qlist & tlist =========================
[{"__UUID__":"cd67cf0f-f2f1-4b16-89e3-e00e5584d23a"}]
tlist.size()=1885
########## iaCheckMissing res -> {"response":{"missing_annot_uuid_list":[{"__UUID__":"523e8e9d-941c-4879-a5a6-aeafebf34f65"}],"missing_image_uuid_list":[]},"status":{"message":"Missing image and/or annotation UUIDs (0, 1)","cache":-1,"code":600,"success":false}}
WARN: IBEISIA.beginIdentity() failed due to an exception: org.json.JSONException: JSONObject["missing_image_annot_list"] not found.
org.json.JSONException: JSONObject["missing_image_annot_list"] not found.
*/
    //should return true if we attempted to add missing and caller should try again
    private static boolean iaCheckMissing(JSONObject res) {
System.out.println("########## iaCheckMissing res -> " + res);
//if (res != null) throw new RuntimeException("fubar!");
        if (!((res != null) && (res.getJSONObject("status") != null) && (res.getJSONObject("status").getInt("code") == 600))) return false;  // not a needy 600
        boolean tryAgain = false;

//TODO handle loop where we keep trying to add same objects but keep failing (e.g. store count of attempts internally in class?)

        if ((res.getJSONObject("response") != null) && res.getJSONObject("response").has("missing_image_uuid_list")) {
            JSONArray list = res.getJSONObject("response").getJSONArray("missing_image_uuid_list");
            if (list.length() > 0) {
                for (int i = 0 ; i < list.length() ; i++) {
                    String uuid = fromFancyUUID(list.getJSONObject(i));
System.out.println("**** FAKE ATTEMPT to sendMediaAssets: uuid=" + uuid);
                    //TODO $##@*&!! need to have a way to load MediaAsset by uuid.  i knew it. :(
                }
            }
        }

        if ((res.getJSONObject("response") != null) && res.getJSONObject("response").has("missing_annot_uuid_list")) {
            JSONArray list = res.getJSONObject("response").getJSONArray("missing_annot_uuid_list");
            if (list.length() > 0) {
                ArrayList<Annotation> anns = new ArrayList<Annotation>();
                Shepherd myShepherd = new Shepherd("context0");
                myShepherd.beginDBTransaction();
                try{
                  for (int i = 0 ; i < list.length() ; i++) {
                      String uuid = fromFancyUUID(list.getJSONObject(i));
                      Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, uuid), true)));
                      anns.add(ann);
                  }
                }
                catch(Exception e){e.printStackTrace();}
                finally{
                  myShepherd.rollbackDBTransaction();
                  myShepherd.closeDBTransaction();
                }
                //would this ever recurse? seems like a 600 would only happen inside sendAnnotations for missing MediaAssets. is this true? TODO
System.out.println("**** attempting to make up for missing Annotation(s): " + anns.toString());
                JSONObject srtn = null;
                try {
                    sendAnnotations(anns);
                } catch (Exception ex) { }
System.out.println(" returned --> " + srtn);
                if ((srtn != null) && (srtn.getJSONObject("status") != null) && srtn.getJSONObject("status").getBoolean("success")) tryAgain = true;  //it "worked"?
            }
        }
System.out.println("iaCheckMissing -> " + tryAgain);
        return tryAgain;
    }


    private static Object mediaAssetToUri(MediaAsset ma) {
//System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
        if (ma.getStore() instanceof LocalAssetStore) {
            //return ma.localPath().toString(); //nah, lets skip local and go for "url" flavor?
            return ma.webURL().toString();
        } else if (ma.getStore() instanceof S3AssetStore) {
            return ma.getParameters();
/*
            JSONObject params = ma.getParameters();
            if (params == null) return null;
            //return "s3://s3.amazon.com/" + params.getString("bucket") + "/" + params.getString("key");
            JSONObject b = new JSONObject();
            b.put("bucket", params.getString("bucket"));
            b.put("key", params.getString("key"));
            return b;
*/
        } else {
            return ma.toString();
        }
    }


    //actually ties the whole thing together and starts a job with all the pieces needed
    public static JSONObject beginIdentify(ArrayList<Encounter> queryEncs, ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String taskID, String baseUrl, String context) {
        //TODO possibly could exclude qencs from tencs?
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();  //0th item will have "query" encounter
        ArrayList<Annotation> qanns = new ArrayList<Annotation>();
        ArrayList<Annotation> tanns = new ArrayList<Annotation>();
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();

        if (queryEncs.size() < 1) {
            results.put("error", "queryEncs is empty");
            return results;
        }
        if (targetEncs.size() < 1) {
            results.put("error", "targetEncs is empty");
            return results;
        }

        log(taskID, jobID, new JSONObject("{\"_action\": \"init\"}"), context);

        try {
            for (Encounter enc : queryEncs) {
/*
                //MediaAsset ma = enc.spotImageAsMediaAsset(baseDir, myShepherd);
                MediaAsset ma = enc.findOneMediaByLabel(myShepherd, "_spot");
System.out.println("find _spot on " + enc.getCatalogNumber() + " -> " + ma);
*/
                ArrayList<Annotation> annotations = enc.getAnnotations();
                for (Annotation ann : annotations) {
                    allAnns.add(ann);
                    qanns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma != null) mas.add(ma);
                }
            }
            for (Encounter enc : targetEncs) {
                ArrayList<Annotation> annotations = enc.getAnnotations();
                for (Annotation ann : annotations) {
                    allAnns.add(ann);
                    tanns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma != null) mas.add(ma);
                }
            }

/*
System.out.println("======= beginIdentify (qanns, tanns, allAnns) =====");
System.out.println(qanns);
System.out.println(tanns);
System.out.println(allAnns);
*/
            results.put("sendMediaAssets", sendMediaAssets(mas));
            results.put("sendAnnotations", sendAnnotations(allAnns));

            //this should attempt to repair missing Annotations
            boolean tryAgain = true;
            JSONObject identRtn = null;
            while (tryAgain) {
                identRtn = sendIdentify(qanns, tanns, baseUrl);
                tryAgain = iaCheckMissing(identRtn);
            }
            results.put("sendIdentify", identRtn);

            //if ((identRtn != null) && (identRtn.get("status") != null) && identRtn.get("status")  //TODO check success == true  :/
//########## iaCheckMissing res -> {"response":[],"status":{"message":"","cache":-1,"code":200,"success":true}}
            if ((identRtn != null) && identRtn.has("status") && identRtn.getJSONObject("status").getBoolean("success")) {
                jobID = identRtn.get("response").toString();
                results.put("success", true);
            } else {
System.out.println("beginIdentify() unsuccessful on sendIdentify(): " + identRtn);
                results.put("error", identRtn.get("status"));
                results.put("success", false);
            }

        } catch (Exception ex) {  //most likely from sendFoo()
            System.out.println("WARN: IBEISIA.beginIdentity() failed due to an exception: " + ex.toString());
            ex.printStackTrace();
            results.put("success", false);
            results.put("error", ex.toString());
        }

        JSONObject jlog = new JSONObject();
        jlog.put("_action", "sendIdentify");
        jlog.put("_response", results);
        log(taskID, jobID, jlog, context);

        return results;
    }


    //a slightly different flavor -- we can explicitely pass the query annotation
    public static JSONObject beginIdentify(Annotation qann, ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String taskID, String baseUrl, String context) {
        //TODO possibly could exclude qencs from tencs?
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        ArrayList<Annotation> tanns = new ArrayList<Annotation>();
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();

        if (targetEncs.size() < 1) {
            results.put("error", "targetEncs is empty");
            return results;
        }

        log(taskID, qann.getId(), jobID, new JSONObject("{\"_action\": \"init\"}"), context);

        try {
            allAnns.add(qann);
            MediaAsset qma = qann.getDerivedMediaAsset();
            if (qma == null) qma = qann.getMediaAsset();
            if (qma != null) mas.add(qma);

            for (Encounter enc : targetEncs) {
                ArrayList<Annotation> annotations = enc.getAnnotations();
                for (Annotation ann : annotations) {
                    if (qann.getId().equals(ann.getId())) continue;  //skip the query annotation
                    allAnns.add(ann);
                    tanns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma != null) mas.add(ma);
                }
            }

            results.put("sendMediaAssets", sendMediaAssets(mas));
            results.put("sendAnnotations", sendAnnotations(allAnns));

            //this should attempt to repair missing Annotations
            boolean tryAgain = true;
            JSONObject identRtn = null;
            while (tryAgain) {
                ArrayList<Annotation> qanns = new ArrayList<Annotation>();
                qanns.add(qann);
                identRtn = sendIdentify(qanns, tanns, baseUrl);
                tryAgain = iaCheckMissing(identRtn);
            }
            results.put("sendIdentify", identRtn);

            //if ((identRtn != null) && (identRtn.get("status") != null) && identRtn.get("status")  //TODO check success == true  :/
//########## iaCheckMissing res -> {"response":[],"status":{"message":"","cache":-1,"code":200,"success":true}}
            if ((identRtn != null) && identRtn.has("status") && identRtn.getJSONObject("status").getBoolean("success")) {
                jobID = identRtn.get("response").toString();
                results.put("success", true);
            } else {
System.out.println("beginIdentify() unsuccessful on sendIdentify(): " + identRtn);
                results.put("error", identRtn.get("status"));
                results.put("success", false);
            }

        } catch (Exception ex) {  //most likely from sendFoo()
            System.out.println("WARN: IBEISIA.beginIdentity() failed due to an exception: " + ex.toString());
            ex.printStackTrace();
            results.put("success", false);
            results.put("error", ex.toString());
        }

        JSONObject jlog = new JSONObject();
        jlog.put("_action", "sendIdentify");
        jlog.put("_response", results);
        log(taskID, jobID, jlog, context);

        return results;
    }



    public static IdentityServiceLog log(String taskID, String jobID, JSONObject jlog, String context) {
        return log(taskID, null, jobID, jlog, context);
    }
    public static IdentityServiceLog log(String taskID, String objectID, String jobID, JSONObject jlog, String context) {
//System.out.println("#LOG: taskID=" + taskID + ", jobID=" + jobID + " --> " + jlog.toString());
        IdentityServiceLog log = new IdentityServiceLog(taskID, objectID, SERVICE_NAME, jobID, jlog);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.beginDBTransaction();
        try{
          log.save(myShepherd);
        }
        catch(Exception e){e.printStackTrace();}
        finally{
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
        }

        return log;
    }


    //this finds the taskID associated with this IBEIS-IA jobID
    public static String findTaskIDFromJobID(String jobID, Shepherd myShepherd) {
	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByServiceJobID(SERVICE_NAME, jobID, myShepherd);
        if (logs == null) return null;
        Collections.reverse(logs);
        for (IdentityServiceLog l : logs) {  //reverse it, so we get most recent first
            if (l.getTaskID() != null) return l.getTaskID();
        }
        return null;
    }

    public static String[] findTaskIDsFromObjectID(String objectID, Shepherd myShepherd) {
	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByObjectID(SERVICE_NAME, objectID, myShepherd);
        if ((logs == null) || (logs.size() < 1)) return null;
        
        String[] ids = new String[logs.size()];
        int ct = 0;
        for (IdentityServiceLog l : logs) {
            if (l.getTaskID() == null) continue;
            if (Arrays.asList(ids).contains(l.getTaskID())) continue;
            ids[ct] = l.getTaskID();
            ct++;
        }
        return ids;
    }

    public static String findJobIDFromTaskID(String taskID, Shepherd myShepherd) {
	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME, myShepherd);
        if ((logs == null) || (logs.size() < 1)) return null;

        String jobID = logs.get(logs.size() - 1).getServiceJobID();
        if ("-1".equals(jobID)) return null;
        return jobID;
    }


    // IBEIS-IA wants a uuid as a single-key json object like: {"__UUID__": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx"} so we use these to go back and forth
    public static String fromFancyUUID(JSONObject u) {
        if (u == null) return null;
        return u.optString("__UUID__", null);
    }
    public static JSONObject toFancyUUID(String u) {
        JSONObject j = new JSONObject();
        j.put("__UUID__", u);
        return j;
    }


    private static boolean needToSend(MediaAsset ma) {
        //return true;
        return ((alreadySentMA.get(ma.getId()) == null) || !alreadySentMA.get(ma.getId()));
    }
    private static void markSent(MediaAsset ma) {
        alreadySentMA.put(ma.getId(), true);
    }
    private static boolean needToSend(Annotation ann) {
        //return true;
        return ((alreadySentAnn.get(ann.getId()) == null) || !alreadySentAnn.get(ann.getId()));
    }
    private static void markSent(Annotation ann) {
        alreadySentAnn.put(ann.getId(), true);
    }

/*   no longer needed??
    public static JSONObject send(URL url, JSONObject jobj) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
System.out.println("SENDING: ------\n" + jobj.toString() + "\n---------- to " + iaUrl.toString());
        JSONObject jrtn = RestClient.post(iaUrl, jobj);
System.out.println("RESPONSE:\n" + jrtn.toString());
        return jrtn;
    }
*/



/*
image_attrs = {
    ~'image_rowid': 'INTEGER',
    'image_uuid': 'UUID',
    'image_uri': 'TEXT',
    'image_ext': 'TEXT',
    *'image_original_name': 'TEXT',
    'image_width': 'INTEGER',
    'image_height': 'INTEGER',
    *'image_time_posix': 'INTEGER',
    *'image_gps_lat': 'REAL',
    *'image_gps_lon': 'REAL',
    !'image_toggle_enabled': 'INTEGER',
    !'image_toggle_reviewed': 'INTEGER',
    ~'image_note': 'TEXT',
    *'image_timedelta_posix': 'INTEGER',
    *'image_original_path': 'TEXT',
    !'image_location_code': 'TEXT',
    *'contributor_tag': 'TEXT',
    *'party_tag': 'TEXT',
}
*/

/*
    public static JSONObject imageJSONObjectFromMediaAsset(MediaAsset ma) {
        JSONObject obj = new JSONObject();
        obj.put("image_uuid", ma.getUUID());
        ImageAttributes iatt = ma.getImageAttributes();
        obj.put("image_width", (int) iatt.getWidth());
        obj.put("image_height", (int) iatt.getHeight());
        obj.put("image_ext", iatt.getExtension());

        JSONObject params = new JSONObject(ma.getParameters(), JSONObject.getNames(ma.getParameters()));
        params.put("store_type", ma.getStore().getType());
        obj.put("image_storage_parameters", params);
        return obj;
    }
*/


        //String baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
    public static ArrayList<String> startTrainingJobs(ArrayList<Encounter> encs, String taskPrefix, String taxonomyString, Shepherd myShepherd, String baseUrl, String context) {
        ArrayList<String> ids = new ArrayList<String>();
System.out.println("beginning IBEIS-IA training jobs on " + encs.size() + " encounters (taskPrefix " + taskPrefix + ")");
        for(int i = 0 ; i < encs.size() ; i++) {
            Encounter qenc = encs.get(i);
            ArrayList<Encounter> qencs=new ArrayList<Encounter>();
            qencs.add(qenc);
            ArrayList<Encounter> tencs=new ArrayList<Encounter>();
            for (int j = (i+1) ; j < encs.size() ; j++) {
              tencs.add(encs.get(j));
            } 
            String taskID = taskPrefix + qenc.getEncounterNumber();
System.out.println(i + ") beginIdentify (taskID=" + taskID + ") ========================================================================================");
            JSONObject res = IBEISIA.beginIdentify(qencs, tencs, myShepherd, taxonomyString, taskID, baseUrl, context);
            if (res.optBoolean("success")) {
                ids.add(taskID);
            } else {
                System.out.println("WARNING - could not start job for " + taskID + ": " + res.optString("error", "[unknown error]") + "; skipping");
            }
        }
        return ids;
    }

    public static void waitForTrainingJobs(ArrayList<String> taskIds, Shepherd myShepherd) {
        boolean stillWaiting = true;
        int countdown = 100;
        while (stillWaiting && (countdown > 0)) {
            countdown--;
            stillWaiting = false; //optimism; prove us wrong
            int idLen = taskIds.size();
            for (int i = 0 ; i < idLen ; i++) {
                if (waitingOnTask(taskIds.get(i), myShepherd)) {
System.out.println("++++ waitForTrainingJobs() still waiting on " + taskIds.get(i) + " so will sleep a while (countdown=" + countdown + "; passed " + i + " of " + idLen +")");
                    stillWaiting = true;
                    break; //this is cause enough to sleep for a bit -- we dont need to check any more!
                }
            }
            if (stillWaiting) {
                try { Thread.sleep(3000); } catch (java.lang.InterruptedException ex) {}
            }
        }
System.out.println("!!!! waitForTrainingJobs() has finished.");
    }
    

    
    /*
     * This static method sends all annotations and media assets for a species in Wildbook to Image Analysis in preparation for future matching.
     * It basically primes the system.
     */
    public static JSONObject primeImageAnalysisForSpecies(ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String baseUrl, String context) {
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();  //0th item will have "query" encounter
        ArrayList<Annotation> tanns = new ArrayList<Annotation>();
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();

        
        if (targetEncs.size() < 1) {
            results.put("error", "targetEncs is empty");
            return results;
        }

        log("Prime image analysis for "+species, jobID, new JSONObject("{\"_action\": \"init\"}"), context);

        try {
            
            for (Encounter enc : targetEncs) {
                ArrayList<Annotation> annotations = enc.getAnnotations();
                for (Annotation ann : annotations) {
                    allAnns.add(ann);
                    tanns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma != null) mas.add(ma);
                }
            }

/*
System.out.println("======= beginIdentify (qanns, tanns, allAnns) =====");
System.out.println(qanns);
System.out.println(tanns);
System.out.println(allAnns);
*/
            results.put("sendMediaAssets", sendMediaAssets(mas));
            results.put("sendAnnotations", sendAnnotations(allAnns));

            //this should attempt to repair missing Annotations
            
            /*
            boolean tryAgain = true;
            JSONObject identRtn = null;
            while (tryAgain) {
                identRtn = sendIdentify(qanns, tanns, baseUrl);
                tryAgain = iaCheckMissing(identRtn);
            }
            results.put("sendIdentify", identRtn);
            

            //if ((identRtn != null) && (identRtn.get("status") != null) && identRtn.get("status")  //TODO check success == true  :/
//########## iaCheckMissing res -> {"response":[],"status":{"message":"","cache":-1,"code":200,"success":true}}
            if ((identRtn != null) && identRtn.has("status") && identRtn.getJSONObject("status").getBoolean("success")) {
                jobID = identRtn.get("response").toString();
                results.put("success", true);
            } else {
System.out.println("beginIdentify() unsuccessful on sendIdentify(): " + identRtn);
                results.put("error", identRtn.get("status"));
                results.put("success", false);
            }
            */

        results.put("success", true);    
            
        } 
        catch (Exception ex) {  //most likely from sendFoo()
            System.out.println("WARN: IBEISIA.primeImageAnalysisForSpecies() failed due to an exception: " + ex.toString());
            ex.printStackTrace();
            results.put("success", false);
            results.put("error", ex.toString());
        }

        JSONObject jlog = new JSONObject();
        jlog.put("_action", "primeImageAnalysisForSpecies: "+species);
        jlog.put("_response", results);
        log("Prime image analysis for "+species, jobID, jlog, context);

        return results;
    }
    

//placeholder for the real thing...
    public static JSONObject iaStatus(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        JSONObject rtn = new JSONObject();
        rtn.put("iaEnabled", (CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context) == null));
        rtn.put("timestamp", System.currentTimeMillis());
        return rtn;
    }

}
