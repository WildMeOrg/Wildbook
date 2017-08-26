package org.ecocean.identity;

import org.ecocean.ImageAttributes;

import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.YouTube;
import org.ecocean.media.YouTubeAssetStore;
import org.ecocean.ocr.ocr;
//import org.ecocean.youtube.PostQuestion;
import org.ecocean.translate.DetectTranslate;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdProperties;
import org.ecocean.Encounter;
import org.ecocean.Occurrence;
import org.ecocean.MarkedIndividual;
import org.ecocean.ContextConfiguration;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.CommonConfiguration;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

import org.json.JSONObject;
import org.json.JSONArray;
import java.net.URL;
import org.ecocean.CommonConfiguration;
import org.ecocean.media.*;
import org.ecocean.RestClient;
import java.io.IOException;
import javax.servlet.ServletException;
import java.io.File;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.joda.time.DateTime;
import org.apache.commons.lang3.StringUtils;
import javax.servlet.http.HttpServletRequest;

import java.util.concurrent.atomic.AtomicBoolean;

//date time
import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.text.DateFormatSymbols;
import java.util.Locale;
//natural language processing for date/time
//import com.joestelmach.natty.*;
import java.util.Date;
import org.joda.time.Instant;

import twitter4j.Status;
import twitter4j.*;


public class IBEISIA {

    //TODO move this ish to its own class asap!
    private static final Map<String, String[]> speciesMap;
    static {
        speciesMap = new HashMap<String, String[]>();
        speciesMap.put("zebra_plains", new String[]{"Equus","quagga"});
        speciesMap.put("zebra_grevys", new String[]{"Equus","grevyi"});
        speciesMap.put("whale shark", new String[]{"Rhincodon","typus"});
    }

    public static String STATUS_PENDING = "pending";  //pending review (needs action by user)
    public static String STATUS_COMPLETE = "complete";  //process is done
    public static String STATUS_PROCESSING = "processing";  //off at IA, awaiting results
    public static String STATUS_ERROR = "error";

    private static long TIMEOUT_DETECTION = 20 * 60 * 1000;   //in milliseconds
    private static String SERVICE_NAME = "IBEISIA";
    private static String IA_UNKNOWN_NAME = "____";

    private static AtomicBoolean iaPrimed = new AtomicBoolean(false);
    private static HashMap<Integer,Boolean> alreadySentMA = new HashMap<Integer,Boolean>();
    private static HashMap<String,Boolean> alreadySentAnn = new HashMap<String,Boolean>();
    private static HashMap<String,Boolean> alreadySentExemplar = new HashMap<String,Boolean>();
    //private static HashMap<String,String> identificationMatchingState = new HashMap<String,String>();
    private static HashMap<String,String> identificationUserActiveTaskId = new HashMap<String,String>();

    //cache-like, in order to speed up IA; TODO make this some kind of smarter class
    private static HashMap<String,String> cacheAnnotIndiv = new HashMap<String,String>();
    private static HashMap<String,ArrayList<JSONObject>> targetIdsListCache = new HashMap<String,ArrayList<JSONObject>>();
    private static HashMap<String,ArrayList<String>> targetNameListCache = new HashMap<String,ArrayList<String>>();

    private static String iaBaseURL = null;  //gets set the first time it is needed by iaURL()

    //public static JSONObject post(URL url, JSONObject data) throws RuntimeException, MalformedURLException, IOException {

    /*
        NOTE: good practice is to call IBEISIA.waitForIAPriming(); before any of the sendFoo() or beginFoo() methods, so that some of the
        time-consuming hassle can finish.  these things should happen upon tomcat startup, but can take a few minutes to run, so there may be
        cases where IA jobs are started during this time.  technically nothing "bad" happens if a job starts during this process, but it will create
        a longer wait time.  there is, however, a chance that waitForIAPriming() times out with a RuntimeException thrown.
    */

    //a convenience way to send MediaAssets with no (i.e. with only the "trivial") Annotation
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return sendMediaAssets(mas, null, context);
    }

    //other is a HashMap of additional properties to build lists out of (e.g. Encounter ids and so on), that do not live in/on MediaAsset
    public static JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, HashMap<MediaAsset,HashMap<String,Object>> other, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendMediaAssets() called without IA primed");
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddImages", context);
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
            ImageAttributes iatt = ma.getImageAttributes();
            int w = 0;
            int h = 0;
            if (iatt != null) {
                w = (int) iatt.getWidth();
                h = (int) iatt.getHeight();
            }
            //we are *required* to have a width/height to pass to IA, so lets skip...  or fail???  TODO
            if ((w < 1) || (h < 1)) {
                throw new RuntimeException("could not use " + ma.toString() + " - unable to find width/height");
                //continue;  //skip?
            }

            map.get("image_width_list").add(w);
            map.get("image_height_list").add(h);

            map.get("image_uuid_list").add(toFancyUUID(ma.getUUID()));
            map.get("image_uri_list").add(mediaAssetToUri(ma));

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
        return RestClient.post(url, hashMapToJSONObject(map));
    }



            //Annotation ann = new Annotation(ma, species);

    public static JSONObject sendAnnotations(ArrayList<Annotation> anns, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendAnnotations() called without IA primed");
        String u = CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context);
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);

        int ct = 0;
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());

        Shepherd myShepherd = new Shepherd("context0");
        myShepherd.setAction("IBEISIA.class_sendAnnotations");
        myShepherd.beginDBTransaction();
        for (Annotation ann : anns) {
            if (!needToSend(ann)) continue;
            int[] bbox = ann.getBbox();
            if (bbox == null) throw new RuntimeException("failed to add " + ann + "; could not getBbox()");
            map.get("annot_bbox_list").add(bbox);
            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
            map.get("annot_species_list").add(ann.getSpecies());
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            markSent(ann);
            ct++;
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

System.out.println("sendAnnotations(): sending " + ct);
        if (ct < 1) return null;

        //this should only be checking for missing images, i guess?
        boolean tryAgain = true;
        JSONObject res = null;
        while (tryAgain) {
            res = RestClient.post(url, hashMapToJSONObject(map));
            tryAgain = iaCheckMissing(res, context);
        }
        return res;
    }

    //note: if tanns here is null, then it is exemplar for this species
    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, JSONObject queryConfigDict,
                                          JSONObject userConfidence, String baseUrl, String context)
                                          throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendIdentify() called without IA primed");
        String u = CommonConfiguration.getProperty("IBEISIARestUrlStartIdentifyAnnotations", context);
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
        URL url = new URL(u);

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.sendIdentify");
        myShepherd.beginDBTransaction();

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
        String species = null;
        for (Annotation ann : qanns) {
            if (species == null) species = ann.getSpecies();
            qlist.add(toFancyUUID(ann.getUUID()));
/* jonc now fixed it so we can have null/unknown ids... but apparently this needs to be "____" (4 underscores) ; also names are now just strings (not uuids)
            //TODO i guess (???) we need some kinda ID for query annotations (even tho we dont know who they are); so wing it?
            qnlist.add(toFancyUUID(Util.generateUUID()));
*/
            qnlist.add(IA_UNKNOWN_NAME);
        }

        boolean setExemplarCaches = false;
        if (tanns == null) {
System.out.println("--- exemplar!");
            if (targetNameListCache.get(species) == null) {
System.out.println("     gotta compute :(");
                tanns = Annotation.getExemplars(species, myShepherd);
                setExemplarCaches = true;
            } else {
System.out.println("     free ride :)");
                tlist = targetIdsListCache.get(species);
                tnlist = targetNameListCache.get(species);
            }
        }

        if (tanns != null) for (Annotation ann : tanns) {
            tlist.add(toFancyUUID(ann.getUUID()));
            String indivId = annotGetIndiv(ann, myShepherd);
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

        if (setExemplarCaches) {
           targetIdsListCache.put(species, tlist);
           targetNameListCache.put(species, tnlist);
        }
        map.put("query_annot_uuid_list", qlist);
        map.put("database_annot_uuid_list", tlist);
        map.put("query_annot_name_list", qnlist);
        map.put("database_annot_name_list", tnlist);


System.out.println("===================================== qlist & tlist =========================");
System.out.println(qlist + " callback=" + baseUrl + "/IBEISIAGetJobStatus.jsp");
System.out.println("tlist.size()=" + tlist.size());
System.out.println(map);
myShepherd.rollbackDBTransaction();
myShepherd.closeDBTransaction();
        return RestClient.post(url, hashMapToJSONObject2(map));
    }


    public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendDetect() called without IA primed");
        String u = CommonConfiguration.getProperty("IBEISIARestUrlStartDetectImages", context);
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlStartDetectImages is not set");
        URL url = new URL(u);

        HashMap<String,Object> map = new HashMap<String,Object>();
        map.put("callback_url", baseUrl + "/IBEISIAGetJobStatus.jsp");
System.out.println("sendDetect() baseUrl = " + baseUrl);
        ArrayList<JSONObject> malist = new ArrayList<JSONObject>();

        for (MediaAsset ma : mas) {
            malist.add(toFancyUUID(ma.getUUID()));
        }
        map.put("image_uuid_list", malist);

        return RestClient.post(url, new JSONObject(map));
    }


    public static JSONObject getJobStatus(String jobID, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobStatus", context);
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobStatus is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    //note: this passes directly to IA so can be problematic! (ia down? and more importantly: ia restarted so job # is diff and old job is gone!)
    //  better(?) to use getJobResultLogged() below!
    public static JSONObject getJobResult(String jobID, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = CommonConfiguration.getProperty("IBEISIARestUrlGetJobResult", context);
        if (u == null) throw new MalformedURLException("configuration value IBEISIARestUrlGetJobResult is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    public static JSONObject getJobResultLogged(String jobID, String context) {
        String taskId = findTaskIDFromJobID(jobID, context);
        if (taskId == null) {
            System.out.println("getJobResultLogged(" + jobID + ") could not find taskId for this job");
            return null;
        }
System.out.println("getJobResultLogged(" + jobID + ") -> taskId " + taskId);
        //note: this is a little(!) in that it relies on the "raw" results living in "_debug" from getTaskResults so we can reconstruct it to be the output
        //  that getJobResult() above gives.  :/
        JSONObject tr = getTaskResults(taskId, context);
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
    public static JSONObject OLDgetTaskResults(String taskID, String context) {
        JSONObject rtn = getTaskResultsBasic(taskID, context);
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
    public static JSONObject getTaskResults(String taskID, String context) {
        JSONObject rtn = getTaskResultsBasic(taskID, context);
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


    public static JSONObject getTaskResultsDetect(String taskID, String context) {
        JSONObject rtn = getTaskResultsBasic(taskID, context);
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



    public static JSONObject getTaskResultsBasic(String taskID, String context) {
        Shepherd myShepherd=new Shepherd(context);
        myShepherd.setAction("IBEISIA.getTaskResultsBasic");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME, myShepherd);

        JSONObject returnMe= getTaskResultsBasic(taskID, logs);
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        return returnMe;
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

    public static HashMap<String,Object> getTaskResultsAsHashMap(String taskID, String context) {
        JSONObject jres = getTaskResults(taskID, context);
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
            Shepherd myShepherd=new Shepherd(context);
            myShepherd.setAction("IBEISIA.getTaskResultsAsHashMap");
            myShepherd.beginDBTransaction();
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
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            res.put("results", rout);
        }

        return res;
    }


    public static boolean waitingOnTask(String taskID, String context) {
        JSONObject res = getTaskResults(taskID, context);
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
    public static boolean iaCheckMissing(JSONObject res, String context) {
/////System.out.println("########## iaCheckMissing res -> " + res);
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
                    //TODO actually send the mediaasset duh ... future-jon, please fix this
                }
            }
        }

        if ((res.getJSONObject("response") != null) && res.getJSONObject("response").has("missing_annot_uuid_list")) {
            JSONArray list = res.getJSONObject("response").getJSONArray("missing_annot_uuid_list");
            if (list.length() > 0) {
                ArrayList<Annotation> anns = new ArrayList<Annotation>();
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("IBEISIA.iaCheckMissing");
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
                    sendAnnotations(anns, context);
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
            return ma.webURL().toString();  //a better last gasp hope
        }
    }


    //like below, but you can pass Encounters (which will be mined for Annotations and passed along)
    public static JSONObject beginIdentify(ArrayList<Encounter> queryEncs, ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String taskID, String baseUrl, String context, JSONObject opt) {
        if (!isIAPrimed()) System.out.println("WARNING: beginIdentify() called without IA primed");
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

        JSONObject queryConfigDict = queryConfigDict(myShepherd, species, opt);

        return beginIdentifyAnnotations(qanns, tanns, queryConfigDict, null, myShepherd, species, taskID, baseUrl);
    }

    //actually ties the whole thing together and starts a job with all the pieces needed
    // note: if tanns is null, that means we get all exemplar for species
    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns, JSONObject queryConfigDict,
                                                      JSONObject userConfidence, Shepherd myShepherd, String species, String taskID, String baseUrl) {
        if (!isIAPrimed()) System.out.println("WARNING: beginIdentifyAnnotations() called without IA primed");
        //TODO possibly could exclude qencs from tencs?
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false);  //pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();  //0th item will have "query" encounter
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();

        log(taskID, jobID, new JSONObject("{\"_action\": \"initIdentify\"}"), myShepherd.getContext());

        try {
            for (Annotation ann : qanns) {
                allAnns.add(ann);
                MediaAsset ma = ann.getDerivedMediaAsset();
                if (ma == null) ma = ann.getMediaAsset();
                if (ma != null) mas.add(ma);
            }

            boolean isExemplar = false;
            if (tanns == null) {
                isExemplar = true;
                if ((alreadySentExemplar.get(species) == null) || !alreadySentExemplar.get(species)) {
System.out.println("   ... have to set tanns.  :(");
                    tanns = Annotation.getExemplars(species, myShepherd);
                    alreadySentExemplar.put(species, true);
                }
            }

System.out.println("- mark 2");
            if (tanns != null) {
            for (Annotation ann : tanns) {
                allAnns.add(ann);
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
            results.put("sendMediaAssets", sendMediaAssets(mas, myShepherd.getContext()));
            results.put("sendAnnotations", sendAnnotations(allAnns, myShepherd.getContext()));

            if (isExemplar) tanns = null;  //reset it for sendIdentify() below

            //this should attempt to repair missing Annotations
            boolean tryAgain = true;
            JSONObject identRtn = null;
            while (tryAgain) {
                identRtn = sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl, myShepherd.getContext());
                tryAgain = iaCheckMissing(identRtn, myShepherd.getContext());
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
        log(taskID, jobID, jlog, myShepherd.getContext());

        return results;
    }


    //a slightly different flavor -- we can explicitely pass the query annotation
	//  NOTE!!! TODO this might be redundant with beginIdentifyAnnotaions above. (this came from crc)
/*
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

*/


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
        myShepherd.setAction("IBEISIA.log");
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
    public static String findTaskIDFromJobID(String jobID, String context) {
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("IBEISIA.findTaskIDFromJobID");
      myShepherd.beginDBTransaction();
      ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByServiceJobID(SERVICE_NAME, jobID, myShepherd);
        if (logs == null) {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          return null;
        }
        for (int i = logs.size() - 1 ; i >= 0 ; i--) {
            if (logs.get(i).getTaskID() != null) {
              String id=logs.get(i).getTaskID();
              myShepherd.rollbackDBTransaction();
              myShepherd.closeDBTransaction();
              return id;
            }  //get first one we find. too bad!
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
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

    public static String findJobIDFromTaskID(String taskID, String context) {
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("IBEISIA.findJobIDFromTaskID");
      myShepherd.beginDBTransaction();
      ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME, myShepherd);
        if ((logs == null) || (logs.size() < 1)) {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          return null;
        }

        String jobID = logs.get(logs.size() - 1).getServiceJobID();
        if ("-1".equals(jobID)) {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          return null;
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
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
            JSONObject res = beginIdentify(qencs, tencs, myShepherd, taxonomyString, taskID, baseUrl, context, null);
            if (res.optBoolean("success")) {
                ids.add(taskID);
            } else {
                System.out.println("WARNING - could not start job for " + taskID + ": " + res.optString("error", "[unknown error]") + "; skipping");
            }
        }
        return ids;
    }

    public static void waitForTrainingJobs(ArrayList<String> taskIds, String context) {
        boolean stillWaiting = true;
        int countdown = 100;
        while (stillWaiting && (countdown > 0)) {
            countdown--;
            stillWaiting = false; //optimism; prove us wrong
            int idLen = taskIds.size();
            for (int i = 0 ; i < idLen ; i++) {
                if (waitingOnTask(taskIds.get(i), context)) {
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
    public static Annotation createAnnotationFromIAResult(JSONObject jann, MediaAsset asset, Shepherd myShepherd, boolean skipEncounter) {
        Annotation ann = convertAnnotation(asset, jann);
        if (ann == null) return null;
        if (skipEncounter) {
            myShepherd.getPM().makePersistent(ann);
System.out.println("* createAnnotationFromIAResult() CREATED " + ann + " [with no Encounter!]");
            return ann;
        }
        Encounter enc = ann.toEncounter(myShepherd);  //this does the magic of making a new Encounter if needed etc.  good luck!
        Occurrence occ = asset.getOccurrence();
        if (occ != null) {
            enc.setOccurrenceID(occ.getOccurrenceID());
            occ.addEncounter(enc);
        }
        myShepherd.getPM().makePersistent(ann);
        myShepherd.getPM().makePersistent(enc);
        if (occ != null) myShepherd.getPM().makePersistent(occ);
System.out.println("* createAnnotationFromIAResult() CREATED " + ann + " on Encounter " + enc.getCatalogNumber());
        return ann;
    }

    public static Annotation convertAnnotation(MediaAsset ma, JSONObject iaResult) {
        if (iaResult == null) return null;

        //this is hard-coded for whaleshark.org branch .... need to generalize this!!  TODO
        if (!iaResult.optString("class", "_FAIL_").equals("whale_shark")) {
            System.out.println("WARNING: bailing on IA results due to invalid species detected -- " + iaResult.toString());
            return null;
        }

        JSONObject fparams = new JSONObject();
        fparams.put("detectionConfidence", iaResult.optDouble("confidence", -2.0));
        Feature ft = ma.generateFeatureFromBbox(iaResult.optDouble("width", 0), iaResult.optDouble("height", 0),
                                                iaResult.optDouble("xtl", 0), iaResult.optDouble("ytl", 0), fparams);
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

    public static JSONObject processCallback(String taskID, JSONObject resp, HttpServletRequest request) {
System.out.println("CALLBACK GOT: (taskID " + taskID + ") " + resp);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        rtn.put("taskId", taskID);
        if (taskID == null) return rtn;
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd=new Shepherd(context);
        myShepherd.setAction("IBEISIA.processCallback");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, "IBEISIA", myShepherd);
        rtn.put("_logs", logs);
        if ((logs == null) || (logs.size() < 1)) return rtn;

        String type = getTaskType(logs);
System.out.println("**** type ---------------> [" + type + "]");
        if ("detect".equals(type)) {
            rtn.put("success", true);
            rtn.put("processResult", processCallbackDetect(taskID, logs, resp, myShepherd, request));

        } else if ("identify".equals(type)) {
            rtn.put("success", true);
            rtn.put("processResult", processCallbackIdentify(taskID, logs, resp, request));
        } else {
            rtn.put("error", "unknown task action type " + type);
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        return rtn;
    }

/* resp ->
{"_action":"getJobResult","_response":{"response":{"json_result":{"score_list":[0],"results_list":[[{"xtl":679,"theta":0,"height":366,"width":421,"class":"elephant_savanna","confidence":0.215,"ytl":279},{"xtl":71,"theta":0,"height":206,"width":166,"class":"elephant_savanna","confidence":0.2685,"ytl":425},{"xtl":1190,"theta":0,"height":222,"width":67,"class":"elephant_savanna","confidence":0.2947,"ytl":433}]],"image_uuid_list":[{"__UUID__":"f0f9cc19-a56d-3a81-be40-bc51e65714e6"}]},"status":"ok","jobid":"jobid-0025"},"status":{"message":"","cache":-1,"code":200,"success":true}},"jobID":"jobid-0025"}
*/

    private static JSONObject processCallbackDetect(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd, HttpServletRequest request) {
      return processCallbackDetect(taskID, logs, resp, myShepherd, request, null, null, null);
    }

    private static JSONObject processCallbackDetect(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd, HttpServletRequest request, String screenName, String imageId, Twitter twitterInst) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String[] ids = IdentityServiceLog.findObjectIDs(logs);
System.out.println("***** ids = " + ids);
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
System.out.println("RESP ===>>>>>> " + resp.toString(2));
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
                JSONObject ident = new JSONObject();
                List<Annotation> allAnns = new ArrayList<Annotation>();
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
//System.out.println("============================================================== JANNS " + janns.toString(2));
                    boolean skipEncounters = asset.hasLabel("_frame");
System.out.println("+++++++++++ >>>> skipEncounters ???? " + skipEncounters);
                    for (int a = 0 ; a < janns.length() ; a++) {
                        JSONObject jann = janns.optJSONObject(a);
                        if (jann == null) continue;
                        if (jann.optDouble("confidence", -1.0) < getDetectionCutoffValue() || !jann.optString("species", "unkown").equals("whale_fluke")) { // wasn't detected with high confidence or wasn't a identified as a whale fluke

                            needsReview = true;
                            System.out.println("Detection didn't find a whale fluke");
                            // TwitterUtil.sendDetectionAndIdentificationTweet(screenName, imageId, twitterInst, whaleId, false, false, ""); //TODO find a way to get screenName, imageId, etc. over here
                            continue;
                        }
                        //these are annotations we can make automatically from ia detection.  we also do the same upon review return
                        //  note this creates other stuff too, like encounter
                        Annotation ann = createAnnotationFromIAResult(jann, asset, myShepherd, skipEncounters);
                        if (ann == null) {
                            System.out.println("WARNING: could not create Annotation from " + asset + " and " + jann);
                            continue;
                        }
                        if (!skipEncounters) _tellEncounter(myShepherd, request, ann);
                        allAnns.add(ann);  //this is cumulative over *all MAs*
                        newAnns.put(ann.getId());
                        try {
                            //TODO how to know *if* we should start identification
                            if(jann.optDouble("confidence", -1.0) >= getDetectionCutoffValue() && jann.optString("species", "unkown").equals("whale_fluke")){
                              System.out.println("Detection found a whale fluke; sending to identification");
                              ident.put(ann.getId(), IAIntake(ann, myShepherd, request));
                            }
                        } catch (Exception ex) {
                            System.out.println("WARNING: IAIntake threw exception " + ex);
                        }
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

                //this will do nothing in the non-video-frame case
                List<Encounter> encs = Encounter.collateFrameAnnotations(allAnns, myShepherd);
                if ((encs != null) && (encs.size() > 0)) {
                    Occurrence occ = new Occurrence();
                    occ.setOccurrenceID(Util.generateUUID());
                    occ.setDWCDateLastModified();
                    occ.setDateTimeCreated();
                    occ.addComments("<i>created during frame collation by IA</i>");

                    JSONArray je = new JSONArray();
                    for (Encounter enc : encs) {
                        enc.setOccurrenceID(occ.getOccurrenceID());
                        occ.addEncounter(enc);
                        occ.setSocialMediaSourceID(enc.getEventID());
                        myShepherd.getPM().makePersistent(enc);
                        je.put(enc.getCatalogNumber());
                    }
                    myShepherd.getPM().makePersistent(occ);
                    fromDetection(occ, myShepherd, request, ServletUtilities.getContext(request));
                    jlog.put("collatedEncounters", je);
                    jlog.put("collatedOccurrence", occ.getOccurrenceID());
                }

                jlog.put("_action", "processedCallbackDetect");
                if (amap.length() > 0) jlog.put("annotations", amap);
                if (ident.length() > 0) jlog.put("identificationTasks", ident);
                if (needReview.length() > 0) jlog.put("needReview", needReview);
                log(taskID, null, jlog, myShepherd.getContext());

            } else {
                rtn.put("error", "results_list is empty");
            }
        }

        return rtn;
    }

    private static void _tellEncounter(Shepherd myShepherd, HttpServletRequest request, Annotation ann) {
System.out.println("/------ _tellEncounter ann = " + ann);
        Encounter enc = ann.toEncounter(myShepherd);
System.out.println("\\------ _tellEncounter enc = " + enc);
        if (enc == null) return;
        myShepherd.getPM().makePersistent(enc);
        enc.detectedAnnotation(myShepherd, request, ann);
    }

    private static JSONObject processCallbackIdentify(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, HttpServletRequest request) {
      return processCallbackIdentify(taskID, logs, resp, request, null, null, null);
    }

    private static JSONObject processCallbackIdentify(String taskID, ArrayList<IdentityServiceLog> logs, JSONObject resp, HttpServletRequest request, String screenName, String imageId, Twitter twitterInst) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String[] ids = IdentityServiceLog.findObjectIDs(logs);
        if (ids == null) {
            rtn.put("error", "could not find any Annotation ids from logs");
            return rtn;
        }
        HashMap<String,Annotation> anns = new HashMap<String,Annotation>();
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd=new Shepherd(context);
        myShepherd.setAction("IBEISIA.processCallbackIdentify");
        myShepherd.beginDBTransaction();
        for (int i = 0 ; i < ids.length ; i++) {
            Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, ids[i]), true)));
System.out.println("**** " + ann);
            if (ann != null) anns.put(ids[i], ann);
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
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
                //NOTE: it *seems like* annot_uuid_1 is *always* the member that is from the query_annot_uuid_list... but?? is it? NOTE: Mark and Chris assumed this was true in the line below that looks like String matchUuid = rlist.getJSONObject(i).optJSONObject("annot_uuid_2");

                //NOTE: will the review_pair_list and confidence_list always be in descending order? IF not, then TODO we'll have to only select the best match (what if there's more than one really good match)
                String annId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1"));  //gets not opts here... so ungraceful fail possible
                if (!needReviewMap.containsKey(annId)) needReviewMap.put(annId, false); //only set first, so if set true it stays true
                if (needIdentificationReview(rlist, clist, i, context)) {
                    needReview = true;
                    needReviewMap.put(annId, true);
                } else if(clist.optDouble(i, -99.0) >= getIdentificationCutoffValue()){
                  System.out.println("Maybe identified it??");
                  try{

                    //String matchUuid = rlist.getJSONObject(i).optJSONObject("annot_uuid_2").toString();

                    String matchUuid = rlist.getJSONObject(i).getJSONObject("annot_uuid_2").toString();

                    System.out.println(matchUuid);
                    //TODO get baseURL
                    // String info = baseURL + "/individuals.jsp/?number=" + matchUuid;
                    if(screenName != null && imageId != null && twitterInst != null){
                      //TODO pass info to tweet
                    } else{
                      System.out.println("Arguments to generate a return tweet were not available; skipped.");
                    }

                  } catch(Exception e){
                    e.printStackTrace();
                  }

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

        log(taskID, null, jlog, myShepherd.getContext());
        return rtn;
    }

    private static void exitIdentificationLoop(JSONObject infDict, Shepherd myShepherd) {
System.out.println("*****************\nhey i think we are happy with these annotations!\n*********************\n" + infDict);
            //here we can use cluster_dict to find out what to create/persist on our side
    }


    //scores < these will require human review (otherwise they carry on automatically)
    public static double getDetectionCutoffValue() {
        return 0.25;
    }
    public static double getIdentificationCutoffValue() {
        return 0.8;
    }
    //tests review_pair_list and confidence_list for element at i and determines if we need review
    private static boolean needIdentificationReview(JSONArray rlist, JSONArray clist, int i, String context) {
        if ((rlist == null) || (clist == null) || (i < 0) || (rlist.length() == 0) || (clist.length() == 0) ||
            (rlist.length() != clist.length()) || (i >= rlist.length())) return false;

////TODO work is still out if we need to ignore based on our own matchingState!!!  for now we skip review if we already did it
            if (rlist.optJSONObject(i) == null) return false;
            String ms = getIdentificationMatchingState(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_1")),
                                                       fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")), context);
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
    public static String getIdentificationMatchingState(String ann1Id, String ann2Id, String context) {
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("IBEISIA.getIdentificationMatchingState");
      myShepherd.beginDBTransaction();
      IBEISIAIdentificationMatchingState m = IBEISIAIdentificationMatchingState.load(ann1Id, ann2Id, myShepherd);
        if (m == null) {
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          return null;
        }
        String result=m.getState();
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return result;
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


    //builds urls for IA, based on just one.  kinda hacky (as opposed to per-endpoint setting in CommonConfiguration); but can be useful
    public static URL iaURL(String context, String urlSuffix) {
        if (iaBaseURL == null) {
            String u = CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context);
            if (u == null) throw new RuntimeException("configuration value IBEISIARestUrlAddAnnotations is not set");
            int i = u.indexOf("/", 9);  //9 should get us past "http://" to get to post-hostname /
            if (i < -1) throw new RuntimeException("could not parse IBEISIARestUrlAddAnnotations for iaBaseURL");
            iaBaseURL = u.substring(0,i+1);  //will include trailing slash
            System.out.println("INFO: setting iaBaseURL=" + iaBaseURL);
        }
        String ustr = iaBaseURL;
        if (urlSuffix != null) {
            if (urlSuffix.indexOf("/") == 0) urlSuffix = urlSuffix.substring(1);  //get rid of leading /
            ustr += urlSuffix;
        }
        try {
            return new URL(ustr);
        } catch (Exception ex) {
            throw new RuntimeException("iaURL() could not parse URL");
        }
    }

    ////note: we *could* try to grab these as lists from IA, but that is more complicated so lets iterate for now...
    public static List<Annotation> grabAnnotations(List<String> annIds, Shepherd myShepherd) {
        List<Annotation> anns = new ArrayList<Annotation>();
        for (String annId : annIds) {
            Annotation ann = null;
            try {
                ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annId), true)));
            } catch (org.datanucleus.exceptions.NucleusObjectNotFoundException ex) {
                //System.out.println("NOTE: grabAnnotations(" + annId + ") swallowed " + ex.toString());
            } catch (javax.jdo.JDOObjectNotFoundException ex) {
                //System.out.println("NOTE: grabAnnotations(" + annId + ") swallowed " + ex.toString());
            }
            //TODO do we need to verify MediaAsset has been retreived?  for now, lets assume that happend during creation
            if (ann != null) {
                anns.add(ann);
                continue;
            }
System.out.println("need " + annId + " from IA, i guess?");
            ann = getAnnotationFromIA(annId, myShepherd);
            if (ann == null) throw new RuntimeException("Could not getAnnotationFromIA(" + annId + ")");
            anns.add(ann);
        }
        return anns;
    }

    public static List<Annotation> grabAnnotationsDEBUG(List<String> annIds, Shepherd myShepherd, PrintWriter out) {
        List<Annotation> anns = new ArrayList<Annotation>();
        for (String annId : annIds) {
            Annotation ann = null;
            try {
                ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, annId), true)));
            } catch (org.datanucleus.exceptions.NucleusObjectNotFoundException ex) {
                //System.out.println("NOTE: grabAnnotations(" + annId + ") swallowed " + ex.toString());
            } catch (javax.jdo.JDOObjectNotFoundException ex) {
                //System.out.println("NOTE: grabAnnotations(" + annId + ") swallowed " + ex.toString());
            }
            //TODO do we need to verify MediaAsset has been retreived?  for now, lets assume that happend during creation
            if (ann != null) {
                anns.add(ann);
                continue;
            }
            ann = getAnnotationFromIADEBUG(annId, myShepherd, out);
            //if (ann == null) throw new RuntimeException("Could not getAnnotationFromIA(" + annId + ")");
            anns.add(ann);
        }
        return anns;
    }


    public static Annotation getAnnotationFromIA(String annId, Shepherd myShepherd) {
        String context = myShepherd.getContext();

        try {
            String idSuffix = "?annot_uuid_list=[" + toFancyUUID(annId) + "]";
            JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/image/uuid/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONObject(0) == null)) throw new RuntimeException("could not get image uuid");
            String imageUUID = fromFancyUUID(rtn.getJSONArray("response").getJSONObject(0));
            MediaAsset ma = grabMediaAsset(imageUUID, myShepherd);
            if (ma == null) throw new RuntimeException("could not find MediaAsset " + imageUUID);

            //now we need the bbox to make the Feature
            rtn = RestClient.get(iaURL(context, "/api/annot/bbox/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONArray(0) == null)) throw new RuntimeException("could not get annot bbox");
            JSONArray jbb = rtn.getJSONArray("response").getJSONArray(0);
            JSONObject fparams = new JSONObject();
            fparams.put("x", jbb.optInt(0, 0));
            fparams.put("y", jbb.optInt(1, 0));
            fparams.put("width", jbb.optInt(2, -1));
            fparams.put("height", jbb.optInt(3, -1));
            Feature ft = new Feature("org.ecocean.boundingBox", fparams);
            ma.addFeature(ft);

            rtn = RestClient.get(iaURL(context, "/api/annot/species/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optString(0, null) == null)) throw new RuntimeException("could not get annot species");
            String speciesString = rtn.getJSONArray("response").getString(0);

            Annotation ann = new Annotation(speciesString, ft);
            ann.setId(annId);  //nope we dont want random uuid, silly
            rtn = RestClient.get(iaURL(context, "/api/annot/exemplar/json/" + idSuffix));
            if ((rtn != null) && (rtn.optJSONArray("response") != null)) {
                boolean exemplar = (rtn.getJSONArray("response").optInt(0, 0) == 1);
                ann.setIsExemplar(exemplar);
            }
            System.out.println("INFO: " + ann + " pulled from IA");
            return ann;

        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("getAnnotationFromIA(" + annId + ") error " + ex.toString());
        }
    }

    public static Annotation getAnnotationFromIADEBUG(String annId, Shepherd myShepherd, PrintWriter out) {
        String context = myShepherd.getContext();

        try {
            out.println(1);
            String idSuffix = "?annot_uuid_list=[" + toFancyUUID(annId) + "]";
            JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/image/uuid/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONObject(0) == null)) throw new RuntimeException("could not get image uuid");
            String imageUUID = fromFancyUUID(rtn.getJSONArray("response").getJSONObject(0));
            MediaAsset ma = grabMediaAsset(imageUUID, myShepherd);
            if (ma == null) throw new RuntimeException("could not find MediaAsset " + imageUUID);
            out.println(2);

            //now we need the bbox to make the Feature
            rtn = RestClient.get(iaURL(context, "/api/annot/bbox/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONArray(0) == null)) throw new RuntimeException("could not get annot bbox");
            JSONArray jbb = rtn.getJSONArray("response").getJSONArray(0);
            JSONObject fparams = new JSONObject();
            fparams.put("x", jbb.optInt(0, 0));
            fparams.put("y", jbb.optInt(1, 0));
            fparams.put("width", jbb.optInt(2, -1));
            fparams.put("height", jbb.optInt(3, -1));
            Feature ft = new Feature("org.ecocean.boundingBox", fparams);
            ma.addFeature(ft);
            out.println(3);

            rtn = RestClient.get(iaURL(context, "/api/annot/species/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optString(0, null) == null)) throw new RuntimeException("could not get annot species");
            String speciesString = rtn.getJSONArray("response").getString(0);
            out.println(4);

            Annotation ann = new Annotation(speciesString, ft);
            ann.setId(annId);  //nope we dont want random uuid, silly
            rtn = RestClient.get(iaURL(context, "/api/annot/exemplar/json/" + idSuffix));
            if ((rtn != null) && (rtn.optJSONArray("response") != null)) {
                boolean exemplar = (rtn.getJSONArray("response").optInt(0, 0) == 1);
                ann.setIsExemplar(exemplar);
            }
            out.println(5);
            System.out.println("INFO: " + ann + " pulled from IA");
            return ann;

        } catch (Exception ex) {
            ex.printStackTrace(out);
            return null;
        }
    }



    public static MediaAsset grabMediaAsset(String maUUID, Shepherd myShepherd) {
        MediaAsset ma = MediaAssetFactory.loadByUuid(maUUID, myShepherd);
        if (ma != null) return ma;
        return getMediaAssetFromIA(maUUID, myShepherd);
    }

//http://52.37.240.178:5000/api/image/src/json/cb2e67a4-7094-d971-c5c6-3b5bed251fec/
    //making a decision to persist these upon creation... there was a conflict cuz loadByUuid above failed on subsequent
    //  iterations and this was created multiple times before saving
    public static MediaAsset getMediaAssetFromIA(String maUUID, Shepherd myShepherd) {
        //note: we add /fakedir/ cuz the file doesnt need to exist there; we just want to force a hashed subdir to be created in params
        File file = new File("/fakedir/" + maUUID + ".jpg"); //how do we get real extension?
        AssetStore astore = AssetStore.getDefault(myShepherd);
        JSONObject params = astore.createParameters(file);
        MediaAsset ma = new MediaAsset(astore, params);
        ma.setUUID(maUUID);
        try {
            //grab the url to our localPath for convenience (e.g. child assets to be created from)
            file = ma.localPath().toFile();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            //TODO we actually need to handle bad maUUID better.  :( (returns
            RestClient.writeToFile(iaURL(myShepherd.getContext(), "/api/image/src/json/" + maUUID + "/"), file);
            ma.copyIn(file);
            ma.addDerivationMethod("pulledFromIA", System.currentTimeMillis());
            ma.updateMetadata();
            ma.updateStandardChildren(myShepherd);
        } catch (IOException ioe) {
            throw new RuntimeException("getMediaAssetFromIA " + ioe.toString());
        }
        ma.addLabel("_original");
        DateTime dt = null;
        try {
            dt = iaDateTimeFromImageUUID(maUUID, myShepherd.getContext());
        } catch (Exception ex) {}
        if (dt != null) ma.setUserDateTime(dt);

        try {
            Double[] ll = iaLatLonFromImageUUID(maUUID, myShepherd.getContext());
            if ((ll != null) && (ll.length == 2) && (ll[0] != null) && (ll[1] != null)) {
                ma.setUserLatitude(ll[0]);
                ma.setUserLongitude(ll[1]);
            }
        } catch (Exception ex) {}
        myShepherd.getPM().makePersistent(ma);
        System.out.println("INFO: " + ma + " pulled from IA (and persisted!)");
        return ma;
    }

/*
from that you can grab all the images in the image set
then you get all the annotations from the imagse
then you grab the names of the annotations
then you can grab all annotations from each name
the annotations in that set that are not in the occurrence set are the ones that may potentially be merged
I think that is the general walk that needs to happen
*/

    /* generally two things are done here: (1) the setId is made into an Occurrence and the Annotations are added to it (by way of Encounters); and
       (2) a name check is done to possibly merge other Annotations to this indiv  */
    public static JSONObject mergeIAImageSet(String setId, Shepherd myShepherd) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        Occurrence existingOccurrence = null;
        try {
            existingOccurrence = ((Occurrence) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Occurrence.class, setId), true)));
        } catch (Exception ex) {}  //this just means not found... which is good!
        if (existingOccurrence != null) throw new RuntimeException("An Occurrence with id " + setId + " already exists.");
        /*  these are really only for lewa branch
        int setIdInt = iaImageSetIdFromUUID(setId);
        //iaSmartXmlFromSetId
        //iaSmartXmlWaypointIdFromSetId
        */

        //http://52.37.240.178:5000/api/imageset/annot/aids/json/?imageset_uuid_list=[%7B%22__UUID__%22:%228655a73d-749b-4f23-af92-0b07157c0455%22%7D]
        //http://52.37.240.178:5000/api/imageset/annot/uuid/json/?imageset_uuid_list=[{%22__UUID__%22:%228e0850a7-7b29-4150-aedb-8bafb5149757%22}]
        //JSONObject res = RestClient.get(iaURL(myShepherd.getContext(), "/api/imageset/annot/rowid/?imgsetid_list=[" + setId + "]"));
        JSONObject res = RestClient.get(iaURL(myShepherd.getContext(), "/api/imageset/annot/uuid/json/?imageset_uuid_list=[" + toFancyUUID(setId) + "]"));
        if ((res == null) || (res.optJSONArray("response") == null) || (res.getJSONArray("response").optJSONArray(0) == null)) throw new RuntimeException("could not get list of annot ids from setId=" + setId);
        JSONObject rtn = new JSONObject("{\"success\": false}");

        //String setIdUUID = iaImageSetUUIDFromId(setId);

        JSONArray auuids = res.getJSONArray("response").getJSONArray(0);
        System.out.println("auuids = " + auuids);
        if ((auuids == null) || (auuids.length() < 1)) throw new RuntimeException("ImageSet id " + setId + " has no Annotations.");

        //these will be used at the end to know what annots were original in the set (for Occurrence)
        //JSONArray oau = iaAnnotationUUIDsFromIds(aids);
        List<String> origAnnUUIDs = new ArrayList<String>();
        for (int j = 0 ; j < auuids.length() ; j++) {
            origAnnUUIDs.add(fromFancyUUID(auuids.optJSONObject(j)));
        }
        System.out.println("origAnnUUIDs = " + origAnnUUIDs);

        JSONArray nameUUIDs = iaAnnotationNameUUIDsFromUUIDs(auuids, myShepherd.getContext());  //note: these are fancy uuids
        System.out.println("nameUUIDs = " + nameUUIDs);
        List<JSONObject> funuuids = new ArrayList<JSONObject>();  //these are fancy!
        List<String> unuuids = new ArrayList<String>();  //but these are unfancy!
        for (int i = 0 ; i < nameUUIDs.length() ; i++) {
            String n = fromFancyUUID(nameUUIDs.optJSONObject(i));
            if ((n == null) || unuuids.contains(n)) continue;
            funuuids.add(nameUUIDs.optJSONObject(i));
            unuuids.add(n);
        }
System.out.println("unuuids = " + unuuids);
System.out.println("funuuids = " + funuuids);
        //JSONArray jall = iaAnnotationUUIDsFromNameUUIDs(new JSONArray(nameUUIDs));
        JSONArray jall = iaAnnotationUUIDsFromNameUUIDs(new JSONArray(funuuids), myShepherd.getContext());
        if (jall.length() != unuuids.size()) throw new RuntimeException("mergeIAImageSet() annots from name size discrepancy");
        System.out.println("jall = " + jall);

        HashMap<String,String> nameMap = iaNameMapUUIDToString(new JSONArray(funuuids), myShepherd.getContext());
        System.out.println("nameMap = " + nameMap);

        //now we walk through and resolve groups of annotations which must be (re)named....
        List<Annotation> anns = new ArrayList<Annotation>();
        List<Encounter> encs = new ArrayList<Encounter>();
        List<MarkedIndividual> newIndivs = new ArrayList<MarkedIndividual>();
        for (int i = 0 ; i < jall.length() ; i++) {
            JSONArray auuidSet = jall.optJSONArray(i);
            if ((auuidSet == null) || (auuidSet.length() < 1)) continue;
            List<String> auList = new ArrayList<String>();
            for (int j = 0 ; j < auuidSet.length() ; j++) {
                /* critical here is that we only pass on (for assignment) annots which (a) are new from the set, or (b) we already have in wb.
                   not sure what to do of annotations we dont have yet -- they need their own encounters!! TODO FIXME
                   we kind of decided ignore what we dont have yet.... (?) i think. */
                String u = fromFancyUUID(auuidSet.optJSONObject(j));
                if (origAnnUUIDs.contains(u)) {
                    auList.add(u);
                } else {
                    Annotation ann = null;
                    try {
                        ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, u), true)));
                    } catch (Exception ex) {}
                    if (ann != null) {
                        auList.add(u);
                    } else {
                        System.out.println("--- WARNING: Annotation " + u + " was not in original ImageSet but is not in WB so cannot assign name to Encounter");
                    }
                }
            }
            HashMap<String,Object> done = assignFromIA(nameMap.get(unuuids.get(i)), auList, myShepherd);
            anns.addAll((List<Annotation>)done.get("annotations"));
            encs.addAll((List<Encounter>)done.get("encounters"));
            if (done.get("newMarkedIndividual") != null) {
                MarkedIndividual newIndiv = (MarkedIndividual)done.get("newMarkedIndividual");
                System.out.println(" +++ seems we have a new " + newIndiv);
                newIndivs.add(newIndiv);
            }
        }

        rtn.put("success", true);
        System.out.println("_++++++++++++++++++++++++++++++ anns ->\n" + anns);


        //at this point we should have "everything" in wb that "we need".... so we push the relative Encounters into this Occurrence
        Occurrence occ = null;  //gets created when we have our first Annotation below

        JSONArray ji = new JSONArray();
        for (MarkedIndividual ind : newIndivs) {
          System.out.println(" >>>> " + ind);
            myShepherd.getPM().makePersistent(ind);
            ji.put(ind.getIndividualID());
        }
        if (ji.length() > 0) rtn.put("newMarkedIndividuals", ji);

        JSONArray je = new JSONArray();
        for (Encounter enc : encs) {
          System.out.println(" >>>> " + enc);
          System.out.println(" --------------------------_______________________________ " + enc.getIndividualID() + " +++++++++++++++++++++++++++++");
            myShepherd.getPM().makePersistent(enc);
            je.put(enc.getCatalogNumber());
            boolean addToOccurrence = false;
            for (Annotation ea : enc.getAnnotations()) {
                if (origAnnUUIDs.contains(ea.getId())) {
                    addToOccurrence = true;
                    break;
                }
            }
            if (addToOccurrence) {
                if (occ == null) {
                    //TODO should we allow recycling an existing Occurrence?  (i.e. loading it here if it exists)
                    occ = new Occurrence(setId, enc);
                } else {
                    occ.addEncounter(enc);
                }
            }
        }
        if (je.length() > 0) rtn.put("encounters", je);

        JSONArray ja = new JSONArray();
        for (Annotation ann : anns) {
          System.out.println(" >>>> " + ann);
            myShepherd.getPM().makePersistent(ann);
            ja.put(ann.getId());
        }
        if (ja.length() > 0) rtn.put("annotations", ja);

        if (occ != null) {  //would this ever be???
            myShepherd.getPM().makePersistent(occ);
            rtn.put("occurrenceId", occ.getOccurrenceID());
            System.out.println(" >>>>>>> " + occ.getOccurrenceID());
        }

        return rtn;
    }


    //we make an assumption here that if there are orphaned annotations (not in Encounters already) they should be grouped
    //  together into one (new) Encounter, since annUUIDs is assumed to be coming from an Occurrence.  be warned!
    public static HashMap<String,Object> assignFromIA(String individualId, List<String> annUUIDs, Shepherd myShepherd) {
System.out.println("#############################################################################################\nindividualId=" + individualId + "\n assign to --> " + annUUIDs);
        HashMap<String,Object> rtn = new HashMap<String,Object>();
        List<Annotation> anns = grabAnnotations(annUUIDs, myShepherd);
        if (anns.size() != annUUIDs.size()) throw new RuntimeException("assignFromIA() grabbed annots differ in size from passed uuids");
System.out.println(anns);
        List<Encounter> encs = new ArrayList<Encounter>();
        ArrayList<Annotation> needEnc = new ArrayList<Annotation>();

        for (Annotation ann : anns) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (enc == null) {
                needEnc.add(ann);
                continue;
            }
            // now we have to deal with an existing encounter that contains this annot.... if it has sibling annots in the encounter
            //  we have to consider whether they too are getting renamed.  if they are, easy; if not... :( we have to split up this encounter
            ArrayList<Annotation> staying = new ArrayList<Annotation>();
            ArrayList<Annotation> going = new ArrayList<Annotation>();
            for (Annotation eann : enc.getAnnotations()) {
                //we are just going to compare ids, since i dont trust Annotation equivalence
                if (annUUIDs.contains(eann.getId())) {
                    going.add(eann);
                } else {
                    staying.add(eann);
                }
            }
            if (staying.size() == 0) {  //we dont need a new encounter; we just modify the indiv on here
                if (!encs.contains(enc)) encs.add(enc);
            } else {  //we need to split up the encounter, with a newer one that gets the new indiv id
                Encounter newEnc = enc.cloneWithoutAnnotations();
                System.out.println("INFO: assignFromIA() splitting " + enc + " - staying=" + staying + "; to " + newEnc + " going=" + going);
                enc.setAnnotations(staying);
                newEnc.setAnnotations(going);
                encs.add(newEnc);
            }

        }

System.out.println("---------------------------------------------\n encs? " + encs);
System.out.println("---------------------------------------------\n needEnc? " + needEnc);

        if (needEnc.size() > 0) {
            Encounter newEnc = new Encounter(needEnc);
/*  dont need this any more as annot already set times when passed to Encounter() constructor above!
            DateTime dt = null;
            try {
                dt = iaDateTimeFromAnnotUUID(needEnc.get(0).getId());
            } catch (Exception ex) {}
            if (dt != null) newEnc.setDateInMilliseconds(dt.getMillis());
System.out.println(" ============ dt millis = " + dt);
*/
            System.out.println("INFO: assignFromIA() created " + newEnc + " for " + needEnc.size() + " annots");
            encs.add(newEnc);
        }

        for (Encounter enc : encs) {
            if (enc.hasMarkedIndividual() && !enc.getIndividualID().equals(individualId)) {
                System.out.println("WARNING: assignFromIA() assigning indiv " + individualId + " to " + enc + " which will replace " + enc.getIndividualID());
            }
        }

        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(individualId);
        int startE = 0;
        if (indiv == null) {
            indiv = new MarkedIndividual(individualId, encs.get(0));
            encs.get(0).setIndividualID(individualId);
            startE = 1;
            System.out.println("INFO: assignFromIA() created " + indiv);
            rtn.put("newMarkedIndividual", indiv);
        }
        for (int i = startE ; i < encs.size() ; i++) {
            if (individualId.equals(encs.get(i).getIndividualID())) {
                System.out.println("INFO: " + encs.get(i) + " already was assigned to indiv; skipping");
                continue;
            }
            indiv.addEncounter(encs.get(i), myShepherd.getContext());
            encs.get(i).setIndividualID(individualId);
        }
        indiv.refreshNumberEncounters();

        rtn.put("encounters", encs);
        rtn.put("annotations", anns);
        return rtn;
    }

    //this is a "simpler" assignment -- unlike above, it wont make anything new, but rather will only (re)assign if all is good
    public static HashMap<String,Object> assignFromIANoCreation(String individualId, List<String> annUUIDs, Shepherd myShepherd) {
System.out.println("#######(no create)###########################################################################\nindividualId=" + individualId + "\n assign to --> " + annUUIDs);
        HashMap<String,Object> rtn = new HashMap<String,Object>();
        List<Annotation> anns = new ArrayList<Annotation>();
        for (String aid : annUUIDs) {
            Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
            if (ann == null) {
                System.out.println("WARNING: assignFromIANoCreate() could not load annot id=" + aid);
            } else {
                anns.add(ann);
            }
        }
        if (anns.size() != annUUIDs.size()) throw new RuntimeException("assignFromIANoCreation() could not find some annots from passed uuids");

System.out.println(anns);
        List<Encounter> encs = new ArrayList<Encounter>();
        for (Annotation ann : anns) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (enc == null) throw new RuntimeException("assignFromIANoCreation() could not find an encounter for annot " + ann.getId());
            // now we have to deal with an existing encounter that contains this annot.... if it has sibling annots in the encounter
            ArrayList<Annotation> staying = new ArrayList<Annotation>();
            ArrayList<Annotation> going = new ArrayList<Annotation>();
            for (Annotation eann : enc.getAnnotations()) {
                if (annUUIDs.contains(eann.getId())) {
                    going.add(eann);
                } else {
                    //lets see what IA says about this annot. with luck, it should get renamed too!
                    String iaName = "__FAIL1__";
                    try {
                        JSONArray jn = iaNamesFromAnnotUUIDs(new JSONArray("[" + toFancyUUID(eann.getId()) + "]"), myShepherd.getContext());
                        iaName = jn.optString(0, "__FAIL2__");
                    } catch (Exception ex) {
                        System.out.println("WARNING: assignFromIANoCreation() faild name lookup - " + ex.toString());
                    }
                    if (individualId.equals(iaName)) {
                        going.add(eann);
                    } else {
                        //staying.add(eann);
                        System.out.println("CRITICAL: assignFromIANoCreation() " + enc + " requires split; IA reports " + eann + " ident is " + iaName);
                        throw new RuntimeException("reassigning Annotation " + ann.getId() + " to " + individualId + " would cause split on Encounter " + enc.getCatalogNumber());
                    }
                }
            }

/* going to NOT do this for now and just throw exception (above)....  til we get a handle on how often this happens
            if (staying.size() == 0) {  //we dont need a new encounter; we just modify the indiv on here
                if (!encs.contains(enc)) encs.add(enc);
            } else {  //we need to split up the encounter, with a newer one that gets the new indiv id
                System.out.println("CRITICAL: assignFromIANoCreation() " + enc + " requires split - staying=" + staying + "; going=" + going);
                throw new RuntimeException("reassigning Annotation " + ann.getId() + " to " + individualId + " would cause split on Encounter " + enc.getCatalogNumber());
                Encounter newEnc = enc.cloneWithoutAnnotations();
                System.out.println("INFO: assignFromIA() splitting " + enc + " - staying=" + staying + "; to " + newEnc + " going=" + going);
                enc.setAnnotations(staying);
                newEnc.setAnnotations(going);
                encs.add(newEnc);
            }
*/

            //right now if we get to here, this enc is good to be reassigned ... TODO logic here will change a bit when handling splits better
            encs.add(enc);
        }
System.out.println("assignFromIANoCreation() okay to reassign: " + encs);

        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(individualId);
        int startE = 0;
        if (indiv == null) {
            indiv = new MarkedIndividual(individualId, encs.get(0));
            encs.get(0).setIndividualID(individualId);
            startE = 1;
            System.out.println("INFO: assignFromIANoCreate() created " + indiv);
            rtn.put("newMarkedIndividual", indiv);
        }
        for (int i = startE ; i < encs.size() ; i++) {
            if (individualId.equals(encs.get(i).getIndividualID())) {
                System.out.println("INFO: " + encs.get(i) + " already was assigned to indiv; skipping");
                continue;
            }
            indiv.addEncounter(encs.get(i), myShepherd.getContext());
            encs.get(i).setIndividualID(individualId);
        }

        rtn.put("encounters", encs);
        rtn.put("annotations", anns);
        return rtn;
    }

    //a sort of wrapper to the above from the point of view of an api (does saving, json stuff etc)
    // note: "simpler" will not create new objects and only do straight-up assignments -- it will throw exceptions if too complex
    public static JSONObject assignFromIAAPI(JSONObject arg, Shepherd myShepherd, boolean simpler) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        if (arg == null) {
            rtn.put("error", "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
            return rtn;
        }
        String indivId = arg.optString("name", null);
        JSONArray annIds = arg.optJSONArray("annotationIds");
        if ((indivId == null) || (annIds == null) || (annIds.length() < 1)) {
            rtn.put("error", "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
            return rtn;
        }
        List<String> al = new ArrayList<String>();
        for (int i = 0 ; i < annIds.length() ; i++) {
            String a = annIds.optString(i, null);
            if (a != null) al.add(a);
        }
        myShepherd.beginDBTransaction();
        HashMap<String,Object> res = null;
        if (simpler) {
            res = assignFromIANoCreation(indivId, al, myShepherd);
        } else {
            res = assignFromIA(indivId, al, myShepherd);
        }

        rtn.put("success", true);
        if (res.get("newMarkedIndividual") != null) {
            MarkedIndividual ind = (MarkedIndividual)res.get("newMarkedIndividual");
            myShepherd.getPM().makePersistent(ind);
            rtn.put("newMarkedIndividual", ind.getIndividualID());
        }
        if (res.get("encounters") != null) {
            JSONArray je = new JSONArray();
            List<Encounter> encs = (List<Encounter>)res.get("encounters");
            for (Encounter enc : encs) {
                myShepherd.getPM().makePersistent(enc);
                je.put(enc.getCatalogNumber());
            }
            rtn.put("encounters", je);
        }
        if (res.get("annotations") != null) {
            JSONArray ja = new JSONArray();
            List<Annotation> anns = (List<Annotation>)res.get("annotations");
            for (Annotation ann : anns) {
                ja.put(ann.getId());
            }
            rtn.put("annotations", ja);
        }
        myShepherd.commitDBTransaction();
        return rtn;
    }

    //wrapper like above, but *only* get annot ids, we ask IA for their names and group accordingly
    public static JSONObject arbitraryAnnotationsFromIA(JSONArray arg, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        if (arg == null) {
            rtn.put("error", "invalid parameters: pass array of Annotation ids");
            return rtn;
        }
        HashMap<String,JSONObject> map = new HashMap<String,JSONObject>();
        for (int i = 0 ; i < arg.length() ; i++) {
            String aid = arg.optString(i, null);
            if (aid == null) continue;
            JSONArray auuids = new JSONArray();
            auuids.put(toFancyUUID(aid));
            JSONArray namesRes = null;
            try {
                namesRes = iaAnnotationNamesFromUUIDs(auuids, myShepherd.getContext());
            } catch (Exception ex) {}
            if ((namesRes == null) || (namesRes.length() < 1)) {
                System.out.println("WARNING: arbitraryAnnotationsFromIA() could not get a name for annot " + aid + "; skipping");
                continue;
            }
            String name = namesRes.optString(0, null);
            if (name == null) {
                System.out.println("WARNING: arbitraryAnnotationsFromIA() could not get[2] a name for annot " + aid + "; skipping");
                continue;
            }
            if (map.get(name) == null) {
                JSONObject j = new JSONObject();
                j.put("name", name);
                j.put("annotationIds", new JSONArray());
                map.put(name, j);
            }
            map.get(name).getJSONArray("annotationIds").put(aid);
        }
System.out.println(map);

        rtn.put("success", true);
        JSONObject all = new JSONObject();
        for (String nm : map.keySet()) {
            all.put(nm, assignFromIAAPI(map.get(nm), myShepherd, false));
        }
        rtn.put("reassignments", all);
        return rtn;
    }

/*
    these are mostly utility functions to fetch stuff from IA ... some of these may be unused currently but got made during chaostime
*/

    public static JSONArray __iaAnnotationUUIDsFromIds(JSONArray aids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/uuid/?aid_list=" + aids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/annot/name/uuid/json/?annot_uuid_list=[{%22__UUID__%22:%22c368747b-a4a8-4f59-900d-a9a529c92bca%22}]&__format__=True
    public static JSONArray iaAnnotationNameUUIDsFromUUIDs(JSONArray uuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/name/uuid/json/?annot_uuid_list=" + uuids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot name uuid from uuids=" + uuids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/annot/name/text/json/?annot_uuid_list=[{%22__UUID__%22:%22c368747b-a4a8-4f59-900d-a9a529c92bca%22}]&__format__=True
    public static JSONArray iaAnnotationNamesFromUUIDs(JSONArray uuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/name/text/json/?annot_uuid_list=" + uuids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot name uuid from uuids=" + uuids);
        return rtn.getJSONArray("response");
    }
    public static JSONArray __iaAnnotationNameIdsFromIds(JSONArray aids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/name/rowid/?aid_list=" + aids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }
    public static JSONArray __iaAnnotationNamesFromIds(JSONArray aids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/name/?aid_list=" + aids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/name/annot/rowid/?nid_list=[5]
    public static JSONArray ___iaAnnotationIdsFromNameIds(JSONArray nids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/name/annot/rowid/?nid_list=" + nids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot ids from nids=" + nids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/name/annot/uuid/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True
    public static JSONArray iaAnnotationUUIDsFromNameUUIDs(JSONArray uuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/name/annot/uuid/json/?name_uuid_list=" + uuids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get annot uuids from name uuids=" + uuids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/name/text/?name_rowid_list=[5,21]&__format__=True
    public static JSONArray __iaNamesFromNameIds(JSONArray nids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/name/text/?name_rowid_list=" + nids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get names from nids=" + nids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/name/text/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True
    public static JSONArray iaNamesFromNameUUIDs(JSONArray uuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/name/text/json/?name_uuid_list=" + uuids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get names from name uuids=" + uuids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/annot/name/text/json/?annot_uuid_list=[{%22__UUID__%22:%20%22deee5d41-c264-4179-aa6c-5b735975cbc9%22}]&__format__=True
    public static JSONArray iaNamesFromAnnotUUIDs(JSONArray auuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/name/text/json/?annot_uuid_list=" + auuids.toString()));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get names from auuids=" + auuids);
        return rtn.getJSONArray("response");
    }
//http://52.37.240.178:5000/api/imageset/smart/xml/file/content/?imageset_rowid_list=[65]
    public static String iaSmartXmlFromSetId(int setId, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/imageset/smart/xml/file/content/?imageset_rowid_list=[" + setId + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get set smartXml from set id=" + setId);
        return rtn.getJSONArray("response").optString(0, null);
    }
//http://52.37.240.178:5000/api/imageset/smart/waypoint/?imageset_rowid_list=[55]
    public static int iaSmartXmlWaypointIdFromSetId(int setId, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/imageset/smart/waypoint/?imageset_rowid_list=[" + setId + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get set smartXml waypoint id from set id=" + setId);
        return rtn.getJSONArray("response").optInt(0, -1);
    }
    public static HashMap<Integer,String> __iaNameMapIdToString(JSONArray nids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        HashMap<Integer,String> map = new HashMap<Integer,String>();
        JSONArray names = __iaNamesFromNameIds(nids, context);
        if (nids.length() != names.length()) throw new RuntimeException("iaNameMapIdToString() arrays have different lengths");
        for (int i = 0 ; i < names.length() ; i++) {
            map.put(nids.optInt(i, -1), names.optString(i, null));
        }
        return map;
    }
    public static HashMap<String,String> iaNameMapUUIDToString(JSONArray uuids, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        HashMap<String,String> map = new HashMap<String,String>();
        JSONArray names = iaNamesFromNameUUIDs(uuids, context);
        if (uuids.length() != names.length()) throw new RuntimeException("iaNameMapUUIDToString() arrays have different lengths");
        for (int i = 0 ; i < names.length() ; i++) {
            map.put(fromFancyUUID(uuids.optJSONObject(i)), names.optString(i, null));
        }
        return map;
    }
//http://52.37.240.178:5000/api/imageset/uuid/?imgsetid_list=[3]
    public static String iaImageSetUUIDFromId(int setId, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/imageset/uuid/?imgsetid_list=[" + setId + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get set uuid from id=" + setId);
        return fromFancyUUID(rtn.getJSONArray("response").optJSONObject(0));
    }
//http://52.37.240.178:5000/api/imageset/rowid/uuid/?uuid_list=[%7B%22__UUID__%22:%228e0850a7-7b29-4150-aedb-8bafb5149757%22%7D]
    public static int iaImageSetIdFromUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/imageset/rowid/uuid/?uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get set id from uuid=" + uuid);
        return rtn.getJSONArray("response").optInt(0, -1);
    }
//  this --> is from annot uuid  (note returns in seconds, not milli)
//http://52.37.240.178:5000/api/annot/image/unixtime/json/?annot_uuid_list=[{%22__UUID__%22:%20%22e95f6af3-4b7a-4d29-822f-5074d5d91c9c%22}]
    public static DateTime iaDateTimeFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/image/unixtime/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get unixtime from annot uuid=" + uuid);
        long t = rtn.getJSONArray("response").optLong(0, -1);
        if (t == -1) return null;
        return new DateTime(t * 1000);  //IA returns secs not millisecs
    }
//http://52.37.240.178:5000/api/annot/image/gps/json/?annot_uuid_list=[{%22__UUID__%22:%20%22e95f6af3-4b7a-4d29-822f-5074d5d91c9c%22}]
    public static Double[] iaLatLonFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/image/gps/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONArray(0) == null)) throw new RuntimeException("could not get gps from annot uuid=" + uuid);
        JSONArray ll = rtn.getJSONArray("response").getJSONArray(0);
        return new Double[]{ ll.optDouble(0), ll.optDouble(1) };
    }
//http://52.37.240.178:5000/api/image/lat/json/?image_uuid_list=[{%22__UUID__%22:%22e985b3d4-bb2a-8291-07af-1ec4028d4649%22}]
    public static Double[] iaLatLonFromImageUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/image/gps/json/?image_uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null) || (rtn.getJSONArray("response").optJSONArray(0) == null)) throw new RuntimeException("could not get gps from image uuid=" + uuid);
        JSONArray ll = rtn.getJSONArray("response").getJSONArray(0);
        return new Double[]{ ll.optDouble(0), ll.optDouble(1) };
    }
//http://52.37.240.178:5000/api/image/unixtime/json/?image_uuid_list=[{%22__UUID__%22:%22cb2e67a4-7094-d971-c5c6-3b5bed251fec%22}]
    public static DateTime iaDateTimeFromImageUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/image/unixtime/json/?image_uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get unixtime from image uuid=" + uuid);
        long t = rtn.getJSONArray("response").optLong(0, -1);
        if (t == -1) return null;
        return new DateTime(t * 1000);  //IA returns secs not millisecs
    }
//http://52.37.240.178:5000/api/name/sex/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True
/*
    public static String iaSexFromName(String name) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL("context0", "/api/annot/image/unixtime/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get unixtime from annot uuid=" + uuid);
        long t = rtn.getJSONArray("response").optLong(0, -1);
        if (t == -1) return null;
        return null;
    }
*/
//http://52.37.240.178:5000/api/annot/sex/json/?annot_uuid_list=[{%22__UUID__%22:%224517636f-65ad-a236-950c-107f2c962c19%22}]
    public static String iaSexFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/sex/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));
System.out.println(">>>>>>>> sex -> " + rtn);
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get age from annot uuid=" + uuid);
        int sexi = rtn.getJSONArray("response").optInt(0, -1);
        if (sexi == -1) return null;
        //what else???
        return null;
    }
//http://52.37.240.178:5000/api/annot/age/months/json/?annot_uuid_list=[{%22__UUID__%22:%224517636f-65ad-a236-950c-107f2c962c19%22}]
// note - returns array with min/max.... doubles?
    public static Double iaAgeFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context, "/api/annot/age/months/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));
System.out.println(">>>>>>>> age -> " + rtn);
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) throw new RuntimeException("could not get age from annot uuid=" + uuid);
        //return rtn.getJSONArray("response").optDouble(0, (Double)null);
        return (Double)null;
    }

    public static boolean iaEnabled(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        return (CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context) != null);
    }
    public static boolean iaEnabled() {
        return (CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", ContextConfiguration.getDefaultContext()) != null);
    }

    public static JSONObject iaStatus(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        JSONObject rtn = new JSONObject();
        String utest = CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context);
        if (utest == null) {
            rtn.put("iaURL", (String)null);
            rtn.put("iaEnabled", false);
        } else {
            URL iau = iaURL(context, "");
            rtn.put("iaURL", iau.toString());
            rtn.put("iaEnabled", true);
/*  turns out this is kinda crazy expensive on the IA side!  so we certainly dont want to do this unless we really need to.
            try {
                // these 2 seem borked
                //JSONObject rtn = RestClient.get(iaURL("context0", "/api/core/version/")
                //JSONObject rtn = RestClient.get(iaURL("context0", "/api/core/db/version/")
                JSONObject r = RestClient.get(iaURL("context0", "/api/core/db/name/"));
                if ((r != null) && (r.optString("response", null) != null)) rtn.put("iaDbName", r.getString("response"));
                r = RestClient.get(iaURL("context0", "/api/core/db/info/"));
                if ((r != null) && (r.optString("response", null) != null)) rtn.put("iaDbInfo", r.getString("response"));
            } catch (Exception ex) {}
*/
        }
        rtn.put("timestamp", System.currentTimeMillis());
        JSONObject settings = new JSONObject();  //TODO this is just one, as a kind of sanity check/debugging -- sh/could expand to more if needed
        settings.put("IBEISIARestUrlAddAnnotations", CommonConfiguration.getProperty("IBEISIARestUrlAddAnnotations", context));
        rtn.put("settings", settings);
        return rtn;
    }

    public static boolean unknownName(String name) {
        if (name == null) return true;
        if (name.equals(IA_UNKNOWN_NAME)) return true;
        return false;
    }

     /*
     *   DEPRECATED!   see primeIA() instead
     * This static method sends all annotations and media assets for a species in Wildbook to Image Analysis in preparation for future matching.
     * It basically primes the system.
     */
    public static JSONObject primeImageAnalysisForSpecies(ArrayList<Encounter> targetEncs, Shepherd myShepherd, String species, String baseUrl) {
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

        log("Prime image analysis for "+species, jobID, new JSONObject("{\"_action\": \"init\"}"), myShepherd.getContext());

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
            results.put("sendMediaAssets", sendMediaAssets(mas, myShepherd.getContext()));
            results.put("sendAnnotations", sendAnnotations(allAnns, myShepherd.getContext()));

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
        log("Prime image analysis for "+species, jobID, jlog, myShepherd.getContext());

        return results;
    }


/*
status: {
_action: "getJobResult",
_response: {
response: {
json_result: {
query_annot_uuid_list: [
{
__UUID__: "ea272459-c82c-4f37-9800-045965fd1393"
}
],
query_config_dict: { },
inference_dict: {
annot_pair_dict: {
review_pair_list: [
{
prior_matching_state: {
p_match: 0.9470680954707609,
p_nomatch: 0.05293190452923913,
p_notcomp: 0
},
annot_uuid_2: {
__UUID__: "b889b610-55aa-4407-8b02-b5632839a201"
},
annot_uuid_1: {
__UUID__: "ea272459-c82c-4f37-9800-045965fd1393"
},
annot_uuid_key: {
__UUID__: "ea272459-c82c-4f37-9800-045965fd1393"
}
}
],
confidence_list: [
0.7994795279514134
]
},
*/
    //qid (query id) can be null, in which case the first one we find is good enough
    public static JSONArray simpleResultsFromAnnotPairDict(JSONObject apd, String qid) {
        if (apd == null) return null;
        JSONArray rlist = apd.optJSONArray("review_pair_list");
        JSONArray clist = apd.optJSONArray("confidence_list");
        if ((rlist == null) || (rlist.length() < 1)) return null;
        if (qid == null) qid = fromFancyUUID(rlist.getJSONObject(0).optJSONObject("annot_uuid_key"));
System.out.println("using qid -> " + qid);
        JSONArray res = new JSONArray();
        for (int i = 0 ; i < rlist.length() ; i++) {
            if (rlist.optJSONObject(i) == null) continue;
            if (!qid.equals(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_key")))) continue;
            JSONArray s = new JSONArray();
            s.put(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")));
            s.put(clist.optDouble(i, 0.0));
            res.put(s);
        }
        if (res.length() < 1) return null;
        return res;
    }

    //not really sure what/how to do this...
    public static JSONObject queryConfigDict(Shepherd myShepherd, String species, JSONObject opt) {
System.out.println("queryConfigDict() got species=" + species + "; and opt = " + opt);
        if (opt == null) return null;

        // and this is oriented curvature + weighted dynamic time-warping
        if (opt.optBoolean("OC_WDTW", false)) return new JSONObject("{\"pipeline_root\": \"OC_WDTW\"}");

        //boring fallback...
        return null;

        // this is trailing edge matching but takes foreeeevvvver
        //return new JSONObject("{\"pipeline_root\": \"BC_DTW\"}");
    }

    private static String annotGetIndiv(Annotation ann, Shepherd myShepherd) {
        String id = cacheAnnotIndiv.get(ann.getId());
        if (id != null) return id;
        id = ann.findIndividualId(myShepherd);
        cacheAnnotIndiv.put(ann.getId(), id);
        return id;
    }

    public static void primeIA() {
      primeIA(ContextConfiguration.getDefaultContext());
    }

    public static void primeIA(final String context) {
        setIAPrimed(false);
        if (!iaEnabled()) return;
System.out.println("<<<<< BEFORE : " + isIAPrimed());
System.out.println(" ............. alreadySentMA size = " + alreadySentMA.keySet().size());
        Runnable r = new Runnable() {
            public void run() {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("IBEISIA.class.run");
                myShepherd.beginDBTransaction();
                ArrayList<Annotation> anns = Annotation.getExemplars(myShepherd);
System.out.println("-- priming IBEISIA (anns size: " + anns.size() + ")");
                ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
                for (Annotation ann : anns) {
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma != null) mas.add(ma);
                }
                try {
                    sendMediaAssets(mas,  context);
                    sendAnnotations(anns, context);
                } catch (Exception ex) {
                    System.out.println("!! IBEISIA.primeIA() failed: " + ex.toString());
ex.printStackTrace();
                }
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                setIAPrimed(true);
System.out.println("-- priming IBEISIA **complete**");
            }
        };
        new Thread(r).start();
System.out.println(">>>>>> AFTER : " + isIAPrimed());
    }

    public static synchronized boolean isIAPrimed() {
System.out.println(" ............. alreadySentMA size = " + alreadySentMA.keySet().size());
        return iaPrimed.get();
    }
    public static synchronized void setIAPrimed(boolean b) {
System.out.println(" ???? setting iaPrimed to " + b);
        iaPrimed.set(b);
    }

    public static void waitForIAPriming() {
        int count = 150;
        while (!isIAPrimed()) {
            count--;
            if (count < 0) throw new RuntimeException("waitForIAPriming() gave up! :(");
System.out.println("waitForIAPriming() patiently waiting");
            try { Thread.sleep(2000); } catch (java.lang.InterruptedException ex) {}
        }
        return;
    }

// ** attempt to create some placeholder calls to mimic what will some day (hopefully SOON!) be generalized IA class.  navigate with care.
//    probably want to talk to jon if you have questions

    public static String IAIntake(MediaAsset ma, Shepherd myShepherd, HttpServletRequest request) throws ServletException, IOException {
        return IAIntake(new ArrayList<MediaAsset>(Arrays.asList(ma)), myShepherd, request);  //singleton just sends as list
    }

    public static String IAIntake(List<MediaAsset> mas, Shepherd myShepherd, HttpServletRequest request) throws ServletException, IOException {
//TODO support parent-task (eventually?)
//roughly IA.intake(MediaAsset) i hope... thus this does detection (returns taskId)
//superhactacular!  now we piggyback on IAGateway which kinda does this.  sorta.  obvs this will come into IA.intake() ultimately no?
        String baseUrl = null;
        try {
            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
        } catch (java.net.URISyntaxException ex) {}
        String context = ServletUtilities.getContext(request);
        JSONObject jin = new JSONObject();
        JSONObject jm = new JSONObject();
        JSONArray jmids = new JSONArray();
        for (MediaAsset ma : mas) {
            jmids.put(ma.getId());
        }
        jm.put("mediaAssetIds", jmids);
        jin.put("detect", jm);
        JSONObject res = new JSONObject();
        String taskId = Util.generateUUID();
        res.put("taskId", taskId);
        org.ecocean.servlet.IAGateway._doDetect(jin, res, myShepherd, baseUrl);
System.out.println("IAIntake(detect:" + mas + ") [taskId=" + taskId + "] -> " + res);
        return taskId;
    }

    //ditto above, most things
    public static String IAIntake(Annotation ann, Shepherd myShepherd, HttpServletRequest request) throws ServletException, IOException {
System.out.println("* * * * * * * IAIntake(ident) NOT YET IMPLEMENTED ====> " + ann);
return Util.generateUUID();
/*
        String baseUrl = null;
        try {
            baseUrl = CommonConfiguration.getServerURL(request, request.getContextPath());
        } catch (java.net.URISyntaxException ex) {}
        String context = ServletUtilities.getContext(request);
        JSONObject jin = new JSONObject();
        JSONObject ja = new JSONObject();
        JSONArray jaids = new JSONArray();
        jaids.put(ann.getId());
        ja.put("annotationIds", jaids);
        jin.put("identify", ja);
        JSONObject res = new JSONObject();
        String taskId = Util.generateUUID();
        res.put("taskId", taskId);
        org.ecocean.servlet.IAGateway._doIdentify(jin, res, myShepherd, context, baseUrl);
System.out.println("IAIntake(identify:" + ann + ") [taskId=" + taskId + "] -> " + res);
        return taskId;
*/
    }

/*
    public static String IAIntake(List<Annotation> anns, Shepherd myShepherd, HttpServletRequest request) throws ServletException, IOException {
System.out.println("* * * * * * * IAIntake(ident) NOT YET IMPLEMENTED ====> " + ann);
return Util.generateUUID();
    }
*/

    //this is called when a batch of encounters (which should be on this occurrence) were made from detection
    // *as a group* ... see also Encounter.detectedAnnotation() for the one-at-a-time equivalent
    public static void fromDetection(Occurrence occ, Shepherd myShepherd, HttpServletRequest request, String context)  {
        System.out.println(">>>>>> detection created " + occ.toString());

        //set the locationID/location/date on all encounters by inspecting detected comments on the first encounter
        if((occ.getEncounters()!=null)&&(occ.getEncounters().get(0)!=null)){


          String locCode=null;
          String location="";
          int year=-1;
          int month=-1;
          int day=-1;
          List<Encounter> encounters=occ.getEncounters();
          int numEncounters=encounters.size();
          Encounter enc=encounters.get(0);
          String ytRemarks=enc.getOccurrenceRemarks().trim().toLowerCase();

          String detectedLanguage="en";
          try{
            detectedLanguage= DetectTranslate.detect(ytRemarks, context);

            if(!detectedLanguage.toLowerCase().startsWith("en")){
              ytRemarks= DetectTranslate.translate(ytRemarks, context);
            }
          }
          catch(Exception e){
            System.out.println("I hit an exception trying to detect language.");
            e.printStackTrace();
          }
          //grab texts from yt videos through OCR (before we parse for location/ID and Date) and add it to remarks variable.
          String ocrRemarks="";
          try {
            if((occ.getEncounters()!=null)&&(occ.getEncounters().size()>0)){
              Encounter myEnc=occ.getEncounters().get(0);
              List<MediaAsset> assets= myEnc.getMedia();
              if((assets!=null)&&(assets.size()>0)){
                MediaAsset myAsset = assets.get(0);
                MediaAsset parent = myAsset.getParent(myShepherd);
                if(parent!=null){
                  ArrayList<MediaAsset> frames= YouTubeAssetStore.findFrames(parent, myShepherd);
                  if((frames!=null)&&(frames.size()>0)){
                      ArrayList<File>filesFrames= ocr.makeFilesFrames(frames);
                      //if (ocr.getTextFrames(filesFrames)!=null) {
                        ocrRemarks = ocr.getTextFrames(filesFrames, context);
                        if(ocrRemarks==null)ocrRemarks="";
                        System.out.println("I found OCR remarks: "+ocrRemarks);
                      //}
                      //else {
                      //  ocrRemarks= "";
                      //}
                    }
                  }
                  else{
                    System.out.println("I could not find any frames from YouTubeAssetStore.findFrames for asset:"+myAsset.getId()+" from Encounter "+myEnc.getCatalogNumber());
                  }
              }
              }
            }
            catch (Exception e) {
              e.printStackTrace();
              System.out.println("I hit an exception trying to find ocrRemarks.");
            }

          if(enc.getOccurrenceRemarks()!=null){

            String remarks=ytRemarks+" "+enc.getRComments().trim().toLowerCase()+" "+ ocrRemarks.toLowerCase();

            System.out.println("Let's parse these remarks for date and location: "+remarks);

            Properties props = new Properties();

            //OK, let's check the comments and tags for retrievable metadata
            try {


            //first parse for location and locationID
              try{
                props=ShepherdProperties.getProperties("submitActionClass.properties", "",context);
                Enumeration m_enum = props.propertyNames();
                while (m_enum.hasMoreElements()) {
                  String aLocationSnippet = ((String) m_enum.nextElement()).trim();
                  System.out.println("     Looking for: "+aLocationSnippet);
                  if (remarks.indexOf(aLocationSnippet) != -1) {
                    locCode = props.getProperty(aLocationSnippet);
                    location+=(aLocationSnippet+" ");
                    System.out.println(".....Building an idea of location: "+location);
                  }
                }

              }
              catch(Exception e){
                e.printStackTrace();
              }


              //reset date to exclude OCR, which can currently confuse NLP
              //remarks=ytRemarks+" "+enc.getRComments().trim().toLowerCase();



              //reset remarks to avoid dates embedded in researcher comments
//              remarks=enc.getOccurrenceRemarks().trim().toLowerCase();
              //if no one has set the date already, use NLP to try to figure it out
              boolean setDate=true;
              if(enc.getDateInMilliseconds()!=null){setDate=false;}
              //next use natural language processing for date
              if(setDate){
                boolean NLPsuccess=false;
                try{
                    System.out.println(">>>>>> looking for date with NLP");
                    //call Stanford NLP function to find and select a date from ytRemarks
                    String myDate= ServletUtilities.nlpDateParse(remarks);
                    System.out.println("Finished nlpPrseDate");;
                    //parse through the selected date to grab year, month and day separately.Remove cero from month and day with intValue.
                    if (myDate!=null) {
                        System.out.println(">>>>>> NLP found date: "+myDate);
                        //int numCharact= myDate.length();

                        /*if(numCharact>=4){

                          try{
                            year=(new Integer(myDate.substring(0, 4))).intValue();
                            NLPsuccess=true;

                            if(numCharact>=7){
                              try {
                                month=(new Integer(myDate.substring(5, 7))).intValue();
                                if(numCharact>=10){
                                  try {
                                    day=(new Integer(myDate.substring(8, 10))).intValue();
                                    }
                                  catch (Exception e) { day=-1; }
                                }
                              else{day=-1;}
                              }
                              catch (Exception e) { month=-1;}
                            }
                            else{month=-1;}

                          }
                          catch(Exception e){
                            e.printStackTrace();
                          }
                      }
                        */

                        //current datetime just for quality comparison
                        LocalDateTime dt = new LocalDateTime();

                        DateTimeFormatter parser1 = ISODateTimeFormat.dateOptionalTimeParser();
                        LocalDateTime reportedDateTime=new LocalDateTime(parser1.parseLocalDateTime(myDate));
                        //System.out.println("     reportedDateTime is: "+reportedDateTime.toString(parser1));
                        StringTokenizer str=new StringTokenizer(myDate,"-");
                        int numTokens=str.countTokens();
                        System.out.println("     StringTokenizer for date has "+numTokens+" tokens for String input "+str.toString());

                        if(numTokens>=1){
                          //try {
                          year=reportedDateTime.getYear();
                            if(year>(dt.getYear()+1)){
                              //badDate=true;
                              year=-1;
                              //throw new Exception("    An unknown exception occurred during date processing in EncounterForm. The user may have input an improper format: "+year+" > "+dt.getYear());
                            }

                         //} catch (Exception e) { year=-1;}
                        }
                        if(numTokens>=2){
                          try { month=reportedDateTime.getMonthOfYear(); } catch (Exception e) { month=-1;}
                        }
                        else{month=-1;}
                        //see if we can get a day, because we do want to support only yyy-MM too
                        if(str.countTokens()>=3){
                          try { day=reportedDateTime.getDayOfMonth(); } catch (Exception e) { day=0; }
                        }
                        else{day=-1;}


                    }

//                      Parser parser = new Parser();
//                      List groups = parser.parse(ytRemarks);
//                      int numGroups=groups.size();
//                      //just grab the first group
//                      if(numGroups>0){
//                          List<Date> dates = ((DateGroup)groups.get(0)).getDates();
//                          int numDates=dates.size();
//                          if(numDates>0){
//                            Date myDate=dates.get(0);
//                            LocalDateTime dt = LocalDateTime.fromDateFields(myDate);
//                            String detectedDate=dt.toString().replaceAll("T", "-");
//                            System.out.println(">>>>>> NLP found date: "+detectedDate);
//                            StringTokenizer str=new StringTokenizer(detectedDate,"-");
//                            int numTokens=str.countTokens();
//                            if(numTokens>=1){
//                              NLPsuccess=true;
//                              year=(new Integer(str.nextToken())).intValue();
//                            }
//                            if(numTokens>=2){
//                              try { month=(new Integer(str.nextToken())).intValue();
//                              } catch (Exception e) { month=-1;}
//                            }
//                            else{month=-1;}
//                            if(numTokens>=3){
//                              try {
//                                String myToken=str.nextToken();
//                                day=(new Integer(myToken.replaceFirst("^0+(?!$)", ""))).intValue(); } catch (Exception e) { day=-1; }
//                            }
//                            else{day=-1;}
//                        }
//                    }
                }
                catch(Exception e){
                    System.out.println("Exception in NLP in IBEISIA.class");
                    e.printStackTrace();
                }

                  //NLP failure? let's try brute force detection across all languages supported by this Wildbook
                  if(!NLPsuccess){
                    System.out.println(">>>>>> looking for date with brute force");
                    //next parse for year
                    LocalDateTime dt = new LocalDateTime();
                    int nowYear=dt.getYear();
                    int oldestYear=nowYear-20;
                    for(int i=nowYear;i>oldestYear;i--){
                      String yearCheck=(new Integer(i)).toString();
                      if (ytRemarks.indexOf(yearCheck) != -1) {
                        year=i;
                        System.out.println("...detected a year in comments!");

                        /**
                        //check for month
                        List<String> langs=CommonConfiguration.getIndexedPropertyValues("language", context);
                        int numLangs=langs.size();
                        for(int k=0;k<numLangs;k++){
                            try{
                              Locale locale=new Locale(langs.get(k));
                              DateFormatSymbols sym=new DateFormatSymbols(locale);
                              String[] months=sym.getMonths();
                              int numMonths=months.length;
                              for(int m=0;m<numMonths;m++){
                                String thisMonth=months[m];
                                if (remarks.indexOf(thisMonth) != -1) {
                                  month=m;
                                  System.out.println("...detected a month in comments!");
                                }
                              }
                            }
                            catch(Exception e){e.printStackTrace();}
                          } //end for
                        */
                        }

                      }
                }

                //end brute force date detection if NLP failed


                  //if we found a date via NLP or brute force, let's use it here
                  if(year>-1){
                    for(int i=0;i<numEncounters;i++){
                      Encounter enctemp=encounters.get(i);
                      enctemp.setYear(year);
                      if(month>-1){
                        enctemp.setMonth(month);
                        if(day>-1){enc.setDay(day);}
                      }
                    }

                  }

            }//end if set date


              }

            catch (Exception props_e) {
              props_e.printStackTrace();
            }
          }

          //if we found a locationID, iterate and set it on every Encounter
          if(locCode!=null){

            for(int i=0;i<numEncounters;i++){
              Encounter enctemp=encounters.get(i);
              enctemp.setLocationID(locCode);
              System.out.println("Setting locationID for detected Encounter to: "+locCode);
              if(!location.equals("")){
                enctemp.setLocation(location.trim());
                System.out.println("Setting location for detected Encounter to: "+location);
                }
            }
          }


          //set the Wildbook A.I. user if it exists
          if(myShepherd.getUser("wildbookai")!=null){
            for(int i=0;i<numEncounters;i++){
              Encounter enctemp=encounters.get(i);
              enctemp.setSubmitterID("wildbookai");
            }
          }

          //if date and/or location not found, ask youtube poster through comment section.
          //          cred= ShepherdProperties.getProperties("youtubeCredentials.properties", "");
          try{
            //YouTube.init(request);
            Properties quest = new Properties();
            //Properties questEs = new Properties();

            //TBD-simplify to one set of files
            quest= ShepherdProperties.getProperties("quest.properties", detectedLanguage);
            //questEs= ShepherdProperties.getProperties("questEs.properties");

            String questionToPost=null;

            if((enc.getDateInMilliseconds()==null)&&(locCode==null)){
              questionToPost= quest.getProperty("whenWhere");

            }
            else if(enc.getDateInMilliseconds()==null){
              questionToPost= quest.getProperty("when");

            }
            else if(locCode==null){
              questionToPost= quest.getProperty("where");
            }

            if(questionToPost!=null){
            String videoId = enc.getEventID().replaceAll("youtube:","");
              //String videoId = "JhIcP4K-M6c"; //using Jason's yt account for testing, instead of calling enc.getEventID() to get real videoId
              try{
                YouTube.postQuestion(questionToPost,videoId, occ);
              }
              catch(Exception e){e.printStackTrace();}
            }

        }
         catch(Exception yet){
           System.out.println("Caught exception trying to post a YouTube question.");
           yet.printStackTrace();
         }



        }
        //end set date/location/locationID on Encounters

    }


    //// TOTAL HACK... buy jon a drink and he will tell you about these.....
    public static JSONObject hashMapToJSONObject(HashMap<String,ArrayList> map) {
        if (map == null) return null;
        //return new JSONObject(map);  // this *used to work*, i swear!!!
        JSONObject rtn = new JSONObject();
        for (String k : map.keySet()) {
            rtn.put(k, map.get(k));
        }
        return rtn;
    }
    public static JSONObject hashMapToJSONObject2(HashMap<String,Object> map) {   //note: Object-flavoured
        if (map == null) return null;
        //return new JSONObject(map);  // this *used to work*, i swear!!!
        JSONObject rtn = new JSONObject();
        for (String k : map.keySet()) {
            rtn.put(k, map.get(k));
        }
        return rtn;
    }


}
