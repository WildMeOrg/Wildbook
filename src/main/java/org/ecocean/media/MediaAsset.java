/*
 * This file is a part of Wildbook.
 * Copyright (C) 2015 WildMe
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Foobar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Wildbook.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.ecocean.media;

import org.ecocean.Occurrence;
import org.ecocean.CommonConfiguration;
import org.ecocean.ImageAttributes;
import org.ecocean.Keyword;
import org.ecocean.Annotation;
import org.ecocean.AccessControl;
import org.ecocean.Shepherd;
import org.ecocean.servlet.ServletUtilities;
import org.ecocean.Util;
import org.ecocean.identity.IdentityServiceLog;
import org.ecocean.identity.IBEISIA;
import org.ecocean.Encounter;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Files;
//import java.time.LocalDateTime;
import org.joda.time.DateTime;
import java.util.Date;
import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import java.util.Set;
import java.util.List;
import java.util.HashMap;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.builder.ToStringBuilder;
import java.util.UUID;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
//import java.io.FileInputStream;
import java.io.FileOutputStream;
import javax.jdo.Query;
import javax.xml.bind.DatatypeConverter;

/*
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.util.Iterator;
*/

/*
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.*;
import com.drew.metadata.exif.ExifSubIFDDirectory;
*/

/**
 * MediaAsset describes a photo or video that can be displayed or used
 * for processing and analysis.
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

    protected String detectionStatus;
    protected String identificationStatus;

    protected Double userLatitude;
    protected Double userLongitude;

    protected DateTime userDateTime;




    //protected MediaAssetType type;
    //protected Integer submitterid;


    //protected Set<String> tags;
    //protected Integer rootId;

    //protected AssetStore thumbStore;
    //protected Path thumbPath;
    //protected Path midPath;

    //private LocalDateTime metaTimestamp;
    //private Double metaLatitude;
    //private Double metaLongitude;


    /**
     * To be called by AssetStore factory method.
     */
/*
    public MediaAsset(final AssetStore store, final JSONObject params, final String category)
    {
        this(MediaAssetFactory.NOT_SAVED, store, params, MediaAssetType.fromFilename(path.toString()), category);
    }
*/


    public MediaAsset(final AssetStore store, final JSONObject params) {
        //this(store, params, null);
        this(MediaAssetFactory.NOT_SAVED, store, params);
    }


    public MediaAsset(final int id,
                      final AssetStore store,
                      final JSONObject params)
    {
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

    private URL getUrl(final AssetStore store, final Path path) {
        if (store == null) {
            return null;
        }

        return null; //store.webPath(path);
    }

    private String getUrlString(final URL url) {
        if (url == null) {
            return null;
        }

        return url.toExternalForm();
    }


    public int getId()
    {
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

    //this is for Annotation mostly?  provides are reproducible uuid based on the MediaAsset id
    public String getUUID() {
        if (uuid != null) return uuid;
        //UUID v3 seems to take an arbitrary bytearray in, so we construct one that is basically "Ma____" where "____" is the int id
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

    //note this function will not allow "invalid" (< 0) ids... so see above for hack for new MediaAssets
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
        b[2] = (byte) (id >> 24);
        b[3] = (byte) (id >> 16);
        b[4] = (byte) (id >> 8);
        b[5] = (byte) (id >> 0);
        return UUID.nameUUIDFromBytes(b).toString();
    }

    public AssetStore getStore()
    {
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
        if (par == null) return this;  //reached the root!
        return par.getParentRoot(myShepherd);
    }

    //returns null if no parent
    public MediaAsset getParent(Shepherd myShepherd) {
        Integer pid = this.getParentId();
        if (pid == null) return null;
        return MediaAssetFactory.load(pid, myShepherd);
    }

    public JSONObject getParameters() {
        if (parameters != null) return parameters;
        //System.out.println("NOTE: getParameters() on " + this + " was null, so trying to get from parametersAsString()");
        JSONObject j = Util.stringToJSONObject(parametersAsString);
        parameters = j;
        return j;
    }

    public void setParameters(JSONObject p) {
        if (p == null) {
            System.out.println("WARNING: attempted to set null parameters on " + this + "; ignoring");
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
            System.out.println("WARNING: attempted to set null parametersAsString on " + this + "; ignoring");
            return;
        }
        parametersAsString = p;
        //now we also set parameters as the JSONObject (or try)
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
            System.out.println(this + " -- error parsing derivation json string (" + d + "): " + je.toString());
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
//System.out.println("hashCode on " + this + " = " + this.hashCode);
        return this.hashCode;
    }

    //this is store-specific, and null should be interpreted to mean "i guess i dont really have one"
    // in some cases, this might be some sort of unique-ish identifier (e.g. youtube id), so ymmv
    public String getFilename() {
        if (store == null) return null;
        return store.getFilename(this);
    }

    public ArrayList<String> getLabels() {
        return labels;
    }
    public void setLabels(ArrayList<String> l) {
        labels = l;
    }
    public void addLabel(String s) {
        if (labels == null) labels = new ArrayList<String>();
        if (!labels.contains(s)) labels.add(s);
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

    //kinda sorta really only for Encounter.findAllMediaByFeatureId()
    public boolean hasFeatures(String[] featureIds) {
        if ((features == null) || (features.size() < 1)) return false;
        for (Feature f : features) {
            for (int i = 0 ; i < featureIds.length ; i++) {
                if (f.isType(featureIds[i])) return true;   //short-circuit on first match
            }
        }
        return false;
    }

    public Path localPath()
    {
        if (store == null) return null;
        return store.localPath(this);
    }

    public boolean cacheLocal() throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, false);
    }

    public boolean cacheLocal(boolean force) throws Exception {
        if (store == null) return false;
        return store.cacheLocal(this, force);
    }

    //indisputable attributes about the image (e.g. type, dimensions, colorspaces etc)
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
        if (metadata==null) metadata = new MediaAssetMetadata();
        metadata.addDatum(key, value);
    }




    /**
     this function resolves (how???) various difference in "when" this image was taken.  it might use different metadata (in EXIF etc) and/or
     human-input (e.g. perhaps encounter data might trump it?)   TODO wtf should we do?
     FOR NOW: we rely first on (a) metadata.attributes.dateTime (as iso8601 string),
              then (b) crawl metadata.exif for something date-y

        TODO maybe someday this actually should be *only* punting to store.getDateTime() ????
    */
    public DateTime getDateTime() {
        if (this.userDateTime != null) return this.userDateTime;
        if (this.store != null) {
            DateTime dt = this.store.getDateTime(this);
            if (dt != null) return dt;
        }
        if (getMetadata() == null) return null;
        String adt = getMetadata().getAttributes().optString("dateTime", null);
        if (adt != null) return DateTime.parse(adt);  //lets hope it is in iso8601 format like it should be!
        //meh, gotta find it the hard way then...
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

    //note: default behavior will add this to the features on this MediaAsset -- can pass false to disable
    //TODO expand to handle things other than images (some day)
    public Feature generateUnityFeature() {
        return generateUnityFeature(true);
    }
    public Feature generateUnityFeature(boolean addToMediaAsset) {
        Feature f = new Feature();
        if (addToMediaAsset) this.addFeature(f);
        return f;
    }

    //if unity feature is appropriate, generates that; otherwise does a boundingBox one
    //   'params' is extra params to use, and can be null
    public Feature generateFeatureFromBbox(double w, double h, double x, double y, JSONObject params) {
        Feature f = null;
        if ((x != 0) || (y != 0) || (w != this.getWidth()) || (h != this.getHeight())) {
            if (params == null) params = new JSONObject();
            params.put("width", w);
            params.put("height", h);
            params.put("x", x);
            params.put("y", y);
            f = new Feature("org.ecocean.boundingBox", params);
            this.addFeature(f);
        } else {
            //oopsy this ignores extra params!   TODO FIXME should we change this?
            f = this.generateUnityFeature();
        }
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

/*
        return annotations;
    }

    //this will create the "trivial" Annotation (dimensions of the MediaAsset) iff no Annotations exist
    public ArrayList<Annotation> getAnnotationsGenerate(String species) {
        if (annotations == null) annotations = new ArrayList<Annotation>();
        if (annotations.size() < 1) addTrivialAnnotation(species);
        return annotations;
    }

    //TODO check if it is already here?  maybe?
    public Annotation addTrivialAnnotation(String species) {
        Annotation ann = new Annotation(this, species);  //this will add it to our .annotations collection as well
        String newId = generateUUIDv3((byte)65, (byte)110);  //set Annotation UUID relative to our ID  An___
        if (newId != null) ann.setId(newId);
        return ann;
    }

    public int getAnnotationCount() {
        if (annotations == null) return 0;
        return annotations.size();
    }

    public static MediaAsset findByAnnotation(Annotation annot, Shepherd myShepherd) {
        String queryString = "SELECT FROM org.ecocean.media.MediaAsset WHERE annotations.contains(ann) && ann.id == \"" + annot.getId() + "\"";
        Query query = myShepherd.getPM().newQuery(queryString);
        List results = (List)query.execute();
        if (results.size() < 1) return null;
        return (MediaAsset)results.get(0);
    }
*/


/*
    public Path getThumbPath()
    {
        return thumbPath;
    }

    public Path getMidPath()
    {
        return midPath;
    }
*/

/*
    public MediaAssetType getType() {
        return type;
    }
*/

    /**
     * Return a full web-accessible url to the asset, or null if the
     * asset is not web-accessible.
     *  NOTE: now you should *almost always* use .safeURL() to return something to a user -- this will hide original files when necessary
     */
    public URL webURL() {

        if (store == null) {
          System.out.println("MediaAsset "+this.getUUID()+" has no store!");
          return null;
        }

        try {
            int i = ((store.getUsage() == null) ? -1 : store.getUsage().indexOf("PLACEHOLDERHACK:"));
            if (i == 0) return new URL(store.getUsage().substring(16));
        } catch (java.net.MalformedURLException ex) {}

        return store.webURL(this);
    }

/*    has been deprecated, cuz you should make a better choice about what you want the url of. see: safeURL() and friends
    public String webURLString() {
        return getUrlString(this.webURL());
    }
*/


    //the primary purpose here is to mask (i.e. never send) the original (uploaded) image file.
    //  right now "master" labelled image is used, if available, otherwise children are chosen by allChildTypes() order....
    public URL safeURL(Shepherd myShepherd, HttpServletRequest request, String bestType) {
        MediaAsset ma = bestSafeAsset(myShepherd, request, bestType);
        if (ma == null) return null;
        return ma.webURL();
    }
    public URL safeURL(Shepherd myShepherd, HttpServletRequest request) {
        return safeURL(myShepherd, request, null);
    }
    //this assumes you weakest privileges
    public URL safeURL(Shepherd myShepherd) {
        return safeURL(myShepherd, null);
    }

    public URL safeURL(HttpServletRequest request) {
        String context = "context0";
        if (request != null) context = ServletUtilities.getContext(request);  //kinda rough, but....
        //the throw-away Shepherd object is [mostly!] ok here since we arent returning the MediaAsset it is used to find
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
    public MediaAsset bestSafeAsset(Shepherd myShepherd, HttpServletRequest request, String bestType) {
        if (store == null) return null;
        //this logic is simplistic now, but TODO make more complex (e.g. configurable) later....
        //TODO should be block "original" ???  is that overkill??
        if (bestType == null) bestType = "master";
        //note, this next line means bestType may get bumped *up* for anon user.... so we should TODO some logic in there if ever needed
        if (AccessControl.isAnonymous(request)) bestType = "mid";
        if (store instanceof URLAssetStore) bestType = "original";  //this is cuz it is assumed to be a "public" url

        // hack for flukebook
        bestType = "master";
        //System.out.println("bestSafeAsset: ma #"+getId()+" has bestType "+bestType);

        //gotta consider that wre are the best!
        if (this.hasLabel("_" + bestType)) return this;

        //if we are a child asset, we need to find our parent then find best from there!
        MediaAsset top = this;  //assume we are the parent-est
        if (parentId != null) {
            top = MediaAssetFactory.load(parentId, myShepherd);
            if (top == null) throw new RuntimeException("bestSafeAsset() failed to find parent on " + this);
            if (!top.hasLabel("_original")) {
                // Commented out below because it prints 5k lines at a time when loading an occurrence (!?!?)
                //System.out.println("INFO: " + this + " had a non-_original parent of " + top + "; so using this");
                return this;  //we stick with this cuz we are kinda at a dead end
            }
        }

        boolean gotBest = false;
        List<String> types = store.allChildTypes();  //note: do we need to care that top may have changed stores????
        for (String t : types) {

            if (t.equals(bestType)) gotBest = true;
            else gotBest = false;
            if (!gotBest) continue;  //skip over any "better" types until we get to best we can use
//System.out.println("   ....  ??? do we have a " + t);
            //now try to see if we have one!
            ArrayList<MediaAsset> kids = top.findChildrenByLabel(myShepherd, "_" + t);
            if ((kids != null) && (kids.size() > 0)) {
                MediaAsset kid = kids.get(0);
                return kid; 

            } ///not sure how to pick if we have more than one!  "probably rare" case anyway....
        }
        return null;  //got nothing!  :(
    }
    public MediaAsset bestSafeAsset(Shepherd myShepherd, HttpServletRequest request) {
        return bestSafeAsset(myShepherd, request, null);
    }
    public MediaAsset bestSafeAsset(Shepherd myShepherd) {
        return bestSafeAsset(myShepherd, null);
    }

/*
    public String thumbWebPathString() {
        return getUrlString(thumbWebPath());
    }

    public String midWebPathString() {
        return getUrlString(midWebPath());
    }

    public URL thumbWebPath() {
        return getUrl(thumbStore, thumbPath);
    }

    public void setThumb(final AssetStore store, final Path path)
    {
        thumbStore = store;
        thumbPath = path;
    }

    public AssetStore getThumbstore() {
        return thumbStore;
    }

    public URL midWebPath() {
        if (midPath == null) {
            return webPath();
        }

        //
        // Just use thumb store for now.
        //
        return getUrl(thumbStore, midPath);
    }

    public void setMid(final Path path) {
        //
        // Just use thumb store for now.
        //
        this.midPath = path;
    }

*/

/*
    public Integer getSubmitterId() {
        return submitterid;
    }

    public void setSubmitterId(final Integer submitterid) {
        this.submitterid = submitterid;
    }
*/


/*
    public LocalDateTime getMetaTimestamp() {
        return metaTimestamp;
    }


    public void setMetaTimestamp(LocalDateTime metaTimestamp) {
        this.metaTimestamp = metaTimestamp;
    }


    public Double getMetaLatitude() {
        return metaLatitude;
    }


    public void setMetaLatitude(Double metaLatitude) {
        this.metaLatitude = metaLatitude;
    }


    public Double getMetaLongitude() {
        return metaLongitude;
    }


    public void setMetaLongitude(Double metaLongitude) {
        this.metaLongitude = metaLongitude;
    }
*/



/*
    public void delete() {
        MediaAssetFactory.delete(this.id);
        MediaAssetFactory.deleteFromStore(this);
    }
*/

    //this takes contents of this MediaAsset and copies it to the target (note MediaAssets must exist with sufficient params already)
    //please note this uses *source* AssetStore for copying, which can/will affect how, for example, credentials in aws s3 are chosen.
    // for tighter control of this, you can call copyAsset() (or copyAssetAny()?) directly on desired store
    public void copyAssetTo(MediaAsset targetMA) throws IOException {
        if (store == null) throw new IOException("copyAssetTo(): store is null on " + this);
        store.copyAssetAny(this, targetMA);
    }


        public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
                org.datanucleus.api.rest.orgjson.JSONObject jobj) throws org.datanucleus.api.rest.orgjson.JSONException {
            return sanitizeJson(request, jobj, true);
        }

        //fullAccess just gets cascaded down from Encounter -> Annotation -> us... not sure if it should win vs security(request) TODO
        public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
              org.datanucleus.api.rest.orgjson.JSONObject jobj, boolean fullAccess) throws org.datanucleus.api.rest.orgjson.JSONException {
              jobj.put("id", this.getId());
                jobj.put("detectionStatus", this.getDetectionStatus());
              jobj.remove("parametersAsString");
            //jobj.put("guid", "http://" + CommonConfiguration.getURLLocation(request) + "/api/org.ecocean.media.MediaAsset/" + id);

            //TODO something better with store?  fix .put("store", store) ???
            HashMap<String,String> s = new HashMap<String,String>();
            s.put("type", store.getType().toString());
            jobj.put("store", s);

            String context = ServletUtilities.getContext(request);
            Shepherd myShepherd = new Shepherd(context);
            myShepherd.setAction("MediaAsset.class_1");
            myShepherd.beginDBTransaction();

            ArrayList<Feature> fts = getFeatures();
            if ((fts != null) && (fts.size() > 0)) {
                org.datanucleus.api.rest.orgjson.JSONArray jarr = new org.datanucleus.api.rest.orgjson.JSONArray();
                for (int i = 0 ; i < fts.size() ; i++) {
                    org.datanucleus.api.rest.orgjson.JSONObject jf = new org.datanucleus.api.rest.orgjson.JSONObject();
                    Feature ft = fts.get(i);
                    jf.put("id", ft.getId());
                    jf.put("type", ft.getType());
                    JSONObject p = ft.getParameters();
                    if (p != null) jf.put("parameters", Util.toggleJSONObject(p));

                    //we add this stuff for gallery/image to link to co-occurring indiv/enc
                    Annotation ann = ft.getAnnotation();
                    if (ann != null) {
                        jf.put("annotationId", ann.getId());
                        Encounter enc = ann.findEncounter(myShepherd);
                        if (enc != null) {
                            jf.put("encounterId", enc.getCatalogNumber());
                            if (enc.hasMarkedIndividual()) jf.put("individualId", enc.getIndividualID());
                        }
                    }

                    jarr.put(jf);
                }
                jobj.put("features", jarr);
            }
            if ((getMetadata() != null) && (getMetadata().getData() != null) && (getMetadata().getData().opt("attributes") != null)) {
                //jobj.put("metadata", new org.datanucleus.api.rest.orgjson.JSONObject(getMetadata().getData().getJSONObject("attributes").toString()));
                jobj.put("metadata", Util.toggleJSONObject(getMetadata().getData().getJSONObject("attributes")));
            }
            DateTime dt = getDateTime();
            if (dt != null) jobj.put("dateTime", dt.toString());  //DateTime.toString() gives iso8601, noice!

            //note? warning? i guess this will traverse... gulp?
            URL u = safeURL(myShepherd, request);
            if (u != null) jobj.put("url", u.toString());

            ArrayList<MediaAsset> kids = null;
            if (!jobj.optBoolean("_skipChildren", false)) kids = this.findChildren(myShepherd);
            myShepherd.rollbackDBTransaction();
            if ((kids != null) && (kids.size() > 0)) {
                org.datanucleus.api.rest.orgjson.JSONArray k = new org.datanucleus.api.rest.orgjson.JSONArray();
                for (MediaAsset kid : kids) {
                    k.put(kid.sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess));
                }
                jobj.put("children", k);
            }

            if (fullAccess) {
              jobj.put("userLatitude",this.getLatitude());
              jobj.put("userLongitude",this.getLongitude());
              jobj.put("userDateTime",this.getUserDateTime());
              jobj.put("filename", this.getFilename());  //this can "vary" depending on store type
            }

            jobj.put("occurrenceID",this.getOccurrenceID());

            if (this.getLabels() != null) jobj.put("labels", this.getLabels());

            if ((this.getKeywords() != null) && (this.getKeywords().size() > 0)) {
                JSONArray ka = new JSONArray();
                for (Keyword kw : this.getKeywords()) {
                    JSONObject kj = new JSONObject();
                    kj.put("indexname", kw.getIndexname());
                    kj.put("readableName", kw.getReadableName());
                    ka.put(kj);
                }
                jobj.put("keywords", new org.datanucleus.api.rest.orgjson.JSONArray(ka.toString()));
            }
            
            myShepherd.rollbackDBTransaction();
            myShepherd.closeDBTransaction();

            return jobj;
        }


    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("parent", parentId)
                .append("labels", ((labels == null) ? "" : labels.toString()))
                .append("store", store.toString())
                .toString();
    }


    public void copyIn(File file) throws IOException {
        if (store == null) throw new IOException("copyIn(): store is null on " + this);
        store.copyIn(file, getParameters(), false);
    }

    public MediaAsset updateChild(String type, HashMap<String, Object> opts) throws IOException {
        if (store == null) throw new IOException("store is null on " + this);
        return store.updateChild(this, type, opts);
    }

    public MediaAsset updateChild(String type) throws IOException {
        return updateChild(type, null);
    }

    public ArrayList<MediaAsset> detachChildren(Shepherd myShepherd, String type) throws IOException {
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
        if (parentId!=null) return true; // saves the db call below if unnecessary
        List<MediaAsset> children = findChildren(myShepherd);
        return(children!=null && children.size() > 0);
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
    //   (e.g. on an encounter) and will *probably* need to change in the future.    TODO?
    public static MediaAsset findOneByLabel(ArrayList<MediaAsset> mas, Shepherd myShepherd, String label) {
        ArrayList<MediaAsset> all = findAllByLabel(mas, myShepherd, label, true);
        if ((all == null) || (all.size() < 1)) return null;
        return all.get(0);
    }
    public static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas, Shepherd myShepherd, String label) {
        return findAllByLabel(mas, myShepherd, label, false);
    }
    private static ArrayList<MediaAsset> findAllByLabel(ArrayList<MediaAsset> mas, Shepherd myShepherd, String label, boolean onlyOne) {
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


    //creates the "standard" derived children for a MediaAsset (thumb, mid, etc)
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
                System.out.println("updateStandardChildren() failed on type=" + type + ", ma=" + this + " with " + ex.toString());
            }
            if (c != null) mas.add(c);
        }
        return mas;
    }
    //as above, but saves them too
    public ArrayList<MediaAsset> updateStandardChildren(Shepherd myShepherd) {
        ArrayList<MediaAsset> mas = updateStandardChildren();
        for (MediaAsset ma : mas) {
            MediaAssetFactory.save(ma, myShepherd);
        }
        return mas;
    }


    public void setKeywords(ArrayList<Keyword> kws) {
        keywords = kws;
    }
    public ArrayList<Keyword> addKeyword(Keyword k) {
        if (keywords == null) keywords = new ArrayList<Keyword>();
        if (!keywords.contains(k)) keywords.add(k);
        return keywords;
    }
    public ArrayList<Keyword> getKeywords() {
        return keywords;
    }
    
    public boolean hasKeyword(String keywordName){
      if(keywords!=null){
        int numKeywords=keywords.size();
        for(int i=0;i<numKeywords;i++){
          Keyword kw=keywords.get(i);
          if (kw==null) return false;
          if((keywordName.equals(kw.getIndexname())||keywordName.equals(kw.getReadableName()))) return true; 
        }
      }
      
      return false;
    }
    
    public boolean hasKeyword(Keyword key){
      if(keywords!=null){
        if(keywords.contains(key)){return true;}
      }
      return false;
    }


    //if we dont have the Annotation... which kinda sucks but okay
    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd) {
        return toHtmlElement(request, myShepherd, null);
    }

    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd, Annotation ann) {
        if (store == null) return "<!-- ERROR: MediaAsset.toHtmlElement() has no .store value for " + this.toString() + " -->";
        return store.mediaAssetToHtmlElement(this, request, myShepherd, ann);
    }


    //piggybacks off metadata, so that must be set first, otherwise it matches corresponding mime type (major/minor), case-insensitive
    //note: for return values, we standardize on all-lowercase. so there.
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
        String mt = this.metadata.getAttributes().optString("contentType", null);  //note: getAttributes always  returns a JSONObject (even if empty)
        if (mt == null) return null;
        return mt.toLowerCase().split("/");
    }

    //note: we are going to assume Metadata "will just be there" so no magical updates. if it is null, it is null.
    // this implies basically that it is set once when the MediaAsset is created, so make sure that happens, *cough*
    public MediaAssetMetadata getMetadata() {
        return metadata;
    }
    public boolean hasMetadata() {
        MediaAssetMetadata data = getMetadata();
        return ((data != null) && (data.getData() != null) && (data.getData().opt("attributes") != null));
    }
    public void setMetadata() throws IOException {
        setMetadata(updateMetadata());
    }
    public void setMetadata(MediaAssetMetadata md) {
        metadata = md;
    }
    public MediaAssetMetadata updateMetadata() throws IOException {  //TODO should this overwrite existing, or append?
        if (store == null) return null;
        metadata = store.extractMetadata(this);
        return metadata;
    }

    //only gets the "attributes" portion -- which is usually all we need for derived images
    public MediaAssetMetadata updateMinimalMetadata() {
        if (store == null) return null;
        try {
            metadata = store.extractMetadata(this, true);  //true means "attributes" only
        } catch (IOException ioe) {  //we silently eat IOExceptions, but will return null
            System.out.println("WARNING: updateMinimalMetadata() on " + this + " got " + ioe.toString() + "; failed to set");
            return null;
        }
        return metadata;
    }

    //handy cuz we dont need the actual file (if we have these values from elsewhere) and usually the only stuff we "need"
    public MediaAssetMetadata setMinimalMetadata(int width, int height, String contentType) {
        //note, this will overwrite existing "attributes" value if it exists
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

    //takes base64 string and turns to binary content and copies that in as normal
    public void copyInBase64(String b64) throws IOException {
        if (b64 == null) throw new IOException("copyInBase64() null string");
        byte[] imgBytes = new byte[100];
        try {
            imgBytes = DatatypeConverter.parseBase64Binary(b64);
        } catch (IllegalArgumentException ex) {
            throw new IOException("copyInBase64() could not parse: " + ex.toString());
        }
        File file = (this.localPath() != null) ? this.localPath().toFile() : File.createTempFile("b64-" + Util.generateUUID(), ".tmp");
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) parentDir.mkdirs();
        FileOutputStream stream = new FileOutputStream(file);
        try {
            stream.write(imgBytes);
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


}
