package org.ecocean.ia.plugin;

import javax.servlet.ServletContextEvent;
import org.ecocean.Util;
import org.ecocean.ia.IA;
import org.ecocean.RestClient;
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
System.out.println("WildbookIAM init() called on context " + context);
        return true;
    }

    public void startup(ServletContextEvent sce) {
        //TODO genericize this to be under .ia (with startup hooks for *any* IA plugin)
        //if we dont need identificaiton, no need to prime
        boolean skipIdent = Util.booleanNotFalse(IA.getProperty(context, "IBEISIADisableIdentification"));
        if (!skipIdent && !org.ecocean.StartupWildbook.skipInit(sce, "PRIMEIA")) prime(context);
    }


    public void prime(String context) {
        primed = false;
        if (!isEnabled()) return;
/*
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
*/
    }


    public JSONArray apiGetJSONArray(String urlSuffix) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
        URL u = IBEISIA.iaURL(context, urlSuffix);
        JSONObject rtn = RestClient.get(u);
        if ((rtn == null) || (rtn.optJSONObject("status") == null) || (rtn.optJSONArray("response") == null) || !rtn.getJSONObject("status").optBoolean("success", false)) {
            System.out.println("WARNING: WildbookIAM.apiGetJSONArray(" + urlSuffix + ") could not parse " + rtn);
            return null;
        }
        return rtn.getJSONArray("response");
    }

}
