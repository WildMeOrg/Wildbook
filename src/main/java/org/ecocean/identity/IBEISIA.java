package org.ecocean.identity;

import org.ecocean.Annotation;
import org.ecocean.AnnotationLite;
import org.ecocean.CommonConfiguration;
import org.ecocean.ContextConfiguration;
import org.ecocean.Encounter;
import org.ecocean.ia.*;
import org.ecocean.ia.plugin.*;
import org.ecocean.IAJsonProperties;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;

import org.ecocean.LinkedProperties;
import org.ecocean.LocationID;
import org.ecocean.media.YouTubeAssetStore;

import org.ecocean.MarkedIndividual;
import org.ecocean.Occurrence;
import org.ecocean.servlet.importer.ImportTask;
import org.ecocean.servlet.RestKeyword;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.shepherd.core.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.Util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.jdo.Query;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URL;
import org.ecocean.media.*;
import org.ecocean.RestClient;

import java.io.IOException;

import javax.servlet.ServletException;

import java.io.File;
import java.net.MalformedURLException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import javax.servlet.http.HttpServletRequest;

import java.util.concurrent.atomic.AtomicBoolean;

// date time


public class IBEISIA {
    // move this ish to its own class asap!
    private static final Map<String, String[]> speciesMap;
    static {
        speciesMap = new HashMap<String, String[]>();
        speciesMap.put("zebra_plains", new String[] { "Equus", "quagga" });
        speciesMap.put("zebra_grevys", new String[] { "Equus", "grevyi" });
        speciesMap.put("whale shark", new String[] { "Rhincodon", "typus" });
    }

    public static String STATUS_PENDING = "pending"; // pending review (needs action by user)
    public static String STATUS_COMPLETE = "complete"; // process is done
    public static String STATUS_PROCESSING = "processing"; // off at IA, awaiting results
    public static String STATUS_INITIATED = "initiated"; // initiated on our side but may or may not be processing on IA side
    public static String STATUS_ERROR = "error";
    public static final String IA_UNKNOWN_NAME = "____";

    private static long TIMEOUT_DETECTION = 20 * 60 * 1000; // in milliseconds
    private static String SERVICE_NAME = "IBEISIA";

    private static AtomicBoolean iaPrimed = new AtomicBoolean(false);
    private static HashMap<Integer, Boolean> alreadySentMA = new HashMap<Integer, Boolean>();
    private static HashMap<String, Boolean> alreadySentAnn = new HashMap<String, Boolean>();
    private static HashMap<String, String> identificationUserActiveTaskId = new HashMap<String,
        String>();

    // cache-like, in order to speed up IA;  make this some kind of smarter class
    private static HashMap<String, String> cacheAnnotIndiv = new HashMap<String, String>();

    private static String iaBaseURL = null; // gets set the first time it is needed by iaURL()

    // a convenience way to send MediaAssets with no (i.e. with only the "trivial") Annotation
    public static JSONObject __sendMediaAssets(ArrayList<MediaAsset> mas, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        return __sendMediaAssets(mas, null, context);
    }

    // other is a HashMap of additional properties to build lists out of (e.g. Encounter ids and so on), that do not live in/on MediaAsset
    public static JSONObject __sendMediaAssets(ArrayList<MediaAsset> mas,
        HashMap<MediaAsset, HashMap<String, Object> > other, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (!isIAPrimed())
            System.out.println("WARNING: sendMediaAssets() called without IA primed");
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int ct = 0;

        // see: https://erotemic.github.io/ibeis/ibeis.web.html?highlight=add_images_json#ibeis.web.app.add_images_json
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
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
                w = (int)iatt.getWidth();
                h = (int)iatt.getHeight();
            }
            // we are *required* to have a width/height to pass to IA, so lets skip...
            if ((w < 1) || (h < 1)) {
                System.out.println("WARNING: IBEISIA.sendMediaAssets() skipping " + ma.toString() +
                    " - unable to find width/height");
                continue;
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
                map.get("image_time_posix_list").add((int)Math.floor(t.getMillis() / 1000)); // IBIES-IA wants seconds since epoch
            }
            markSent(ma);
            ct++;
        }
        System.out.println("sendMediaAssets(): sending " + ct);
        if (ct < 1) return null; // null for "none to send" ?  is this cool?
        return RestClient.post(url, hashMapToJSONObject(map));
    }

    public static JSONObject __sendAnnotations(ArrayList<Annotation> anns, String context,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (!isIAPrimed())
            System.out.println("WARNING: sendAnnotations() called without IA primed");
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);
        int ct = 0;
        HashMap<String, ArrayList> map = new HashMap<String, ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        for (Annotation ann : anns) {
            if (!needToSend(ann)) continue;
            if (!validForIdentification(ann, context)) {
                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
                continue;
            }
            // Try and get an iaClass from the  annotation. If detection ran correctly.. it should be there.
            // I guess fall back on the species from ann if you don't find anything? Maybe you shouldn't... because detect shouldn't have anything to
            // do
            // with the human friendly "species", just ia class. Oh well, doing it anyway for now.. FIGHT ME ABOUT IT
            String iaClass = null;
            if (Util.stringExists(ann.getIAClass())) {
                iaClass = ann.getIAClass();
                System.out.println("iaClass set from Annotation.");
            } else {
                System.out.println(
                    "===> CRITICAL ERROR: Annotation did not have a useable class candidate to send to identification for iaClass. ");
                continue;
            }
            int[] bbox = ann.getBbox();
            map.get("annot_bbox_list").add(bbox);
            map.get("image_uuid_list").add(toFancyUUID(ann.getMediaAsset().getUUID()));
            map.get("annot_uuid_list").add(toFancyUUID(ann.getUUID()));
            map.get("annot_species_list").add(iaClass);
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            markSent(ann);
            ct++;
        }
        System.out.println("sendAnnotations(): sending " + ct);
        if (ct < 1) return null;
        // this should only be checking for missing images, i guess?
        boolean tryAgain = true;
        JSONObject res = null;
        while (tryAgain) {
            res = RestClient.post(url, hashMapToJSONObject(map));
            tryAgain = iaCheckMissing(res, context, myShepherd);
        }
        return res;
    }

    // note: if tanns here is null, then it is exemplar for this species
    public static JSONObject sendIdentify(ArrayList<Annotation> qanns, ArrayList<Annotation> tanns,
        JSONObject queryConfigDict, JSONObject userConfidence, String baseUrl, String context,
        String taskId, boolean fastlane)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendIdentify() called without IA primed");
        String u = IA.getProperty(context, "IBEISIARestUrlStartIdentifyAnnotations");
        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlStartIdentifyAnnotations is not set");
        URL url = new URL(u);
        long startTime = System.currentTimeMillis();
        Util.mark("sendIdentify-0  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
            startTime);

        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.sendIdentify");
        myShepherd.beginDBTransaction();

        HashMap<String, Object> map = new HashMap<String, Object>();
        map.put("callback_url", callbackUrl(baseUrl));
        map.put("jobid", taskId);
        if (queryConfigDict != null) map.put("query_config_dict", queryConfigDict);
        // OK, check here and dont let HotSpotter in
        boolean isHotspotter = false;
        if (queryConfigDict != null && queryConfigDict.toString().indexOf("sv_on") > -1)
            isHotspotter = true;
        if (fastlane && !isHotspotter) map.put("lane", "fast");
        map.put("matching_state_list",
            IBEISIAIdentificationMatchingState.allAsJSONArray(myShepherd));                             // this is "universal"
        if (userConfidence != null) map.put("user_confidence", userConfidence);
        ArrayList<JSONObject> qlist = new ArrayList<JSONObject>();
        ArrayList<JSONObject> tlist = new ArrayList<JSONObject>();
        ArrayList<String> qnlist = new ArrayList<String>();
        ArrayList<String> tnlist = new ArrayList<String>();

        ///note: for names here, we make the gigantic assumption that they individualID has been migrated to uuid already!
        String iaClass = null;
        Util.mark("sendIdentify-1", startTime);
        for (Annotation ann : qanns) {
            if (!validForIdentification(ann, context)) {
                System.out.println("WARNING: IBEISIA.sendIdentify() [qanns] skipping invalid " +
                    ann);
                continue;
            }
            // Should we fall back on gleaning species from the Enc? We do it to find the iaClass initially.. Redundant? Squishy? Discuss.
            if (iaClass == null) {
                if (ann.getIAClass() != null) {
                    iaClass = ann.getIAClass();
                } else {
                    iaClass = org.ecocean.ia.plugin.WildbookIAM.getIASpecies(ann, myShepherd);
                }
            }
            qlist.add(toFancyUUID(ann.getAcmId()));
            /* jonc now fixed it so we can have null/unknown ids... but apparently this needs to be "____" (4 underscores) ; also names are now just
               strings (not uuids)
               // i guess (???) we need some kinda ID for query annotations (even tho we dont know who they are); so wing it?
               qnlist.add(toFancyUUID(Util.generateUUID()));
             */

            qnlist.add(IA_UNKNOWN_NAME);
        }
        Util.mark("sendIdentify-2", startTime);
        // Do we have a qaan? We need one, or load a failure response.
        if (qlist.isEmpty()) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            JSONObject noQueryAnn = new JSONObject();
            noQueryAnn.put("status", new JSONObject().put("message", "rejected"));
            noQueryAnn.put("error", "No query annotation was valid for identification. ");
            return noQueryAnn;
        }
        Util.mark("sendIdentify-A", startTime);
        boolean setExemplarCaches = false;
        if (tanns == null) {
            System.out.println("--- sendIdentify() passed null tanns..... why???");
            System.out.println("     gotta compute :(");
            tanns = qanns.get(0).getMatchingSet(myShepherd);
        }
        Util.mark("sendIdentify-B  tanns.size()=" + ((tanns == null) ? "null" : tanns.size()),
            startTime);
        // int ct = 0;
        if (tanns != null)
            for (Annotation ann : tanns) {
                // Util.mark(ct + "]  sib-1 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
                if (!validForIdentification(ann, context)) {
                    System.out.println("WARNING: IBEISIA.sendIdentify() [tanns] skipping invalid " +
                        ann);
                    continue;
                }
                // Util.mark("      sib-2 ann=" + ann.getId() + "/" + ann.getAcmId(), startTime);
                // ct++;
                tlist.add(toFancyUUID(ann.getAcmId()));
                String indivId = annotGetIndiv(ann, myShepherd);
                // argh we need to standardize this and/or have a method. :/
                if ((indivId == null) || (indivId.toLowerCase().equals("unassigned"))) {
                    tnlist.add(IA_UNKNOWN_NAME);
                } else {
                    tnlist.add(indivId);
                }
            }
        // query_config_dict={'pipeline_root' : 'BC_DTW'}

        Util.mark("sendIdentify-C", startTime);
        // bail on empty target annots:
        if (Util.collectionIsEmptyOrNull(tlist)) {
            System.out.println("WARNING: bailing on empty target list");
            JSONObject emptyRtn = new JSONObject();
            JSONObject status = new JSONObject();
            status.put("message", "rejected");
            status.put("error", "Empty target annotation list");
            status.put("emptyTargetAnnotations", true);
            emptyRtn.put("status", status);

            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

            return emptyRtn;
        }
        map.put("query_annot_uuid_list", qlist);
        map.put("database_annot_uuid_list", tlist);
        // We need to send IA null in this case. If you send it an empty list of annotation names or uuids it will check against nothing..
        // If the list is null it will check against everything.
        map.put("query_annot_name_list", qnlist);
        // if we have no target lists, pass null for "all"
        if (Util.collectionIsEmptyOrNull(tlist)) {
            map.put("database_annot_uuid_list", null);
        } else {
            map.put("database_annot_uuid_list", tlist);
        }
        if (Util.collectionIsEmptyOrNull(tnlist)) {
            map.put("database_annot_name_list", null);
        } else {
            map.put("database_annot_name_list", tnlist);
        }
        Util.mark("sendIdentify-D", startTime);

        System.out.println(
            "===================================== qlist & tlist ========================= [taskId="
            + taskId + "]");
        System.out.println(qlist + " callback=" + callbackUrl(baseUrl));
        if (Util.collectionIsEmptyOrNull(tlist) || Util.collectionIsEmptyOrNull(tnlist)) {
            System.out.println("tlist/tnlist == null! Checking against all.");
        } else {
            System.out.println("tlist.size()=" + tlist.size() + " annnnd tnlist.size()=" +
                tnlist.size());
        }
        System.out.println("qlist.size()=" + qlist.size() + " annnnd qnlist.size()=" +
            qnlist.size() + ". not printing the map about to be POSTed because it's a big'un.");
        // System.out.println(map);
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        Util.mark("identify process pre-post end");
        return RestClient.post(url, hashMapToJSONObject2(map));
    }

    // this version of sendDetect only works for the first detection algo for a given taxonomy. The more robust version below is used in our ia.json pipeline
    public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl, String context,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        IAJsonProperties iaConfig = new IAJsonProperties();
        JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl);
        String detectUrl = iaConfig.getDetectionUrl(taxy);

        return sendDetect(mas, baseUrl, context, myShepherd, detectArgs, detectUrl);
    }

    // assumes only one detection alg and replicates sendDetect
    public static JSONObject sendDetect(ArrayList<MediaAsset> mas, String baseUrl, String context,
        Shepherd myShepherd, JSONObject detectArgs, String detectUrl)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (!isIAPrimed()) System.out.println("WARNING: sendDetect() called without IA primed");
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        IAJsonProperties iaConfig = new IAJsonProperties();
        JSONObject detectArgsWithMas = Util.copy(detectArgs);
        detectArgsWithMas.put("image_uuid_list", imageUUIDList(mas));
        System.out.println("sendDetect got detectArgs " + detectArgsWithMas.toString());

        URL url = new URL(detectUrl);
        System.out.println("sendDetectNew sending to url " + url);

        return RestClient.post(url, detectArgsWithMas);
    }

    public static JSONArray imageUUIDList(List<MediaAsset> mas) {
        JSONArray uuidList = new JSONArray();

        for (MediaAsset ma : mas) {
        	if(ma.getAcmId()!=null)uuidList.put(toFancyUUID(ma.getAcmId()));
        }
        return uuidList;
    }

    public static Map<String,
        Object> addImageUuidListToDetectArgs(Map<String, Object> detectArgsMap,
        List<MediaAsset> mas) {
        List<JSONObject> malist = new ArrayList<JSONObject>();

        for (MediaAsset ma : mas) {
            if (ma == null) continue;
            if (ma.getAcmId() == null) { // usually this means it was not able to be added to IA (e.g. a video etc)
                System.out.println("WARNING: sendDetect() skipping " + ma +
                    " due to missing acmId");
                ma.setDetectionStatus(STATUS_ERROR); // is this wise?
                continue;
            }
            malist.add(toFancyUUID(ma.getAcmId()));
        }
        detectArgsMap.put("image_uuid_list", malist);
        return detectArgsMap;
    }

    private static String getDetectUrlByModelTag(String context, String modelTag) {
        if (modelTag == null) return IA.getProperty(context, "IBEISIARestUrlStartDetectImages");
        String u = IA.getProperty(context, "IBEISIARestUrlStartDetectImages." + modelTag);
        if (u != null) return u;
        return IA.getProperty(context, "IBEISIARestUrlStartDetectImages");
    }

    private static String getDetectUrlByTaxonomy(Taxonomy taxy, String context) {
        String modelTag = getModelTag(context, taxy);
        String detectUrl = getDetectUrlByModelTag(context, modelTag);

        return modelTag;
    }

    public static String getViewpointTag(String context, Taxonomy tax) {
        if (tax == null && IA.getProperty(context, "viewpointModelTag") == null) return null; // got nothin
        if ((tax == null) || (tax.getScientificName() == null))
            return IA.getProperty(context, "viewpointModelTag").trim(); // best we can hope for
        String propKey = "viewpointModelTag_".concat(tax.getScientificName()).replaceAll(" ", "_");
        System.out.println("[INFO] getViewpointTag() using propKey=" + propKey + " based on " +
            tax);
        String vp = IA.getProperty(context, propKey);
        if (vp == null) vp = IA.getProperty(context, "viewpointModelTag"); // too bad, fallback!
        if (vp != null) { vp = vp.trim(); }
        return vp;
    }

    public static String getLabelerAlgo(String context, Taxonomy tax) {
        if (tax == null && IA.getProperty(context, "labelerAlgo") == null) return null; // got nothin
        if ((tax == null) || (tax.getScientificName() == null))
            return IA.getProperty(context, "labelerAlgo").trim();
        String propKey = "labelerAlgo_".concat(tax.getScientificName()).replaceAll(" ", "_");
        System.out.println("[INFO] getLabelerAlgo() using propKey=" + propKey + " based on " + tax);
        String vp = IA.getProperty(context, propKey);
        if (vp == null) vp = IA.getProperty(context, "labelerAlgo");
        if (vp != null) { vp = vp.trim(); }
        return vp;
    }

    public static String inferIaClass(Annotation ann, Shepherd myShepherd) {
        Taxonomy tax = ann.getTaxonomy(myShepherd);

        System.out.println("inferIaClass got taxonomy " + tax);
        String ans = taxonomyToIAClass(myShepherd.getContext(), tax);
        System.out.println("taxonomyToIAClass mapped that to " + ans);
        return ans;
    }

    public static String taxonomyToIAClass(String context, Taxonomy tax) {
        if (tax == null) return null;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.taxonomyToIAClass");
        myShepherd.beginDBTransaction();
        HashMap<String, Taxonomy> tmap = iaTaxonomyMap(myShepherd);
        myShepherd.rollbackDBTransaction();
        for (String iaClass : tmap.keySet()) {
            if (tax.equals(tmap.get(iaClass))) return iaClass;
        }
        return null;
    }

    public static Taxonomy taxonomyFromMediaAsset(Shepherd myShepherd, MediaAsset ma) {
        if (ma == null) return null;
        ArrayList<Annotation> anns = ma.getAnnotations();
        if (anns.size() < 1) return null;
        // here we step thru all annots on this asset but likely there will be only one (trivial)
        // if there are more then may the gods help us on what we really will get!
        for (Annotation ann : anns) {
            Taxonomy tax = ann.getTaxonomy(myShepherd);
            if (tax != null) {
                return tax;
            }
        }
        return null;
    }

/*
    note: i originally was going to base modelTag_FOO on iaClass, but this seems problematic for allowing one model for multiple species (e.g. "all
       dolphins"), in that taxonomyToIAClass is not going to work, as iaTaxonomyMap means more than one species cannot use the same iaClass.
    sooooo for now i am going to just let modelTag_FOO be of the form modelTag_Scientific_name    -jon
 */
    public static String getModelTag(String context) {
        return getModelTag(context, null);
    }

    public static String getModelTag(String context, Taxonomy tax) {
        if ((tax == null) || (tax.getScientificName() == null))
            return IA.getProperty(context, "modelTag"); // best we can hope for
        String propKey = "modelTag_".concat(tax.getScientificName()).replaceAll(" ", "_");
        System.out.println("[INFO] getModelTag() using propKey=" + propKey + " based on " + tax);
        String mt = IA.getProperty(context, propKey);
        if (mt == null) mt = IA.getProperty(context, "modelTag"); // too bad, fallback!
        return mt;
    }

    public static String getViewpointTag(String context) {
        return getViewpointTag(context, null);
    }

/*
    TODO: As THIS IS NOW UNUSED BY ABOVE (see note above), evaluate full deprecation and removal
    note: this is "for internal use only" -- i.e. this is used for getModelTag above, so re-use with caution?
     (that is, it is meant to generate a string to derive a property key in IA.properties and not much else)

    this uses taxonomyMap, which (via IA.properties) maps detectionClassN -> taxonomyScientificName0
 */
    public static String taxonomyStringToIAClass(String taxonomyString, Shepherd myShepherd) {
        Taxonomy tax = myShepherd.getOrCreateTaxonomy(taxonomyString, false);

        return taxonomyToIAClass(myShepherd.getContext(), tax);
    }

    public static Taxonomy taxonomyFromMediaAssets(String context, List<MediaAsset> mas) {
        if (Util.collectionIsEmptyOrNull(mas)) return null;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.taxonomyFromMediaAssets");
        myShepherd.beginDBTransaction();
        for (MediaAsset ma : mas) {
            Taxonomy tax = taxonomyFromMediaAsset(myShepherd, ma);
            if (tax != null) {
                myShepherd.rollbackAndClose();
                return tax;
            }
        }
        myShepherd.rollbackAndClose();
        return null;
    }

    public static Taxonomy taxonomyFromMediaAssets(String context, List<MediaAsset> mas,
        Shepherd myShepherd) {
        if (Util.collectionIsEmptyOrNull(mas)) return null;
        for (MediaAsset ma : mas) {
            Taxonomy tax = taxonomyFromMediaAsset(myShepherd, ma);
            if (tax != null) {
                return tax;
            }
        }
        return null;
    }

    public static JSONObject getJobStatus(String jobID, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlGetJobStatus");

        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlGetJobStatus is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    // note: this passes directly to IA so can be problematic! (ia down? and more importantly: ia restarted so job # is diff and old job is gone!)
    // better(?) to use getJobResultLogged() below!
    public static JSONObject getJobResult(String jobID, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlGetJobResult");

        if (u == null)
            throw new MalformedURLException(
                      "configuration value IBEISIARestUrlGetJobResult is not set");
        URL url = new URL(u + "?jobid=" + jobID);
        return RestClient.get(url);
    }

    public static JSONObject getJobResultLogged(String jobID, String context) {
        String taskId = findTaskIDFromJobID(jobID, context);

        if (taskId == null) {
            System.out.println("getJobResultLogged(" + jobID +
                ") could not find taskId for this job");
            return null;
        }
        System.out.println("getJobResultLogged(" + jobID + ") -> taskId " + taskId);
        // note: this is a little(!) in that it relies on the "raw" results living in "_debug" from getTaskResults so we can reconstruct it to be the
        // output
        // that getJobResult() above gives.  :/
        JSONObject tr = getTaskResults(taskId, context);
        if ((tr == null) || (tr.optJSONObject("_debug") == null) ||
            (tr.getJSONObject("_debug").optJSONObject("_response") == null)) return null;
        if (tr.optJSONArray("_objectIds") != null) // if we have this, lets bubble it up as part of this return
            tr.getJSONObject("_debug").getJSONObject("_response").put("_objectIds",
                tr.getJSONArray("_objectIds"));
        return tr.getJSONObject("_debug").getJSONObject("_response");
    }

    // this is "new" identification results
    public static JSONObject getTaskResults(String taskID, String context) {
        JSONObject rtn = getTaskResultsBasic(taskID, context);

        if ((rtn == null) || !rtn.optBoolean("success", false)) return rtn; // all the ways we can fail
        JSONObject res = rtn.optJSONObject("_json_result");
        rtn.put("results", res);
        rtn.remove("_json_result");
        return rtn;
    }

    public static JSONObject getTaskResultsDetect(String taskID, String context) {
        JSONObject rtn = getTaskResultsBasic(taskID, context);

        if ((rtn == null) || !rtn.optBoolean("success", false)) return rtn; // all the ways we can fail
        JSONArray resOut = new JSONArray();

        return rtn;
    }

    public static JSONObject getTaskResultsBasic(String taskID, String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IBEISIA.getTaskResultsBasic");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME,
            myShepherd);
        JSONObject returnMe = getTaskResultsBasic(taskID, logs);
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        return returnMe;
    }

    // note: log must be in chrono order by timestamp ASC
    public static JSONObject getTaskResultsBasic(String taskID,
        ArrayList<IdentityServiceLog> logs) {
        JSONObject rtn = new JSONObject();

        ///System.out.println("getTaskResultsBasic logs -->\n" + logs);
        if ((logs == null) || (logs.size() < 1)) {
            rtn.put("success", false);
            rtn.put("error", "could not find any log of task ID = " + taskID);
            return rtn;
        }
        // since "we can", lets also get the object ids here....
        String[] objIds = IdentityServiceLog.findObjectIDs(logs);
        // System.out.println("objIds -> " + objIds);

        // we have to walk through (newest to oldest) and find the (first) one with _action == 'getJobResult' ... but we should also stop on
        // getJobStatus
        // if we see that first -- it means we sent a job and are awaiting results.
        JSONObject last = logs.get(logs.size() - 1).getStatusJson(); // just start with something (most recent at all)
        for (int i = logs.size() - 1; i >= 0; i--) {
            JSONObject j = logs.get(i).getStatusJson();
            if (j.optString("_action").equals("getJobResult") ||
                j.optString("_action").equals("getJobStatus")) {
                last = j;
                break;
            }
        }
        // note: jobstatus == completed seems to be the thing we want
        if ("getJobStatus".equals(last.optString("_action")) &&
            "unknown".equals(last.getJSONObject("_response").getJSONObject("response").getString(
            "jobstatus"))) {
            rtn.put("success", false);
            rtn.put("details", last.get("_response"));
            rtn.put("error",
                "final log for task " + taskID +
                " was an unknown jobstatus, so results were not obtained");
            return rtn;
        }
        System.out.println("-------------\n" + last.toString() + "\n----------");
        if (last.getString("_action").equals("getJobResult")) {
            if (last.getJSONObject("_response").getJSONObject("status").getBoolean("success") &&
                "ok".equals(last.getJSONObject("_response").getJSONObject("response").getString(
                "status"))) {
                rtn.put("success", true);
                rtn.put("_debug", last);
                rtn.put("_json_result",
                    last.getJSONObject("_response").getJSONObject("response").opt("json_result"));  // "should never" fail. HA!
                if (rtn.get("_json_result") == null) {
                    rtn.put("success", false);
                    rtn.put("error", "json_result seems empty");
                }
            } else {
                rtn.put("error",
                    "getJobResult for task " + taskID +
                    " logged as either non successful or with a status not OK");
                rtn.put("details", last.get("_response"));
                rtn.put("success", false);
            }
            // System.out.println("objIds ??? " + objIds);
            if ((objIds != null) && (objIds.length > 0))
                rtn.put("_objectIds", new JSONArray(Arrays.asList(objIds)));
            return rtn;
        }
        // we could also do a comparison with when it was started to enable a failure due to timeout
        return null; // if we fall through, it means we are still waiting ......
    }

    public static HashMap<String, Object> getTaskResultsAsHashMap(String taskID, String context) {
        JSONObject jres = getTaskResults(taskID, context);
        HashMap<String, Object> res = new HashMap<String, Object>();

        if (jres == null) {
            System.out.println(
                "WARNING: getTaskResultsAsHashMap() had null results from getTaskResults(" +
                taskID + "); return empty HashMap");
            return res;
        }
        res.put("taskID", taskID);
        if (jres.has("success")) res.put("success", jres.get("success"));
        if (jres.has("error")) res.put("error", jres.get("error"));
        if (jres.has("results")) {
            HashMap<String, Object> rout = new HashMap<String, Object>();
            JSONArray r = jres.getJSONArray("results");
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("IBEISIA.getTaskResultsAsHashMap");
            myShepherd.beginDBTransaction();
            for (int i = 0; i < r.length(); i++) {
                if (r.getJSONObject(i).has("query_annot_uuid")) {
                    HashMap<String, Double> scores = new HashMap<String, Double>();
                    JSONArray m = r.getJSONObject(i).getJSONArray("match_annot_list");
                    JSONArray s = r.getJSONObject(i).getJSONArray("score_list");
                    for (int j = 0; j < m.length(); j++) {
                        Encounter menc = Encounter.findByAnnotationId(m.getString(j), myShepherd);
                        scores.put(menc.getCatalogNumber(), s.getDouble(j));
                    }
                    Encounter enc = Encounter.findByAnnotationId(r.getJSONObject(i).getString(
                        "query_annot_uuid"), myShepherd);
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

        // System.out.println(" . . . . . . . . . . . . waitingOnTask(" + taskID + ") -> " + res);
        if (res == null) return true;
        return false; // anything else means we are done (good or bad)
    }

    // should return true if we attempted to add missing and caller should try again
    // TODO: Evaluate, deprecate fully and remove: HOPEFULLY THE NEED FOR THIS IS DEPRECATED NOW?
    public static boolean iaCheckMissing(JSONObject res, String context, Shepherd myShepherd) {
        // System.out.println("########## iaCheckMissing res -> " + res);
        try {
            if (!((res != null) && (res.getJSONObject("status") != null) &&
                (res.getJSONObject("status").getInt("code") == 600))) return false;     // note a needy 600
        } catch (JSONException je) {} // You don't always get an error code.. Especially if the anns got swatted before sending
        boolean tryAgain = false;
        // TODO: handle loop where we keep trying to add same objects but keep failing (e.g. store count of attempts internally in class?)
        if ((res.getJSONObject("response") != null) &&
            res.getJSONObject("response").has("missing_image_uuid_list")) {
            JSONArray list = res.getJSONObject("response").getJSONArray("missing_image_uuid_list");
            if (list.length() > 0) {
                for (int i = 0; i < list.length(); i++) {
                    String uuid = fromFancyUUID(list.getJSONObject(i));
                    System.out.println("**** FAKE ATTEMPT to sendMediaAssets: uuid=" + uuid);
                    // TODO: actually send the mediaasset duh
                }
            }
        }
        if ((res.getJSONObject("response") != null) &&
            res.getJSONObject("response").has("missing_annot_uuid_list")) {
            JSONArray list = res.getJSONObject("response").getJSONArray("missing_annot_uuid_list");
            if (list.length() > 0) {
                ArrayList<Annotation> anns = new ArrayList<Annotation>();
                try {
                    for (int i = 0; i < list.length(); i++) {
                        String acmId = fromFancyUUID(list.getJSONObject(i));
                        ArrayList<Annotation> annsTemp = myShepherd.getAnnotationsWithACMId(acmId);
                        anns.add(annsTemp.get(0));
                    }
                } catch (Exception e) { e.printStackTrace(); }
                // would this ever recurse? seems like a 600 would only happen inside sendAnnotations for missing MediaAssets.
                System.out.println("**** attempting to make up for missing Annotation(s): " +
                    anns.toString());
                JSONObject srtn = null;
                try {
                    __sendAnnotations(anns, context, myShepherd);
                } catch (Exception ex) {}
                System.out.println(" returned --> " + srtn);
                if ((srtn != null) && (srtn.getJSONObject("status") != null) &&
                    srtn.getJSONObject("status").getBoolean("success")) tryAgain = true;    // it "worked"?
            }
        }
        System.out.println("iaCheckMissing -> " + tryAgain);
        return tryAgain;
    }

    private static Object mediaAssetToUri(MediaAsset ma) {
        // System.out.println("=================== mediaAssetToUri " + ma + "\n" + ma.getParameters() + ")\n");
        URL curl = ma.containerURLIfPresent();

        if (curl == null) curl = ma.webURL();
        if (ma.getStore() instanceof LocalAssetStore) {
            // return ma.localPath().toString(); //nah, lets skip local and go for "url" flavor?
            if (curl == null) return null;
            return curl.toString();
        } else {
            if (curl == null) return null;
            return curl.toString();
        }
    }

    // If you realllllly want to send species I'll just swallow it.
    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
        Shepherd myShepherd, String species, Task task, String baseUrl, boolean fastlane) {
        System.out.println(
            "INFO: You no longer need to send species with call to beginIdentifyAnnotations. It is derived from the Annotation's Encounters.");
        return beginIdentifyAnnotations(qanns, tanns, queryConfigDict, userConfidence, myShepherd,
                task, baseUrl, fastlane);
    }

    // trying to optimize the original beginIdentifyAnnotations()  [above]
    public static JSONObject beginIdentifyAnnotations(ArrayList<Annotation> qanns,
        ArrayList<Annotation> tanns, JSONObject queryConfigDict, JSONObject userConfidence,
        Shepherd myShepherd, Task task, String baseUrl, boolean fastlane) {
        long tt = System.currentTimeMillis();

        if (!isIAPrimed())
            System.out.println("WARNING: beginIdentifyAnnotations() called without IA primed");
        // possibly could exclude qencs from tencs?
        String jobID = "-1";
        JSONObject results = new JSONObject();
        results.put("success", false); // pessimism!
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();
        String taskID = "_UNKNOWN_";
        if (task != null) taskID = task.getId(); // "should never happen"
        log(taskID, jobID, new JSONObject("{\"_action\": \"initIdentify\"}"),
            myShepherd.getContext());
        String curvrankDailyTag = null;

        Util.mark("OPTIMIZED identify process start", tt);
        try {
            for (Annotation ann : qanns) {
                if (validForIdentification(ann, myShepherd.getContext())) {
                    allAnns.add(ann);
                }
            }
            // this voodoo via JH will insure that .acmId is on the MediaAssets which are loaded via getMatchingSet() below (for speed)
            javax.jdo.FetchGroup grp =
                myShepherd.getPM().getPersistenceManagerFactory().getFetchGroup(MediaAsset.class,
                "BIA");
            grp.addMember("acmId").addMember("store").addMember("id").addMember(
                "parametersAsString").addMember("parameters").addMember("metadata").addMember(
                "labels").addMember("userLatitude").addMember("userLongitude").addMember(
                "userDateTime").addMember("features");
            myShepherd.getPM().getFetchPlan().addGroup("BIA");

            Util.mark("OPT bia 2", tt);
            if (tanns == null || tanns.isEmpty()) {
                String iaClass = qanns.get(0).getIAClass();
                System.out.println(
                    "beginIdentifyAnnotations(): have to set tanns. Matching set being built from the first ann in the list.");
                tanns = qanns.get(0).getMatchingSet(myShepherd,
                    (task == null) ? null : task.getParameters());
                curvrankDailyTag = qanns.get(0).getCurvrankDailyTag((task ==
                    null) ? null : task.getParameters());
            }
            Util.mark("OPT bia 3", tt);

            System.out.println("- mark 2");
            if (tanns != null && !tanns.isEmpty()) {
                System.out.println("INFO: tanns, (matchingSet) is not null. Contains " +
                    tanns.size() + " annotations.");
                for (Annotation ann : tanns) {
                    allAnns.add(ann);
                }
            }
            Util.mark("OPT bia 4", tt);

            results.put("sendAnnotationsAsNeeded", sendAnnotationsAsNeeded(allAnns, myShepherd));
            Util.mark("OPT bia 4X", tt);
            if (tanns != null) {
                System.out.println("                               ... qanns has: " + qanns.size() +
                    " ... taans has: " + tanns.size());
            } else {
                System.out.println("                               ... qanns has: " + qanns.size() +
                    " ... taans is null! Target is all annotations.");
            }
            if (curvrankDailyTag != null) {
                if (queryConfigDict == null) queryConfigDict = new JSONObject();
                // from JP on 12/27/2019 - if we want to specify an unfiltered list, just omit the tag
                if (!curvrankDailyTag.toLowerCase().equals("user:any") &&
                    !curvrankDailyTag.toLowerCase().equals("user:any;locs:")) {
                    queryConfigDict.put("curvrank_daily_tag", curvrankDailyTag);
                }
            }
            Util.mark("bia 4C", tt);
            // this should attempt to repair missing Annotations
            boolean tryAgain = true;
            JSONObject identRtn = null;
            while (tryAgain) {
                identRtn = sendIdentify(qanns, tanns, queryConfigDict, userConfidence, baseUrl,
                    myShepherd.getContext(), taskID, fastlane);
                System.out.println("identRtn contains ========> " + identRtn);
                if (identRtn == null) {
                    results.put("error", "identRtn == NULL");
                    results.put("success", false);
                    return results;
                } else if (identRtn != null && identRtn.getJSONObject("status") != null &&
                    !identRtn.getJSONObject("status").getString("message").equals("rejected")) {
                    tryAgain = iaCheckMissing(identRtn, myShepherd.getContext(), myShepherd);
                } else {
                    results.put("error", identRtn.get("status"));
                    results.put("success", false);
                    return results;
                }
            }
            results.put("sendIdentify", identRtn);

            System.out.println("sendIdentify ---> " + identRtn);
            if ((identRtn != null) && identRtn.has("status") &&
                identRtn.getJSONObject("status").getBoolean("success")) {
                jobID = identRtn.get("response").toString();
                results.put("success", true);
            } else {
                System.out.println("beginIdentifyAnnotations() unsuccessful on sendIdentify(): " +
                    identRtn);
                results.put("error", identRtn.get("status"));
                results.put("success", false);
            }
        } catch (Exception ex) { // most likely from sendFoo()
            System.out.println("WARN: IBEISIA.beginIdentity() failed due to an exception: " +
                ex.toString());
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

    // a slightly different flavor -- we can explicitely pass the query annotation
    // NOTE!!!  this might be redundant with beginIdentifyAnnotations above. (this came from crc)

    public static IdentityServiceLog log(String taskID, String jobID, JSONObject jlog,
        String context) {
        String[] sa = null;

        return log(taskID, sa, jobID, jlog, context);
    }

    public static IdentityServiceLog log(String taskID, String objectID, String jobID,
        JSONObject jlog, String context) {
        String[] sa = new String[1];

        sa[0] = objectID;
        return log(taskID, sa, jobID, jlog, context);
    }

    public static IdentityServiceLog log(String taskID, String[] objectIDs, String jobID,
        JSONObject jlog, String context) {
        // System.out.println("#LOG: taskID=" + taskID + ", jobID=" + jobID + " --> " + jlog.toString());
        IdentityServiceLog log = new IdentityServiceLog(taskID, objectIDs, SERVICE_NAME, jobID,
            jlog);
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IBEISIA.log");
        myShepherd.beginDBTransaction();
        try {
            log.save(myShepherd);
        } catch (Exception e) { e.printStackTrace(); } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }
        return log;
    }

    // this finds the *most recent* taskID associated with this IBEIS-IA jobID
    public static String findTaskIDFromJobID(String jobID, String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IBEISIA.findTaskIDFromJobID");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByServiceJobID(SERVICE_NAME,
            jobID, myShepherd);
        if (logs == null) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return null;
        }
        for (int i = logs.size() - 1; i >= 0; i--) {
            if (logs.get(i).getTaskID() != null) {
                String id = logs.get(i).getTaskID();
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                return id;
            } // get first one we find. too bad!
        }
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return null;
    }

    public static String[] findTaskIDsFromObjectID(String objectID, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByObjectID(SERVICE_NAME,
            objectID, myShepherd);

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
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IBEISIA.findJobIDFromTaskID");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, SERVICE_NAME,
            myShepherd);
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

    // IBEIS-IA wants a uuid as a single-key json object like: {"__UUID__": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxx"} so we use these to go back and
    // forth
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
        // return true;
        return ((alreadySentMA.get(ma.getId()) == null) || !alreadySentMA.get(ma.getId()));
    }

    private static void markSent(MediaAsset ma) {
        alreadySentMA.put(ma.getIdInt(), true);
    }

    private static boolean needToSend(Annotation ann) {
        // return true;
        return ((alreadySentAnn.get(ann.getId()) == null) || !alreadySentAnn.get(ann.getId()));
    }

    private static void markSent(Annotation ann) {
        alreadySentAnn.put(ann.getId(), true);
    }

/*
    this is the re-tooling of this method which does nothing with encounter(s)
    REMINDER: shouldUpdateSpeciesFromIa() no longer gets called here and thus should be called once all annots are made
 */
    public static Annotation createAnnotationFromIAResult(JSONObject jann, MediaAsset asset,
        Shepherd myShepherd, String context, String rootDir) {
        Annotation ann = convertAnnotation(asset, jann, myShepherd, context, rootDir);

        if (ann == null) return null;
        myShepherd.getPM().makePersistent(ann);
        System.out.println("* createAnnotationFromIAResult() CREATED " + ann +
            " [with no Encounter!]");
        return ann;
    }

    // TODO: evaluate for deprecation and remove: this is the old deprecated version for prosperity or whatever
    public static Annotation createAnnotationFromIAResultDEPRECATED(JSONObject jann,
        MediaAsset asset, Shepherd myShepherd, String context, String rootDir,
        boolean skipEncounter) {
        Annotation ann = convertAnnotation(asset, jann, myShepherd, context, rootDir);

        if (ann == null) return null;
        if (skipEncounter) {
            myShepherd.getPM().makePersistent(ann);
            System.out.println("* createAnnotationFromIAResult() CREATED " + ann +
                " [with no Encounter!]");
            return ann;
        }
        Encounter enc = null;
        try {
            myShepherd.getPM().makePersistent(enc);

            enc.detectedAnnotation(myShepherd, ann); // this is a stub presently, so meh?
            myShepherd.getPM().makePersistent(ann);
            if (ann.getFeatures() != null) {
                for (Feature ft : ann.getFeatures()) {
                    myShepherd.getPM().makePersistent(ft);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("* createAnnotationFromIAResult() CREATED " + ann + " on Encounter " +
            enc.getCatalogNumber());
        // this is to tell IA to update species on the newly-created annot on its side
        String taxonomyString = enc.getTaxonomyString();
        System.out.println("[INFO]: Checking if iaUpdateSpecies should be used for " +
            taxonomyString + "...");
        if (Util.stringExists(taxonomyString) && shouldUpdateSpeciesFromIa(taxonomyString,
            context)) {
            System.out.println("[INFO]: iaUpdateSpecies is active for " + taxonomyString + ".");
            List<String> uuids = new ArrayList<String>();
            List<String> species = new ArrayList<String>();
            uuids.add(ann.getAcmId());
            species.add(taxonomyString);
            try {
                // iaUpdateSpecies(uuids, species, context);
            } catch (Exception ex) {
                System.out.println("ERROR: iaUpdateSpecies() failed! " + ex.toString());
                ex.printStackTrace();
            }
        } else {
            System.out.println("WARNING: cannot update IA, no taxonomy on " + ann);
        }
        return ann;
    }

    // this takes the place of iaUpdateSpecies code above that has been deprecated
    // basically tells IA to alter the species associated with these annots
    public static int updateSpeciesOnIA(Shepherd myShepherd, List<Annotation> anns) {
        if (Util.collectionIsEmptyOrNull(anns)) return 0;
        List<String> uuids = new ArrayList<String>();
        List<String> species = new ArrayList<String>();
        for (Annotation ann : anns) {
            Encounter enc = ann.findEncounter(myShepherd);
            System.out.println("updateSpeciesOnIA(): " + ann + " is on " + enc);
            if ((enc == null) || (ann.getAcmId() == null)) continue;
            String taxonomyString = enc.getTaxonomyString();
            if (!shouldUpdateSpeciesFromIa(taxonomyString, myShepherd.getContext())) continue;
            // switch species to iaClass
            if (ann.getIAClass() == null) continue;
            uuids.add(ann.getAcmId());
            // species.add(taxonomyString);
            species.add(ann.getIAClass().replaceAll("\\+", "%2B"));
        }
        System.out.println("updateSpeciesOnIA(): " + uuids);
        System.out.println("updateSpeciesOnIA(): " + species);
        if (uuids.size() > 0) {
            try {
                iaUpdateSpecies(uuids, species, myShepherd.getContext());
            } catch (Exception ex) {
                System.out.println("ERROR: updateSpeciesOnIA() - iaUpdateSpecies() failed! " +
                    ex.toString());
                ex.printStackTrace();
            }
        }
        return uuids.size();
    }

    public static boolean shouldUpdateSpeciesFromIa(String taxonomyString, String context) {
        if (taxonomyString == null || "".equals(taxonomyString)) return false;
        String taxKey = taxonomyString.replaceAll(" ", "_");
        return Util.booleanNotFalse(IA.getProperty(context, "useIaUpdateSpecies_" + taxKey));
    }

    // here's where we'll attach viewpoint from IA's detection results
    public static Annotation convertAnnotation(MediaAsset ma, JSONObject iaResult,
        Shepherd myShepherd, String context, String rootDir) {
        if (iaResult == null || duplicateDetection(ma, iaResult)) return null;
        String iaClass = iaResult.optString("class", "_FAIL_");
        Taxonomy taxonomyBeforeDetection = ma.getTaxonomy(myShepherd);
        IAJsonProperties iaConf = IAJsonProperties.iaConfig();

        // record whether we do an iaClass swap - part 1
        boolean madeIAClassSwap = false;
        String originalIAClass = iaClass;

        // record whether we do an iaClass swap - part 2
        iaClass = iaConf.convertIAClassForTaxonomy(iaClass, taxonomyBeforeDetection);
        if (iaClass != null && !iaClass.equals(originalIAClass)) {
            madeIAClassSwap = true;
        }
        if (!iaConf.isValidIAClass(taxonomyBeforeDetection, iaClass)) { // null could mean "invalid IA taxonomy"
            System.out.println("WARNING: convertAnnotation found false for isValidIAClass(" +
                taxonomyBeforeDetection + ", " + iaClass +
                "). Continuing anyway to make & save the annotation");
        }
        String viewpoint = iaResult.optString("viewpoint", null);
        if (Util.stringExists(viewpoint)) {
            String kwName = RestKeyword.getKwNameFromIaViewpoint(viewpoint, taxonomyBeforeDetection,
                context);
            if (kwName != null) {
                Keyword kw = myShepherd.getOrCreateKeyword(kwName);
                ma.addKeyword(kw);
                System.out.println("[INFO] convertAnnotation viewpoint got ia viewpoint " +
                    viewpoint + " mapped to kwName " + kwName + " and is adding kw " + kw);
            }
            System.out.println("[WARNING] convertAnnotation viewpoint got ia viewpoint " +
                viewpoint + " but was unable to map to a WB viewpoint using IA.properties");
        } else {
            System.out.println("[INFO] convertAnnotation viewpoint got no viewpoint from IA");
        }
        JSONObject fparams = new JSONObject();
        fparams.put("detectionConfidence", iaResult.optDouble("confidence", -2.0));
        fparams.put("theta", iaResult.optDouble("theta", 0.0));
        if (viewpoint != null) fparams.put("viewpoint", viewpoint);
        Feature ft = ma.generateFeatureFromBbox(iaResult.optDouble("width", 0),
            iaResult.optDouble("height", 0), iaResult.optDouble("xtl", 0),
            iaResult.optDouble("ytl", 0), fparams);
        System.out.println("convertAnnotation() generated ft = " + ft + "; params = " +
            ft.getParameters());
        // get rid of convertSpecies stuff re: Taxonomy!!!!
        Annotation ann = new Annotation(convertSpeciesToString(iaResult.optString("class", null)),
            ft, iaClass);
        ann.setIAExtractedKeywords(myShepherd, taxonomyBeforeDetection);
        ann.setAcmId(fromFancyUUID(iaResult.optJSONObject("uuid")));
        String vp = iaResult.optString("viewpoint", null); // not always supported by IA
        if ("None".equals(vp)) vp = null; // the ol' "None" means null joke!
        ann.setViewpoint(vp);
        if (validForIdentification(ann, context) && iaConf.isValidIAClass(taxonomyBeforeDetection,
            iaClass)) {
            ann.setMatchAgainst(true);
        }
        // record whether we do an iaClass swap, letting WBIA know - part 3
        if (madeIAClassSwap) {
            List<Annotation> annots = new ArrayList<Annotation>();
            annots.add(ann);
            updateSpeciesOnIA(myShepherd, annots);
        }
        return ann;
    }

    private static boolean isDuplicateDetection(MediaAsset ma, JSONObject iaResult) {
        return duplicateDetection(ma, iaResult);
    }

    private static boolean duplicateDetection(MediaAsset ma, JSONObject iaResult) {
        // jann is iaResult
        System.out.println(
            "-- Verifying that we do not have a feature for this detection already...");
        if (ma.getFeatures() != null && ma.getFeatures().size() > 0) {
            double width = iaResult.optDouble("width", 0);
            double height = iaResult.optDouble("height", 0);
            double xtl = iaResult.optDouble("xtl", 0);
            double ytl = iaResult.optDouble("ytl", 0);
            ArrayList<Feature> ftrs = ma.getFeatures();
            for (Feature ft : ftrs) {
                try {
                    JSONObject params = ft.getParameters();
                    if (params != null) {
                        Double ftWidth = params.optDouble("width", 0);
                        Double ftHeight = params.optDouble("height", 0);
                        Double ftXtl = params.optDouble("x", 0);
                        Double ftYtl = params.optDouble("y", 0);
                        // yikes!
                        if (ftHeight == 0 || ftHeight == 0 || height == 0 || width == 0) {
                            continue;
                        }
                        if ((width == ftWidth) && (height == ftHeight) && (ytl == ftYtl) &&
                            (xtl == ftXtl)) {
                            System.out.println(
                                "We have an Identicle detection feature! Skip this ann.");
                            return true;
                        }
                    }
                } catch (NullPointerException npe) { continue; }
            }
        }
        System.out.println("---- Did not find an identical feature.");
        return false;
    }

    // this is the "preferred" way to go from iaClass to Taxonomy (and thus then .getScientificName() or whatever)
    public static Taxonomy iaClassToTaxonomy(String iaClass, Shepherd myShepherd) {
        if (iaClass == null) return null;
        return iaTaxonomyMap(myShepherd).get(iaClass);
    }

    // see above
    public static String convertSpeciesToString(String iaClassLabel) {
        String[] s = convertSpecies(iaClassLabel);

        if (s == null) return null;
        return StringUtils.join(s, " ");
    }

    public static String[] convertSpecies(String iaClassLabel) {
        if (iaClassLabel == null) return null;
        if (speciesMap.containsKey(iaClassLabel)) return speciesMap.get(iaClassLabel);
        return null; // we FAIL now if no explicit mapping.... sorry
    }

    public static String getTaskType(ArrayList<IdentityServiceLog> logs) {
        for (IdentityServiceLog l : logs) {
            JSONObject j = l.getStatusJson();
            if ((j == null) || j.optString("_action").equals("")) continue;
            if (j.getString("_action").indexOf("init") == 0)
                return j.getString("_action").substring(4).toLowerCase();
        }
        return null;
    }

    public static JSONObject processCallback(String taskID, JSONObject resp,
        HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        String rootDir = request.getSession().getServletContext().getRealPath("/");

        return processCallback(taskID, resp, context, rootDir);
    }

    public static void logCallback(String taskId, JSONObject resp) {
        String jobId = resp.optString("jobId");
        JSONObject _response = resp.optJSONObject("_response");

        if (_response == null) {
            System.out.println("error parsing callback response for taskId " + taskId);
            System.out.println("got response: " + resp);
            return;
        }
        JSONObject status = _response.optJSONObject("status");
        if (status == null) {
            System.out.println("error parsing callback response for taskId " + taskId);
            System.out.println("got response: " + resp);
            return;
        }
        boolean success = status.optBoolean("success");
        if (success) {
            System.out.println("processCallback got a successful response for taskId=" + taskId +
                ", jobId=" + jobId);
        } else {
            System.out.println("processCallback got an UNsuccessful response for taskId=" + taskId +
                ", jobId=" + jobId);
            System.out.println("got response: " + resp);
        }
    }

    public static JSONObject processCallback(String taskID, JSONObject resp, String context,
        String rootDir) {
        logCallback(taskID, resp);
        JSONObject rtn = new JSONObject("{\"success\": false}");
        rtn.put("taskId", taskID);
        if (taskID == null) return rtn;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.processCallback");
        myShepherd.beginDBTransaction();
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadByTaskID(taskID, "IBEISIA",
            myShepherd);
        rtn.put("_logs", logs);
        if ((logs == null) || (logs.size() < 1)) return rtn;
        JSONObject newAnns = null;
        String type = getTaskType(logs);
        System.out.println("**** type ---------------> [" + type + "]");
        if ("detect".equals(type)) {
            rtn.put("success", true);
            JSONObject dres = processCallbackDetect(taskID, logs, resp, myShepherd, context,
                rootDir);
            rtn.put("processResult", dres);
            /*
                for detection, we have to check if we have generated any Annotations, which we then pass on to IA.intake() for identification ... BUT
             * only after we commit* (below) !! since ident stuff is queue-based
             */
            newAnns = dres.optJSONObject("annotations");
        } else if ("identify".equals(type)) {
            rtn.put("success", true);
            rtn.put("processResult", processCallbackIdentify(taskID, logs, resp, context, rootDir));
        } else {
            rtn.put("error", "unknown task action type " + type);
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();

        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context,
            "IBEISIADisableIdentification"));
        // now we pick up IA.intake(anns) from detection above (if applicable)
        // should we cluster these based on MediaAsset instead? send them in groups to IA.intake()?
        if (!skipIdent && (newAnns != null)) {
            List<Annotation> needIdentifying = new ArrayList<Annotation>();
            Shepherd myShepherd2 = new Shepherd(context);
            myShepherd2.setAction("IBEISIA.processCallback-IA.intake");
            myShepherd2.beginDBTransaction();
            Task parentTask = Task.load(taskID, myShepherd2);
            // Task parametersSkipIdent looks at the parent task.. It works for non-ID Wildbooks. If you are sending multiple annotations from a
            // single image, and some need
            // ID, some don't, you need to check downstream.
            if (taskParametersSkipIdent(parentTask)) {
                System.out.println("NOTICE: IBEISIA.processCallback() " + parentTask +
                    " skipped identification");
            } else {
                Iterator<?> keys = newAnns.keys();
                while (keys.hasNext()) {
                    String maId = (String)keys.next();
                    System.out.println("maId -> " + maId);
                    JSONArray annIds = newAnns.optJSONArray(maId);
                    if (annIds == null) continue;
                    System.out.println("     ---> " + annIds);
                    for (int i = 0; i < annIds.length(); i++) {
                        String aid = annIds.optString(i, null);
                        if (aid == null) continue;
                        Annotation ann = ((Annotation)(myShepherd2.getPM().getObjectById(
                            myShepherd2.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                        if (ann != null && IBEISIA.validForIdentification(ann,
                            myShepherd2.getContext())) {
                            needIdentifying.add(ann);
                        }
                    }
                }
            }
            if (needIdentifying.size() > 0) {
                // split the results into encounters
                HashMap<String, ArrayList<Annotation> > needIdentifyingMap = new HashMap<String,
                    ArrayList<Annotation> >();
                for (Annotation annot : needIdentifying) {
                    Encounter enc = annot.findEncounter(myShepherd2);
                    if (enc != null) {
                        if (needIdentifyingMap.containsKey(enc.getCatalogNumber())) {
                            ArrayList<Annotation> annots = needIdentifyingMap.get(
                                enc.getCatalogNumber());
                            annots.add(annot);
                            needIdentifyingMap.put(enc.getCatalogNumber(), annots);
                        } else {
                            ArrayList<Annotation> annots = new ArrayList<Annotation>();
                            annots.add(annot);
                            needIdentifyingMap.put(enc.getCatalogNumber(), annots);
                        }
                    }
                }
                // send to ID by Encounter
                for (String encUUID : needIdentifyingMap.keySet()) {
                    ArrayList<Annotation> annots = needIdentifyingMap.get(encUUID);
                    JSONObject taskParameters = new JSONObject();
                    JSONObject mf = new JSONObject();
                    Encounter enc = myShepherd2.getEncounter(encUUID);
                    if (enc != null && enc.getLocationID() != null) {
                        ArrayList<String> locationIDs = new ArrayList<String>();
                        List<String> matchTheseLocationIDs = LocationID.getIDForParentAndChildren(
                            enc.getLocationID(), locationIDs, null);
                        mf.put("locationIds", matchTheseLocationIDs);
                    }
                    taskParameters.put("matchingSetFilter", mf);
                    Task subParentTask = new Task();
                    subParentTask.setParameters(taskParameters);
                    myShepherd2.storeNewTask(subParentTask);
                    myShepherd2.updateDBTransaction();
                    
                    
                    Task childTask = IA.intakeAnnotations(myShepherd2, annots, subParentTask,
                        false);
                    myShepherd2.storeNewTask(childTask);
                    myShepherd2.updateDBTransaction();
                    subParentTask.addChild(childTask);
                    myShepherd2.updateDBTransaction();
                }
            } else {
                System.out.println(
                    "[INFO]: No annotations were suitable for identification. Check resulting identification class(es).");
                myShepherd2.rollbackDBTransaction();
            }
            myShepherd2.rollbackAndClose();
        }
        return rtn;
    }

    private static JSONObject processCallbackDetect(String taskID,
        ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd,
        HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        String rootDir = request.getSession().getServletContext().getRealPath("/");

        return processCallbackDetect(taskID, logs, resp, myShepherd, context, rootDir);
    }

    private static JSONObject processCallbackDetect(String taskID,
        ArrayList<IdentityServiceLog> logs, JSONObject resp, Shepherd myShepherd, String context,
        String rootDir) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        Task task = Task.load(taskID, myShepherd);
        String[] ids = IdentityServiceLog.findObjectIDs(logs);

        System.out.println("***** ids = " + ids);
        if (ids == null) {
            rtn.put("error", "could not find any MediaAsset ids from logs");
            return rtn;
        }
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (int i = 0; i < ids.length; i++) {
            MediaAsset ma = MediaAssetFactory.load(Integer.parseInt(ids[i]), myShepherd);
            if (ma != null) mas.add(ma);
        }
        int numCreated = 0;
        System.out.println("RESP ===>>>>>> " + resp.toString(2));
        if ((resp.optJSONObject("_response") != null) &&
            (resp.getJSONObject("_response").optJSONObject("response") != null) &&
            (resp.getJSONObject("_response").getJSONObject("response").optJSONObject(
            "json_result") != null)) {
            JSONObject j = resp.getJSONObject("_response").getJSONObject("response").getJSONObject(
                "json_result");
            JSONArray rlist = j.optJSONArray("results_list");
            JSONArray ilist = j.optJSONArray("image_uuid_list");
/*  lots to consider here:
    1. how do we determine where the cutoff is for auto-creating the annotation?-- made some methods for this
    2. if we do create (or dont!) how do we denote this for the sake of the user/ui querying status?
    3. do we first clear out existing annotations?
    4. do we allow duplicate (identical) annoations?  if not, do we block that at the level where we attach to encounter? or globally?
    5. do we have to tell IA when we auto-approve (i.e. no user review) results?
    6. how do (when do) we kick off *identification* on an annotation? and what are the target annotations?
    7.  etc???
    update: we now must _first_ build all the Annotations, and then after that decide how they get distributed to Encounters...
 */
            if ((rlist != null) && (rlist.length() > 0) && (ilist != null) &&
                (ilist.length() == rlist.length())) {
                FeatureType.initAll(myShepherd);
                JSONArray needReview = new JSONArray();
                JSONObject amap = new JSONObject();
                List<Annotation> allAnns = new ArrayList<Annotation>();
                List<Integer> alreadyDetected = new ArrayList<Integer>();
                for (int i = 0; i < rlist.length(); i++) {
                    JSONArray janns = rlist.optJSONArray(i);
                    if (janns == null) continue;
                    JSONObject jiuuid = ilist.optJSONObject(i);
                    if (jiuuid == null) continue;
                    String iuuid = fromFancyUUID(jiuuid);
                    MediaAsset asset = null;
                    for (MediaAsset ma : mas) {
                        if (ma.getAcmId() == null) continue; // was likely an asset rejected (e.g. video)
                        if (ma.getAcmId().equals(iuuid) && !alreadyDetected.contains(ma.getIdInt())) {
                            alreadyDetected.add(ma.getIdInt());
                            asset = ma;
                            break;
                        }
                    }
                    if (asset == null) {
                        System.out.println("WARN: could not find MediaAsset for " + iuuid +
                            " in detection results for task " + taskID);
                        continue;
                    }
                    JSONArray newAnns = new JSONArray();
                    if (janns.length() == 0) {
                        // OK, for some species and conditions we may just want to trust the user
                        // that there is an animal in the image and set trivial annot to matchAgainst=true
                        // this case janns is empty, so loop below will be skipped
                        if (asset.getAnnotations() != null && asset.getAnnotations().size() == 1 &&
                            asset.getAnnotations().get(0).isTrivial()) {
                            // so this media asset currently only has one trivial annot
                            Annotation annot = asset.getAnnotations().get(0);
                            Encounter enc = annot.findEncounter(myShepherd);
                            if (enc.getGenus() != null && enc.getSpecificEpithet() != null &&
                                IA.getProperty(context, "matchTrivial",
                                enc.getTaxonomy(myShepherd)) != null) {
                                if (IA.getProperty(context, "matchTrivial",
                                    enc.getTaxonomy(myShepherd)).equals("true")) {
                                    annot.setMatchAgainst(true);
                                    myShepherd.updateDBTransaction();

                                    allAnns.add(annot); // this is cumulative over *all MAs*
                                }
                            }
                        }
                    }
                    boolean needsReview = false;
                    boolean skipEncounters = asset.hasLabel("_frame");
                    for (int a = 0; a < janns.length(); a++) {
                        JSONObject jann = janns.optJSONObject(a);
                        if (jann == null) {
                            continue;
                        }
                        if (jann.optDouble("confidence", -1.0) < getDetectionCutoffValue(context,
                            task)) {
                            needsReview = true;
                            continue;
                        }
                        // these are annotations we can make automatically from ia detection.  we also do the same upon review return
                        // note this creates other stuff too, like encounter
                        // new version *will not* create encounter(s)
                        Annotation ann = createAnnotationFromIAResult(jann, asset, myShepherd,
                            context, rootDir);
                        if (ann == null) {
                            System.out.println(
                                "WARNING: IBEISIA detection callback could not create Annotation from "
                                + asset + " and " + jann);
                            continue;
                        }
                        // TODO: evaluate for deprecation and remove: MAYBE NOT NEEDED - same(?) logic in createAnnotationFromIAResult above ?????   if (!skipEncounters)
                        // _tellEncounter(myShepherd, ann);  // ???, context, rootDir);
                        allAnns.add(ann); // this is cumulative over *all MAs*
                        newAnns.put(ann.getId());
                        ///note: *removed* IA.intake (or IAIntake?) from here, as it needs to be done post-commit,
                        ///  so we use 'annotations' in returned JSON to kick that off (since they all would have passed confidence)
                        numCreated++;
                    }
                    if (needsReview) {
                        needReview.put(asset.getId());
                        asset.setDetectionStatus(STATUS_PENDING);
                    } else {
                        asset.setDetectionStatus(STATUS_COMPLETE);
                    }
                    if (newAnns.length() > 0) {
                        List<Encounter> assignedEncs = asset.assignEncounters(myShepherd); // here is where we make some encounter(s) if we need to
                        rtn.put("_assignedEncsSize", assignedEncs.size());
                        amap.put(Integer.toString(asset.getIdInt()), newAnns);
                        // now we have to collect them under an Occurrence and/or ImportTask as applicable
                        // we basically pick the first of these we find (in case there is more than one?)
                        // and only assign it where there is none.
                        if (!Util.collectionIsEmptyOrNull(assignedEncs)) {
                            ImportTask itask = null;
                            Occurrence occ = null;
                            for (Encounter enc : assignedEncs) {
                                if (itask == null) itask = enc.getImportTask(myShepherd);
                                if (occ == null) occ = myShepherd.getOccurrence(enc);
                            }
                            if (occ == null) { // make one if we have none
                                occ = new Occurrence();
                                occ.setOccurrenceID(Util.generateUUID());
                                occ.setDWCDateLastModified();
                                occ.setDateTimeCreated();
                                occ.addComments("<i>created after assignEncounters</i>");
                            }
                            for (Encounter enc : assignedEncs) {
                                if ((itask != null) && (enc.getImportTask(myShepherd) == null))
                                    itask.addEncounter(enc);
                                if (myShepherd.getOccurrence(enc) == null) {
                                    occ.addEncounter(enc);
                                    enc.setOccurrenceID(occ.getOccurrenceID());
                                }
                            }
                            myShepherd.getPM().makePersistent(occ); // just in case it is new
                        }
                    }
                }
                updateSpeciesOnIA(myShepherd, allAnns); // tells IA what species we know about these annots now
                rtn.put("_note",
                    "created " + numCreated + " annotations for " + rlist.length() + " images");
                rtn.put("success", true);
                task.setStatus("completed");
                task.setCompletionDateInMilliseconds(Long.valueOf(System.currentTimeMillis()));
                myShepherd.updateDBTransaction();
                if (amap.length() > 0) rtn.put("annotations", amap); // needed to kick off ident jobs with return value

                JSONObject jlog = new JSONObject();

                // this will do nothing in the non-video-frame case
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
                    fromDetection(occ, myShepherd, context, rootDir);
                    jlog.put("collatedEncounters", je);
                    jlog.put("collatedOccurrence", occ.getOccurrenceID());
                }
                jlog.put("_action", "processedCallbackDetect");
                if (amap.length() > 0) jlog.put("annotations", amap);
                if (needReview.length() > 0) jlog.put("needReview", needReview);
                log(taskID, null, jlog, myShepherd.getContext());
            } else {
                rtn.put("error", "results_list is empty");
            }
        }
        return rtn;
    }

    private static JSONObject processCallbackIdentify(String taskID,
        ArrayList<IdentityServiceLog> logs, JSONObject resp, String context, String rootDir) {
        JSONObject rtn = new JSONObject("{\"success\": false}");
        String[] ids = IdentityServiceLog.findObjectIDs(logs);

        if (ids == null) {
            rtn.put("error", "could not find any Annotation ids from logs");
            return rtn;
        }
        HashMap<String, Annotation> anns = new HashMap<String, Annotation>(); // NOTE: the key is now the acmId !!
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IBEISIA.processCallbackIdentify");
        myShepherd.beginDBTransaction();
        for (int i = 0; i < ids.length; i++) {
            try {
                Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
                    myShepherd.getPM().newObjectIdInstance(Annotation.class, ids[i]), true)));
                System.out.println("**** " + ann);
                // "should not happen" that we have an annot with no acmId, since this is result post-IA (which needs acmId)
                if (ann != null)
                    anns.put((ann.getAcmId() != null) ? ann.getAcmId() : ann.getId(), ann);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        int numCreated = 0;
        JSONObject infDict = null;
        JSONObject j = null;
        // System.out.println("___________________________________________________\n" + resp + "\n------------------------------------------------------");
        if ((resp.optJSONObject("_response") != null) &&
            (resp.getJSONObject("_response").optJSONObject("response") != null) &&
            (resp.getJSONObject("_response").getJSONObject("response").optJSONObject(
            "json_result") != null)) {
            j = resp.getJSONObject("_response").getJSONObject("response").getJSONObject(
                "json_result");
            if (j.optJSONObject("inference_dict") != null)
                infDict = j.getJSONObject("inference_dict");
        }
        if (infDict == null) {
            rtn.put("error", "could not parse inference_dict from results");

            // set "error" on Task
            Task task = myShepherd.getTask(taskID);
            if (task != null) {
                task.setStatus("error");
            }
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return rtn;
        }
        boolean needReview = false; // set as a whole
        HashMap<String, Boolean> needReviewMap = new HashMap<String, Boolean>();
        if ((infDict.optJSONObject("annot_pair_dict") != null) &&
            (infDict.getJSONObject("annot_pair_dict").optJSONArray("review_pair_list") != null)) {
            JSONArray rlist = infDict.getJSONObject("annot_pair_dict").getJSONArray(
                "review_pair_list");
            JSONArray clist = infDict.getJSONObject("annot_pair_dict").optJSONArray(
                "confidence_list");                                                                      // this allows for null case, fyi
            for (int i = 0; i < rlist.length(); i++) {
                // NOTE: it *seems like* annot_uuid_1 is *always* the member that is from the query_annot_uuid_list... but?? is it? NOTE: Mark and
                // Chris assumed this was true in the line below that looks like String matchUuid =
                // rlist.getJSONObject(i).optJSONObject("annot_uuid_2");

                // NOTE: will the review_pair_list and confidence_list always be in descending order? IF not, then we'll have to only select the
                // best match (what if there's more than one really good match)
                String acmId = fromFancyUUID(rlist.getJSONObject(i).getJSONObject("annot_uuid_1")); // gets not opts here... so ungraceful fail possible
                if (!needReviewMap.containsKey(acmId)) needReviewMap.put(acmId, false); // only set first, so if set true it stays true
                if (needIdentificationReview(rlist, clist, i, context)) {
                    needReview = true;
                    needReviewMap.put(acmId, true);
                }
            }
        }
        JSONObject jlog = new JSONObject();
        jlog.put("_action", "processedCallbackIdentify");

        rtn.put("success", true);
        rtn.put("needReview", needReview);
        jlog.put("needReview", needReview);
        ArrayList<Annotation> needNameResolution = new ArrayList<Annotation>();
        if (needReview) {
            jlog.put("needReviewMap", needReviewMap);
            for (String id : needReviewMap.keySet()) {
                if (!anns.containsKey(id)) {
                    System.out.println(
                        "WARNING: processCallbackIdentify() unable to load Annotation " + id +
                        " to set identificationStatus");
                } else {
                    anns.get(id).setIdentificationStatus(STATUS_PENDING);
                }
            }
            for (String aid : anns.keySet()) { // set annots *not* in needReviewMap complete.  (will there even be any?)
                if (!needReviewMap.keySet().contains(aid)) {
                    anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
                    needNameResolution.add(anns.get(aid));
                }
            }
        } else {
            for (String aid : anns.keySet()) {
                anns.get(aid).setIdentificationStatus(STATUS_COMPLETE);
                needNameResolution.add(anns.get(aid));
            }
            jlog.put("loopComplete", true);
            rtn.put("loopComplete", true);
            jlog.put("_infDict", infDict);
            exitIdentificationLoop(infDict, myShepherd);
        }
        resolveNames(needNameResolution, j.optJSONObject("cm_dict"), myShepherd);
        log(taskID, null, jlog, myShepherd.getContext());

        // set "completed" on Task
        Task task = myShepherd.getTask(taskID);
        if (task != null) {
            task.setStatus("completed");
            task.setCompletionDateInMilliseconds(Long.valueOf(System.currentTimeMillis()));
        }
        myShepherd.commitDBTransaction();
        myShepherd.closeDBTransaction();
        return rtn;
    }

    private static void exitIdentificationLoop(JSONObject infDict, Shepherd myShepherd) {
        System.out.println(
            "*****************\nhey i think we are happy with these annotations!\n*********************\n");
        System.out.println("I am not printing infDict. Sorry.");
        // here we can use cluster_dict to find out what to create/persist on our side
    }

    public static double getDetectionCutoffValue(String context) {
        return getDetectionCutoffValue(context, null);
    }

    // scores < these will require human review (otherwise they carry on automatically)
    // task is optional, but can have a parameter "detectionCutoffValue"
    public static double getDetectionCutoffValue(String context, Task task) {
        if ((task != null) && (task.getParameters() != null) &&
            (task.getParameters().optDouble("detectionCutoffValue", -1) > 0))
            return task.getParameters().getDouble("detectionCutoffValue");
        String c = IA.getProperty(context, "IBEISIADetectionCutoffValue");
        if (c != null) {
            try {
                return Double.parseDouble(c);
            } catch (java.lang.NumberFormatException ex) {}
        }
        return 0.35; // lowish value cuz we trust detection by default
    }

    public static double getIdentificationCutoffValue(String context) {
        String c = IA.getProperty(context, "IBEISIAIdentificationCutoffValue");

        if (c != null) {
            try {
                return Double.parseDouble(c);
            } catch (java.lang.NumberFormatException ex) {}
        }
        return 0.8;
    }

    // tests review_pair_list and confidence_list for element at i and determines if we need review
    private static boolean needIdentificationReview(JSONArray rlist, JSONArray clist, int i,
        String context) {
        if ((rlist == null) || (clist == null) || (i < 0) || (rlist.length() == 0) ||
            (clist.length() == 0) || (rlist.length() != clist.length()) || (i >= rlist.length()))
            return false;
        // work is still out if we need to ignore based on our own matchingState!!!  for now we skip review if we already did it
        if (rlist.optJSONObject(i) == null) return false;
        String ms = getIdentificationMatchingState(fromFancyUUID(rlist.getJSONObject(
            i).optJSONObject("annot_uuid_1")),
            fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")), context);
        System.out.println(
            "needIdentificationReview() got matching_state --------------------------> " + ms);
        if (ms != null) return false;
        return (clist.optDouble(i, -99.0) < getIdentificationCutoffValue(context));
    }

    public static String parseDetectionStatus(String maId, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA",
            maId, myShepherd);

        if ((logs == null) || (logs.size() < 1)) return null;
        for (IdentityServiceLog log : logs) {
            String s = _parseDetection(maId, log);
            if (s != null) return s;
        }
        System.out.println("WARNING: parseDetectionStatus(" + maId +
            ") fell through; returning error.");
        return "error";
    }

    // basically returning null means nothing parsable/usable/interesting found
    private static String _parseDetection(String maId, IdentityServiceLog log) {
        JSONObject status = log.getStatusJson();

        if (status == null) return null;
        String action = status.optString("_action", null);
        if (action == null) return null;
        if (action.equals("processedCallbackDetection")) {
            JSONArray need = status.optJSONArray("needReview");
            if ((need == null) || (need.length() < 1)) return "complete";
            for (int i = 0; i < need.length(); i++) {
                if (maId.equals(need.optString(i))) return "pending";
            }
            return "complete";
        }
        System.out.println("detection most recent action found is " + action);
        if ((System.currentTimeMillis() - log.getTimestamp()) > TIMEOUT_DETECTION) {
            System.out.println("WARNING: detection processing timeout for " + log.toString() +
                "; returning error detection status");
            return "error";
        }
        return "processing";
    }

    public static String parseIdentificationStatus(String annId, Shepherd myShepherd) {
        ArrayList<IdentityServiceLog> logs = IdentityServiceLog.loadMostRecentByObjectID("IBEISIA",
            annId, myShepherd);

        if ((logs == null) || (logs.size() < 1)) return null;
        for (IdentityServiceLog log : logs) {
            String s = _parseIdentification(log);
            if (s != null) return s;
        }
        System.out.println("WARNING: parseIdentificationStatus(" + annId +
            ") fell through; returning error.");
        return "error";
    }

    // basically returning null means nothing parsable/usable/interesting found
    private static String _parseIdentification(IdentityServiceLog log) {
        JSONObject status = log.getStatusJson();

        if (status == null) return null;
        String action = status.optString("_action", null);
        if (action == null) return null;
        System.out.println("identification most recent action found is " + action);
        return "processing";
    }

    public static void setIdentificationMatchingState(String ann1Id, String ann2Id, String state,
        Shepherd myShepherd) {
        IBEISIAIdentificationMatchingState.set(ann1Id, ann2Id, state, myShepherd);
    }

    public static String getIdentificationMatchingState(String ann1Id, String ann2Id,
        String context) {
        Shepherd myShepherd = new Shepherd(context);

        myShepherd.setAction("IBEISIA.getIdentificationMatchingState");
        myShepherd.beginDBTransaction();
        IBEISIAIdentificationMatchingState m = IBEISIAIdentificationMatchingState.load(ann1Id,
            ann2Id, myShepherd);
        if (m == null) {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
            return null;
        }
        String result = m.getState();
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
        // if (request.getUserPrincipal() == null) return null;
        if (request.getUserPrincipal() == null) return "__ANONYMOUS__";
        return request.getUserPrincipal().getName();
    }

    // builds urls for IA, based on just one.  kinda hacky (as opposed to per-endpoint setting in CommonConfiguration); but can be useful
    public static URL iaURL(String context, String urlSuffix) {
        if (iaBaseURL == null) {
            String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
            if (u == null)
                throw new RuntimeException(
                          "configuration value IBEISIARestUrlAddAnnotations is not set");
            int i = u.indexOf("/", 9); // 9 should get us past "http://" to get to post-hostname /
            if (i < -1)
                throw new RuntimeException(
                          "could not parse IBEISIARestUrlAddAnnotations for iaBaseURL");
            iaBaseURL = u.substring(0, i + 1); // will include trailing slash
            System.out.println("INFO: setting iaBaseURL=" + iaBaseURL);
        }
        String ustr = iaBaseURL;

        System.out.println("!!!ustr: " + iaBaseURL);
        System.out.println("!!!urlSuffix: " + urlSuffix);
        if (urlSuffix != null) {
            if (urlSuffix.indexOf("/") == 0) urlSuffix = urlSuffix.substring(1); // get rid of leading /
            ustr += urlSuffix;
        }
        try {
            return new URL(ustr);
        } catch (Exception ex) {
            throw new RuntimeException("iaURL() could not parse URL");
        }
    }

    // note: we *could* try to grab these as lists from IA, but that is more complicated so lets iterate for now...
    public static List<Annotation> grabAnnotations(List<String> annIds, Shepherd myShepherd) {
        List<Annotation> anns = new ArrayList<Annotation>();

        for (String annId : annIds) {
            Annotation ann = null;
            ArrayList<Annotation> existing = myShepherd.getAnnotationsWithACMId(annId);
            // do we need to verify MediaAsset has been retreived?  for now, lets assume that happened during creation
            if ((existing != null) && (existing.size() > 0)) { // we take the first one that exists
                anns.add(existing.get(0));
                continue;
            }
            ann = getAnnotationFromIA(annId, myShepherd);
            if (ann == null)
                throw new RuntimeException("Could not getAnnotationFromIA(" + annId + ")");
            anns.add(ann);
        }
        return anns;
    }

    public static Annotation getAnnotationFromIA(String acmId, Shepherd myShepherd) {
        String context = myShepherd.getContext();

        try {
            String idSuffix = "?annot_uuid_list=[" + toFancyUUID(acmId) + "]";
            JSONObject rtn = RestClient.get(iaURL(context,
                "/api/annot/image/uuid/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
                (rtn.getJSONArray("response").optJSONObject(0) == null))
                throw new RuntimeException("could not get image uuid");
            String imageUUID = fromFancyUUID(rtn.getJSONArray("response").getJSONObject(0));
            MediaAsset ma = grabMediaAsset(imageUUID, myShepherd);
            Taxonomy originalTaxy = ma.getTaxonomy(myShepherd);
            if (ma == null) throw new RuntimeException("could not find MediaAsset " + imageUUID);
            // now we need the bbox to make the Feature
            rtn = RestClient.get(iaURL(context, "/api/annot/bbox/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
                (rtn.getJSONArray("response").optJSONArray(0) == null))
                throw new RuntimeException("could not get annot bbox");
            JSONArray jbb = rtn.getJSONArray("response").getJSONArray(0);
            JSONObject fparams = new JSONObject();
            fparams.put("x", jbb.optInt(0, 0));
            fparams.put("y", jbb.optInt(1, 0));
            fparams.put("width", jbb.optInt(2, -1));
            fparams.put("height", jbb.optInt(3, -1));
            fparams.put("theta", iaThetaFromAnnotUUID(acmId, context)); // now with vitamin THETA!
            Feature ft = new Feature("org.ecocean.boundingBox", fparams);
            ma.addFeature(ft);

            rtn = RestClient.get(iaURL(context, "/api/annot/species/json/" + idSuffix));
            if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
                (rtn.getJSONArray("response").optString(0, null) == null))
                throw new RuntimeException("could not get annot species for iaClass");
            // iaClass... not your scientific name species

            String returnedIAClass = rtn.getJSONArray("response").optString(0, null);
            IAJsonProperties iaConf = IAJsonProperties.iaConfig();
            Taxonomy taxy = ma.getTaxonomy(myShepherd);
            String iaClass = iaConf.convertIAClassForTaxonomy(returnedIAClass, taxy);
            Annotation ann = new Annotation(convertSpeciesToString(iaClass), ft, iaClass);
            ann.setIAExtractedKeywords(myShepherd, originalTaxy);
            // note: ann.id is a random UUID at this point; should we set to acmId??
            // ann.setId(acmId);
            ann.setAcmId(acmId);
            rtn = RestClient.get(iaURL(context, "/api/annot/exemplar/json/" + idSuffix));
            if ((rtn != null) && (rtn.optJSONArray("response") != null)) {
                boolean exemplar = (rtn.getJSONArray("response").optInt(0, 0) == 1);
                ann.setIsExemplar(exemplar);
            }
            Boolean aoi = iaIsOfInterestFromAnnotUUID(acmId, context);
            ann.setIsOfInterest(aoi);
            ann.setMatchAgainst(true); // kosher?
            ann.setViewpointFromIA(context); // note: can block ... but wygd
            System.out.println("INFO: " + ann + " pulled from IA");
            return ann;
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("getAnnotationFromIA(" + acmId + ") error " + ex.toString());
        }
    }

    public static MediaAsset grabMediaAsset(String maUUID, Shepherd myShepherd) {
        // note: there may be more than one acmId with this value, but for this case we dont (cant?) care...
        MediaAsset ma = MediaAssetFactory.loadByAcmId(maUUID, myShepherd);

        if (ma != null) return ma;
        return getMediaAssetFromIA(maUUID, myShepherd);
    }

    // http://52.37.240.178:5000/api/image/src/json/cb2e67a4-7094-d971-c5c6-3b5bed251fec/
    // making a decision to persist these upon creation... there was a conflict cuz loadByUuid above failed on subsequent
    // iterations and this was created multiple times before saving
    public static MediaAsset getMediaAssetFromIA(String maUUID, Shepherd myShepherd) {
        String context = myShepherd.getContext();
        String filename = maUUID + ".jpg"; // hopefully will be updated with real filename!
        String filepath = null;

        try {
            filepath = iaFilepathFromImageUUID(maUUID, context);
            filename = new File(filepath).getName();
        } catch (Exception ex) {
            System.out.println("WARNING: failed to get iaFilepath of " + maUUID + ": " +
                ex.toString());
        }
        // note: we add /fakedir/ cuz the file doesnt need to exist there; we just want to force a hashed subdir to be created in params
        File file = new File("/fakedir/" + filename);
        AssetStore astore = AssetStore.getDefault(myShepherd);
        JSONObject params = astore.createParameters(file);
        if (filepath != null) params.put("iaOriginalFilepath", filepath);
        MediaAsset ma = new MediaAsset(astore, params);
        ma.setAcmId(maUUID);
        // similarly, do we want to set uuid on ma based on acmId???
        // ma.setUUID(maUUID);
        try {
            // grab the url to our localPath for convenience (e.g. child assets to be created from)
            file = ma.localPath().toFile();
            File dir = file.getParentFile();
            if (!dir.exists()) dir.mkdirs();
            // we actually need to handle bad maUUID better.  :( (returns
            RestClient.writeToFile(iaURL(context, "/api/image/src/json/" + maUUID + "/"), file);
            ma.copyIn(file);
            ma.addDerivationMethod("pulledFromIA", System.currentTimeMillis());
            ma.updateMetadata();
            MediaAssetFactory.save(ma, myShepherd);
            ma.updateStandardChildren(myShepherd);
        } catch (IOException ioe) {
            throw new RuntimeException("ERROR: getMediaAssetFromIA " + ioe.toString());
        }
        ma.addLabel("_original");
        ma.setDetectionStatus(STATUS_COMPLETE); // kosher?
        DateTime dt = null;
        try {
            dt = iaDateTimeFromImageUUID(maUUID, context);
        } catch (Exception ex) {}
        if (dt != null) ma.setUserDateTime(dt);
        try {
            Double[] ll = iaLatLonFromImageUUID(maUUID, context);
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
   from that you can grab all the images in the image set then you get all the annotations from the imagse then you grab the names of the annotations
      then you can grab all annotations from each name the annotations in that set that are not in the occurrence set are the ones that may
      potentially be merged I think that is the general walk that needs to happen
 */

    /* generally two things are done here: (1) the setId is made into an Occurrence and the Annotations are added to it (by way of Encounters); and
       (2) a name check is done to possibly merge other Annotations to this indiv  */
    public static JSONObject mergeIAImageSet(String setId, Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        Occurrence existingOccurrence = null;

        try {
            existingOccurrence = ((Occurrence)(myShepherd.getPM().getObjectById(
                myShepherd.getPM().newObjectIdInstance(Occurrence.class, setId), true)));
        } catch (Exception ex) {} // this just means not found... which is good!
        if (existingOccurrence != null)
            throw new RuntimeException("An Occurrence with id " + setId + " already exists.");
        /*  these are really only for lewa branch int setIdInt = iaImageSetIdFromUUID(setId);
           //iaSmartXmlFromSetId
           //iaSmartXmlWaypointIdFromSetId
         */

        // http://52.37.240.178:5000/api/imageset/annot/aids/json/?imageset_uuid_list=[%7B%22__UUID__%22:%228655a73d-749b-4f23-af92-0b07157c0455%22%7D]
        // http://52.37.240.178:5000/api/imageset/annot/uuid/json/?imageset_uuid_list=[{%22__UUID__%22:%228e0850a7-7b29-4150-aedb-8bafb5149757%22}]
        // JSONObject res = RestClient.get(iaURL(myShepherd.getContext(), "/api/imageset/annot/rowid/?imgsetid_list=[" + setId + "]"));
        JSONObject res = RestClient.get(iaURL(myShepherd.getContext(),
            "/api/imageset/annot/uuid/json/?imageset_uuid_list=[" + toFancyUUID(setId) + "]"));
        if ((res == null) || (res.optJSONArray("response") == null) ||
            (res.getJSONArray("response").optJSONArray(0) == null))
            throw new RuntimeException("could not get list of annot ids from setId=" + setId);
        JSONObject rtn = new JSONObject("{\"success\": false}");

        // String setIdUUID = iaImageSetUUIDFromId(setId);
        JSONArray auuids = res.getJSONArray("response").getJSONArray(0);
        System.out.println("auuids = " + auuids);
        if ((auuids == null) || (auuids.length() < 1))
            throw new RuntimeException("ImageSet id " + setId + " has no Annotations.");
        // these will be used at the end to know what annots were original in the set (for Occurrence)
        // JSONArray oau = iaAnnotationUUIDsFromIds(aids);
        List<String> origAnnUUIDs = new ArrayList<String>();
        for (int j = 0; j < auuids.length(); j++) {
            origAnnUUIDs.add(fromFancyUUID(auuids.optJSONObject(j)));
        }
        System.out.println("origAnnUUIDs = " + origAnnUUIDs);

        JSONArray nameUUIDs = iaAnnotationNameUUIDsFromUUIDs(auuids, myShepherd.getContext()); // note: these are fancy uuids
        System.out.println("nameUUIDs = " + nameUUIDs);
        List<JSONObject> funuuids = new ArrayList<JSONObject>(); // these are fancy!
        List<String> unuuids = new ArrayList<String>(); // but these are unfancy!
        for (int i = 0; i < nameUUIDs.length(); i++) {
            String n = fromFancyUUID(nameUUIDs.optJSONObject(i));
            if ((n == null) || unuuids.contains(n)) continue;
            funuuids.add(nameUUIDs.optJSONObject(i));
            unuuids.add(n);
        }
        System.out.println("unuuids = " + unuuids);
        System.out.println("funuuids = " + funuuids);
        // JSONArray jall = iaAnnotationUUIDsFromNameUUIDs(new JSONArray(nameUUIDs));
        JSONArray jall = iaAnnotationUUIDsFromNameUUIDs(new JSONArray(funuuids),
            myShepherd.getContext());
        if (jall.length() != unuuids.size())
            throw new RuntimeException("mergeIAImageSet() annots from name size discrepancy");
        System.out.println("jall = " + jall);

        HashMap<String, String> nameMap = iaNameMapUUIDToString(new JSONArray(funuuids),
            myShepherd.getContext());
        System.out.println("nameMap = " + nameMap);

        // now we walk through and resolve groups of annotations which must be (re)named....
        List<Annotation> anns = new ArrayList<Annotation>();
        List<Encounter> encs = new ArrayList<Encounter>();
        List<MarkedIndividual> newIndivs = new ArrayList<MarkedIndividual>();
        for (int i = 0; i < jall.length(); i++) {
            JSONArray auuidSet = jall.optJSONArray(i);
            if ((auuidSet == null) || (auuidSet.length() < 1)) continue;
            List<String> auList = new ArrayList<String>();
            for (int j = 0; j < auuidSet.length(); j++) {
                /* critical here is that we only pass on (for assignment) annots which (a) are new from the set, or (b) we already have in wb.
                   not sure what to do of annotations we dont have yet -- they need their own encounters!! */
                String u = fromFancyUUID(auuidSet.optJSONObject(j));
                if (origAnnUUIDs.contains(u)) {
                    auList.add(u);
                } else {
                    Annotation ann = null;
                    try {
                        ann = ((Annotation)(myShepherd.getPM().getObjectById(
                            myShepherd.getPM().newObjectIdInstance(Annotation.class, u), true)));
                    } catch (Exception ex) {}
                    if (ann != null) {
                        auList.add(u);
                    } else {
                        System.out.println("--- WARNING: Annotation " + u +
                            " was not in original ImageSet but is not in WB so cannot assign name to Encounter");
                    }
                }
            }
            HashMap<String, Object> done = assignFromIA(nameMap.get(unuuids.get(i)), auList,
                myShepherd);
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

        // at this point we should have "everything" in wb that "we need".... so we push the relative Encounters into this Occurrence
        Occurrence occ = null; // gets created when we have our first Annotation below
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
            System.out.println(" --------------------------_______________________________ " +
                enc.getIndividualID() + " +++++++++++++++++++++++++++++");
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
                    // should we allow recycling an existing Occurrence?  (i.e. loading it here if it exists)
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
        if (occ != null) { // would this ever be???
            myShepherd.getPM().makePersistent(occ);
            rtn.put("occurrenceId", occ.getOccurrenceID());
            System.out.println(" >>>>>>> " + occ.getOccurrenceID());
        }
        return rtn;
    }

    // we make an assumption here that if there are orphaned annotations (not in Encounters already) they should be grouped
    // together into one (new) Encounter, since annUUIDs is assumed to be coming from an Occurrence.  be warned!
    public static HashMap<String, Object> assignFromIA(String individualId, List<String> annUUIDs,
        Shepherd myShepherd) {
        System.out.println(
            "#############################################################################################\nindividualId="
            + individualId + "\n assign to --> " + annUUIDs);
        HashMap<String, Object> rtn = new HashMap<String, Object>();
        List<Annotation> anns = grabAnnotations(annUUIDs, myShepherd);
        if (anns.size() != annUUIDs.size())
            throw new RuntimeException(
                      "assignFromIA() grabbed annots differ in size from passed uuids");
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
            // we have to consider whether they too are getting renamed.  if they are, easy; if not... :( we have to split up this encounter
            ArrayList<Annotation> staying = new ArrayList<Annotation>();
            ArrayList<Annotation> going = new ArrayList<Annotation>();
            for (Annotation eann : enc.getAnnotations()) {
                // we are just going to compare ids, since i dont trust Annotation equivalence
                if (annUUIDs.contains(eann.getId())) {
                    going.add(eann);
                } else {
                    staying.add(eann);
                }
            }
            if (staying.size() == 0) { // we dont need a new encounter; we just modify the indiv on here
                if (!encs.contains(enc)) encs.add(enc);
            } else { // we need to split up the encounter, with a newer one that gets the new indiv id
                Encounter newEnc = enc.cloneWithoutAnnotations(myShepherd);
                System.out.println("INFO: assignFromIA() splitting " + enc + " - staying=" +
                    staying + "; to " + newEnc + " going=" + going);
                enc.setAnnotations(staying);
                newEnc.setAnnotations(going);
                encs.add(newEnc);
            }
        }
        System.out.println("---------------------------------------------\n encs? " + encs);
        System.out.println("---------------------------------------------\n needEnc? " + needEnc);
        if (needEnc.size() > 0) {
            Encounter newEnc = new Encounter(needEnc);

            System.out.println("INFO: assignFromIA() created " + newEnc + " for " + needEnc.size() +
                " annots");
            encs.add(newEnc);
        }
        for (Encounter enc : encs) {
            if (enc.hasMarkedIndividual() && !enc.getIndividualID().equals(individualId)) {
                System.out.println("WARNING: assignFromIA() assigning indiv " + individualId +
                    " to " + enc + " which will replace " + enc.getIndividualID());
            }
        }
        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(individualId);
        int startE = 0;
        if (indiv == null) {
            indiv = new MarkedIndividual(individualId, encs.get(0));
            // encs.get(0).setIndividualID(individualId);
            startE = 1;
            System.out.println("INFO: assignFromIA() created " + indiv);
            rtn.put("newMarkedIndividual", indiv);
        }
        for (int i = startE; i < encs.size(); i++) {
            if (individualId.equals(encs.get(i).getIndividualID())) {
                System.out.println("INFO: " + encs.get(i) +
                    " already was assigned to indiv; skipping");
                continue;
            }
            indiv.addEncounter(encs.get(i));
            // encs.get(i).setIndividualID(individualId);
        }
        indiv.refreshNumberEncounters();

        rtn.put("encounters", encs);
        rtn.put("annotations", anns);
        return rtn;
    }

    // this is a "simpler" assignment -- unlike above, it wont make anything new, but rather will only (re)assign if all is good
    public static HashMap<String, Object> assignFromIANoCreation(String individualId,
        List<String> annUUIDs, Shepherd myShepherd) {
        System.out.println(
            "#######(no create)###########################################################################\nindividualId="
            + individualId + "\n assign to --> " + annUUIDs);
        HashMap<String, Object> rtn = new HashMap<String, Object>();
        List<Annotation> anns = new ArrayList<Annotation>();
        for (String aid : annUUIDs) {
            Annotation ann = ((Annotation)(myShepherd.getPM().getObjectById(
                myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
            if (ann == null) {
                System.out.println("WARNING: assignFromIANoCreate() could not load annot id=" +
                    aid);
            } else {
                anns.add(ann);
            }
        }
        if (anns.size() != annUUIDs.size())
            throw new RuntimeException(
                      "assignFromIANoCreation() could not find some annots from passed uuids");
        System.out.println(anns);
        List<Encounter> encs = new ArrayList<Encounter>();
        for (Annotation ann : anns) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (enc == null)
                throw new RuntimeException(
                          "assignFromIANoCreation() could not find an encounter for annot " +
                          ann.getId());
            // now we have to deal with an existing encounter that contains this annot.... if it has sibling annots in the encounter
            ArrayList<Annotation> staying = new ArrayList<Annotation>();
            ArrayList<Annotation> going = new ArrayList<Annotation>();
            for (Annotation eann : enc.getAnnotations()) {
                if (annUUIDs.contains(eann.getId())) {
                    going.add(eann);
                } else {
                    // lets see what IA says about this annot. with luck, it should get renamed too!
                    String iaName = "__FAIL1__";
                    try {
                        JSONArray jn = iaNamesFromAnnotUUIDs(new JSONArray("[" +
                            toFancyUUID(eann.getId()) + "]"), myShepherd.getContext());
                        iaName = jn.optString(0, "__FAIL2__");
                    } catch (Exception ex) {
                        System.out.println(
                            "WARNING: assignFromIANoCreation() faild name lookup - " +
                            ex.toString());
                    }
                    if (individualId.equals(iaName)) {
                        going.add(eann);
                    } else {
                        // staying.add(eann);
                        System.out.println("CRITICAL: assignFromIANoCreation() " + enc +
                            " requires split; IA reports " + eann + " ident is " + iaName);
                        throw new RuntimeException("reassigning Annotation " + ann.getId() +
                                " to " + individualId + " would cause split on Encounter " +
                                enc.getCatalogNumber());
                    }
                }
            }
            // right now if we get to here, this enc is good to be reassigned ...  logic here will change a bit when handling splits better
            encs.add(enc);
        }
        System.out.println("assignFromIANoCreation() okay to reassign: " + encs);

        MarkedIndividual indiv = myShepherd.getMarkedIndividualQuiet(individualId);
        int startE = 0;
        if (indiv == null) {
            indiv = new MarkedIndividual(individualId, encs.get(0));
            // encs.get(0).setIndividualID(individualId);
            startE = 1;
            System.out.println("INFO: assignFromIANoCreate() created " + indiv);
            rtn.put("newMarkedIndividual", indiv);
        }
        for (int i = startE; i < encs.size(); i++) {
            if (individualId.equals(encs.get(i).getIndividualID())) {
                System.out.println("INFO: " + encs.get(i) +
                    " already was assigned to indiv; skipping");
                continue;
            }
            indiv.addEncounter(encs.get(i));
            // encs.get(i).setIndividualID(individualId);
        }
        rtn.put("encounters", encs);
        rtn.put("annotations", anns);
        return rtn;
    }

    // a sort of wrapper to the above from the point of view of an api (does saving, json stuff etc)
    // note: "simpler" will not create new objects and only do straight-up assignments -- it will throw exceptions if too complex
    public static JSONObject assignFromIAAPI(JSONObject arg, Shepherd myShepherd, boolean simpler) {
        JSONObject rtn = new JSONObject("{\"success\": false}");

        if (arg == null) {
            rtn.put("error",
                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
            return rtn;
        }
        String indivId = arg.optString("name", null);
        JSONArray annIds = arg.optJSONArray("annotationIds");
        if ((indivId == null) || (annIds == null) || (annIds.length() < 1)) {
            rtn.put("error",
                "invalid parameters passed. should be { name: N, annotationIds: [a1,a2,a3] }");
            return rtn;
        }
        List<String> al = new ArrayList<String>();
        for (int i = 0; i < annIds.length(); i++) {
            String a = annIds.optString(i, null);
            if (a != null) al.add(a);
        }
        myShepherd.beginDBTransaction();
        HashMap<String, Object> res = null;
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

    // wrapper like above, but *only* get annot ids, we ask IA for their names and group accordingly
    public static JSONObject arbitraryAnnotationsFromIA(JSONArray arg, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject("{\"success\": false}");

        if (arg == null) {
            rtn.put("error", "invalid parameters: pass array of Annotation ids");
            return rtn;
        }
        HashMap<String, JSONObject> map = new HashMap<String, JSONObject>();
        for (int i = 0; i < arg.length(); i++) {
            String aid = arg.optString(i, null);
            if (aid == null) continue;
            JSONArray auuids = new JSONArray();
            auuids.put(toFancyUUID(aid));
            JSONArray namesRes = null;
            try {
                namesRes = iaAnnotationNamesFromUUIDs(auuids, myShepherd.getContext());
            } catch (Exception ex) {}
            if ((namesRes == null) || (namesRes.length() < 1)) {
                System.out.println(
                    "WARNING: arbitraryAnnotationsFromIA() could not get a name for annot " + aid +
                    "; skipping");
                continue;
            }
            String name = namesRes.optString(0, null);
            if (name == null) {
                System.out.println(
                    "WARNING: arbitraryAnnotationsFromIA() could not get[2] a name for annot " +
                    aid + "; skipping");
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

    // these are mostly utility functions to fetch stuff from IA ... some of these may be unused currently but got made during chaostime
    public static JSONArray __iaAnnotationUUIDsFromIds(JSONArray aids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/uuid/?aid_list=" + aids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/annot/name/uuid/json/?annot_uuid_list=[{%22__UUID__%22:%22c368747b-a4a8-4f59-900d-a9a529c92bca%22}]&__format__=True
    public static JSONArray iaAnnotationNameUUIDsFromUUIDs(JSONArray uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/name/uuid/json/?annot_uuid_list=" + uuids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot name uuid from uuids=" + uuids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/annot/name/text/json/?annot_uuid_list=[{%22__UUID__%22:%22c368747b-a4a8-4f59-900d-a9a529c92bca%22}]&__format__=True
    public static JSONArray iaAnnotationNamesFromUUIDs(JSONArray uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/name/text/json/?annot_uuid_list=" + uuids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot name uuid from uuids=" + uuids);
        return rtn.getJSONArray("response");
    }

    public static JSONArray __iaAnnotationNameIdsFromIds(JSONArray aids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/name/rowid/?aid_list=" + aids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }

    public static JSONArray __iaAnnotationNamesFromIds(JSONArray aids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/name/?aid_list=" + aids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot uuid from aids=" + aids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/name/annot/rowid/?nid_list=[5]
    public static JSONArray ___iaAnnotationIdsFromNameIds(JSONArray nids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/name/annot/rowid/?nid_list=" + nids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot ids from nids=" + nids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/name/annot/uuid/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True
    public static JSONArray iaAnnotationUUIDsFromNameUUIDs(JSONArray uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/name/annot/uuid/json/?name_uuid_list=" + uuids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get annot uuids from name uuids=" + uuids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/name/text/?name_rowid_list=[5,21]&__format__=True
    public static JSONArray __iaNamesFromNameIds(JSONArray nids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/name/text/?name_rowid_list=" + nids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get names from nids=" + nids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/name/text/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True
    public static JSONArray iaNamesFromNameUUIDs(JSONArray uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/name/text/json/?name_uuid_list=" + uuids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get names from name uuids=" + uuids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/annot/name/text/json/?annot_uuid_list=[{%22__UUID__%22:%20%22deee5d41-c264-4179-aa6c-5b735975cbc9%22}]&__format__=True
    public static JSONArray iaNamesFromAnnotUUIDs(JSONArray auuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/name/text/json/?annot_uuid_list=" + auuids.toString()));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get names from auuids=" + auuids);
        return rtn.getJSONArray("response");
    }

    // http://52.37.240.178:5000/api/imageset/smart/xml/file/content/?imageset_rowid_list=[65]
    public static String iaSmartXmlFromSetId(int setId, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/imageset/smart/xml/file/content/?imageset_rowid_list=[" + setId + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get set smartXml from set id=" + setId);
        return rtn.getJSONArray("response").optString(0, null);
    }

    // http://52.37.240.178:5000/api/imageset/smart/waypoint/?imageset_rowid_list=[55]
    public static int iaSmartXmlWaypointIdFromSetId(int setId, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/imageset/smart/waypoint/?imageset_rowid_list=[" + setId + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get set smartXml waypoint id from set id=" +
                    setId);
        return rtn.getJSONArray("response").optInt(0, -1);
    }

    public static HashMap<Integer, String> __iaNameMapIdToString(JSONArray nids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        HashMap<Integer, String> map = new HashMap<Integer, String>();
        JSONArray names = __iaNamesFromNameIds(nids, context);

        if (nids.length() != names.length())
            throw new RuntimeException("iaNameMapIdToString() arrays have different lengths");
        for (int i = 0; i < names.length(); i++) {
            map.put(nids.optInt(i, -1), names.optString(i, null));
        }
        return map;
    }

    public static HashMap<String, String> iaNameMapUUIDToString(JSONArray uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        HashMap<String, String> map = new HashMap<String, String>();
        JSONArray names = iaNamesFromNameUUIDs(uuids, context);

        if (uuids.length() != names.length())
            throw new RuntimeException("iaNameMapUUIDToString() arrays have different lengths");
        for (int i = 0; i < names.length(); i++) {
            map.put(fromFancyUUID(uuids.optJSONObject(i)), names.optString(i, null));
        }
        return map;
    }

    // http://52.37.240.178:5000/api/imageset/uuid/?imgsetid_list=[3]
    public static String iaImageSetUUIDFromId(int setId, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/imageset/uuid/?imgsetid_list=[" + setId + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get set uuid from id=" + setId);
        return fromFancyUUID(rtn.getJSONArray("response").optJSONObject(0));
    }

    // http://52.37.240.178:5000/api/imageset/rowid/uuid/?uuid_list=[%7B%22__UUID__%22:%228e0850a7-7b29-4150-aedb-8bafb5149757%22%7D]
    public static int iaImageSetIdFromUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/imageset/rowid/uuid/?uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get set id from uuid=" + uuid);
        return rtn.getJSONArray("response").optInt(0, -1);
    }

    // this --> is from annot uuid  (note returns in seconds, not milli)
    // http://52.37.240.178:5000/api/annot/image/unixtime/json/?annot_uuid_list=[{%22__UUID__%22:%20%22e95f6af3-4b7a-4d29-822f-5074d5d91c9c%22}]
    public static DateTime iaDateTimeFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/image/unixtime/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get unixtime from annot uuid=" + uuid);
        long t = rtn.getJSONArray("response").optLong(0, -1);
        if (t == -1) return null;
        return new DateTime(t * 1000); // IA returns secs not millisecs
    }

    // http://71.59.132.88:5007/api/annot/interest/json/?annot_uuid_list=[{"__UUID__":"8ddbb0fa-6eda-44ae-862a-c2ad333e7918"}]
    public static Boolean iaIsOfInterestFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/interest/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get isOfInterest from annot uuid=" + uuid);
        if (rtn.getJSONArray("response").isNull(0)) return null;
        return rtn.getJSONArray("response").optBoolean(0);
    }

    // http://52.37.240.178:5000/api/annot/image/gps/json/?annot_uuid_list=[{%22__UUID__%22:%20%22e95f6af3-4b7a-4d29-822f-5074d5d91c9c%22}]
    public static Double[] iaLatLonFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/image/gps/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
            (rtn.getJSONArray("response").optJSONArray(0) == null))
            throw new RuntimeException("could not get gps from annot uuid=" + uuid);
        JSONArray ll = rtn.getJSONArray("response").getJSONArray(0);
        return new Double[] { ll.optDouble(0), ll.optDouble(1) };
    }

    // http://71.59.132.88:5005/api/annot/theta/json/?annot_uuid_list=[{%22__UUID__%22:%224ec2f978-cb4d-48f8-adaf-8eecca120285%22}]
    public static Double iaThetaFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/theta/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get theta from annot uuid=" + uuid);
        return rtn.getJSONArray("response").optDouble(0, 0.0);
    }

    // http://52.37.240.178:5000/api/image/lat/json/?image_uuid_list=[{%22__UUID__%22:%22e985b3d4-bb2a-8291-07af-1ec4028d4649%22}]
    public static Double[] iaLatLonFromImageUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/image/gps/json/?image_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
            (rtn.getJSONArray("response").optJSONArray(0) == null))
            throw new RuntimeException("could not get gps from image uuid=" + uuid);
        JSONArray ll = rtn.getJSONArray("response").getJSONArray(0);
        Double lat = ll.optDouble(0, -1.0D);
        Double lon = ll.optDouble(1, -1.0D);
        // see also:  https://en.wikipedia.org/wiki/Null_Island
        if ((lat == -1.0D) || (lon == -1.0D)) { // these are the values IA uses for unset as well!
            lat = null;
            lon = null;
        }
        return new Double[] { lat, lon };
    }

    // http://52.37.240.178:5000/api/image/unixtime/json/?image_uuid_list=[{%22__UUID__%22:%22cb2e67a4-7094-d971-c5c6-3b5bed251fec%22}]
    public static DateTime iaDateTimeFromImageUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/image/unixtime/json/?image_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get unixtime from image uuid=" + uuid);
        long t = rtn.getJSONArray("response").optLong(0, -1);
        if (t == -1) return null;
        return new DateTime(t * 1000); // IA returns secs not millisecs
    }

    // http://52.37.240.178:5000/api/name/sex/json/?name_uuid_list=[{%22__UUID__%22:%22302cc5dc-4028-490b-99ee-5dc1680d057e%22}]&__format__=True

    // http://52.37.240.178:5000/api/annot/sex/json/?annot_uuid_list=[{%22__UUID__%22:%224517636f-65ad-a236-950c-107f2c962c19%22}]
    public static String iaSexFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/sex/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

        System.out.println(">>>>>>>> sex -> " + rtn);
        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get sex from annot uuid=" + uuid);
        int sexi = rtn.getJSONArray("response").optInt(0, -1);
        if (sexi == -1) {
            return null;
        } else if (sexi == 0) {
            return "female";
        } else if (sexi == 1) {
            return "male";
        }
        System.out.println("WARNING: iaSexFromAnnotUUID(" + uuid +
            ") returned unknown integer sex value=" + sexi);
        return null;
    }

    // NOTE!  this will "block" and can take a while as it synchronously will attempt to label it if it has not before
    // response comes from ia thus: "response": [{"score": 0.9783339699109396, "species": "giraffe_reticulated", "viewpoint": "right"}]
    public static JSONObject iaViewpointFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        String algo = IA.getProperty(context, "labelerAlgo"); // TODO: handle the taxonomy-flavor of these
        String tag = IA.getProperty(context, "labelerModelTag");

        if ((algo == null) || (tag == null))
            throw new IOException(
                      "iaViewPointFromAnnotUUID() must have labelerAlgo and labelerModelTag values set");
        JSONObject data = new JSONObject();
        data.put("algo", algo);
        data.put("model_tag", tag);
        if (uuid != null) data.put("annot_uuid_list", "[" + toFancyUUID(uuid).toString() + "]");
        JSONObject rtn = RestClient.post(iaURL(context, "/api/labeler/cnn/json/"), data);
        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get viewpoint from annot uuid=" + uuid);
        return rtn.getJSONArray("response").optJSONObject(0);
    }

    public static void iaUpdateSpecies(List<String> uuids, List<String> species, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (Util.collectionIsEmptyOrNull(uuids) || Util.collectionIsEmptyOrNull(species)) {
            System.out.println("WARNING: iaUpdateSpecies() received empty uuids/species; ignoring");
            return;
        }
        if (uuids.size() != species.size()) {
            System.out.println(
                "WARNING: iaUpdateSpecies() has mismatched uuids/species lengths! ignoring");
            return;
        }
        JSONArray idList = new JSONArray();
        JSONArray speciesList = new JSONArray();
        System.out.println("!!!IGOTS: " + species.toString());
        for (int i = 0; i < uuids.size(); i++) {
            idList.put(toFancyUUID(uuids.get(i)));
            speciesList.put(species.get(i));
        }
        System.out.println("!!!IPUTS: " + speciesList.toString());
        JSONObject rtn = RestClient.put(iaURL(context,
            "/api/annot/species/json/?annot_uuid_list=" + idList.toString() +
            "&species_text_list=" + speciesList.toString()), null);
    }

// https://kaiju.dyn.wildme.io:5005/api/annot/species/json/?annot_uuid_list=[{%E2%80%9C__UUID__%E2%80%9C:%E2%80%9D079700f0-98ed-46ab-885a-29450bd63924%22},{%E2%80...........
    public static List<String> iaGetSpecies(List<String> uuids, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        if (Util.collectionIsEmptyOrNull(uuids)) {
            System.out.println("WARNING: iaGetSpecies() received empty uuids; ignoring");
            return null;
        }
        // we have to split into smaller jobs due to GET url ... hence: recursion!
        int maxSize = 100;
        if (uuids.size() > maxSize) {
            System.out.println("[INFO] iaGetSpecies() batching " + uuids.size() + " items into " +
                maxSize + "-sized batches");
            List<String> all = new ArrayList<String>();
            for (int i = 0; i < uuids.size(); i += maxSize) {
                int z = i + maxSize;
                if (z > uuids.size()) z = uuids.size();
                all.addAll(iaGetSpecies(uuids.subList(i, z), context));
            }
            return all;
        }
        JSONArray idList = new JSONArray();
        for (int i = 0; i < uuids.size(); i++) {
            idList.put(toFancyUUID(uuids.get(i)));
        }
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/species/json/?annot_uuid_list=" + idList.toString()), null);
        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not iaGetSpecies response");
        List<String> spec = new ArrayList<String>();
        JSONArray jarr = rtn.getJSONArray("response");
        for (int i = 0; i < jarr.length(); i++) {
            if ((jarr.optString(i, null) == null) || jarr.getString(i).equals(IA_UNKNOWN_NAME)) {
                spec.add(null);
            } else {
                spec.add(jarr.optString(i));
            }
        }
        return spec;
    }

// http://104.42.42.134:5010/api/image/uri/original/json/?image_uuid_list=[{%22__UUID__%22:%2283e2439f-d112-1084-af4a-4fa9a5094e0d%22}]
    public static String iaFilepathFromImageUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/image/uri/original/json/?image_uuid_list=[" + toFancyUUID(uuid) + "]"));

        if ((rtn == null) || (rtn.optJSONArray("response") == null))
            throw new RuntimeException("could not get filename from image uuid=" + uuid);
        return rtn.getJSONArray("response").optString(0, null);
    }

// http://52.37.240.178:5000/api/annot/age/months/json/?annot_uuid_list=[{%22__UUID__%22:%224517636f-65ad-a236-950c-107f2c962c19%22}]
// note - returns array with min/max.... doubles?
    public static Double iaAgeFromAnnotUUID(String uuid, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
// we also have /max (or /min) we can add, like:
// http://104.42.42.134:5010/api/annot/age/months/max/json/?annot_uuid_list=[{%22__UUID__%22:%22dbbf90ea-61ef-4ac6-8ddc-d4879df14ea0%22}]
        JSONObject rtn = RestClient.get(iaURL(context,
            "/api/annot/age/months/json/?annot_uuid_list=[" + toFancyUUID(uuid) + "]"));

// NOTE the double JSONArray here:   {"response":[[6,11]], ...
        System.out.println(">>>>>>>> age -> " + rtn);
        if ((rtn == null) || (rtn.optJSONArray("response") == null) ||
            (rtn.getJSONArray("response").optJSONArray(0) == null))
            throw new RuntimeException("could not get age from annot uuid=" + uuid);
        // NOTE!  this is in months. UNSURE what **unit** Encounter.age is meant to be!  storing value as-is (months)  FIXME
        Double min = rtn.getJSONArray("response").getJSONArray(0).optDouble(0, -1.0);
        Double max = rtn.getJSONArray("response").getJSONArray(0).optDouble(1, -1.0);
        if (max != -1.0D) { // we basically favor max (if both min and max are set)   kosher???  we could average
            return max;
        } else if (min != -1.0D) {
            return min;
        }
        return null;
    }

    // note: for list of valid viewpoint values "consult IA".  *wink*
    public static JSONObject iaSetViewpointForAnnotUUID(String uuid, String viewpoint,
        String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        JSONObject rtn = RestClient.put(iaURL(context,
            "/api/annot/viewpoint/json/?annot_uuid_list=[" + toFancyUUID(uuid) +
            "]&viewpoint_list=[\"" + ((viewpoint == null) ? "unknown" : viewpoint) + "\"]"), null);

        return rtn;
    }

    public static JSONObject iaSetViewpointsForAnnotUUIDs(List<String> uuids,
        List<String> viewpoints, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        List<String> fancyUUIDs = new ArrayList<String>();

        for (String uuid : uuids) {
            fancyUUIDs.add(toFancyUUID(uuid).toString());
        }
        String uuidList = Util.joinStrings(fancyUUIDs, ",");
        String viewList = Util.joinStrings(viewpoints, ",");
        JSONObject rtn = RestClient.put(iaURL(context,
            "/api/annot/viewpoint/json/?annot_uuid_list=[" + uuidList + "]&viewpoint_list=[" +
            viewList + "]"), null);
        return rtn;
    }

    public static boolean iaEnabled(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);

        return (IA.getProperty(context, "IBEISIARestUrlAddAnnotations") != null);
    }

    public static boolean iaEnabled() {
        return (IA.getProperty(ContextConfiguration.getDefaultContext(),
                "IBEISIARestUrlAddAnnotations") != null);
    }

    public static JSONObject iaStatus(HttpServletRequest request) {
        String context = ServletUtilities.getContext(request);
        JSONObject rtn = new JSONObject();
        String utest = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");

        if (utest == null) {
            rtn.put("iaURL", (String)null);
            rtn.put("iaEnabled", false);
        } else {
            URL iau = iaURL(context, "");
            rtn.put("iaURL", iau.toString());
            rtn.put("iaEnabled", true);
        }
        rtn.put("timestamp", System.currentTimeMillis());
        JSONObject settings = new JSONObject();
        settings.put("IBEISIARestUrlAddAnnotations",
            IA.getProperty(context, "IBEISIARestUrlAddAnnotations"));

        String boolString = IA.getProperty(context, "requireSpeciesForId");
        if (boolString == null || boolString == "") boolString = "false";
        settings.put("requireSpeciesForId", boolString);
        rtn.put("settings", settings);
        rtn.put("identOpts", identOpts(context));
        return rtn;
    }

    public static boolean unknownName(String name) {
        if (name == null) return true;
        if (name.equals(IA_UNKNOWN_NAME)) return true;
        return false;
    }

    /*
     * TODO: evaluate as deprecated and remove. See primeIA() instead This static method sends all annotations and media assets for a species in Wildbook to Image Analysis in
     * preparation for future matching. It basically primes the system.
     */
    public static JSONObject primeImageAnalysisForSpecies(ArrayList<Encounter> targetEncs,
        Shepherd myShepherd, String species, String baseUrl) {
        String jobID = "-1";
        JSONObject results = new JSONObject();

        results.put("success", false); // pessimism!
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>(); // 0th item will have "query" encounter
        ArrayList<Annotation> tanns = new ArrayList<Annotation>();
        ArrayList<Annotation> allAnns = new ArrayList<Annotation>();
        if (targetEncs.size() < 1) {
            results.put("error", "targetEncs is empty");
            return results;
        }
        log("Prime image analysis for " + species, jobID, new JSONObject("{\"_action\": \"init\"}"),
            myShepherd.getContext());

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
            results.put("sendMediaAssets", sendMediaAssetsNew(mas, myShepherd.getContext()));
            results.put("sendAnnotations",
                sendAnnotationsNew(allAnns, myShepherd.getContext(), myShepherd));

            // this should attempt to repair missing Annotations

            results.put("success", true);
        } catch (Exception ex) { // most likely from sendFoo()
            System.out.println(
                "WARN: IBEISIA.primeImageAnalysisForSpecies() failed due to an exception: " +
                ex.toString());
            ex.printStackTrace();
            results.put("success", false);
            results.put("error", ex.toString());
        }
        JSONObject jlog = new JSONObject();
        jlog.put("_action", "primeImageAnalysisForSpecies: " + species);
        jlog.put("_response", results);
        log("Prime image analysis for " + species, jobID, jlog, myShepherd.getContext());

        return results;
    }

    // qid (query id) can be null, in which case the first one we find is good enough
    public static JSONArray simpleResultsFromAnnotPairDict(JSONObject apd, String qid) {
        if (apd == null) return null;
        JSONArray rlist = apd.optJSONArray("review_pair_list");
        JSONArray clist = apd.optJSONArray("confidence_list");
        if ((rlist == null) || (rlist.length() < 1)) return null;
        if (qid == null)
            qid = fromFancyUUID(rlist.getJSONObject(0).optJSONObject("annot_uuid_key"));
        System.out.println("using qid -> " + qid);
        JSONArray res = new JSONArray();
        for (int i = 0; i < rlist.length(); i++) {
            if (rlist.optJSONObject(i) == null) continue;
            if (!qid.equals(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_key"))))
                continue;
            JSONArray s = new JSONArray();
            s.put(fromFancyUUID(rlist.getJSONObject(i).optJSONObject("annot_uuid_2")));
            s.put(clist.optDouble(i, 0.0));
            res.put(s);
        }
        if (res.length() < 1) return null;
        return res;
    }

    // right now this just uses opt.queryConfigDict as query_config_dict so it passes thru as-is
    public static JSONObject queryConfigDict(Shepherd myShepherd, JSONObject opt) {
        System.out.println("queryConfigDict() get opt = " + opt);
        if (opt == null) return null;
        return opt.optJSONObject("queryConfigDict");
    }

    private static String annotGetIndiv(Annotation ann, Shepherd myShepherd) {
        if (ann == null) return null;
        AnnotationLite annl = AnnotationLite.getCache(ann.getAcmId());
        if ((annl != null) && (annl.getIndividualId() != null)) return annl.getIndividualId();
        String id = cacheAnnotIndiv.get(ann.getId());
        if (id == null) {
            id = ann.findIndividualId(myShepherd);
            cacheAnnotIndiv.put(ann.getId(), id);
        }
        if (annl == null) {
            annl = new AnnotationLite((id == null) ? "____" : id);
        } else {
            annl.setIndividualId((id == null) ? "____" : id);
        }
        AnnotationLite.setCache(ann.getAcmId(), annl);
        return id;
    }

    public static synchronized boolean isIAPrimed() {
        System.out.println(" ............. alreadySentMA size = " + alreadySentMA.keySet().size());
// return true;  // uncomment this, comment-out below, to hard-skip iaPriming (has been useful on staging servers)
        return iaPrimed.get();
    }

    public static synchronized void setIAPrimed(boolean b) {
        System.out.println(" ???? setting iaPrimed to " + b);
        iaPrimed.set(b);
    }

    public static void waitForIAPriming() {
        if (!isIAPrimed() && new File("/tmp/WB_PRIMEFAKE").exists()) {
            System.out.println("INFO: /tmp/WB_PRIMEFAKE encountered, faking IA priming");
            setIAPrimed(true);
            return;
        }
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
// probably want to talk to jon if you have questions

    public static String IAIntake(MediaAsset ma, Shepherd myShepherd, HttpServletRequest request)
    throws ServletException, IOException {
        return IAIntake(new ArrayList<MediaAsset>(Arrays.asList(ma)), myShepherd, request); // singleton just sends as list
    }

    public static String IAIntake(List<MediaAsset> mas, Shepherd myShepherd,
        HttpServletRequest request)
    throws ServletException, IOException {
        // support parent-task (eventually?)
        // roughly IA.intake(MediaAsset) i hope... thus this does detection (returns taskId)
        // superhactacular!  now we piggyback on IAGateway which kinda does this.  sorta.  obvs this will come into IA.intake() ultimately no?
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

    // deprecating these as no IA should be handled via IA Queue, which is (awkwardly) handled via IAGateway presently
    public static String _deprecated_IAIntake(Annotation ann, Shepherd myShepherd,
        HttpServletRequest request)
    throws ServletException, IOException {
        String context = ServletUtilities.getContext(request);
        String rootDir = request.getSession().getServletContext().getRealPath("/");

        return _deprecated_IAIntake(ann, myShepherd, context, rootDir);
    }

    // ditto above, most things
    public static String _deprecated_IAIntake(Annotation ann, Shepherd myShepherd, String context,
        String rootDir)
    throws ServletException, IOException {
        System.out.println("* * * * * * * IAIntake(ident) NOT YET IMPLEMENTED ====> " + ann);
        return Util.generateUUID();
        // how do we know when IA has auto-started identification when detection found an annotation???
    }

    // this is called when a batch of encounters (which should be on this occurrence) were made from detection
    // *as a group* ... see also Encounter.detectedAnnotation() for the one-at-a-time equivalent
    public static void fromDetection(Occurrence occ, Shepherd myShepherd, String context,
        String rootDir) {
        System.out.println(">>>>>> detection created " + occ.toString());
    }

    //// TOTAL HACK... buy jon a drink and he will tell you about these.....
    public static JSONObject hashMapToJSONObject(HashMap<String, ArrayList> map) {
        if (map == null) return null;
        return new JSONObject(map); // this *used to work*, i swear!!!
    }

    public static JSONObject hashMapToJSONObject2(HashMap<String, Object> map) { // note: Object-flavoured
        if (map == null) return null;
        return new JSONObject(map); // this *used to work*, i swear!!!
    }

    // cache???
    public static HashMap<String, Taxonomy> iaTaxonomyMap(Shepherd myShepherd) {
        String context = myShepherd.getContext();
        HashMap<String, Taxonomy> map = new HashMap<String, Taxonomy>();
        String sciName = "";
        int i = 0;

        while (sciName != null) {
            sciName = IA.getProperty(context, "taxonomyScientificName" + i);
            if (sciName == null) continue;
            String iaClass = IA.getProperty(context, "detectionClass" + i);
            if (iaClass == null) iaClass = sciName; // tough love
            map.put(iaClass, myShepherd.getOrCreateTaxonomy(sciName, true));
            i++;
        }
        return map;
    }

    // in IA.properties as stringified JSON objects, like:
    // IBEISIdentOpt1={"OC_WDTW": true}

    public static List<JSONObject> identOpts(String context) {
        List<JSONObject> opt = new ArrayList<JSONObject>();
        int i = 0;
        String jstring = "";

        while (jstring != null) {
            jstring = IA.getProperty(context, "IBEISIdentOpt" + i);
            if (jstring == null) break;
            JSONObject j = Util.stringToJSONObject(jstring);
            if (j != null) opt.add(j);
            i++;
        }
        if (opt.size() < 1) opt.add((JSONObject)null); // we should always have *one* -- the default empty one
        return opt;
    }

    // lets try to figure out what identOpts to use, based on Annotation
    public static List<JSONObject> identOpts(Shepherd myShepherd, Annotation ann) {
        String context = myShepherd.getContext();

        if (ann == null) return identOpts(context); // old-fashioned way, just in case?
        Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) return identOpts(context); // also nope
        String taxString = enc.getTaxonomyString();
        // this basically mimics logic/behavior of getModelTag() and getViewpointTag()
        if (taxString == null) return identOpts(context);
        String prefix = "IBEISIdentOpt_" + taxString.replaceAll(" ", "_");
        System.out.println("[INFO] identOpts() using prefix=" + prefix + " based on " + taxString);

        // now we see if we have *any* of these props (may have none for this taxonomy-prefix)
        List<JSONObject> opt = new ArrayList<JSONObject>();
        int i = 0;
        String jstring = "";
        while (jstring != null) {
            jstring = IA.getProperty(context, prefix + i);
            if (jstring == null) break;
            JSONObject j = Util.stringToJSONObject(jstring);
            if (j != null) opt.add(j);
            i++;
        }
        if (opt.size() < 1) return identOpts(context); // didnt have any, so lets return the default case
        return opt;
    }

    public static String callbackUrl(String baseUrl) {
        return baseUrl + "/ia?callback";
    }

    // this is built explicitly for Queue support (to lose dependency on passing request around)
    public static void callbackFromQueue(JSONObject qjob) {
        System.out.println("INFO: callbackFromQueue() -> " + qjob);
        if (qjob == null) return;
        String context = qjob.optString("context", null);
        String rootDir = qjob.optString("rootDir", null);
        String jobId = qjob.optString("jobId", null);
        JSONObject res = qjob.optJSONObject("dataJson");
        if ((context == null) || (rootDir == null) || (jobId == null)) { // not requiring res so we can have GET callbacks
            System.out.println("ERROR: callbackFromQueue() has insufficient parameters");
            return;
        }
        System.out.println("callbackFromQueue OK!!!!");

        // from here on has been grafted on from IBEISIAGetJobStatus.jsp
        JSONObject statusResponse = new JSONObject();
        try {
            statusResponse = getJobStatus(jobId, context);
        } catch (Exception ex) {
            System.out.println("except? " + ex.toString());
            statusResponse.put("_error", ex.toString());
        }
        System.out.println(statusResponse.toString());
        JSONObject jlog = new JSONObject();
        jlog.put("jobId", jobId);
        String taskId = findTaskIDFromJobID(jobId, context);
        if (taskId == null) {
            jlog.put("error", "could not determine task ID from job " + jobId);
        } else {
            jlog.put("taskId", taskId);
        }
        jlog.put("_action", "getJobStatus");
        jlog.put("_response", statusResponse);

        log(taskId, jobId, jlog, context);

        JSONObject all = new JSONObject();
        all.put("jobStatus", jlog);
        System.out.println(">>>>------[ jobId = " + jobId + " -> taskId = " + taskId +
            " ]----------------------------------------------------");

        try {
            if ((statusResponse != null) && statusResponse.has("status") &&
                statusResponse.getJSONObject("status").getBoolean("success") &&
                statusResponse.has("response") &&
                statusResponse.getJSONObject("response").has("status") &&
                "ok".equals(statusResponse.getJSONObject("response").getString("status")) &&
                "completed".equals(statusResponse.getJSONObject("response").getString(
                "jobstatus"))) {
                System.out.println("HEYYYYYYY i am trying to getJobResult(" + jobId + ")");
                JSONObject resultResponse = getJobResult(jobId, context);
                JSONObject rlog = new JSONObject();
                rlog.put("jobId", jobId);
                rlog.put("_action", "getJobResult");
                rlog.put("_response", resultResponse);
                log(taskId, jobId, rlog, context);
                all.put("jobResult", rlog);

                JSONObject proc = processCallback(taskId, rlog, context, rootDir);
                logProcessCallback(proc, taskId);
            }
        } catch (Exception ex) {
            System.out.println("whoops got exception: " + ex.toString());
            ex.printStackTrace();
        }
        all.put("_timestamp", System.currentTimeMillis());
        System.out.println(
            "-------- >>> all.size() (omitting all.toString() because it's too big!) " +
            all.length() + "\n##################################################################");
        return;
    }

    public static void logProcessCallback(JSONObject proc, String taskId) {
        boolean success = proc.optBoolean("success");

        if (success) {
            List<String> jobIds = getProcessCallbackJobIds(proc, taskId);
            System.out.println("processCallback returned successfully for taskId=" + taskId +
                " . IA job ids we found for this task are " + jobIds.toString());
        } else {
            System.out.println("processCallback returned UNsuccessfully for taskId=" + taskId);
            System.out.println("processCallback returned --> " + proc);
        }
    }

    public static List<String> getProcessCallbackJobIds(JSONObject proc, String taskId) {
        // this whole method is just navigating the pyramid of doom
        JSONArray logs = proc.optJSONArray("_logs");

        if (logs == null) {
            System.out.println(
                "failed to parse jobIds (couldn't find \"_logs\") from processCallback for task " +
                taskId);
            return new ArrayList<String>();
        }
        Set<String> jobIds = new HashSet<String>();
        for (int i = 0; i < logs.length(); i++) {
            JSONObject thisJson = logs.optJSONObject(i);
            if (thisJson == null) continue;
            String serviceJobId = thisJson.optString("serviceJobID");
            if (Util.stringExists(serviceJobId) && !"-1".equals(serviceJobId)) {
                jobIds.add(serviceJobId);
            }
        }
        return Util.asSortedList(jobIds);
    }

    public static boolean validIAClassForIdentification(Annotation ann, String context) {
        ArrayList<String> idClasses = getAllIdentificationClasses(context);

        if (ann.getIAClass() == null && (idClasses.isEmpty() || idClasses == null)) return true;
        if (ann.getIAClass() != null &&
            (idClasses.contains(ann.getIAClass()) || idClasses.isEmpty() || idClasses == null)) {
            return true;
        }
        return false;
    }

    public static boolean validIAClassForIdentification(String iaClassName, String context) {
        ArrayList<String> idClasses = getAllIdentificationClasses(context);

        if (iaClassName == null && (idClasses.isEmpty() || idClasses == null)) return true;
        if (iaClassName != null &&
            (idClasses.contains(iaClassName) || idClasses.isEmpty() || idClasses == null)) {
            return true;
        }
        return false;
    }

    public static boolean validForIdentification(Annotation ann) {
        return validForIdentification(ann, null);
    }

    public static boolean validForIdentification(Annotation ann, String context) {
        if (ann == null) return false;
        String acmId = ann.getAcmId();
        /*
            NOTE: we need to allow for case where ann.acmId is null; namely, this is an annot IA knows nothing about.
            in this case, we will be keeping this out of AnnotationLite.cache, as it should get added later
         */
        AnnotationLite annl = null;
        if (acmId != null) {
            annl = AnnotationLite.getCache(acmId);
            if ((annl != null) && (annl.getValidForIdentification() != null))
                return annl.getValidForIdentification();
        }
        // System.out.println("BBOX features -> " + ann.getFeatures()); //please leave this line in (ask jon... sigh)
        List<Feature> forceJdoToUnpackTheseFeatures = ann.getFeatures();
        String ungodlyHackString = "";
        if (forceJdoToUnpackTheseFeatures != null)
            ungodlyHackString = forceJdoToUnpackTheseFeatures.toString();
        int[] bbox = ann.getBbox();
        if (bbox == null) {
            System.out.println("NOTE: IBEISIA.validToSendToIA() failing " + ann.toString() +
                " - invalid bbox");
            if (acmId != null) {
                if (annl == null) {
                    annl = new AnnotationLite(false);
                } else {
                    annl.setValidForIdentification(false);
                }
                AnnotationLite.setCache(acmId, annl);
            }
            return false;
        }
        if (context != null && !validIAClassForIdentification(ann, context) && !ann.isTrivial()) {
            System.out.println("NOTE: IBEISIA.validForIdentification() failing " + ann.toString() +
                " - annotation does not have valid Identification class.");
            if (acmId != null) {
                if (annl == null) {
                    annl = new AnnotationLite(false);
                } else {
                    annl.setValidForIdentification(false);
                }
                AnnotationLite.setCache(acmId, annl);
            }
            return false;
        }
        if (acmId != null) {
            if (annl == null) {
                annl = new AnnotationLite(true);
            } else {
                annl.setValidForIdentification(true);
            }
            AnnotationLite.setCache(acmId, annl);
        }
        return true;
    }

    // this is likely deprecated as it uses the properties file rather than IA.json
    public static ArrayList<String> getAllIdentificationClasses(String context) {
        String className = "";
        ArrayList<String> allClasses = new ArrayList<String>();
        int i = 0;

        while (className != null) {
            className = IA.getProperty(context, "identificationClass" + i);
            if (className == null) break;
            allClasses.add(className);
            i++;
        }
        return allClasses;
    }

    // does this task want us to skip identification?
    public static boolean taskParametersSkipIdent(Task task) {
        if ((task == null) || (task.getParameters() == null) ||
            !task.getParameters().optBoolean("skipIdent", false)) return false;
        return true;
    }

    private static void resolveNames(ArrayList<Annotation> anns, JSONObject cmDict,
        Shepherd myShepherd) {
// under development!
// cmDict has a structure like:  { acmId1: { dname_list: [], dannot_uuid_list: [] } } .... um, i think?
        // resolveNames(anns, j.optJSONObject("cm_dict"), myShepherd);
    }

    // duct-tape piecemeal fixes for IA-Next
    public static WildbookIAM getPluginInstance(String context) {
        IAPlugin p = IAPluginManager.getIAPluginInstanceFromClass(WildbookIAM.class, context);

        return (WildbookIAM)p;
    }

    public static JSONObject sendMediaAssetsNew(ArrayList<MediaAsset> mas, String context)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        WildbookIAM plugin = getPluginInstance(context);

        return plugin.sendMediaAssets(mas, true);
    }

    public static JSONObject sendAnnotationsNew(ArrayList<Annotation> anns, String context,
        Shepherd myShepherd)
    throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException,
        InvalidKeyException {
        WildbookIAM plugin = getPluginInstance(context);

        return plugin.sendAnnotations(anns, true, myShepherd);
    }

    public static JSONObject sendAnnotationsAsNeeded(ArrayList<Annotation> anns,
        Shepherd myShepherd) {
        long tt = System.currentTimeMillis();

        Util.mark("sendAnnotationsAsNeeded -in- ", tt);
        JSONObject rtn = new JSONObject();
        rtn.put("numAnnotsTotal", Util.collectionSize(anns));
        if (Util.collectionIsEmptyOrNull(anns)) return rtn;
        WildbookIAM plugin = getPluginInstance(myShepherd.getContext());
        ArrayList<Annotation> annsToSend = new ArrayList<Annotation>();
        // List<String> iaAnnotIds = plugin.iaAnnotationIds();
        HashSet<String> iaAnnotIds = new HashSet(plugin.iaAnnotationIds());
        if (iaAnnotIds.isEmpty())
            throw new RuntimeException("iaAnnotIds is empty; possible IA problems");
        Util.mark("sendAnnotationsAsNeeded 1 ", tt);
        ArrayList<MediaAsset> masToSend = new ArrayList<MediaAsset>();
        // List<String> iaImageIds = plugin.iaImageIds();  //in a better world we would do this *after* we have built up masToSend
        HashSet<String> iaImageIds = null;
        Util.mark("sendAnnotationsAsNeeded 2-hs ", tt);
        for (Annotation ann : anns) {
            if (iaAnnotIds.contains(ann.getAcmId())) continue;
            MediaAsset ma = ann.getMediaAsset();
            if (ma == null) continue; // snh #bad
            annsToSend.add(ann);
            // get iaImageIds only if we need it
            if (iaImageIds == null) iaImageIds = new HashSet(plugin.iaImageIds());
            if (iaImageIds.isEmpty())
                throw new RuntimeException("iaImageIds is empty; possible IA problems");
            if (iaImageIds.contains(ma.getAcmId())) continue;
            masToSend.add(ma);
        }
        Util.mark("sendAnnotationsAsNeeded 3-hs ", tt);
        rtn.put("numAnnotsToSend", Util.collectionSize(annsToSend));
        rtn.put("numAssetsToSend", Util.collectionSize(masToSend));
        try {
            if (!Util.collectionIsEmptyOrNull(masToSend))
                rtn.put("sendMediaAssets", plugin.sendMediaAssets(masToSend, false));
            Util.mark("sendAnnotationsAsNeeded 4 ", tt);
            if (!Util.collectionIsEmptyOrNull(annsToSend))
                rtn.put("sendAnnotations", plugin.sendAnnotations(annsToSend, false, myShepherd));
        } catch (Exception ex) {
            rtn.put("sendAnnotMAException", ex.toString());
        }
        Util.mark("sendAnnotationsAsNeeded -out- ", tt);
        return rtn;
    }

    // note, this is (likely) mixed-case with space, so be warned.
    public static Map<String, String> acmIdSpeciesMap(Shepherd myShepherd) {
        String sql =
            "SELECT \"ANNOTATION\".\"ACMID\" as acmId, \"ENCOUNTER\".\"GENUS\" as genus, \"ENCOUNTER\".\"SPECIFICEPITHET\" as specificEpithet FROM \"ANNOTATION\" JOIN \"ENCOUNTER_ANNOTATIONS\" ON (\"ENCOUNTER_ANNOTATIONS\".\"ID_EID\" = \"ANNOTATION\".\"ID\") JOIN \"ENCOUNTER\" ON (\"ENCOUNTER_ANNOTATIONS\".\"CATALOGNUMBER_OID\" = \"ENCOUNTER\".\"CATALOGNUMBER\") WHERE \"ANNOTATION\".\"ACMID\" IS NOT NULL;";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        Map<String, String> rtn = new HashMap<String, String>();
        List results = (List)q.execute();
        Iterator it = results.iterator();

        while (it.hasNext()) {
            Object[] row = (Object[])it.next();
            String acmId = (String)row[0];
            String genus = (String)row[1];
            String specificEpithet = (String)row[2];
            rtn.put(acmId, Util.taxonomyString(genus, specificEpithet));
        }
        q.closeAll();
        return rtn;
    }

    // returns map of acmId=>species where IA was set incorrectly
    // "cleanup" will set acmId=>_cleanup for IDs which are unknown to wb side
    public static Map<String, String> iaSpeciesDiff(Shepherd myShepherd, boolean includeCleanup) {
        String context = myShepherd.getContext();
        Map<String, String> ourMap = acmIdSpeciesMap(myShepherd);
        List<String> iaIds = org.ecocean.ia.plugin.WildbookIAM.iaAnnotationIds(context);
        int orig = iaIds.size();
        List<String> ourIds = new ArrayList<String>(ourMap.keySet());
        List<String> needCleanup = null;

        if (includeCleanup) {
            needCleanup = new ArrayList<String>(iaIds);
            needCleanup.removeAll(ourIds);
            System.out.println("[INFO] iaSpeciesDiff() wanting cleanup of " + needCleanup.size() +
                " unknown ia ids");
        }
        // now we need the intersection of ids in IA (iaIds) and what we have -- which ideally should be the same! "ha"
        iaIds.retainAll(ourIds);
        System.out.println("[INFO] iaSpeciesDiff() locally reduced iaIds from " + orig + " to " +
            iaIds.size());
        List<String> iaList = null;
        try {
            iaList = iaGetSpecies(iaIds, context);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        if (iaList == null) {
            System.out.println("ERROR: iaSpeciesDiff() could not obtain iaList; failing");
            return null;
        }
        System.out.println("ourMap.size = " + ourMap.size());
        System.out.println("iaIds.size = " + iaIds.size());
        System.out.println("iaList.size = " + iaList.size());
        if (iaIds.size() != iaList.size()) {
            System.out.println("ERROR: iaSpeciesDiff() iaIds (" + iaIds.size() +
                ") differs in size from iaList (" + iaList.size() + "); failing");
            return null;
        }
        Map<String, String> diff = new HashMap<String, String>();
        for (int i = 0; i < iaIds.size(); i++) {
            String ours = ourMap.get(iaIds.get(i));
            // if this is null, i guess it means we have null species set *OR* we dont have that. should never be the latter?
            // ... either way, not going to tell IA to set to null, so....    (is this bad?)
            if (ours == null) continue;
            String ias = iaList.get(i);
            ours = ours.replaceAll(" ", "_").toLowerCase();
            if (ias == null) {
                System.out.println("[INFO] iaSpeciesDiff() acmId=" + iaIds.get(i) +
                    " got NULL from IA versus local " + ours);
                diff.put(iaIds.get(i), ours);
            } else if (!ours.equals(ias.replaceAll(" ", "_").toLowerCase())) {
                System.out.println("[INFO] iaSpeciesDiff() acmId=" + iaIds.get(i) + " got " + ias +
                    " from IA versus local " + ours);
                diff.put(iaIds.get(i), ours);
            }
        }
        if (!Util.collectionIsEmptyOrNull(needCleanup))
            for (String id : needCleanup) {
                diff.put(id, "_cleanup");
            }
        return diff;
    }
}
