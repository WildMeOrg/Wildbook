package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ShepherdPMF;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.RestClient;
import org.ecocean.media.*;
import org.ecocean.Annotation;
import org.ecocean.acm.AcmUtil;
import java.util.List;
import java.util.HashMap;
import java.util.ArrayList;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.joda.time.DateTime;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;

//NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
import org.ecocean.identity.IBEISIA;

/*
    Wildbook Image Analysis Module (IAM)

    Initial stab at "plugin architecture" for "Image Analysis"

*/
public class WildbookIAM extends IAPlugin {
    private String context = null;

    public WildbookIAM() {
        super();
    }
    public WildbookIAM(String context) {
        super(context);
        this.context = context;
    }

    @Override
    public boolean isEnabled() {
        return true;  //FIXME
    }

    @Override
    public boolean init(String context) {
        this.context = context;
        IA.log("WildbookIAM init() called on context " + context);
        return true;
    }

    @Override
    public void startup(ServletContextEvent sce) {
        //TODO genericize this to be under .ia (with startup hooks for *any* IA plugin)
        //if we dont need identificaiton, no need to prime
        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context, "IBEISIADisableIdentification"));
        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    }

    //TODO we need to "reclaim" these from IA.intake() stuff!
    @Override
    public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas, final Task parentTask) {
        return null;
    }
    @Override
    public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
        return null;
    }

    //for now "primed" is stored in IBEISIA still.  <scratches head>
    public boolean isPrimed() {
        return IBEISIA.isIAPrimed();
    }

    public void prime() {
        IA.log("INFO: WildbookIAM.prime(" + this.context + ") called");
        IBEISIA.setIAPrimed(false);
        if (!isEnabled()) return;

        final List<String> iaAnnotIds = iaAnnotationIds();
        final List<String> iaImageIds = iaImageIds();

        Runnable r = new Runnable() {
            public void run() {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("WildbookIAM.prime");
                myShepherd.beginDBTransaction();
                ArrayList<Annotation> matchingSet = Annotation.getMatchingSetAllSpecies(myShepherd);
                ArrayList<Annotation> sendAnns = new ArrayList<Annotation>();
                ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
                for (Annotation ann : matchingSet) {
                    if (iaAnnotIds.contains(ann.getAcmId())) continue;  //no need to send
                    sendAnns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma == null) continue;
                    if (iaImageIds.contains(ma.getAcmId())) continue;
                    mas.add(ma);
                }
                IA.log("INFO: WildbookIAM.prime(" + context + ") sending " + sendAnns.size() + " annots (of " + matchingSet.size() + ") and " + mas.size() + " images");
                try {
                    //think we can checkFirst on both of these -- no need to re-send anything during priming
                    sendMediaAssets(mas, true);
                    sendAnnotations(sendAnns, true);
                } catch (Exception ex) {
                    IA.log("ERROR: WildbookIAM.prime() failed due to " + ex.toString());
                    ex.printStackTrace();
                }
/*
                for (MediaAsset ma : mas) {
System.out.println("B: " + ma.getAcmId() + " --> " + ma);
                    MediaAssetFactory.save(ma, myShepherd);
                }
*/
                myShepherd.commitDBTransaction();  //MAs and annots may have had acmIds changed
                myShepherd.closeDBTransaction();
                IBEISIA.setIAPrimed(true);
                IA.log("INFO: WildbookIAM.prime(" + context + ") complete");
            }
        };
        new Thread(r).start();
    }

/*
    note: sendMediaAssets() and sendAnnotations() need to be *batched* now in small chunks, particularly sendMediaAssets().
    this is because we **must** get the return value from the POST, in order that we can map the corresponding (returned) acmId
    values.  if we *timeout* in the POST, this *will not happen*.  and it is a lengthy process on the IA side: as IA must grab
    the image over the network and generate the acmId from it!  hence, batchSize... which we kind of guestimate and cross our fingers.
*/

    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int batchSize = 30;
        int numBatches = Math.round(mas.size() / batchSize + 1);

        //sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaImageIds = new ArrayList<String>();
        if (checkFirst) iaImageIds = iaImageIds();

        //initial initialization(!)
        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());
        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); //for rectifyMediaAssetIds below
        int batchCt = 1;
        JSONObject allRtn = new JSONObject();
        allRtn.put("_batchSize", batchSize);
        allRtn.put("_totalSize", mas.size());
        JSONArray bres = new JSONArray();

        for (int i = 0 ; i < mas.size() ; i++) {
            MediaAsset ma = mas.get(i);
            if (iaImageIds.contains(ma.getAcmId())) continue;
            if (ma.isValidImageForIA()!=null&&!ma.isValidImageForIA()) {
                IA.log("WARNING: WildbookIAM.sendMediaAssets() found a corrupt or otherwise invalid MediaAsset with Id: " + ma.getId());
                continue;
            }
            if (!validMediaAsset(ma)) {
                IA.log("WARNING: WildbookIAM.sendMediaAssets() skipping invalid " + ma);
                continue;
            }
            acmList.add(ma);
            map.get("image_uri_list").add(mediaAssetToUri(ma));
            map.get("image_gps_lat_list").add(ma.getLatitude());
            map.get("image_gps_lon_list").add(ma.getLongitude());
            DateTime t = ma.getDateTime();
            if (t == null) {
                map.get("image_unixtime_list").add(null);
            } else {
                map.get("image_unixtime_list").add((int)Math.floor(t.getMillis() / 1000));  //IA wants seconds since epoch
            }

            if ( (i == (mas.size() - 1))  ||  ((i > 0) && (i % batchSize == 0)) ) {   //end of all; or end of a batch
                if (acmList.size() > 0) {
                    IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + acmList.size() + " with batchSize=" + batchSize + " (" + batchCt + " of " + numBatches + " batches)");
                    JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
System.out.println(batchCt + "]  sendMediaAssets() -> " + rtn);
                    List<String> acmIds = acmIdsFromResponse(rtn);
                    if (acmIds == null) {
                        IA.log("WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: " + rtn);
                    } else {
                        int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
                        IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged + " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
                    }
                    bres.put(rtn);
                    //initialize for next batch (if any)
                    map.put("image_uri_list", new ArrayList<JSONObject>());
                    map.put("image_unixtime_list", new ArrayList<Integer>());
                    map.put("image_gps_lat_list", new ArrayList<Double>());
                    map.put("image_gps_lon_list", new ArrayList<Double>());
                    acmList = new ArrayList<MediaAsset>();
                } else {
                    bres.put("EMPTY BATCH");
                }
                batchCt++;
            }
        }
        allRtn.put("batchResults", bres);
        return allRtn;
    }

    public JSONObject sendAnnotations(ArrayList<Annotation> anns, boolean checkFirst) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddAnnotations");
        if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddAnnotations is not set");
        URL url = new URL(u);
        int ct = 0;
        //may be different shepherd, but findIndividualId() below will only work if its all persisted anyway. :/
        Shepherd myShepherd = null;
        try {
            myShepherd = new Shepherd(context);
        } catch (Exception e) {
            e.printStackTrace();
        }
        myShepherd.setAction("WildbookIAM.sendAnnotations");
        myShepherd.beginDBTransaction();

        //sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaAnnotIds = new ArrayList<String>();
        if (checkFirst) iaAnnotIds = iaAnnotationIds();

        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uuid_list", new ArrayList<String>());
        map.put("annot_species_list", new ArrayList<String>());
        map.put("annot_bbox_list", new ArrayList<int[]>());
        map.put("annot_name_list", new ArrayList<String>());
        map.put("annot_theta_list", new ArrayList<Double>());

        List<Annotation> acmList = new ArrayList<Annotation>(); //for rectifyAnnotationIds below
        for (Annotation ann : anns) {
            if (iaAnnotIds.contains(ann.getAcmId())) continue;
            if (ann.getMediaAsset() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann + "; skipping!");
                continue;
            }
            if (ann.getMediaAsset().getAcmId() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find acmId for " + ann + " (MediaAsset id=" + ann.getMediaAsset().getId() + " not added to IA?); skipping!");
                continue;
            }
            if (!IBEISIA.validForIdentification(ann)) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() skipping invalid " + ann);
                continue;
            }
            JSONObject iid = toFancyUUID(ann.getMediaAsset().getAcmId());
            if (iid == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset.acmId for " + ann.getMediaAsset() + " on " + ann + "; skipping!");
                continue;
            }
            acmList.add(ann);
            map.get("image_uuid_list").add(iid);
            int[] bbox = ann.getBbox();
            map.get("annot_bbox_list").add(bbox);
//TODO both of these shepherd/db calls can probably be combined !!!  FIXME
            map.get("annot_species_list").add(getIASpecies(ann, myShepherd));
            map.get("annot_theta_list").add(ann.getTheta());
            String name = ann.findIndividualId(myShepherd);
            map.get("annot_name_list").add((name == null) ? "____" : name);
            ct++;
        }
        myShepherd.rollbackDBTransaction();

        IA.log("INFO: WildbookIAM.sendAnnotations() is sending " + ct);
        if (ct < 1) return null;  //null for "none to send" ?  is this cool?
System.out.println("sendAnnotations(): data -->\n" + map);
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
System.out.println("sendAnnotations() -> " + rtn);
        List<String> acmIds = acmIdsFromResponse(rtn);
        if (acmIds == null) {
            IA.log("WARNING: WildbookIAM.sendAnnotations() could not get list of acmIds from response: " + rtn);
        } else {
            int numChanged = AcmUtil.rectifyAnnotationIds(acmList, acmIds);
            IA.log("INFO: WildbookIAM.sendAnnotations() updated " + numChanged + " Annotation(s) acmId(s) via rectifyAnnotationIds()");
        }
        return rtn;
    }


    public static List<String> acmIdsFromResponse(JSONObject rtn) {
        if ((rtn == null) || (rtn.optJSONArray("response") == null)) return null;
        List<String> ids = new ArrayList<String>();
        for (int i = 0 ; i < rtn.getJSONArray("response").length() ; i++) {
            if (rtn.getJSONArray("response").optJSONObject(i) == null) {
                //IA returns null when it cant localize/etc, so we need to add this to keep array length the same
                ids.add(null);
            } else {
                ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
            }
        }
System.out.println("fromResponse ---> " + ids);
        return ids;
    }

    //instance version of below (since context is known)
    public List<String> iaAnnotationIds() {
        return iaAnnotationIds(this.context);
    }
    //this fails "gracefully" with empty list if network fubar.  bad decision?
    public static List<String> iaAnnotationIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;
        try {
            jids = apiGetJSONArray("/api/annot/json/", context);
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaAnnotationIds() returning empty; failed due to " + ex.toString());
        }
        if (jids != null) {
            try {
                for (int i = 0 ; i < jids.length() ; i++) {
                    if (jids.optJSONObject(i) != null) ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaAnnotationIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }

    //as above, but images
    public List<String> iaImageIds() {
        return iaImageIds(this.context);
    }
    public static List<String> iaImageIds(String context) {
        List<String> ids = new ArrayList<String>();
        JSONArray jids = null;
        try {
            jids = apiGetJSONArray("/api/image/json/", context);
        } catch (Exception ex) {
            ex.printStackTrace();
            IA.log("ERROR: WildbookIAM.iaImageIds() returning empty; failed due to " + ex.toString());
        }
        if (jids != null) {
            try {
                for (int i = 0 ; i < jids.length() ; i++) {
                    if (jids.optJSONObject(i) != null) ids.add(fromFancyUUID(jids.getJSONObject(i)));
                }
            } catch (Exception ex) {
                ex.printStackTrace();
                IA.log("ERROR: WildbookIAM.iaImageIds() parsing error " + ex.toString());
            }
        }
        return ids;
    }


    public JSONArray apiGetJSONArray(String urlSuffix) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        return apiGetJSONArray(urlSuffix, this.context);
    }
    public static JSONArray apiGetJSONArray(String urlSuffix, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        URL u = IBEISIA.iaURL(context, urlSuffix);
        JSONObject rtn = RestClient.get(u);
        if ((rtn == null) || (rtn.optJSONObject("status") == null) || (rtn.optJSONArray("response") == null) || !rtn.getJSONObject("status").optBoolean("success", false)) {
            IA.log("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " + rtn);
            return null;
        }
        return rtn.getJSONArray("response");
    }


    public static String fromFancyUUID(JSONObject u) {
        if (u == null) return null;
        return u.optString("__UUID__", null);
    }
    public static JSONObject toFancyUUID(String u) {
        JSONObject j = new JSONObject();
        j.put("__UUID__", u);
        return j;
    }

    private static Object mediaAssetToUri(MediaAsset ma) {
        //URL curl = ma.containerURLIfPresent();  //what is this??
        //if (curl == null) curl = ma.webURL();
        URL curl = ma.webURL();
        if (ma.getStore() instanceof LocalAssetStore) {
            if (curl == null) return null;
            return curl.toString();
        } else if (ma.getStore() instanceof S3AssetStore) {
            return ma.getParameters();
        } else {
            if (curl == null) return null;
            return curl.toString();
        }
    }

    //basically "should we send to IA?"
    public static boolean validMediaAsset(MediaAsset ma) {
        if (ma == null) return false;
        if (!ma.isMimeTypeMajor("image")) return false;
        if ((ma.getWidth() < 1) || (ma.getHeight() < 1)) return false;
        if (mediaAssetToUri(ma) == null) {
            System.out.println("WARNING: WildbookIAM.validMediaAsset() failing from null mediaAssetToUri() for " + ma);
            return false;
        }
        return true;
    }

    //this is used to give a string to IA for annot_species_list specifially
    //  hence the term "IASpecies"
    public static String getIASpecies(Annotation ann, Shepherd myShepherd) {
        //NOTE: returning null here is probably "bad" btw....
        org.ecocean.Encounter enc = ann.findEncounter(myShepherd);
        if (enc == null) return null;
        String ts = enc.getTaxonomyString();
        if (ts == null) return null;
        return ts.replaceAll(" ", "_");
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("WildbookIAM IA Plugin")
                .toString();
    }

}
