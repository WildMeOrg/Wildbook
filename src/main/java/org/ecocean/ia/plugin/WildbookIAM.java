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
    public Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        return null;
    }
    @Override
    public Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
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
                    sendMediaAssets(mas, false);
                    sendAnnotations(sendAnns, false);
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


    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas, boolean checkFirst) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        String u = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if (u == null) throw new MalformedURLException("WildbookIAM configuration value IBEISIARestUrlAddImages is not set");
        URL url = new URL(u);
        int ct = 0;

        //sometimes (i.e. when we already did the work, like priming) we dont want to check IA first
        List<String> iaImageIds = new ArrayList<String>();
        if (checkFirst) iaImageIds = iaImageIds();

        HashMap<String,ArrayList> map = new HashMap<String,ArrayList>();
        map.put("image_uri_list", new ArrayList<JSONObject>());
        map.put("image_unixtime_list", new ArrayList<Integer>());
        map.put("image_gps_lat_list", new ArrayList<Double>());
        map.put("image_gps_lon_list", new ArrayList<Double>());

        List<MediaAsset> acmList = new ArrayList<MediaAsset>(); //for rectifyMediaAssetIds below
        for (MediaAsset ma : mas) {
            if (iaImageIds.contains(ma.getAcmId())) continue;
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
            ct++;
        }

        IA.log("INFO: WildbookIAM.sendMediaAssets() is sending " + ct);
        if (ct < 1) return null;  //null for "none to send" ?  is this cool?
        JSONObject rtn = RestClient.post(url, IBEISIA.hashMapToJSONObject(map));
System.out.println("sendMediaAssets() -> " + rtn);
        List<String> acmIds = acmIdsFromResponse(rtn);
        if (acmIds == null) {
            IA.log("WARNING: WildbookIAM.sendMediaAssets() could not get list of acmIds from response: " + rtn);
        } else {
            int numChanged = AcmUtil.rectifyMediaAssetIds(acmList, acmIds);
            IA.log("INFO: WildbookIAM.sendMediaAssets() updated " + numChanged + " MediaAsset(s) acmId(s) via rectifyMediaAssetIds()");
        }
        return rtn;
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
/*
            if (!validForIdentification(ann)) {
                System.out.println("WARNING: IBEISIA.sendAnnotations() skipping invalid " + ann);
                continue;
            }
*/
            if (ann.getMediaAsset() == null) {
                IA.log("WARNING: WildbookIAM.sendAnnotations() unable to find asset for " + ann + "; skipping!");
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
            map.get("annot_species_list").add(ann.getSpecies(myShepherd));
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
            if (rtn.getJSONArray("response").optJSONObject(i) == null) continue;
            ids.add(fromFancyUUID(rtn.getJSONArray("response").getJSONObject(i)));
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


    public String toString() {
        return new ToStringBuilder(this)
                .append("WildbookIAM IA Plugin")
                .toString();
    }

}
