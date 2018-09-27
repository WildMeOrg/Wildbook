package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ia.IA;
import org.ecocean.RestClient;
import org.ecocean.media.MediaAsset;
import org.ecocean.Annotation;
import java.util.List;
import java.util.ArrayList;
import java.net.URL;
import java.net.MalformedURLException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import org.json.JSONObject;
import org.json.JSONArray;

//NOTE!  this steals **a lot** from IBEISIA right now. eventually lets move it all here and kill that off!
import org.ecocean.identity.IBEISIA;

/*
    Wildbook Image Analysis Module (IAM)

    Initial stab at "plugin architecture" for "Image Analysis"

*/
public class WildbookIAM extends IAPlugin {
    private boolean primed = false;
    private String context = null;

    public WildbookIAM() {
        super();
    }
    public WildbookIAM(String context) {
        super(context);
    }

    public boolean isEnabled() {
        return true;  //FIXME
    }

    public boolean init(String context) {
        this.context = context;
        IA.log("WildbookIAM init() called on context " + context);
        return true;
    }

    public void startup(ServletContextEvent sce) {
        //TODO genericize this to be under .ia (with startup hooks for *any* IA plugin)
        //if we dont need identificaiton, no need to prime
        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context, "IBEISIADisableIdentification"));
        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime();
    }


    public void prime() {
        IA.log("INFO: WildbookIAM.prime(" + this.context + ") called");
        primed = false;
        if (!isEnabled() || true) return;

        final List<String> iaAnnotIds = iaAnnotationIds();
        final List<String> iaImageIds = iaImageIds();

        Runnable r = new Runnable() {
            public void run() {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("WildbookIAM.prime");
                myShepherd.beginDBTransaction();
                ArrayList<Annotation> exemAnns = Annotation.getExemplars(myShepherd);
                ArrayList<Annotation> sendAnns = new ArrayList<Annotation>();
                ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
                for (Annotation ann : exemAnns) {
                    if (iaAnnotIds.contains(ann.getAcmId())) continue;  //no need to send
                    sendAnns.add(ann);
                    MediaAsset ma = ann.getDerivedMediaAsset();
                    if (ma == null) ma = ann.getMediaAsset();
                    if (ma == null) continue;
                    if (iaImageIds.contains(ma.getAcmId())) continue;
                    mas.add(ma);
                }
                IA.log("INFO: WildbookIAM.prime(" + context + ") sending " + sendAnns.size() + " annots (of " + exemAnns.size() + ") and " + mas.size() + " images");
                try {
                    sendMediaAssets(mas);
                    sendAnnotations(sendAnns);
                } catch (Exception ex) {
                    IA.log("ERROR: WildbookIAM.prime() failed due to " + ex.toString());
                    ex.printStackTrace();
                }
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
                primed = true;
                IA.log("INFO: prime(" + context + ") complete");
            }
        };
        new Thread(r).start();
System.out.println(">>>>>> AFTER : " + primed);
    }


    public JSONObject sendMediaAssets(ArrayList<MediaAsset> mas) {
        return null;
    }
    public JSONObject sendAnnotations(ArrayList<Annotation> anns) {
        return null;
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
            IA.log("ERROR: WildbookIAM.iaAnnotationsIds() returning empty; failed due to " + ex.toString());
        }
        if (jids != null) {
            for (int i = 0 ; i < jids.length() ; i++) {
                if (jids.optJSONObject(i) != null) ids.add(fromFancyUUID(jids.getJSONObject(i)));
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
            for (int i = 0 ; i < jids.length() ; i++) {
                if (jids.optJSONObject(i) != null) ids.add(fromFancyUUID(jids.getJSONObject(i)));
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


}
