
package org.ecocean;

import javax.servlet.ServletContext;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.concurrent.ConcurrentHashMap;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

public class AnnotationLite {
    /*
        should imageId and individualId be stored as UUID?  it is 16 bytes vs 36 (string)... but is it slower to convert?  TODO

        also note:  we dont (seem to) need imageId, bbox, or theta, as they are available via the ann already
    */
    //private String imageId;
    private String individualId = null;
    //private int[] bbox;
    private int taxonomyId = -1;
    //private double theta;
    private Boolean validForIdentification = null;

/*
    note: taxonomyList is not atomic, and there is no concurrent list (impossible)
      but hopefully this will suffice, since most of the *additive* behavior will be in the tomcat thread
      and the list will only grow?

    might be better to have something like ConcurrentHashMap with int as key, but then this is tricky to prevent
    race conditions on what the "next" key would be during simultaneous inserts...  must be some kind of atomic
    *and* concurrent increment out there....  perhaps:
        https://docs.oracle.com/javase/8/docs/api/java/util/concurrent/atomic/AtomicInteger.html
*/
    private static List<String> taxonomyList = new ArrayList<String>();
    private static ConcurrentHashMap<String,AnnotationLite> cache = new ConcurrentHashMap<String,AnnotationLite>();
    private static final String CACHE_FILEPATH = "WEB-INF/AnnotationLiteCache.json";  //will be relative to data_dir

    private static ScheduledExecutorService runningSES = null;
    private static ScheduledFuture runningSF = null;

    public AnnotationLite() {}

/*
    public AnnotationLite(Annotation ann) {
        this();
        if (ann != null) {
            if (ann.getMediaAsset() != null) this.setImageId(ann.getMediaAsset().getAcmId());
            this.setBbox(ann.getBbox());
            this.setTheta(ann.getTheta());
        }
    }
    public AnnotationLite(Annotation ann, String indivId) {
        this(ann);
        this.setIndividualId(indivId);
    }
    public AnnotationLite(Annotation ann, String indivId, String tax) {
        this(ann);
        this.setIndividualId(indivId);
        this.setTaxonomy(tax);
    }
*/
    public AnnotationLite(String indivId) {
        this();
        this.setIndividualId(indivId);
    }
    public AnnotationLite(String indivId, String tax) {
        this();
        this.setIndividualId(indivId);
        this.setTaxonomy(tax);
    }
    public AnnotationLite(boolean valid) {
        this();
        this.setValidForIdentification(valid);
    }

    public Boolean getValidForIdentification() {
        return validForIdentification;
    }
    public void setValidForIdentification(Boolean b) {
        validForIdentification = b;
    }
/*
    public String getImageId() {
        return imageId;
    }
    public void setImageId(String id) {
        imageId = id;
    }


    public int[] getBbox() {
        return bbox;
    }
    public void setBbox(int[] b) {
        bbox = b;
    }

    public double getTheta() {
        return theta;
    }
    public void setTheta(double t) {
        theta = t;
    }
*/

    public String getIndividualId() {
        return individualId;
    }
    public void setIndividualId(String i) {
        individualId = i;
    }

    public String getTaxonomy() {
        if ((taxonomyId < 0) || (taxonomyId >= taxonomyList.size())) return null;
        return taxonomyList.get(taxonomyId);
    }
    public void setTaxonomy(String tax) {
        if (tax == null) return;
        int ind = taxonomyList.indexOf(tax);
        if (ind > -1) {
            taxonomyId = ind;
        } else {
            taxonomyList.add(tax);
            taxonomyId = taxonomyList.size() - 1;
        }
    }
    public int getTaxonomyId() {
        return taxonomyId;
    }
    public void setTaxonomyId(int id) {
        taxonomyId = id;
    }

    public static List<String> getTaxonomyList() {
        return taxonomyList;
    }

    public static AnnotationLite getCache(String id) {
        if (id == null) return null;
        return cache.get(id);
    }
    public static void setCache(String id, AnnotationLite annl) {
        //System.out.println("ALSC[" + id + "]=" + annl);
        if (id == null) return;
        cache.put(id, annl);
    }

    public static ConcurrentHashMap<String, AnnotationLite> getCache() {
        return cache;
    }

    public String toString() {
        return "[" + this.getTaxonomy() + ":" + this.getIndividualId() + ":" + this.getValidForIdentification() + "]";
    }

    public static JSONObject cacheToJSONObject() {
        JSONObject c = new JSONObject();
        c.put("timestamp", System.currentTimeMillis());
        c.put("taxonomyList", new JSONArray(taxonomyList));
        JSONObject jcache = new JSONObject();
        for (String id : cache.keySet()) {
            JSONArray jarr = new JSONArray();
            jarr.put(cache.get(id).getValidForIdentification());
            jarr.put(cache.get(id).getTaxonomyId());
            jarr.put(cache.get(id).getIndividualId());
            jcache.put(id, jarr);
        }
        c.put("cache", jcache);
        return c;
    }

    public static void cacheWrite(String filepath) throws IOException {
        Util.writeToFile(cacheToJSONObject().toString(), filepath);
    }
    public static void cacheRead(String filepath) throws IOException {
        long t = System.currentTimeMillis();
        String cont = Util.readFromFile(filepath);
        JSONObject jcont = Util.stringToJSONObject(cont);
        if (jcont == null) {
            System.out.println("ERROR: AnnotationLite.cacheRead() from " + filepath + " could not parse contents");
            return;
        }
        System.out.println("INFO: AnnotationLite.cacheRead() from " + filepath + " timestamp=" + jcont.optLong("timestamp"));
        JSONObject jcache = jcont.optJSONObject("cache");
        if ((jcache == null) || (jcache.length() < 1)) {
            System.out.println("ERROR: AnnotationLite.cacheRead() from " + filepath + " has empty .cache; fail!");
            return;
        }
        JSONArray tlist = jcont.optJSONArray("taxonomyList");
        if (tlist == null) {
            System.out.println("WARNING: AnnotationLite.cacheRead() from " + filepath + " has no taxonomyList");
        } else {
            taxonomyList = new ArrayList<String>();
            for (int i = 0 ; i < tlist.length() ; i++) {
                taxonomyList.add(tlist.optString(i, null));  //we add nulls too (snh,flw) to maintain offset
            }
        }
        //i guess??? we should zero out this cache when reading
        cache = new ConcurrentHashMap<String,AnnotationLite>();
        Iterator it = jcache.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            JSONArray arr = jcache.optJSONArray(key);
            if (arr == null) continue;
            Boolean valid = null;
            if (arr.isNull(0)) {
                valid = null;
            } else {
                try {
                    valid = arr.getBoolean(0);
                } catch (JSONException ex) {
                    System.out.println("WARNING: failed boolean at 0 on " + arr + " => " + ex.toString());
                }
            }
            int tId = arr.optInt(1, -99);
            String iId = arr.optString(2, null);
            AnnotationLite annl = new AnnotationLite();
            if ((tId >= 0) && (tId < taxonomyList.size())) {
                annl.setTaxonomyId(tId);
            } else {
                annl.setTaxonomyId(-1);
            }
            annl.setIndividualId(iId);
            annl.setValidForIdentification(valid);
            cache.put(key, annl);
        }
        System.out.println("INFO: AnnotationLite.cacheRead() from " + filepath + " complete with " + cache.size() + " objects in " + (System.currentTimeMillis() - t) + "ms");
    }


    //this reads the cache from disk *and* starts a thread for saving upon shutdown, oy
    public static void startup(final ServletContext sContext, final String context) {
        try {
            cacheRead(CommonConfiguration.getDataDirectory(sContext, context) + "/" + CACHE_FILEPATH);
        } catch (IOException ex) {
            System.out.println("WARNING: AnnotationLite.startup() could not read AnnotationLite cache json -> " + ex.toString());
        }

        runningSES = Executors.newScheduledThreadPool(1);
        runningSF = runningSES.scheduleWithFixedDelay(new Runnable() {
            public void run() {
                //NOOP
            }
        },
        10,  //initial delay
        10,  //period delay *after* execution finishes
        TimeUnit.SECONDS);
        System.out.println("AnnotationLite.startup() ---- about to awaitTermination() ----");
        try {
            runningSES.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AnnotationLite.startup() thread interrupted -- " + ex.toString());
        }
        System.out.println("==== AnnotationLite.startup() schedExec.shutdown() called, apparently");
    }


    //this is called during shutdown via StartupWildbook
    public static void cleanup(ServletContext sContext, String context) {
        long t = System.currentTimeMillis();
        Util.mark("AnnotationLite.cleanup() begun", t);

        try {
            cacheWrite(CommonConfiguration.getDataDirectory(sContext, context) + "/" + CACHE_FILEPATH);
        } catch (IOException ex) {
            System.out.println("WARNING: AnnotationLite.cleanup() could not write cache json -> " + ex.toString());
        }

        runningSES.shutdown();
        try {
            if (runningSES.awaitTermination(20, TimeUnit.SECONDS)) {
                runningSES.shutdownNow();
                if (runningSES.awaitTermination(20, TimeUnit.SECONDS)) {
                    System.out.println("!!! AnnotationLite.cleanup() -- ExecutorService did not terminate");
                }
            }
        } catch (InterruptedException ie) {
            runningSES.shutdownNow();
            Thread.currentThread().interrupt();
        }
        runningSF.cancel(true);
        Util.mark("AnnotationLite.cleanup() complete", t);
    }

}
