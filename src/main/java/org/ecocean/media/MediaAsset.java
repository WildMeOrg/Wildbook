package org.ecocean.media;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.activation.MimeType;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.AccessControl;
import org.ecocean.Annotation;
import org.ecocean.CommonConfiguration;
import org.ecocean.Encounter;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.IAJsonProperties;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;
import org.ecocean.LabeledKeyword;
import org.ecocean.Occurrence;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.Util;
import org.joda.time.DateTime;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * MediaAsset describes a photo or video that can be displayed or used for processing and analysis.
 */
public class MediaAsset implements java.io.Serializable {
    static final long serialVersionUID = 8844223450447974780L;
    protected int id = MediaAssetFactory.NOT_SAVED;

    protected String uuid = null;

    protected AssetStore store;
    protected String parametersAsString;
    protected JSONObject parameters;

    protected Occurrence occurrence;

    protected Integer parentId;

    protected long revision;

    protected AccessControl accessControl = null;

    protected JSONObject derivationMethod = null;

    protected MediaAssetMetadata metadata = null;

    protected ArrayList<String> labels;

    protected ArrayList<Feature> features;

    protected ArrayList<Keyword> keywords;

    protected String hashCode;
    protected String contentHash; // see Util.fileContentHash()

    protected String detectionStatus;
    protected String identificationStatus;

    protected Double userLatitude;
    protected Double userLongitude;

    protected DateTime userDateTime;

    // Variables used in the Survey, SurveyTrack, Path, Location model

    private String correspondingSurveyTrackID;
    private String correspondingSurveyID;
    private String acmId;

    private Boolean validImageForIA;

    /**
     * To be called by AssetStore factory method.
     */

    public MediaAsset(final AssetStore store, final JSONObject params) {
        // this(store, params, null);
        this(MediaAssetFactory.NOT_SAVED, store, params);
    }

    public MediaAsset(final int id, final AssetStore store, final JSONObject params) {
        this.id = id;
        this.setUUID();
        this.store = store;
        this.parameters = params;
        if (params != null) this.parametersAsString = params.toString();
        this.setRevision();
        this.setHashCode();
    }

    public AccessControl getAccessControl() {
        return accessControl;
    }

    public void setAccessControl(AccessControl ac) {
        accessControl = ac;
    }

    public void setAccessControl(HttpServletRequest request) {
        this.setAccessControl(new AccessControl(request));
    }

    public void setAcmId(String id) {
        this.acmId = id;
    }

    public String getAcmId() {
        return this.acmId;
    }

    public boolean hasAcmId() {
        return (null != this.acmId);
    }

    private URL getUrl(final AssetStore store, final Path path) {
        if (store == null) {
            return null;
        }
        return null; // store.webPath(path);
    }

    private String getUrlString(final URL url) {
        if (url == null) {
            return null;
        }
        return url.toExternalForm();
    }

    public int getId() {
        return id;
    }

    public void setId(int i) {
        id = i;
    }

    public Occurrence getOccurrence() {
        return this.occurrence;
    }

    public String getOccurrenceID() {
        if (this.occurrence == null) return null;
        return this.occurrence.getOccurrenceID();
    }

    public void setOccurrence(Occurrence occ) {
        this.occurrence = occ;
    }

    public void setCorrespondingSurveyTrackID(String id) {
        if (id != null && !id.equals("")) {
            correspondingSurveyTrackID = id;
        }
    }

    public String getCorrespondingSurveyTrackID() {
        if (correspondingSurveyTrackID != null) {
            return correspondingSurveyTrackID;
        }
        return null;
    }

    public void setCorrespondingSurveyID(String id) {
        if (id != null && !id.equals("")) {
            correspondingSurveyID = id;
        }
    }

    public String getCorrespondingSurveyID() {
        if (correspondingSurveyID != null) {
            return correspondingSurveyID;
        }
        return null;
    }

    public String getDetectionStatus() {
        return this.detectionStatus;
    }

    public void setDetectionStatus(String status) {
        this.detectionStatus = status;
    }

    public String getIdentificationStatus() {
        return this.identificationStatus;
    }

    public void setIdentificationStatus(String status) {
        this.identificationStatus = status;
    }

    // this is for Annotation mostly?  provides are reproducible uuid based on the MediaAsset id
    public String getUUID() {
        if (uuid != null) return uuid;
        // UUID v3 seems to take an arbitrary bytearray in, so we construct one that is basically "Ma____" where "____" is the int id
        return generateUUIDFromId();
    }

    public void setUUID(String u) {
        uuid = u;
    }

    /* note: this is used for *new* MediaAssets (via constructor), so we want it to *always* give us something.
       this we try to get a value no matter what.  in 99% of the cases, a new MediaAsset will have id = -1, so generateUUIDv3() will fail.
       thus this essentially will almost always use a v4 uuid (random).  so be it! */
    private void setUUID() {
        uuid = this.generateUUIDFromId();
        if (uuid == null) uuid = Util.generateUUID();
    }

    // note this function will not allow "invalid" (< 0) ids... so see above for hack for new MediaAssets
    public String generateUUIDFromId() {
        if (this.id == MediaAssetFactory.NOT_SAVED) return null;
        if (this.id < 0) return null;
        return this.generateUUIDv3(this.id, (byte)77, (byte)97);
    }

    public static String generateUUIDv3(int id, byte b1, byte b2) {
        if (id == MediaAssetFactory.NOT_SAVED) return null;
        if (id < 0) return null;
        byte[] b = new byte[6];
        b[0] = b1;
        b[1] = b2;
        b[2] = (byte)(id >> 24);
        b[3] = (byte)(id >> 16);
        b[4] = (byte)(id >> 8);
        b[5] = (byte)(id >> 0);
        return UUID.nameUUIDFromBytes(b).toString();
    }

    public AssetStore getStore() {
        return store;
    }

    public Integer getParentId() {
        return parentId;
    }

    public void setParentId(Integer pid) {
        parentId = pid;
    }

    public MediaAsset getParentRoot(Shepherd myShepherd) {
        MediaAsset par = this.getParent(myShepherd);

        if (par == null) return this; // reached the root!
        return par.getParentRoot(myShepherd);
    }

    // returns null if no parent
    public MediaAsset getParent(Shepherd myShepherd) {
        Integer pid = this.getParentId();

        if (pid == null) return null;
        return MediaAssetFactory.load(pid, myShepherd);
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        // System.out.println("NOTE: getParameters() on " + this + " was null, so trying to get from parametersAsString()");
        JSONObject j = Util.stringToJSONObject(parametersAsString);
        parameters = j;
        return j;
    }

    public void setParameters(JSONObject p) {
        if (p == null) {
            System.out.println("WARNING: attempted to set null parameters on " + this +
                "; ignoring");
            return;
        }
        parameters = p;
        parametersAsString = p.toString();
    }

    ///note: really the only place that should call getParametersAsString or setParametersAsString is datanucleus...
    ///  always use getParameters() and setParameters() instead!
    public String getParametersAsString() {
        if (parametersAsString != null) return parametersAsString;
        if (parameters == null) return null;
        parametersAsString = parameters.toString();
        return parametersAsString;
    }

    public void setParametersAsString(String p) {
        if (p == null) {
            System.out.println("WARNING: attempted to set null parametersAsString on " + this +
                "; ignoring");
            return;
        }
        parametersAsString = p;
        // now we also set parameters as the JSONObject (or try)
        JSONObject j = Util.stringToJSONObject(p);
        if (j != null) parameters = j;
    }

    public JSONObject getDerivationMethod() {
        return derivationMethod;
    }

    public void setDerivationMethod(JSONObject dm) {
        derivationMethod = dm;
    }

    public void addDerivationMethod(String k, Object val) {
        if (derivationMethod == null) derivationMethod = new JSONObject();
        derivationMethod.put(k, val);
    }

    public String getDerivationMethodAsString() {
        if (derivationMethod == null) return null;
        return derivationMethod.toString();
    }

    public void setDerivationMethodAsString(String d) {
        if (d == null) {
            derivationMethod = null;
            return;
        }
        try {
            derivationMethod = new JSONObject(d);
        } catch (JSONException je) {
            System.out.println(this + " -- error parsing derivation json string (" + d + "): " +
                je.toString());
            derivationMethod = null;
        }
    }

    public long setRevision() {
        this.revision = System.currentTimeMillis();
        return this.revision;
    }

    public String setHashCode() {
        if (store == null) return null;
        this.hashCode = store.hashCode(getParameters());
        // System.out.println("hashCode on " + this + " = " + this.hashCode);
        return this.hashCode;
    }

    // this is store-specific, and null should be interpreted to mean "i guess i dont really have one"
    // in some cases, this might be some sort of unique-ish identifier (e.g. youtube id), so ymmv
    public String getFilename() {
        if (store == null) return null;
        return store.getFilename(this);
    }

    // "user-provided" (fancy, may have utf8 etc) displayable filename
    public String getUserFilename() {
        if (store == null) return null;
        return store.getUserFilename(this);
    }

    public ArrayList<String> getLabels() {
        return labels;
    }

    public void setLabels(ArrayList<String> l) {
        labels = l;
    }

    public void addLabel(String s) {
        if (labels == null) labels = new ArrayList<String>();
        if (!labels.contains(s)) {
            ArrayList<String> dup = new ArrayList<String>(labels);
            dup.add(s);
            labels = dup;
        }
    }

    public void removeLabel(String s) {
        if (labels == null) return;
        ArrayList<String> dup = new ArrayList<String>(labels);
        dup.remove(s);
        labels = dup;
    }

    public boolean hasLabel(String s) {
        if (labels == null) return false;
        return labels.contains(s);
    }

    public ArrayList<Feature> getFeatures() {
        return features;
    }

    public void setFeatures(ArrayList<Feature> f) {
        features = f;
    }

    public void addFeature(Feature f) {
        if (features == null) features = new ArrayList<Feature>();
        if (!features.contains(f)) {
            features.add(f);
            f.asset = this;
        }
    }

    // note: this will outright deletes feature (from db, blame datanucleus), and thus will
    // break the reference from Annotation-Feature that (likely) existed ... oops?
    public void removeFeature(Feature f) {
        if (features == null) return;
        System.out.println("INFO: removeFeature() killing off " + f + " from asset id=" + this.id);
        features.remove(f);
    }

    // kinda sorta really only for Encounter.findAllMediaByFeatureId()
    public boolean hasFeatures(String[] featureIds) {
        if ((features == null) || (features.size() < 1)) return false;
        for (Feature f : features) {
            for (int i = 0; i < featureIds.length; i++) {
                if (f.isType(featureIds[i])) return true; // short-circuit on first match
            }
        }
        return false;
    }

    public String getRotationInfo() {
        if (this.getMetadata() == null) return null;
        HashMap<String, String> orient = this.getMetadata().findRecurse(".*orient.*");
        if (orient == null) return null;
        for (String k : orient.keySet()) {
            if (k.toLowerCase().contains("thumb")) continue; // we skip exif sections with "thumb" in them
            if (orient.get(k).matches(".*90.*")) return orient.get(k);
            if (orient.get(k).matches(".*270.*")) return orient.get(k);
        }
        return null;
    }

    public boolean isRotated90Or270() {
        return (this.getRotationInfo() != null);
    }

    public Path localPath() {
        if (store == null) return null;
        return store.localPath(this);
    }

    public boolean cacheLocal()
    throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, false);
    }

    public boolean cacheLocal(boolean force)
    throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, force);
    }

    // indisputable attributes about the image (e.g. type, dimensions, colorspaces etc)
    // this is (seemingly?) always derived from MediaAssetMetadata, so .. yeah. make sure that is set (see note by getMetadata() )
    public ImageAttributes getImageAttributes() {
        if ((metadata == null) || (metadata.getData() == null)) return null;
        JSONObject attr = metadata.getData().optJSONObject("attributes");
        if (attr == null) return null;
        double w = attr.optDouble("width", -1);
        double h = attr.optDouble("height", -1);
        String type = attr.optString("contentType");
        if ((w < 1) || (h < 1)) return null;
        return new ImageAttributes(w, h, type);
    }

    public double getWidth() {
        ImageAttributes iattr = getImageAttributes();

        if (iattr == null) return 0;
        return iattr.getWidth();
    }

    public double getHeight() {
        ImageAttributes iattr = getImageAttributes();

        if (iattr == null) return 0;
        return iattr.getHeight();
    }

    public void addToMetadata(String key, String value) {
        if (metadata == null) metadata = new MediaAssetMetadata();
        metadata.addDatum(key, value);
    }

    /**
       this function resolves (how???) various difference in "when" this image was taken.  it might use different metadata (in EXIF etc) and/or human-input 
       FOR NOW: we rely first on (a) metadata.attributes.dateTime (as iso8601 string), then (b) crawl metadata.exif for something date-y
     */
    public DateTime getDateTime() {
        if (this.userDateTime != null) return this.userDateTime;
        if (this.store != null) {
            DateTime dt = this.store.getDateTime(this);
            if (dt != null) return dt;
        }
        if (getMetadata() == null) return null;
        String adt = getMetadata().getAttributes().optString("dateTime", null);
        if (adt != null) return DateTime.parse(adt); // lets hope it is in iso8601 format like it should be!
        // meh, gotta find it the hard way then...
        return getMetadata().getDateTime();
    }

    public void setUserDateTime(DateTime dt) {
        this.userDateTime = dt;
    }

    public DateTime getUserDateTime() {
        return this.userDateTime;
    }

    /**
       like getDateTime() this is considered "definitive" -- so it must resolve differences in metadata vs other (e.g. encounter etc) values
     */
    public Double getUserLatitude() {
        return this.userLatitude;
    }

    public Double getLatitude() {
        if (this.userLatitude != null) return this.userLatitude;
        if (getMetadata() == null) return null;
        double lat = getMetadata().getAttributes().optDouble("latitude");
        if (!Double.isNaN(lat)) return lat;
        return getMetadata().getLatitude();
    }

    public void setUserLatitude(Double lat) {
        this.userLatitude = lat;
    }

    public Double getUserLongitude() {
        return this.userLongitude;
    }

    public Double getLongitude() {
        if (this.userLongitude != null) return this.userLongitude;
        if (getMetadata() == null) return null;
        double lon = getMetadata().getAttributes().optDouble("longitude");
        if (!Double.isNaN(lon)) return lon;
        return getMetadata().getLongitude();
    }

    public void setUserLongitude(Double lon) {
        this.userLongitude = lon;
    }

    // note: default behavior will add this to the features on this MediaAsset -- can pass false to disable
    public Feature generateUnityFeature() {
        return generateUnityFeature(true);
    }

    public Feature generateUnityFeature(boolean addToMediaAsset) {
        Feature f = new Feature();

        if (addToMediaAsset) this.addFeature(f);
        return f;
    }

    // if unity feature is appropriate, generates that; otherwise does a boundingBox one
    // 'params' is extra params to use, and can be null
    public Feature generateFeatureFromBbox(double w, double h, double x, double y,
        JSONObject params) {
        if (params == null) params = new JSONObject();
        params.put("width", w);
        params.put("height", h);
        params.put("x", x);
        params.put("y", y);
        Feature f = new Feature("org.ecocean.boundingBox", params);
        this.addFeature(f);
        return f;
    }

    public ArrayList<Annotation> getAnnotations() {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();

        if ((this.getFeatures() == null) || (this.getFeatures().size() < 1)) return anns;
        for (Feature f : this.getFeatures()) {
            if (f.getAnnotation() != null) anns.add(f.getAnnotation());
        }
        return anns;
    }

    public boolean hasAnnotations() {
        return (getAnnotations().size() > 0);
    }

    public List<Taxonomy> getTaxonomies(Shepherd myShepherd) {
        Set<Taxonomy> taxis = new HashSet<Taxonomy>();

        for (Annotation ann : getAnnotations()) {
            Taxonomy taxy = ann.getTaxonomy(myShepherd);
            taxis.add(taxy);
        }
        return new ArrayList(taxis);
    }

    public Taxonomy getTaxonomy(Shepherd myShepherd) {
        for (Annotation ann : getAnnotations()) {
            Taxonomy taxy = ann.getTaxonomy(myShepherd);
            if (taxy != null) return taxy;
        }
        return null;
    }

    public List<Annotation> getAnnotationsSortedPositionally() {
        List<Annotation> ord = new ArrayList<Annotation>(this.getAnnotations());

        if (Util.collectionSize(ord) < 2) return ord; // no sorting necessary
        Collections.sort(ord, new AnnotationPositionalComparator());
        return ord;
    }

    class AnnotationPositionalComparator implements Comparator<Annotation> {
        @Override public int compare(Annotation annA, Annotation annB) {
            return annA.comparePositional(annB);
        }
    }


    /**
     * Return a full web-accessible url to the asset, or null if the asset is not web-accessible. NOTE: now you should *almost always* use .safeURL()
     * to return something to a user -- this will hide original files when necessary
     */
    public URL webURL() {
        if (store == null) {
            System.out.println("MediaAsset " + this.getUUID() + " has no store!");
            return null;
        }
        try {
            int i = ((store.getUsage() ==
                null) ? -1 : store.getUsage().indexOf("PLACEHOLDERHACK:"));
            if (i == 0) {
                String localURL = store.getUsage().substring(16);
                return new URL(localURL);
            }
        } catch (java.net.MalformedURLException ex) {}
        return store.webURL(this);
    }

    // the primary purpose here is to mask (i.e. never send) the original (uploaded) image file.
    // right now "master" labelled image is used, if available, otherwise children are chosen by allChildTypes() order....
    public URL safeURL(Shepherd myShepherd, HttpServletRequest request, String bestType) {
        MediaAsset ma = bestSafeAsset(myShepherd, request, bestType);

        if (ma == null) return null;
        return ma.webURL();
    }

    public URL safeURL(Shepherd myShepherd, HttpServletRequest request) {
        return safeURL(myShepherd, request, null);
    }

    // this assumes you weakest privileges
    public URL safeURL(Shepherd myShepherd) {
        return safeURL(myShepherd, null);
    }

    public URL safeURL(HttpServletRequest request) {
        String context = "context0";

        if (request != null) context = ServletUtilities.getContext(request); // kinda rough, but....
        // the throw-away Shepherd object is [mostly!] ok here since we arent returning the MediaAsset it is used to find
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAsset.safeURL");
        myShepherd.beginDBTransaction();
        URL u = safeURL(myShepherd, request);
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return u;
    }

    public URL safeURL() {
        return safeURL((HttpServletRequest)null);
    }

    public URL containerURLIfPresent() {
        String containerName = CommonConfiguration.getProperty("containerName", "context0");
        URL localURL = store.getConfig().getURL("webroot");

        if (localURL == null) return null;
        String hostname = localURL.getHost();
        if (containerName != null && containerName != "") {
            try {
                System.out.println("Using containerName for MediaAsset URL domain..");
                return new URL(store.webURL(this).getProtocol(), containerName, 80,
                        store.webURL(this).getFile());
            } catch (java.net.MalformedURLException ex) {}
        }
        try {
            return new URL(hostname);
        } catch (java.net.MalformedURLException mue) {}
        return null;
    }

    public MediaAsset bestSafeAsset(Shepherd myShepherd, HttpServletRequest request,
        String bestType) {
        if (store == null) return null;
        if (bestType == null) bestType = "master";
        // note, this next line means bestType may get bumped *up* for anon user
        if (AccessControl.isAnonymous(request)) bestType = "mid";
        if (store instanceof URLAssetStore) bestType = "original"; // this is cuz it is assumed to be a "public" url

        // hack for flukebook
        bestType = "master";
        // System.out.println("bestSafeAsset: ma #"+getId()+" has bestType "+bestType);
        // gotta consider that wre are the best!
        if (this.hasLabel("_" + bestType)) return this;
        // if we are a child asset, we need to find our parent then find best from there!
        MediaAsset top = this; // assume we are the parent-est
        if (parentId != null) {
            top = MediaAssetFactory.load(parentId, myShepherd);
            if (top == null)
                throw new RuntimeException("bestSafeAsset() failed to find parent on " + this);
            if (!top.hasLabel("_original")) {
                // Commented out below because it prints 5k lines at a time when loading an occurrence (!?!?)
                // System.out.println("INFO: " + this + " had a non-_original parent of " + top + "; so using this");
                return this; // we stick with this cuz we are kinda at a dead end
            }
        }
        boolean gotBest = false;
        List<String> types = store.allChildTypes(); // note: do we need to care that top may have changed stores????
        for (String t : types) {
            if (t.equals(bestType)) gotBest = true;
            else gotBest = false;
            if (!gotBest) continue; // skip over any "better" types until we get to best we can use
            // System.out.println("   ....  ??? do we have a " + t);
            // now try to see if we have one!
            ArrayList<MediaAsset> kids = top.findChildrenByLabel(myShepherd, "_" + t);
            if ((kids != null) && (kids.size() > 0)) {
                MediaAsset kid = kids.get(0);
                return kid;
            } ///not sure how to pick if we have more than one!  "probably rare" case anyway....
        }
        return null; // got nothing!  :(
    }

    public MediaAsset bestSafeAsset(Shepherd myShepherd, HttpServletRequest request) {
        return bestSafeAsset(myShepherd, request, null);
    }

    public MediaAsset bestSafeAsset(Shepherd myShepherd) {
        return bestSafeAsset(myShepherd, null);
    }

    // this takes contents of this MediaAsset and copies it to the target (note MediaAssets must exist with sufficient params already)
    // please note this uses *source* AssetStore for copying, which can/will affect how, for example, credentials in aws s3 are chosen.
    // for tighter control of this, you can call copyAsset() (or copyAssetAny()?) directly on desired store
    public void copyAssetTo(MediaAsset targetMA)
    throws IOException {
        if (store == null) throw new IOException("copyAssetTo(): store is null on " + this);
        store.copyAssetAny(this, targetMA);
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return sanitizeJson(request, jobj, true);
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj, boolean fullAccess)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        String context = ServletUtilities.getContext(request);

        org.datanucleus.api.rest.orgjson.JSONObject obj = null;
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MediaAsset.santizeJSON");
        myShepherd.beginDBTransaction();
        try {
            obj = sanitizeJson(request, jobj, true, myShepherd);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();
        }
        return obj;
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj, Shepherd myShepherd)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return sanitizeJson(request, jobj, true, myShepherd);
    }

    // fullAccess just gets cascaded down from Encounter -> Annotation -> us... not sure if it should win vs security(request)
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        org.datanucleus.api.rest.orgjson.JSONObject jobj, boolean fullAccess, Shepherd myShepherd)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        jobj.put("id", this.getId());
        jobj.put("acmId", this.getAcmId());
        jobj.put("detectionStatus", this.getDetectionStatus());
        jobj.remove("parametersAsString");
        // jobj.put("guid", "http://" + CommonConfiguration.getURLLocation(request) + "/api/org.ecocean.media.MediaAsset/" + id);

        HashMap<String, String> s = new HashMap<String, String>();
        s.put("type", store.getType().toString());
        jobj.put("store", s);

        String context = ServletUtilities.getContext(request);
        // Shepherd myShepherd = new Shepherd(context);
        // myShepherd.setAction("MediaAsset.class_1");
        // myShepherd.beginDBTransaction();
        ArrayList<Feature> fts = getFeatures();
        if ((fts != null) && (fts.size() > 0)) {
            org.datanucleus.api.rest.orgjson.JSONArray jarr =
                new org.datanucleus.api.rest.orgjson.JSONArray();
            for (int i = 0; i < fts.size(); i++) {
                org.datanucleus.api.rest.orgjson.JSONObject jf =
                    new org.datanucleus.api.rest.orgjson.JSONObject();
                Feature ft = fts.get(i);
                jf.put("id", ft.getId());
                try { // for some reason(?) this will get a jdo error for "row not found".  why???  anyhow, we catch it
                    jf.put("type", ft.getType());
                } catch (Exception ex) {
                    jf.put("type", "unknown");
                    System.out.println("ERROR: MediaAsset.sanitizeJson() on " + this.toString() +
                        " threw " + ex.toString());
                }
                JSONObject p = ft.getParameters();
                if (p != null) jf.put("parameters", Util.toggleJSONObject(p));
                // we add this stuff for gallery/image to link to co-occurring indiv/enc
                Annotation ann = ft.getAnnotation();
                if (ann != null) {
                    jf.put("annotationAcmId", ann.getAcmId());
                    jf.put("iaClass", ann.getIAClass());
                    jf.put("annotationId", ann.getId());
                    jf.put("annotationIsOfInterest", ann.getIsOfInterest());
                    Encounter enc = ann.findEncounter(myShepherd);
                    if (enc != null) {
                        jf.put("encounterId", enc.getCatalogNumber());
                        if (enc.hasMarkedIndividual()) {
                            jf.put("individualId", enc.getIndividualID());
                            String displayName = enc.getIndividual().getDisplayName(request,
                                myShepherd);
                            if (!Util.stringExists(displayName))
                                displayName = enc.getIndividualID();
                            jf.put("displayName", displayName);
                        }
                        if (enc.getGenus() != null && enc.getSpecificEpithet() != null) {
                            jf.put("genus", enc.getGenus());
                            jf.put("specificEpithet", enc.getSpecificEpithet());
                        }
                    }
                }
                jarr.put(jf);
            }
            jobj.put("features", jarr);
        }
        if ((getMetadata() != null) && (getMetadata().getData() != null) &&
            (getMetadata().getData().opt("attributes") != null)) {
            // jobj.put("metadata", new org.datanucleus.api.rest.orgjson.JSONObject(getMetadata().getData().getJSONObject("attributes").toString()));
            jobj.put("metadata",
                Util.toggleJSONObject(getMetadata().getData().getJSONObject("attributes")));
        }
        DateTime dt = getDateTime();
        if (dt != null) jobj.put("dateTime", dt.toString()); // DateTime.toString() gives iso8601, noice!

        // note? warning? i guess this will traverse... gulp?
        URL u = safeURL(myShepherd, request);
        if (u != null) jobj.put("url", u.toString());
        ArrayList<MediaAsset> kids = null;
        if (!jobj.optBoolean("_skipChildren", false)) kids = this.findChildren(myShepherd);
        // myShepherd.rollbackDBTransaction();
        if ((kids != null) && (kids.size() > 0)) {
            org.datanucleus.api.rest.orgjson.JSONArray k =
                new org.datanucleus.api.rest.orgjson.JSONArray();
            for (MediaAsset kid : kids) {
                k.put(kid.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(),
                    fullAccess, myShepherd));
            }
            jobj.put("children", k);
        }
        if (fullAccess) {
            jobj.put("userLatitude", this.getLatitude());
            jobj.put("userLongitude", this.getLongitude());
            jobj.put("userDateTime", this.getUserDateTime());
            jobj.put("filename", this.getFilename()); // this can "vary" depending on store type
            jobj.put("userFilename", this.getUserFilename());
        }
        jobj.put("occurrenceID", this.getOccurrenceID());
        if (this.getLabels() != null) jobj.put("labels", this.getLabels());
        if ((this.getKeywords() != null) && (this.getKeywords().size() > 0)) {
            JSONArray ka = new JSONArray();
            for (Keyword kw : this.getKeywords()) {
                JSONObject kj = new JSONObject();
                kj.put("indexname", kw.getIndexname());
                kj.put("readableName", kw.getReadableName());
                kj.put("displayName", kw.getDisplayName());
                if (kw instanceof LabeledKeyword) {
                    LabeledKeyword lkw = (LabeledKeyword)kw;
                    kj.put("label", lkw.getLabel());
                }
                ka.put(kj);
            }
            jobj.put("keywords", new org.datanucleus.api.rest.orgjson.JSONArray(ka.toString()));
        }
        // myShepherd.rollbackDBTransaction();
        // myShepherd.closeDBTransaction();

        return jobj;
    }

    // carefree, safe json version
    public JSONObject toSimpleJSONObject() {
        JSONObject j = new JSONObject();

        j.put("id", getId());
        j.put("uuid", getUUID());
        j.put("url", safeURL());
        if ((getMetadata() != null) && (getMetadata().getData() != null) &&
            (getMetadata().getData().opt("attributes") != null)) {
            j.put("attributes", getMetadata().getData().opt("attributes"));
        }
        return j;
    }

    public String toString() {
        List<String> kwNames = getKeywordNames();
        String kwString = (kwNames == null) ? "None" : Util.joinStrings(kwNames);

        return new ToStringBuilder(this)
                   .append("id", id)
                   .append("parent", parentId)
                   .append("labels", ((labels == null) ? "" : labels.toString()))
                   .append("store", store.toString())
                   .append("keywords", kwString)
                   .toString();
    }

    public void copyIn(File file)
    throws IOException {
        if (store == null) throw new IOException("copyIn(): store is null on " + this);
        store.copyIn(file, getParameters(), false);
    }

    public MediaAsset updateChild(String type, HashMap<String, Object> opts)
    throws IOException {
        if (store == null) throw new IOException("store is null on " + this);
        return store.updateChild(this, type, opts);
    }

    public MediaAsset updateChild(String type)
    throws IOException {
        return updateChild(type, null);
    }

    // this will simply recreate the resultant target file for the child (called on parent)
    public boolean redoChild(MediaAsset child)
    throws IOException {
        if (child == null) throw new IOException("null child passed");
        if (store == null) throw new IOException("store is null on " + this);
        String type = child.getChildType();
        if (type == null) throw new IOException("child does not have valid type");
        File sourceFile = (this.localPath() == null) ? null : this.localPath().toFile();
        File targetFile = (child.localPath() == null) ? null : child.localPath().toFile();
        if ((sourceFile == null) || (targetFile == null))
            throw new IOException("could not get localPath on source or target");
        boolean ok = store._updateChildLocalWork(this, type, null, sourceFile, targetFile, false);
        System.out.println("INFO: redoChild() on parent=" + this + ", child=" + child + " => " +
            ok);
        return ok;
    }

    public void redoAllChildren(Shepherd myShepherd)
    throws IOException {
        ArrayList<MediaAsset> kids = this.findChildren(myShepherd);

        if (kids == null) return;
        for (MediaAsset kid : kids) {
            this.redoChild(kid);
        }
    }

    public ArrayList<MediaAsset> detachChildren(Shepherd myShepherd, String type)
    throws IOException {
        if (store == null) throw new IOException("store is null on " + this);
        ArrayList<MediaAsset> disposable = this.findChildrenByLabel(myShepherd, "_" + type);
        if ((disposable != null) && (disposable.size() > 0)) {
            for (MediaAsset ma : disposable) {
                ma.setParentId(null);
                ma.addDerivationMethod("detachedFrom", this.getId());
                System.out.println("INFO: detached child " + ma + " from " + this);
            }
        }
        return disposable;
    }

    public boolean hasFamily(Shepherd myShepherd) {
        if (parentId != null) return true; // saves the db call below if unnecessary
        List<MediaAsset> children = findChildren(myShepherd);
        return (children != null && children.size() > 0);
    }

    public ArrayList<MediaAsset> findChildren(Shepherd myShepherd) {
        if (store == null) return null;
        ArrayList<MediaAsset> all = store.findAllChildren(this, myShepherd);
        return all;
    }

    public ArrayList<MediaAsset> findChildrenByLabel(Shepherd myShepherd, String label) {
        ArrayList<MediaAsset> all = this.findChildren(myShepherd);

        if ((all == null) || (all.size() < 1)) return null;
        ArrayList<MediaAsset> matches = new ArrayList<MediaAsset>();
        for (MediaAsset ma : all) {
            if ((ma.getLabels() != null) && ma.getLabels().contains(label)) matches.add(ma);
        }
        return matches;
    }

    // NOTE: these currrently do not recurse.  this makes a big assumption that one only wants children of _original
    // (e.g. on an encounter) and will *probably* need to change in the future.
    public static MediaAsset findOneByLabel(ArrayList<MediaAsset> mas, Shepherd myShepherd,
        String label) {
        ArrayList<MediaAsset> all = findAllByLabel(mas, myShepherd, label, true);

        if ((all == null) || (all.size() < 1)) return null;
        return all.get(0);
    }

    public static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas,
        Shepherd myShepherd, String label) {
        return findAllByLabel(mas, myShepherd, label, false);
    }

    private static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas,
        Shepherd myShepherd, String label, boolean onlyOne) {
        if ((mas == null) || (mas.size() < 1)) return null;
        ArrayList<MediaAsset> found = new ArrayList<MediaAsset>();
        for (MediaAsset ma : mas) {
            if ((ma.getLabels() != null) && ma.getLabels().contains(label)) {
                found.add(ma);
                if (onlyOne) return found;
            }
            ArrayList<MediaAsset> kids = ma.findChildrenByLabel(myShepherd, label);
            if ((kids != null) && (kids.size() > 0)) {
                if (onlyOne) {
                    found.add(kids.get(0));
                    return found;
                } else {
                    found.addAll(kids);
                }
            }
        }
        return found;
    }

    // makes the assumption (rightly so?) can have at most one valid child type
    public String getChildType() {
        if (store == null) return null;
        for (String ct : store.standardChildTypes()) {
            if (this.hasLabel("_" + ct)) return ct;
        }
        return null;
    }

    // creates the "standard" derived children for a MediaAsset (thumb, mid, etc)
    public ArrayList<MediaAsset> updateStandardChildren() {
        if (store == null) return null;
        List<String> types = store.standardChildTypes();
        if ((types == null) || (types.size() < 1)) return null;
        ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
        for (String type : types) {
            System.out.println(">> updateStandardChildren(): type = " + type);
            MediaAsset c = null;
            try {
                c = this.updateChild(type);
            } catch (IOException ex) {
                System.out.println("updateStandardChildren() failed on type=" + type + ", ma=" +
                    this + " with " + ex.toString());
            }
            if (c != null) mas.add(c);
        }
        return mas;
    }

    // as above, but saves them too
    public ArrayList<MediaAsset> updateStandardChildren(Shepherd myShepherd) {
        ArrayList<MediaAsset> mas = updateStandardChildren();

        for (MediaAsset ma : mas) {
            MediaAssetFactory.save(ma, myShepherd);
        }
        return mas;
    }

/*
    >> EXPERIMENTAL <<
    as above, but not only saves the children MediaAssets, but does so in the background.
    this *requires* that the asset has been presisted, as it will re-read it from the db.  this is to insure that the .store is also usable. NOTE: it
       is better to send a huge list of ids here, than iterate over them one at a time, to create a sort of pseudo-queue for large jobs, rather than
       multiple (simultaneous) threads..  ouch!
 */
    public static void updateStandardChildrenBackground(final String context,
        final List<Integer> ids) {
        if ((ids == null) || (ids.size() < 1)) return;
        final String tid = Util.generateUUID().substring(0, 8);
        System.out.println("updateStandardChildrenBackground() [" + tid + "] forking for " +
            ids.size() + " MediaAsset ids >>>>");
        Runnable rn = new Runnable() {
            public void run() {
                Shepherd myShepherd = new Shepherd(context);
                myShepherd.setAction("updateStandardChildrenBackground:" + tid);
                myShepherd.beginDBTransaction();
                int ct = 0;
                for (Integer id : ids) {
                    ct++;
                    MediaAsset ma = MediaAssetFactory.load(id, myShepherd);
                    if (ma == null) continue;
                    ArrayList<MediaAsset> kids = ma.updateStandardChildren(myShepherd);
                    System.out.println("+ [" + ct + "] updateStandardChildrenBackground() [" + tid +
                        "] completed " + kids.size() + " children for id=" + id);
                }
                myShepherd.commitDBTransaction();
                myShepherd.closeDBTransaction();
            }
        };
        new Thread(rn).start();
        System.out.println("updateStandardChildrenBackground() [" + tid + "] out of fork for ct=" +
            ids.size() + " <<<<");
    }

    // convenience, XXX  BUT see not above about sending multiple ids when possible!  XXX
    public static void updateStandardChildrenBackground(final String context, int id) {
        updateStandardChildrenBackground(context, new ArrayList<Integer>(id));
    }

    public void updateStandardChildrenBackground(String context) { // convenience
        updateStandardChildrenBackground(context, this.getId());
    }

    public void setKeywords(ArrayList<Keyword> kws) {
        keywords = kws;
    }

    public ArrayList<Keyword> addKeyword(Keyword k) {
        if (keywords == null) keywords = new ArrayList<Keyword>();
        if (!keywords.contains(k)) keywords.add(k);
        return keywords;
    }

    public int numKeywords() {
        if (keywords == null) return 0;
        return keywords.size();
    }

    public int numKeywordsStrict() {
        if (keywords == null) return 0;
        return getKeywordsStrict().size();
    }

    public int numLabeledKeywords() {
        if (keywords == null) return 0;
        // iterate keywords for labeled keywords
        int numLabeled = 0;
        for (Keyword kw : keywords) {
            if (kw instanceof LabeledKeyword) {
                numLabeled++;
            }
        }
        return numLabeled;
    }

    public Keyword getKeyword(int i) {
        return keywords.get(i);
    }

    public Keyword getKeywordStrict(int i) {
        if (getKeywordsStrict() != null) {
            return getKeywordsStrict().get(i);
        }
        return null;
    }

    public ArrayList<Keyword> getKeywords() {
        return keywords;
    }

    public ArrayList<LabeledKeyword> getLabeledKeywords() {
        if (keywords == null) return null;
        ArrayList<LabeledKeyword> lkws = new ArrayList<LabeledKeyword>();
        for (Keyword kw : keywords) {
            if (kw instanceof LabeledKeyword) {
                lkws.add((LabeledKeyword)kw);
            }
        }
        return lkws;
    }

    public ArrayList<Keyword> getKeywordsStrict() {
        if (keywords == null) return null;
        ArrayList<Keyword> lkws = new ArrayList<Keyword>();
        for (Keyword kw : keywords) {
            if (kw instanceof LabeledKeyword) {} else {
                lkws.add(kw);
            }
        }
        return lkws;
    }

    public String getLabeledKeywordValue(String label) {
        ArrayList<LabeledKeyword> lkws = getLabeledKeywords();

        if (lkws == null || lkws.size() == 0) return null;
        for (LabeledKeyword lkw : lkws) {
            if (lkw.getLabel().equals(label)) return lkw.getValue();
        }
        return null;
    }

    public List<String> getKeywordNames() {
        List<String> names = new ArrayList<String>();

        if (getKeywords() == null) return names;
        for (Keyword kw : getKeywords()) {
            names.add(kw.getDisplayName());
        }
        return names;
    }

    public boolean hasKeywords() {
        return (keywords != null && (keywords.size() > 0));
    }

    public boolean hasKeyword(String keywordName) {
        if (keywords != null) {
            int numKeywords = keywords.size();
            for (int i = 0; i < numKeywords; i++) {
                Keyword kw = keywords.get(i);
                if (kw == null) return false;
                if ((keywordName.equals(kw.getIndexname()) ||
                    keywordName.equals(kw.getDisplayName()))) return true;
            }
        }
        return false;
    }

    public boolean hasKeyword(Keyword key) {
        if (keywords != null) {
            if (keywords.contains(key)) { return true; }
        }
        return false;
    }

    public void removeKeyword(Keyword k) {
        if (keywords != null) {
            if (keywords.contains(k)) keywords.remove(k);
        }
    }

    // if we dont have the Annotation... which kinda sucks but okay
    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd) {
        return toHtmlElement(request, myShepherd, null);
    }

    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd, Annotation ann) {
        if (store == null)
            return "<!-- ERROR: MediaAsset.toHtmlElement() has no .store value for " +
                       this.toString() + " -->";
        return store.mediaAssetToHtmlElement(this, request, myShepherd, ann);
    }

    // piggybacks off metadata, so that must be set first, otherwise it matches corresponding mime type (major/minor), case-insensitive
    // note: for return values, we standardize on all-lowercase. so there.
    public boolean isMimeTypeMajor(String type) {
        if (type == null) return false;
        return type.toLowerCase().equals(this.getMimeTypeMajor());
    }

    public boolean isMimeTypeMinor(String type) {
        if (type == null) return false;
        return type.toLowerCase().equals(this.getMimeTypeMinor());
    }

    public String getMimeTypeMajor() {
        String[] mt = this.getMimeType();

        if ((mt == null) || (mt.length < 1)) return null;
        return mt[0];
    }

    public String getMimeTypeMinor() {
        String[] mt = this.getMimeType();

        if ((mt == null) || (mt.length < 2)) return null;
        return mt[1];
    }

    public String[] getMimeType() {
        if (this.metadata == null) return null;
        String mt = this.metadata.getAttributes().optString("contentType", null); // note: getAttributes always  returns a JSONObject (even if empty)
        if (mt == null) return null;
        return mt.toLowerCase().split("/");
    }

    // note: we are going to assume Metadata "will just be there" so no magical updates. if it is null, it is null.
    // this implies basically that it is set once when the MediaAsset is created, so make sure that happens, *cough*
    public MediaAssetMetadata getMetadata() {
        return metadata;
    }

    public boolean hasMetadata() {
        MediaAssetMetadata data = getMetadata();

        return ((data != null) && (data.getData() != null) &&
                   (data.getData().opt("attributes") != null));
    }

    public void setMetadata(MediaAssetMetadata md) {
        metadata = md;
    }

    public void setMetadata()
    throws IOException {
        setMetadata(updateMetadata());
    }

    public MediaAssetMetadata updateMetadata()
    throws IOException { 
        if (store == null) return null;
        metadata = store.extractMetadata(this);
        return metadata;
    }

    // only gets the "attributes" portion -- which is usually all we need for derived images
    public MediaAssetMetadata updateMinimalMetadata() {
        if (store == null) return null;
        try {
            metadata = store.extractMetadata(this, true); // true means "attributes" only
        } catch (IOException ioe) { // we silently eat IOExceptions, but will return null
            System.out.println("WARNING: updateMinimalMetadata() on " + this + " got " +
                ioe.toString() + "; failed to set");
            return null;
        }
        return metadata;
    }

    // handy cuz we dont need the actual file (if we have these values from elsewhere) and usually the only stuff we "need"
    public MediaAssetMetadata setMinimalMetadata(int width, int height, String contentType) {
        // note, this will overwrite existing "attributes" value if it exists
        if (metadata == null) metadata = new MediaAssetMetadata();
        metadata.getData().put("width", width);
        metadata.getData().put("height", height);
        metadata.getData().put("contentType", contentType);
        return metadata;
    }

    public void refreshIAStatus(Shepherd myShepherd) {
        String s = IBEISIA.parseDetectionStatus(Integer.toString(this.getId()), myShepherd);

        if (s != null) this.setDetectionStatus(s);
        String cumulative = null;
        for (Annotation ann : this.getAnnotations()) {
            s = IBEISIA.parseIdentificationStatus(ann.getId(), myShepherd);
            if ((s != null) && ((cumulative == null) || !s.equals("complete"))) cumulative = s;
        }
        if (cumulative != null) this.setIdentificationStatus(cumulative);
    }

    public JSONObject getIAStatus() {
        JSONObject rtn = new JSONObject();
        JSONObject j = new JSONObject();

        j.put("status", getDetectionStatus());
        rtn.put("detection", j);

        j = new JSONObject();
        j.put("status", getIdentificationStatus());
        rtn.put("identification", j);
        return rtn;
    }

    // takes base64 string and turns to binary content and copies that in as normal
    public void copyInBase64(String b64)
    throws IOException {
        if (b64 == null) throw new IOException("copyInBase64() null string");
        byte[] imgBytes = new byte[100];
        try {
            imgBytes = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("copyInBase64() could not parse: " + ex.toString());
        }
        File file = (this.localPath() !=
            null) ? this.localPath().toFile() : File.createTempFile("b64-" + Util.generateUUID(),
            ".tmp");
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) parentDir.mkdirs();
        FileOutputStream stream = new FileOutputStream(file);
        try {
            stream.write(imgBytes);
        } catch (Exception e) {
            System.out.println("Exception from Writing FileOutputStream with imgBytes");
            e.printStackTrace();
        } finally {
            stream.close();
        }
        if (file.exists()) {
            this.copyIn(file);
        } else {
            throw new IOException("copyInBase64() could not write " + file);
        }
    }

    public boolean isValidChildType(String type) {
        if (store == null) return false;
        return store.isValidChildType(type);
    }

    public List<Task> getRootIATasks(Shepherd myShepherd) { // convenience
        return Task.getRootTasksFor(this, myShepherd);
    }

    public Boolean isValidImageForIA() {
        return validImageForIA;
    }

    public Boolean validateSourceImage() {
        if ("LOCAL".equals(this.getStore().getType().toString())) {
            Path lPath = this.localPath();
            String typeString = null;
            try {
                typeString = Files.probeContentType(lPath);
            } catch (IOException ioe) { ioe.printStackTrace(); }
            try {
                MimeType type = new MimeType(typeString);
                typeString = type.getPrimaryType();
            } catch (Exception e) { e.printStackTrace(); }
            if ("image".equals(typeString)) {
                File imageFile = this.localPath().toFile();
                this.validImageForIA = AssetStore.isValidImage(imageFile);
            } else {
                System.out.println(
                    "WARNING: validateSourceImage was called on a non-image or corrupt MediaAsset with Id: "
                    + this.getId());
                this.validImageForIA = false;
            }
        }
        return isValidImageForIA();
    }

/*
    WB-945 new magick.  this look at our annots (which may *or may not* have been created via detection results) and decide if they need to have new
       encounter(s) made to hold them.  this will be based on their sibling annots on this asset, among other things.

    note: current logic here ignores edge-case where annots may span multiple species.  this is due in part to the fact that deriving species from
       iaClass is a gray area in some wildbooks.  this will need to be a future enhancement.
 */
    public List<Encounter> assignEncounters(Shepherd myShepherd) {
        List<Encounter> newEncs = new ArrayList<Encounter>();
        List<Annotation> annots = this.getAnnotations();
        List<Annotation> trivialAnnots = new ArrayList<Annotation>();

        if (Util.collectionIsEmptyOrNull(annots)) {
            System.out.println("INFO: assignEncounters() finds no annots on " + this);
            return newEncs;
        }
        Set<Encounter> myEncs = new HashSet<Encounter>();
        List<Annotation> needsEncounter = new ArrayList<Annotation>();
        Map<String, Integer> partCt = new HashMap<String, Integer>(); // holds count for each type of part
        int nonPartCt = 0;
        for (Annotation ann : annots) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (enc == null) {
                if (!ann.isTrivial()) needsEncounter.add(ann); // see comment below
            } else {
                myEncs.add(enc);
            }
            if (ann.isTrivial() && (enc != null)) { // i guess it would be weird to have trivial with no enc, but we dont want it!
                trivialAnnots.add(ann);
            } else if (ann.getIAClass() == null) {
                // what do we do here?
                System.out.println("INFO: assignEncounters() has no iaClass for " + ann);
            } else if (ann.getIAClass().indexOf("+") > -1) { // we are a part, i guess?
                partCt.put(ann.getIAClass(), partCt.getOrDefault(ann.getIAClass(), 0) + 1);
            } else { // "non-part" (aka, um, whole? body?)
                nonPartCt++;
            }
        }
        boolean hasDuplicateParts = false;
        for (String part : partCt.keySet()) {
            if (partCt.get(part) > 1) hasDuplicateParts = true;
        }
        // okay, now we try to make sense of what we have......
        if (needsEncounter.size() < 1) return newEncs; // easiest!
        System.out.println("INFO: assignEncounters() needsEncounter ==> " + needsEncounter);

        Encounter whichever = null;
        // if we dont have *any* enc, we create one out of the blue... probably rare edge case
        if (myEncs.isEmpty()) {
            // this assumes we are dealing with all the same species (so makes one enc to clone), based on first annotation
            whichever = new Encounter(); // will be used to attach annots below
            DateTime dt = this.getDateTime();
            if (dt != null) whichever.setDateInMilliseconds(dt.getMillis());
            if (needsEncounter.get(0).getIAClass() != null) {
                try {
                    IAJsonProperties iaJson = new IAJsonProperties();
                    whichever.setTaxonomy(iaJson.taxonomyFromIAClass(needsEncounter.get(
                        0).getIAClass(), myShepherd));
                } catch (Exception ex) {
                    System.out.println(
                        "INFO: assignEncounters() could not load iaJson, so could not deduce taxonomy for "
                        + whichever + " -- " + ex.toString());
                }
            }
            myShepherd.getPM().makePersistent(whichever);
            System.out.println("INFO: assignEncounters() has empty myEncs for " + this.toString() +
                ", so created`whichever=" + whichever);
        } else {
            whichever = myEncs.iterator().next();
        }
        boolean contiguousAnnots = Annotation.areContiguous(annots);
        System.out.println("INFO: assignEncounters() contiguousAnnots = " + contiguousAnnots);
        if ((nonPartCt > 1) || hasDuplicateParts || !contiguousAnnots || (myEncs.size() > 1)) {
            System.out.println(">>>>> assignEncounters() MANY ENCS ; myEncs=" + myEncs);
            /*
                we dont know where to assign needsEncounter annots, so they each get own Encounter
                - if we have any trivialAnnots, we use those up first and bump them out
                - when no trivialAnnots, we duplicate "a random encounter" (aka whichever) and attach there
             */
            for (Annotation ann : needsEncounter) {
                if (trivialAnnots.size() > 0) {
                    Annotation tann = trivialAnnots.remove(0);
                    tann.setMatchAgainst(false);
                    Encounter tenc = tann.findEncounter(myShepherd); // should never be null, so hope you arent here cuz of a npe
                    tenc.replaceAnnotation(tann, ann);
                    tenc.setDWCDateLastModified();
                    tenc.resetDateInMilliseconds();
                    System.out.println(">>>>> ******** [1] assignEncounters() trivial " + tann +
                        " replaced by " + ann + " on " + tenc);
                    if (!newEncs.contains(tenc)) newEncs.add(tenc);
                } else { // new enc based on whichever
                    try {
                        Encounter newEnc = whichever.cloneWithoutAnnotations(myShepherd); // ok, not really random
                        newEnc.addAnnotation(ann);
                        newEnc.setDWCDateAdded();
                        newEnc.setDWCDateLastModified();
                        newEnc.resetDateInMilliseconds();
                        newEnc.setSpecificEpithet(whichever.getSpecificEpithet());
                        newEnc.setGenus(whichever.getGenus());
                        newEnc.setSex(null);
                        myShepherd.getPM().makePersistent(newEnc);
                        System.out.println(">>>>> ******** [1] assignEncounters() cloned " +
                            whichever + " for " + ann + " yielding " + newEnc);
                        if (!newEncs.contains(newEnc)) newEncs.add(newEnc);
                    } catch (Exception ex) {
                        System.out.println("ERROR: assignEncounters() failed to clone " +
                            whichever + " for " + ann + " -> " + ex.toString());
                        ex.printStackTrace();
                    }
                }
            }
        } else { // all needsEncounter annots can live in harmony.  myEncs should only have 1 enc (or 0, if we created whichever)
            System.out.println(">>>>> assignEncounters() ONE ENC ; myEncs=" + myEncs);
            for (Annotation ann : needsEncounter) {
                if (trivialAnnots.size() > 0) { // we have a trivial, lets replace it
                    Annotation tann = trivialAnnots.remove(0);
                    tann.setMatchAgainst(false);
                    Encounter tenc = tann.findEncounter(myShepherd); // i believe(???) this will always be whenever
                    if (tenc != null) {
                        tenc.replaceAnnotation(tann, ann);
                        tenc.setDWCDateLastModified();
                        tenc.resetDateInMilliseconds();
                        System.out.println(">>>>> ******** [2] assignEncounters() trivial " + tann +
                            " replaced by " + ann + " on " + tenc);
                        if (!newEncs.contains(tenc)) newEncs.add(tenc);
                    }
                } else { // no trivials left, so we just throw it on whichever
                    whichever.addAnnotation(ann);
                    whichever.setDWCDateLastModified();
                    whichever.resetDateInMilliseconds();
                    System.out.println(">>>>> ******** [2] assignEncounters() added " + ann +
                        " to " + whichever);
                    if (!newEncs.contains(whichever)) newEncs.add(whichever);
                }
            }
            // after all annots, we *should* only have 1 enc (whichever) in newEncs!!
        }
        System.out.println(">>>>> assignEncounters() EXIT ; size=" + newEncs.size() + " newEncs=" +
            newEncs);
        return newEncs;
    }

    // need these two so we can use things like List.contains()
    public boolean equals(final Object o2) {
        if (o2 == null) return false;
        if (!(o2 instanceof MediaAsset)) return false;
        MediaAsset two = (MediaAsset)o2;
        if ((this.uuid == null) || (two == null) || (two.getUUID() == null)) return false;
        return this.uuid.equals(two.getUUID());
    }

    public int hashCode() {
        if (uuid == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
        return uuid.hashCode();
    }

    public void setIsValidImageForIA(Boolean value) {
        if (value == null) { validImageForIA = null; } else { validImageForIA = value; }
    }
}
