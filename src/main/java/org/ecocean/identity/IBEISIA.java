package org.ecocean.identity;

import org.ecocean.ImageAttributes;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import org.apache.commons.lang3.StringUtils;
import javax.servlet.http.HttpServletRequest;


public class IBEISIA {

    private static final Map<String, String[]> speciesMap;
    static {
        speciesMap = new HashMap<String, String[]>();
        speciesMap.put("zebra_plains", new String[]{"Equus","quagga"});
        speciesMap.put("zebra_grevys", new String[]{"Equus","grevyi"});
    }

    public static String STATUS_PENDING = "pending";  //pending review (needs action by user)
    public static String STATUS_COMPLETE = "complete";  //process is done
    public static String STATUS_PROCESSING = "processing";  //off at IA, awaiting results
    public static String STATUS_ERROR = "error";

    private static long TIMEOUT_DETECTION = 20 * 60 * 1000;   //in milliseconds
    private static String SERVICE_NAME = "IBEISIA";
    private static String IA_UNKNOWN_NAME = "____";

    private static HashMap<Integer,Boolean> alreadySentMA = new HashMap<Integer,Boolean>();
    private static HashMap<String,Boolean> alreadySentAnn = new HashMap<String,Boolean>();
    //private static HashMap<String,String> identificationMatchingState = new HashMap<String,String>();
    private static HashMap<String,String> identificationUserActiveTaskId = new HashMap<String,String>();

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

    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, JSONObject queryConfigDict,
                                          JSONObject userConfidence, String baseUrl)
                                          throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlStartIdentifyAnnotations", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
        URL url = new URL(u);

        Shepherd myShepherd = new Shepherd("context0");

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("callback_url", baseUrl + "/IBEISIAGetJobStatus.jsp");
        if (queryConfigDict != null) map.put("query_config_dict", queryConfigDict);
        map.put("matching_state_list", IBEISIAIdentificationMatchingState.allAsJSONArray(myShepherd));  //this is "universal"
        if (userConfidence != null) map.put("user_confidence", userConfidence);

        ArrayList<JSONObject> qlist = new ArrayList<JSONObject>();
        ArrayList<JSONObject> tlist = new ArrayList<JSONObject>();
        ArrayList<String> qnlist = new ArrayList<String>();
        ArrayList<String> tnlist = new ArrayList<String>();

///note: for names here, we make the gigantic assumption that they individualID has been migrated to uuid already!
        for (Annotation ann : qanns) {
            qlist.add(toFancyUUID(ann.getUUID()));
/* jonc now fixed it so we can have null/unknown ids... but apparently this needs to be "____" (4 underscores) ; also names are now just strings (not uuids)
            //TODO i guess (???) we need some kinda ID for query annotations (even tho we dont know who they are); so wing it?
            qnlist.add(toFancyUUID(Util.generateUUID()));
*/
            qnlist.add(IA_UNKNOWN_NAME);
        }
        for (Annotation ann : tanns) {
            tlist.add(toFancyUUID(ann.getUUID()));
            String indivId = ann.findIndividualId(myShepherd);
/*  see note above about names
            if (Util.isUUID(indivId)) {
                tnlist.add(toFancyUUID(indivId));
            } else if (indivId == null) {
                tnlist.add(toFancyUUID(Util.generateUUID()));  //we must have one... meh?  TODO fix (and see above)
            } else {
                tnlist.add(indivId);
            }
*/
            //argh we need to standardize this and/or have a method. :/
            if ((indivId == null) || (indivId.toLowerCase().equals("unassigned"))) {
                tnlist.add(IA_UNKNOWN_NAME);
            } else {
                tnlist.add(indivId);
            }
        }
//query_config_dict={'pipeline_root' : 'BC_DTW'}

        map.put("query_annot_uuid_list", qlist);
        map.put("database_annot_uuid_list", tlist);
        map.put("query_annot_name_list", qnlist);
        map.put("database_annot_name_list", tnlist);

        
System.out.println("===================================== qlist & tlist =========================");
System.out.println(qlist + " callback=" + baseUrl + "/IBEISIAGetJobStatus.jsp");
System.out.println("tlist.size()=" + tlist.size());
System.out.println(map);
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

    //note: this passes directly to IA so can be problematic! (ia down? and more importantly: ia restarted so job # is diff and old job is gone!)
    //  better(?) to use getJobResultLogged() below!
    public static JSONObject getJobResult(String jobID) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobResult", "context0");
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobResult is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    public static JSONObject getJobResultLogged(String jobID, Shepherd myShepherd) {
        String taskId = findTaskIDFromJobID(jobID, myShepherd);
        if (taskId == null) {
            System.out.println("getJobResultLogged(" + jobID + ") could not find taskId for this job");
            return null;
        }
System.out.println("getJobResultLogged(" + jobID + ") -> taskId " + taskId);
        //note: this is a little(!) in that it relies on the "raw" results living in "_debug" from getTaskResults so we can reconstruct it to be the output
        //  that getJobResult() above gives.  :/
        JSONObject tr = getTaskResults(taskId, myShepherd);
        if ((tr == null) || (tr.optJSONObject("_debug") == null) || (tr.getJSONObject("_debug").optJSONObject("_response") == null)) return null;
        if (tr.optJSONArray("_objectIds") != null)  //if we have this, lets bubble it up as part of this return
            tr.getJSONObject("_debug").getJSONObject("_response").put("_objectIds", tr.getJSONArray("_objectIds"));
        return tr.getJSONObject("_debug").getJSONObject("_response");
    }


    //null return means we are still waiting... JSONObject will have success property = true/false (and results or error)
    /*
       we get back an *array* like this:
             json_result: "[{"qaid": 492, "daid_list": [493], "score_list": [1.5081310272216797], "qauuid": {"__UUID__": "f6b27df2-5d81-4e62-b770-b56fe1dcf5c2"}, "dauuid_list": [{"__UUID__": "d88c974b-c746-49db-8178-e7b7414708cf"}]}]"
       there would be one element for each queried annotation (492 here)... but we are FOR NOW always only sending one.  we should TODO adapt for many-to-many eventually?
    */
    public static JSONObject OLDgetTaskResults(String taskID, Shepherd myShepherd) {
        JSONObject rtn = getTaskResultsBasic(taskID, myShepherd);
        if ((rtn == null) || !rtn.optBoolean("success", false)) return rtn;  //all the ways we can fail
        JSONArray resOut = new JSONArray();
        JSONArray res = (JSONArray)rtn.get("_json_result");

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
        rtn.remove("_json_result");
        return rtn;
    }


    //this is "new" identification results
    public static JSONObject getTaskResults(String taskID, Shepherd myShepherd) {
        JSONObject rtn = getTaskResultsBasic(taskID, myShepherd);
        if ((rtn == null) || !rtn.optBoolean("success", false)) return rtn;  //all the ways we can fail
        JSONObject res = rtn.optJSONObject("_json_result");
        rtn.put("results", res);
        rtn.remove("_json_result");
        return rtn;
/*

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
        rtn.remove("_json_result");
        return rtn;
*/
    }


    public static JSONObject getTaskResultsDetect(String taskID, Shepherd myShepherd) {
        JSONObject rtn = getTaskResultsBasic(taskID, myShepherd);
        if ((rtn == null) || !rtn.optBoolean("success", false)) return rtn;  //all the ways we can fail
        JSONArray resOut = new JSONArray();
/*
        JSONArray res = (JSONObject)rtn.get("_json_result");

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
        rtn.remove("_json_result");
*/
        return rtn;
    }



    public static JSONObject getTaskResultsBasic(String taskID, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME, myShepherd);
        return getTaskResultsBasic(taskID, logs);
    }

    //note: log must be in chrono order by timestamp ASC
    public static JSONObject getTaskResultsBasic(String taskID, ArrayList<IdentityServiceLog> logs) {
        JSONObject rtn = new JSONObject();
///System.out.println("getTaskResultsBasic logs -->\n" + logs);
        if ((logs == null) || (logs.size() < 1)) {
            rtn.put("success", false);
            rtn.put("error", "could not find any log of task ID = " + taskID);
            return rtn;
        }

        //since "we can", lets also get the object ids here....
        String[] objIds = IdentityServiceLog.findObjectIDs(logs);
//System.out.println("objIds -> " + objIds);

        //we have to walk through (newest to oldest) and find the (first) one with _action == 'getJobResult' ... but we should also stop on getJobStatus
        // if we see that first -- it means we sent a job and are awaiting results.
        JSONObject last = logs.get(logs.size() - 1).getStatusJson();  //just start with something (most recent at all)
        for (int i = logs.size() - 1 ; i >= 0 ; i--) {
            JSONObject j = logs.get(i).getStatusJson();
            if (j.optString("_action").equals("getJobResult") || j.optString("_action").equals("getJobStatus")) {
                last = j;
                break;
            }
        }

// note: jobstatus == completed seems to be the thing we want
        if ("getJobStatus".equals(last.optString("_action")) && "unknown".equals(last.getJSONObject("_response").getJSONObject("response").getString("jobstatus"))) {
            rtn.put("success", false);
            rtn.put("details", last.get("_response"));
            rtn.put("error", "final log for task " + taskID + " was an unknown jobstatus, so results were not obtained");
            return rtn;
        }
System.out.println("-------------\n" + last.toString() + "\n----------");

        if (last.getString("_action").equals("getJobResult")) {
            if (last.getJSONObject("_response").getJSONObject("status").getBoolean("success") && "ok".equals(last.getJSONObject("_response").getJSONObject("response").getString("status"))) {
                rtn.put("success", true);
                rtn.put("_debug", last);
                rtn.put("_json_result", last.getJSONObject("_response").getJSONObject("response").opt("json_result")); //"should never" fail. HA!
                if (rtn.get("_json_result") == null) {
                    rtn.put("success", false);
                    rtn.put("error", "json_result seems empty");
                }
/*

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

*/
            } else {
                rtn.put("error", "getJobResult for task " + taskID + " logged as either non successful or with a status not OK");
                rtn.put("details", last.get("_response"));
                rtn.put("success", false);
            }

//System.out.println("objIds ??? " + objIds);
            if ((objIds != null) && (objIds.length > 0)) rtn.put("_objectIds", new JSONArray(Arrays.asList(objIds)));
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


    //like below, but you can pass Encounters (which will be mined for Annotations and passed along)
    public static JSONObject beginIdentify(ArrayList<Encounter> queryEncs, ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String taskID, String baseUrl, String context) {
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        if ((queryEncs == null) || (queryEncs.size() < 1)) {
            results.put("error", "queryEncs is empty");
            return results;
        }
        if ((targetEncs == null) || (targetEncs.size() < 1)) {
            results.put("error", "targetEncs is empty");
            return results;
        }

        ArrayList<Annotation> qanns = new ArrayList<Annotation>();
        ArrayList<Annotation> tanns = new ArrayList<Annotation>();
        for (Encounter enc : queryEncs) {
            if (enc.getAnnotations() != null) qanns.addAll(enc.getAnnotations());
        }
        for (Encounter enc : targetEncs) {
            if (enc.getAnnotations() != null) tanns.addAll(enc.getAnnotations());
        }

        return beginIdentifyAnnotations(qanns, tanns, null, null, myShepherd, species, taskID, baseUrl, context);
    }

    //actually ties the whole thing together and starts a job with all the pieces needed
    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, JSONObject queryConfigDict,
                                                      JSONObject userConfidence, Shepherd myShepherd, String species, String taskID, String baseUrl, String context) {
        //TODO possibly could exclude qencs from tencs?
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();  //0th item will have "query" encounter
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();

        log(taskID, jobID, new JSONObject("{\"_action\": \"initIdentify\"}"), context);

        try {
            for (Annotation ann : qanns) {
                allAnns.add(ann);
                MediaAsset ma = ann.getDerivedMediaAsset();
                if (ma == null) ma = ann.getMediaAsset();
                if (ma != null) mas.add(ma);
            }
            
            for (Annotation ann : tanns) {
                allAnns.add(ann);
                MediaAsset ma = ann.getDerivedMediaAsset();
                if (ma == null) ma = ann.getMediaAsset();
                if (ma != null) mas.add(ma);
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
                identRtn = sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl);
                tryAgain = iaCheckMissing(identRtn);
            }
            results.put("sendIdentify", identRtn);

System.out.println("sendIdentify ---> " + identRtn);
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
        String[] sa = null;
        return log(taskID, sa, jobID, jlog, context);
    }

    public static IdentityServiceLog log(String taskID, String objectID, String jobID, JSONObject jlog, String context) {
        String[] sa = new String[1];
        sa[0] = objectID;
        return log(taskID, sa, jobID, jlog, context);
    }

    public static IdentityServiceLog log(String taskID, String[] objectIDs, String jobID, JSONObject jlog, String context) {
//System.out.println("#LOG: taskID=" + taskID + ", jobID=" + jobID + " --> " + jlog.toString());
        IdentityServiceLog log = new IdentityServiceLog(taskID, objectIDs, SERVICE_NAME, jobID, jlog);
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


    //this finds the *most recent* taskID associated with this IBEIS-IA jobID
    public static String findTaskIDFromJobID(String jobID, Shepherd myShepherd) {
	ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByServiceJobID(SERVICE_NAME, jobID, myShepherd);
        if (logs == null) return null;
        for (int i = logs.size() - 1 ; i >= 0 ; i--) {
            if (logs.get(i).getTaskID() != null) return logs.get(i).getTaskID();  //get first one we find. too bad!
        }
        return null;
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


//{"xtl":910,"height":413,"theta":0,"width":444,"class":"giraffe_reticulated","confidence":0.2208,"ytl":182}
    public static Annotation createAnnotationFromIAResult(JSONObject jann, MediaAsset asset, Shepherd myShepherd) {
        Annotation ann = convertAnnotation(asset, jann);
        if (ann == null) return null;
        Encounter enc = new Encounter(ann);
        String[] sp = convertSpecies(ann.getSpecies());
        if (sp.length > 0) enc.setGenus(sp[0]);
        if (sp.length > 1) enc.setSpecificEpithet(sp[1]);
//TODO other fields on encounter!!  (esp. dates etc)
        Occurrence occ = asset.getOccurrence();
        if (occ != null) {
            enc.setOccurrenceID(occ.getOccurrenceID());
            occ.addEncounter(enc);
        }
        myShepherd.getPM().makePersistent(ann);
        myShepherd.getPM().makePersistent(enc);
        if (occ != null) myShepherd.getPM().makePersistent(occ);
System.out.println("* CREATED " + ann + " and Encounter " + enc.getCatalogNumber());
        return ann;
    }

    public static Annotation convertAnnotation(MediaAsset ma, JSONObject iaResult) {
        if (iaResult == null) return null;
        Feature ft = ma.generateFeatureFromBbox(iaResult.optDouble("width", 0), iaResult.optDouble("height", 0),
                                                iaResult.optDouble("xtl", 0), iaResult.optDouble("ytl", 0));
System.out.println("convertAnnotation() generated ft = " + ft + "; params = " + ft.getParameters());
        return new Annotation(convertSpeciesToString(iaResult.optString("class", null)), ft);
    }

    public static String convertSpeciesToString(String iaClassLabel) {
        String[] s = convertSpecies(iaClassLabel);
        if (s == null) return null;
        return StringUtils.join(s, " ");
    }
    public static String[] convertSpecies(String iaClassLabel) {
        if (iaClassLabel == null) return null;
        if (speciesMap.containsKey(iaClassLabel)) return speciesMap.get(iaClassLabel);
        return iaClassLabel.split("_| ");
    }

    public static String getTaskType(ArrayList<IdentityServiceLog> logs) {
        for (IdentityServiceLog l : logs) {
            JSONObject j = l.getStatusJson();
            if ((j == null) || j.optString("_action").equals("")) continue;
            if (j.getString("_action").indexOf("init") == 0) return j.getString("_action").substring(4).toLowerCase();
        }
        return null;
    }

    public static JSONObject processCallback(String taskID, JSONObject resp, Shepherd myShepherd) {
System.out.println("CALLBACK GOT: (taskID " + taskID + ") " + resp);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        rtn.put("taskId", taskID);
        if (taskID == null) return rtn;
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, "IBEISIA", myShepherd);
        rtn.put("_logs", logs);
        if ((logs == null) || (logs.size() < 1)) return rtn;

        String type = getTaskType(logs);
        if ("detect".equals(type)) {
            rtn.put("success", true);
            rtn.put("processResult", processCallbackDetect(taskID, logs, resp, myShepherd));
        } else if ("identify".equals(type)) {
            rtn.put("success", true);
            rtn.put("processResult", processCallbackIdentify(taskID, logs, resp, myShepherd));
        } else {
            rtn.put("error", "unknown task action type " + type);
        }
        return rtn;
    }

/* resp ->
{"_action":"getJobResult","_response":{"response":{"json_result":{"score_list":[0],"results_list":[[{"xtl":679,"theta":0,"height":366,"width":421,"class":"elephant_savanna","confidence":0.215,"ytl":279},{"xtl":71,"theta":0,"height":206,"width":166,"class":"elephant_savanna","confidence":0.2685,"ytl":425},{"xtl":1190,"theta":0,"height":222,"width":67,"class":"elephant_savanna","confidence":0.2947,"ytl":433}]],"image_uuid_list":[{"__UUID__":"f0f9cc19-a56d-3a81-be40-bc51e65714e6"}]},"status":"ok","jobid":"jobid-0025"},"status":{"message":"","cache":-1,"code":200,"success":true}},"jobID":"jobid-0025"}
*/

    private static JSONObject processCallbackDetect(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String[] ids = IdentityServiceLog.findObjectIDs(logs);
        if (ids == null) {
            rtn.put("error", "could not find any MediaAsset ids from logs");
            return rtn;
        }
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (int i = 0 ; i < ids.length ; i++) {
            MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(ids[i]), myShepherd);
            if (ma != null) mas.add(ma);
        }
        int numCreated = 0;
        if ((resp.optJSONObject("_response") != null) && (resp.getJSONObject("_response").optJSONObject("response") != null) &&
            (resp.getJSONObject("_response").getJSONObject("response").optJSONObject("json_result") != null)) {
            JSONObject j = resp.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result");
            JSONArray rlist = j.optJSONArray("results_list");
            JSONArray ilist = j.optJSONArray("image_uuid_list");
/* TODO lots to consider here:
    --1. how do we determine where the cutoff is for auto-creating the annotation?-- made some methods for this
    2. if we do create (or dont!) how do we denote this for the sake of the user/ui querying status?
    3. do we first clear out existing annotations?
    4. do we allow duplicate (identical) annoations?  if not, do we block that at the level where we attach to encounter? or globally?
    5. do we have to tell IA when we auto-approve (i.e. no user review) results?
    6. how do (when do) we kick off *identification* on an annotation? and what are the target annotations?
    7.  etc???
*/
            if ((rlist != null) && (rlist.length() > 0) && (ilist != null) && (ilist.length() == rlist.length())) {
                FeatureType.initAll(myShepherd);
                JSONArray needReview = new JSONArray();
                JSONObject amap = new JSONObject();
                for (int i = 0 ; i < rlist.length() ; i++) {
                    JSONArray janns = rlist.optJSONArray(i);
                    if (janns == null) continue;
                    JSONObject jiuuid = ilist.optJSONObject(i);
                    if (jiuuid == null) continue;
                    String iuuid = fromFancyUUID(jiuuid);
                    MediaAsset asset = null;
                    for (MediaAsset ma : mas) {
                        if (ma.getUUID().equals(iuuid)) {
                            asset = ma;
                            break;
                        }
                    }
                    if (asset == null) {
                        System.out.println("WARN: could not find MediaAsset for " + iuuid + " in detection results for task " + taskID);
                        continue;
                    }
                    boolean needsReview = false;
                    JSONArray newAnns = new JSONArray();
                    for (int a = 0 ; a < janns.length() ; a++) {
                        JSONObject jann = janns.optJSONObject(a);
                        if (jann == null) continue;
                        if (jann.optDouble("confidence") < getDetectionCutoffValue()) {
                            needsReview = true;
                            continue;
                        }
                        //these are annotations we can make automatically from ia detection.  we also do the same upon review return
                        //  note this creates other stuff too, like encounter
                        Annotation ann = createAnnotationFromIAResult(jann, asset, myShepherd);
                        if (ann == null) {
                            System.out.println("WARNING: could not create Annotation from " + asset + " and " + jann);
                            continue;
                        }
/*
                        Encounter enc = new Encounter(ann);
                        String[] sp = convertSpecies(ann.getSpecies());
                        if (sp.length > 0) enc.setGenus(sp[0]);
                        if (sp.length > 1) enc.setSpecificEpithet(sp[1]);
//TODO other fields on encounter!!  (esp. dates etc)
                        Occurrence occ = asset.getOccurrence();
                        if (occ != null) {
                            enc.setOccurrenceID(occ.getOccurrenceID());
                            occ.addEncounter(enc);
                        }
                        myShepherd.getPM().makePersistent(ann);
                        myShepherd.getPM().makePersistent(enc);
                        if (occ != null) myShepherd.getPM().makePersistent(occ);
System.out.println("* CREATED " + ann + " and Encounter " + enc.getCatalogNumber());
*/
                        newAnns.put(ann.getId());
                        numCreated++;
                    }
                    if (needsReview) {
                        needReview.put(asset.getId());
                        asset.setDetectionStatus(STATUS_PENDING);
                    } else {
                        asset.setDetectionStatus(STATUS_COMPLETE);
                    }
                    if (newAnns.length() > 0) amap.put(Integer.toString(asset.getId()), newAnns);
                }
                rtn.put("_note", "created " + numCreated + " annotations for " + rlist.length() + " images");
                rtn.put("success", true);
                JSONObject jlog = new JSONObject();
                jlog.put("_action", "processedCallbackDetect");
                if (amap.length() > 0) jlog.put("annotations", amap);
                if (needReview.length() > 0) jlog.put("needReview", needReview);
                log(taskID, null, jlog, "context0");
                
            } else {
                rtn.put("error", "results_list is empty");
            }
        }
        
        return rtn;
    }


    private static JSONObject processCallbackIdentify(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String[] ids = IdentityServiceLog.findObjectIDs(logs);
        if (ids == null) {
            rtn.put("error", "could not find any Annotation ids from logs");
            return rtn;
        }
        HashMap<String,Annotation> anns = new HashMap<String,Annotation>();
        for (int i = 0 ; i < ids.length ; i++) {
            Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, ids[i]), true)));
System.out.println("**** " + ann);
            if (ann != null) anns.put(ids[i], ann);
        }
        int numCreated = 0;
        JSONObject infDict = null;
        JSONObject j = null;
//System.out.println("___________________________________________________\n" + resp + "\n------------------------------------------------------");
        if ((resp.optJSONObject("_response") != null) && (resp.getJSONObject("_response").optJSONObject("response") != null) &&
            (resp.getJSONObject("_response").getJSONObject("response").optJSONObject("json_result") != null)) {
            j = resp.getJSONObject("_response").getJSONObject("response").getJSONObject("json_result");
        //if ((resp != null) && (resp.optJSONObject("json_result") != null) && (resp.getJSONObject("json_result").optJSONObject("inference_dict") != null))
            if (j.optJSONObject("inference_dict") != null) infDict = j.getJSONObject("inference_dict");
        }
        if (infDict == null) {
            rtn.put("error", "could not parse inference_dict from results");
            return rtn;
        }
        boolean needReview = false;  //set as a whole
        HashMap<String,Boolean> needReviewMap = new HashMap<String,Boolean>();
        if ((infDict.optJSONObject("annot_pair_dict") != null) && (infDict.getJSONObject("annot_pair_dict").optJSONArray("review_pair_list") != null)) {
            JSONArray rlist = infDict.getJSONObject("annot_pair_dict").getJSONArray("review_pair_list");
            JSONArray clist = infDict.getJSONObject("annot_pair_dict").optJSONArray("confidence_list");  //this allows for null case, fyi
            for (int i = 0 ; i < rlist.length() ; i++) {
                //note: it *seems like* annot_uuid_1 is *always* the member that is from the query_annot_uuid_list... but?? is it?
                String annId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1"));  //gets not opts here... so ungraceful fail possible
                if (!needReviewMap.containsKey(annId)) needReviewMap.put(annId, false); //only set first, so if set true it stays true
                if (needIdentificationReview(rlist, clist, i, myShepherd)) {
                    needReview = true;
                    needReviewMap.put(annId, true);
                }
            }
        }

        JSONObject jlog = new JSONObject();
        jlog.put("_action", "processedCallbackIdentify");

        rtn.put("success", true);
        rtn.put("needReview", needReview);
        jlog.put("needReview", needReview);
        if (needReview) {
            jlog.put("needReviewMap", needReviewMap);
            for (String id : needReviewMap.keySet()) {
                if (!anns.containsKey(id)) {
                    System.out.println("WARNING: processCallbackIdentify() unable to load Annotation " + id + " to set identificationStatus");
                } else {
                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
                }
            }

        } else {
            for (String aid : anns.keySet()) {
                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
            }
            jlog.put("loopComplete", true);
            rtn.put("loopComplete", true);
            jlog.put("_infDict", infDict);
            exitIdentificationLoop(infDict, myShepherd);
        }

        log(taskID, null, jlog, "context0");
        return rtn;
    }

    private static void exitIdentificationLoop(JSONObject infDict, Shepherd myShepherd) {
System.out.println("*****************\nhey i think we are happy with these annotations!\n*********************\n" + infDict);
            //here we can use cluster_dict to find out what to create/persist on our side
    }


    //scores < these will require human review (otherwise they carry on automatically)
    public static double getDetectionCutoffValue() {
        return 0.8;
    }
    public static double getIdentificationCutoffValue() {
        return 0.8;
    }
    //tests review_pair_list and confidence_list for element at i and determines if we need review
    private static boolean needIdentificationReview(JSONArray rlist, JSONArray clist, int i, Shepherd myShepherd) {
        if ((rlist == null) || (clist == null) || (i < 0) || (rlist.length() == 0) || (clist.length() == 0) ||
            (rlist.length() != clist.length()) || (i >= rlist.length())) return false;

////TODO work is still out if we need to ignore based on our own matchingState!!!  for now we skip review if we already did it
            if (rlist.optJSONObject(i) == null) return false;
            String ms = getIdentificationMatchingState(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_1")),
                                                       fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")), myShepherd);
System.out.println("needIdentificationReview() got matching_state --------------------------> " + ms);
            if (ms != null) return false;
//////

            return (clist.optDouble(i, -99.0) < getIdentificationCutoffValue());
    }

    public static String parseDetectionStatus(String maId, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", maId, myShepherd);
        if ((logs == null) || (logs.size() < 1)) return null;
        for (IdentityServiceLog log : logs) {
            String s = _parseDetection(maId, log);
            if (s != null) return s;
        }
        System.out.println("WARNING: parseDetectionStatus(" + maId + ") fell through; returning error.");
        return "error";
    }
    //basically returning null means nothing parsable/usable/interesting found
    private static String _parseDetection(String maId, IdentityServiceLog log) {
        JSONObject status = log.getStatusJson();
        if (status == null) return null;
        String action = status.optString("_action", null);
        if (action == null) return null;
        if (action.equals("processedCallbackDetection")) {
            JSONArray need = status.optJSONArray("needReview");
            if ((need == null) || (need.length() < 1)) return "complete";
            for (int i = 0 ; i < need.length() ; i++) {
                if (maId.equals(need.optString(i))) return "pending";
            }
            return "complete";
        }
System.out.println("detection most recent action found is " + action);
        if ((System.currentTimeMillis() - log.getTimestamp()) > TIMEOUT_DETECTION) {
            System.out.println("WARNING: detection processing timeout for " + log.toString() + "; returning error detection status");
            return "error";
        }
        return "processing";
    }

    public static String parseIdentificationStatus(String annId, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA", annId, myShepherd);
        if ((logs == null) || (logs.size() < 1)) return null;
        for (IdentityServiceLog log : logs) {
            String s = _parseIdentification(log);
            if (s != null) return s;
        }
        System.out.println("WARNING: parseIdentificationStatus(" + annId + ") fell through; returning error.");
        return "error";
    }
    //basically returning null means nothing parsable/usable/interesting found
    private static String _parseIdentification(IdentityServiceLog log) {
        JSONObject status = log.getStatusJson();
        if (status == null) return null;
        String action = status.optString("_action", null);
        if (action == null) return null;
/*
        if (action.equals("processedCallbackDetection")) {
            JSONArray need = stats.optJSONArray("needReview");
            if ((need == null) || (need.length() < 1)) return "complete";
            return "pending";
        }
*/
System.out.println("identification most recent action found is " + action);
        return "processing";
    }

    public static void setIdentificationMatchingState(String ann1Id, String ann2Id, String state, Shepherd myShepherd) {
        IBEISIAIdentificationMatchingState.set(ann1Id, ann2Id, state, myShepherd);
    }
    public static String getIdentificationMatchingState(String ann1Id, String ann2Id, Shepherd myShepherd) {
        IBEISIAIdentificationMatchingState m = IBEISIAIdentificationMatchingState.load(ann1Id, ann2Id, myShepherd);
        if (m == null) return null;
        return m.getState();
    }

    public static String getActiveTaskId(HttpServletRequest request) {
        String uname = userKey(request);
        if (uname == null) return null;
        return identificationUserActiveTaskId.get(uname);
    }

    public static void setActiveTaskId(HttpServletRequest request, String taskId) {
        String uname = userKey(request);
        if (uname == null) return;
        if (taskId == null) {
            identificationUserActiveTaskId.remove(uname);
        } else {
            identificationUserActiveTaskId.put(uname, taskId);
        }
    }
    private static String userKey(HttpServletRequest request) {
        //if (request.getUserPrincipal() == null) return null;
        if (request.getUserPrincipal() == null) return "__ANONYMOUS__";
        return request.getUserPrincipal().getName();
    }

}


