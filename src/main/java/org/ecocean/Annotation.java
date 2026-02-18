package org.ecocean;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.awt.Rectangle;
import java.io.File;
import java.io.IOException;
import java.util.*;
import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.ecocean.api.ApiException;
import org.ecocean.ia.IA;
import org.ecocean.ia.Task;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.FeatureType;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.shepherd.core.Shepherd;
import org.json.JSONArray;
import org.json.JSONObject;

public class Annotation extends Base implements java.io.Serializable {
    public Annotation() {}
    private String id;
    private static final String[][] VALID_VIEWPOINTS = new String[][] {
        { "up", "up", "up", "up", "up", "up", "up", "up", }, {
            "upfront", "upfrontright", "upright", "upbackright", "upback", "upbackleft", "upleft",
                "upfrontleft"
        }, { "front", "frontright", "right", "backright", "back", "backleft", "left", "frontleft" },
                {
            "downfront", "downfrontright", "downright", "downbackright", "downback", "downbackleft",
                "downleft", "downfrontleft"
        }, { "down", "down", "down", "down", "down", "down", "down", "down" }
    };
    private String species;

    private String iaClass;

    private String name;
    private boolean isExemplar = false;
    private Boolean isOfInterest = null; // aka AoI (Annotation of Interest)
    protected String identificationStatus;
    private ArrayList<Feature> features;
    protected String acmId;

    // this is used to decide "should we match against this"  problem is: that is not very (IA-)algorithm agnostic
    // TODO: was this made obsolete by ACM and friends?
    private boolean matchAgainst = false;

    // TODO: can these (thru mediaAsset) be removed now that there Features?
    private int x;
    private int y;
    private int width;
    private int height;
    private float[] transformMatrix;
    private double theta;
    private long version = System.currentTimeMillis();

    // quality indicates the fidelity of the annotation, e.g. the overall image quality of a picture.
    // This is useful e.g. for researchers who want to account for a bias where "better" images are
    // more likely to produce matches.
    private Double quality;
    // distinctiveness indicates the real-wold distinctiveness of the feature *being recorded*, independent
    // of the recording medium. Useful e.g. for researchers who want to account for a bias where more distinct
    // animals like one with a large scar are easier to re-sight (match).
    private Double distinctiveness;
    private String viewpoint;
    // *'annot_yaw': 'REAL',
    // ~'annot_detect_confidence': 'REAL',
    // ~'annot_exemplar_flag': 'INTEGER',
    // ~'annot_note': 'TEXT',
    // ~'annot_visual_uuid': 'UUID',
    // ~'annot_semantic_uuid': 'UUID',
    // *'annot_quality': 'INTEGER',
    // ~'annot_tags': 'TEXT',

    private MediaAsset mediaAsset = null;
    // end of what will go away

    // the "trivial" Annotation - will have a single feature which references the total MediaAsset
    public Annotation(String species, MediaAsset ma) {
        this(species, ma.generateUnityFeature());
    }

    // single feature convenience constructor
    public Annotation(String species, Feature f) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = new ArrayList<Feature>();
        this.features.add(f);
    }

    public Annotation(String species, ArrayList<Feature> f) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = f;
    }

    // For setting the iaClass returned from detection... No more mangled species names sent to identification
    public Annotation(String species, Feature f, String iaClass) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = new ArrayList<Feature>();
        this.features.add(f);
        this.iaClass = iaClass;
    }

    public Annotation(String species, ArrayList<Feature> f, String iaClass) {
        this.id = Util.generateUUID();
        this.species = species;
        this.features = f;
        this.iaClass = iaClass;
    }

    @Override public String opensearchIndexName() { return "annotation"; }

    @Override public long getVersion() {
        return version;
    }

    public long setVersion() {
        version = System.currentTimeMillis();
        return version;
    }

    public JSONObject opensearchMapping() {
        JSONObject map = super.opensearchMapping();
        JSONObject keywordType = new JSONObject("{\"type\": \"keyword\"}");

/*
        JSONObject keywordNormalType = new org.json.JSONObject(
            "{\"type\": \"keyword\", \"normalizer\": \"wildbook_keyword_normalizer\"}");
 */

        // "id" is done in Base
        map.put("viewpoint", keywordType);
        map.put("iaClass", keywordType);
        map.put("acmId", keywordType);
        map.put("encounterId", keywordType);
        map.put("encounterSubmitterId", keywordType);
        map.put("encounterUserUuid", keywordType);
        map.put("encounterLocationId", keywordType);
        map.put("encounterTaxonomy", keywordType);
        map.put("encounterProjectIds", keywordType);

        // all case-insensitive keyword-ish types
        // map.put("fubar", keywordNormalType);

        return map;
    }

    public void opensearchDocumentSerializer(JsonGenerator jgen, Shepherd myShepherd)
    throws IOException, JsonProcessingException {
        super.opensearchDocumentSerializer(jgen, myShepherd);

        jgen.writeStringField("acmId", this.getAcmId());
        jgen.writeStringField("viewpoint", this.getViewpoint());
        jgen.writeStringField("iaClass", this.getIAClass());
        jgen.writeBooleanField("matchAgainst", this.getMatchAgainst());
        MediaAsset ma = this.getMediaAsset();
        if (ma != null) {
            jgen.writeNumberField("mediaAssetId", ma.getIdInt());
        }
        Encounter enc = this.findEncounter(myShepherd);
        if (enc != null) {
            jgen.writeStringField("encounterId", enc.getId());
            jgen.writeStringField("encounterSubmitterId", enc.getSubmitterID());
            jgen.writeStringField("encounterLocationId", enc.getLocationID());
            jgen.writeStringField("encounterTaxonomy", enc.getTaxonomyString());
            // per discussion on issue 874, including this in indexing, but not (yet) using in matchingSet
            jgen.writeStringField("encounterLivingStatus", enc.getLivingStatus());
            User owner = enc.getSubmitterUser(myShepherd);
            if (owner != null) jgen.writeStringField("encounterUserUuid", owner.getId());
            List<Project> projects = enc.getProjects(myShepherd);
            if (!Util.collectionIsEmptyOrNull(projects)) {
                jgen.writeArrayFieldStart("encounterProjectIds");
                for (Project proj : projects) {
                    jgen.writeString(proj.getId());
                }
                jgen.writeEndArray();
            }
            if (enc.getIndividual() != null) {
                long tod = enc.getIndividual().getTimeOfDeath();
                if (tod > 0) jgen.writeNumberField("encounterIndividualTimeOfDeath", tod);
            }
        }
    }

    // TODO should this also be limited by matchAgainst and acmId?
    @Override public String getAllVersionsSql() {
        return
                "SELECT \"ID\", \"VERSION\" AS version FROM \"ANNOTATION\" ORDER BY \"MATCHAGAINST\" DESC, version";
    }

    @Override public Base getById(Shepherd myShepherd, String id) {
        return myShepherd.getAnnotation(id);
    }

    // comment cruft only needed for Base class
    @Override public String getComments() {
        return null;
    }

    @Override public void setComments(final String comments) {
    }

    @Override public void addComments(final String newComments) {
    }

    // this is for use *only* to migrate old-world Annotations to new-world
    public Feature migrateToFeatures() {
        Feature f;

        if (isTrivial()) { // this gets special "unity" feature, which means the whole thing basically
            f = new Feature();
        } else {
            JSONObject params = new JSONObject();
            params.put("width", getWidth());
            params.put("height", getHeight());
            if (needsTransform()) {
                params.put("transformMatrix", getTransformMatrix());
            } else {
                params.put("x", getX());
                params.put("y", getY());
            }
            f = new Feature("org.ecocean.boundingBox", params);
        }
        __getMediaAsset().addFeature(f);
        addFeature(f);
        return f;
    }

    public void setAcmId(String id) {
        this.acmId = id;
        this.setVersion();
    }

    public String getAcmId() {
        return this.acmId;
    }

    public boolean hasAcmId() {
        return (this.acmId != null);
    }

    public ArrayList<Feature> getFeatures() {
        return features;
    }

    public Feature getFeature() {
        if (Util.collectionSize(features) < 1) return null;
        return features.get(0);
    }

    public void setFeatures(ArrayList<Feature> f) {
        features = f;
        this.setVersion();
    }

    public void addFeature(Feature f) {
        if (features == null) features = new ArrayList<Feature>();
        if (!features.contains(f)) features.add(f);
        this.setVersion();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
        this.setVersion();
    }

    public Double getQuality() {
        return quality;
    }

    public void setQuality(Double quality) {
        this.quality = quality;
    }

    public String getUUID() {
        return id;
    }

    public int getX() {
        return x;
    }

    public void setX(int x) {
        this.x = x;
    }

    public int getY() {
        return y;
    }

    public void setY(int y) {
        this.y = y;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int w) {
        width = w;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int h) {
        height = h;
    }

    public float[] getTransformMatrix() {
        return transformMatrix;
    }

    public void setTransformMatrix(float[] t) {
        transformMatrix = t;
    }

    // transform is not empty or "useless" (e.g. identity)
    public boolean needsTransform() {
        return Util.isIdentityMatrix(transformMatrix);
    }

    public float[] getTransformMatrixClean() {
        if (!needsTransform()) return new float[] { 1, 0, 0, 1, 0, 0 };
        return transformMatrix;
    }

    public Taxonomy getTaxonomy(Shepherd myShepherd) {
        Encounter enc = findEncounter(myShepherd);

        if (enc == null) return null;
        return enc.getTaxonomy(myShepherd);
    }

    public boolean isTrivial() {
        MediaAsset ma = this.getMediaAsset();

        if (ma == null) return false;
        for (Feature ft : getFeatures()) {
            if (ft.isUnity()) return true;
        }
        // prevents zero-values from return true on final test
        if ((getWidth() == 0) || (getHeight() == 0)) return false;
        return (!needsTransform() && (getWidth() == (int)ma.getWidth()) &&
                   (getHeight() == (int)ma.getHeight()));
    }

// .theta property on Annotation usage is deprecated, instead we get
// the value from the Feature [ and likewise deprecate setTheta() ]
    public double getTheta() {
        Feature ft = getFeature();

        if (ft == null) return 0.0d;
        if (ft.getParameters() == null) return 0.0d;
        return ft.getParameters().optDouble("theta", 0.0d);
    }

/*
    public void setTheta(double t) {
        theta = t;
    }
 */
    public boolean isIAReady() {
        MediaAsset ma = this.getMediaAsset();

        if (ma == null) return false;
        if (ma.getHeight() == 0 && ma.getWidth() == 0) return false;
        return true;
    }

    public String getViewpoint() {
        return viewpoint;
    }

    // this returns this viewpoint, plus one to either side
    // (will return null if unset or invalid)
    public String[] getViewpointAndNeighbors() {
        return getViewpointAndNeighbors(this.viewpoint);
    }

    private boolean isViewpointPrimary() {
        List<String> primaryList = new ArrayList<>();

        // I guess primary vp's should never be something that changes?
        Collections.addAll(primaryList,
            new String[] { "front", "right", "back", "left", "up", "down" });
        if (primaryList.contains(this.getViewpoint())) {
            return true;
        }
        return false;
    }

    // (viewpoint == null || viewpoint == 'up' || viewpoint == 'upfront' || viewpoint == 'upfrontright'
    // || viewpoint == 'upright' || viewpoint == 'upbackright' || viewpoint == 'upback'
    // || viewpoint == 'upbackleft' || viewpoint == 'upfront' || viewpoint == 'upfrontleft')

    public static String[] getViewpointAndNeighbors(String vp) {
        List<String> rtn = new ArrayList<>();

        try {
            System.out.println("Input vp to getViewpointAndNeighbors: " + vp);
            if ((vp == null) || !isValidViewpoint(vp)) return null;
            for (int i = 0; i < VALID_VIEWPOINTS.length; i++) {
                String[] innerArr = VALID_VIEWPOINTS[i];
                for (int j = 0; j < innerArr.length; j++) {
                    if (vp.equals(VALID_VIEWPOINTS[i][j])) {
                        // cases: up, down, side edge, lower or upper.
                        // always want the center viewpoint
                        rtn.add(vp);
                        // start with top & bottom EZ cases..
                        if (i == 0) {
                            rtn.addAll(Arrays.asList(VALID_VIEWPOINTS[1]));
                            break;
                        } else if (i == VALID_VIEWPOINTS.length - 1) {
                            rtn.addAll(Arrays.asList(VALID_VIEWPOINTS[VALID_VIEWPOINTS.length -
                                2]));
                            break;
                        }
                        for (int h = -1; h < 2; h++) {
                            for (int w = -1; w < 2; w++) {
                                // gettin trixy.. wrap indexes around
                                int horizontal = j + w;
                                if (horizontal == -1) {
                                    horizontal = VALID_VIEWPOINTS[1].length - 1;
                                } else if (horizontal == 8) {
                                    horizontal = 0;
                                }
                                if (!rtn.contains(VALID_VIEWPOINTS[i + h][horizontal])) {
                                    rtn.add(VALID_VIEWPOINTS[i + h][horizontal]);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Found these Viewpoints in getViewpointAndNeighbors: " + rtn.toString());
        if (rtn.size() == 0) return null;
        String[] rtnArr = new String[rtn.size()];
        return rtn.toArray(rtnArr);
    }

    public void setViewpoint(String v) {
        viewpoint = v;
        this.setVersion();
    }

    // note!  this can block and take a while if IA has yet to compute the viewpoint!
    public String setViewpointFromIA(String context)
    throws IOException {
        if (acmId == null)
            throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
        try {
            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
            if (resp == null) return null;
            viewpoint = resp.optString("viewpoint", null);
            System.out.println("INFO: setViewpointFromIA() got '" + viewpoint + "' (score " +
                resp.optDouble("score", -1.0) + ") for " + this);
            return viewpoint;
        } catch (RuntimeException | IOException | java.security.NoSuchAlgorithmException |
            java.security.InvalidKeyException ex) {
            throw new IOException("setViewpointFromIA() on " + this + " failed: " + ex.toString());
        }
    }

/*
    //  response comes from ia thus: "response": [{"score": 0.9783339699109396, "species": "giraffe_reticulated", "viewpoint": "right"}] public static
       JSONObject iaViewpointFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException,
       NoSuchAlgorithmException, InvalidKeyException {
 */

// TODO: Deprecate "all of this" now that deployed sites are migrated
    public MediaAsset __getMediaAsset() {
        return mediaAsset;
    }

    public MediaAsset getMediaAsset() {
        ArrayList<Feature> fts = getFeatures();

        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) {
            System.out.println("WARNING: annotation " + this.getId() +
                " is featureless, falling back to deprecated __getMediaAsset().  please fix!");
            return __getMediaAsset();
        }
        return fts.get(0).getMediaAsset();
    }

    public boolean hasMediaAsset() {
        return (getMediaAsset() != null);
    }

    public MediaAsset getDerivedMediaAsset() {
        return null;
    }

    // detaches this Annotation from MediaAsset by removing the corresponding feature *from the MediaAsset*
    // (the Feature is deleted forever, tho!)
    public MediaAsset detachFromMediaAsset() {
        ArrayList<Feature> fts = getFeatures();

        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) return null;
        MediaAsset ma = fts.get(0).getMediaAsset();
        if (ma == null) return null;
        ma.removeFeature(fts.get(0));
        return ma;
    }

    // returns null if not MediaAsset (whaaa??), otherwise a list (possibly empty) of siblings on the MediaAsset
    public List<Annotation> getSiblings() {
        if (this.getMediaAsset() == null) return null;
        List<Annotation> sibs = new ArrayList<Annotation>();
        for (Annotation ann : this.getMediaAsset().getAnnotations()) { // fyi .getAnnotations() doesnt return null
            if (!ann.getId().equals(this.getId())) sibs.add(ann);
        }
        return sibs;
    }

/*
    this is an ordering value which equates to "where in the MediaAsset this Annotation lies compared to siblings".
    what this means is greatly open to interpretation!  "english reading order" (top-down/left-right) might seem a decent choice, but after some
       discussion, for boundingBox Annotations, i am going to go with left-most x-value of the bounding box. NOTE: this is dependent on FeatureType,
       so there are loose ends to deal with regarding non-boundingBox Features.
 */
    public int relativePosition() {
        MediaAsset ma = this.getMediaAsset();

        if (ma == null) return -2;
        List<Annotation> anns = ma.getAnnotationsSortedPositionally();
        return anns.indexOf(this);
    }

    // standard -1, 0, 1 expected; return 0 if "not comparable"
    // note that this does not assume they are the same MediaAsset
    public int comparePositional(Annotation other) {
        if (other == null) return 0;
        if ((Util.collectionSize(this.getFeatures()) * Util.collectionSize(other.getFeatures())) ==
            0) return 0;
        // We currently do NxM comparing all Feature combinations (potentially excessive but typically single feature so unneeded)
        for (Feature f1 : this.getFeatures()) {
            for (Feature f2 : other.getFeatures()) {
                if (!f1.equals(f2)) {
                    int c = f1.comparePositional(f2);
                    if (c != 0) return c;
                }
            }
        }
        return 0;
    }

    public String getSpecies(Shepherd myShepherd) {
        Encounter enc = this.findEncounter(myShepherd);

        if (enc == null) return null;
        return enc.getTaxonomyString();
    }

    public String getIAClass() {
        return iaClass;
    }

    public void setIAClass(String iaClass) {
        this.iaClass = iaClass;
        this.setVersion();
    }

    public boolean hasIAClass() {
        return Util.stringExists(getIAClass());
    }

    public void setIAExtractedKeywords(Shepherd myShepherd) {
        setIAExtractedKeywords(myShepherd, getTaxonomy(myShepherd));
    }

    public void setIAExtractedKeywords(Shepherd myShepherd, Taxonomy taxy) {
        if (taxy == null) return;
        if (!this.hasMediaAsset() || !this.hasIAClass()) return;
        IAJsonProperties iaConf = IAJsonProperties.iaConfig();
        String keywordString = iaConf.getKeywordString(taxy, this.getIAClass());
        if (!Util.stringExists(keywordString)) return; // no keyword to save (according to config) scenario

        System.out.println("setIAExtractedKeyword is saving kw " + keywordString + " for annot " +
            getId());
        Keyword kw = myShepherd.getOrCreateKeyword(keywordString);
        myShepherd.beginDBTransaction();
        this.getMediaAsset().addKeyword(kw);
        myShepherd.commitDBTransaction();
    }

    public String getName() {
        return name;
    }

    public void setName(String n) {
        name = n;
    }

    public boolean getIsExemplar() {
        return isExemplar;
    }

    public void setIsExemplar(boolean b) {
        isExemplar = b;
    }

    public Boolean getIsOfInterest() {
        return isOfInterest;
    }

    public void setIsOfInterest(Boolean b) {
        isOfInterest = b;
    }

    public boolean getMatchAgainst() {
        return matchAgainst;
    }

    public void setMatchAgainst(boolean b) {
        matchAgainst = b;
        this.setVersion();
    }

    public String getIdentificationStatus() {
        return this.identificationStatus;
    }

    public void setIdentificationStatus(String status) {
        this.identificationStatus = status;
        this.setVersion();
    }

    // if this cannot determine a bounding box, then we return null
    public int[] getBbox() {
        MediaAsset ma = getMediaAsset();

        if (ma == null) return null;
        Feature found = null;
        for (Feature ft : getFeatures()) {
            if (ft.isUnity() || ft.isType("org.ecocean.boundingBox")) {
                found = ft;
                break;
            }
        }
        if (found == null) return null;
        int[] bbox = new int[4];
        if (found.isUnity()) {
            bbox[0] = 0;
            bbox[1] = 0;
            bbox[2] = (int)ma.getWidth();
            bbox[3] = (int)ma.getHeight();
        } else {
            // guess we derive from feature!
            if (found.getParameters() == null) return null;
            bbox[0] = found.getParameters().optInt("x", 0);
            bbox[1] = found.getParameters().optInt("y", 0);
            bbox[2] = found.getParameters().optInt("width", 0);
            bbox[3] = found.getParameters().optInt("height", 0);
        }
        if ((bbox[2] < 1) || (bbox[3] < 1)) {
            // note: do NOT use toString() in here!  it references .getBbox() !!  see: recursion
            System.out.println("WARNING: Annotation.getBbox() found invalid width/height for id=" +
                this.getId());
            return null;
        }
        // System.out.println("Set new Bounding box.");
        return bbox;
    }

    public String getBboxAsString() {
        return Arrays.toString(this.getBbox());
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("id", id)
                   .append("species", species)
                   .append("iaClass", iaClass)
                   .append("bbox", getBbox())
                   .toString();
    }

    // *for now* this will only(?) be called from an Encounter, which means that Encounter must be sanitized
    // so we assume this *must* be sanitized too.
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
        boolean fullAccess)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj =
            new org.datanucleus.api.rest.orgjson.JSONObject();
        jobj.put("id", id);
        jobj.put("isExemplar", this.getIsExemplar());
        jobj.put("species", this.getIAClass());
        jobj.put("annotationIsOfInterest", this.getIsOfInterest());
        if (this.getFeatures() != null) {
            org.datanucleus.api.rest.orgjson.JSONArray feats =
                new org.datanucleus.api.rest.orgjson.JSONArray();
            for (Feature f : this.getFeatures()) {
                if (f == null) continue;
                feats.put(f.sanitizeJson(request, fullAccess));
            }
            jobj.put("features", feats);
        }
        jobj.put("identificationStatus", this.getIdentificationStatus());
        return jobj;
    }

    // default behavior is limited access
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeJson(request, false);
    }

    // returns only the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query), all they want in return are
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request,
        boolean fullAccess)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj;
        if (this.getMediaAsset() != null) {
            jobj = this.getMediaAsset().sanitizeJson(request,
                new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess);
        } else {
            jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
        }
        return jobj;
    }

    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request)
    throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeMedia(request, false);
    }

    public boolean isPart() {
        return ((this.iaClass != null) && this.iaClass.contains("+"));
    }

    public String getPartIfPresent() {
        String thisPart = "";

        if (this.iaClass != null && this.iaClass.contains("+")) {
            String[] arr = this.iaClass.split("\\+");
            thisPart = arr[arr.length - 1];
        }
        return thisPart;
    }

/*
   both must be arrays which contain objects.
   these will be "mixed into" the built default query. TODO this might cause some conflict or
   overwriting that needs to be addressed in the future
 */
    public JSONObject getMatchingSetQuery(Shepherd myShepherd, JSONObject taskParams,
        boolean useClauses) {
        Encounter enc = this.findEncounter(myShepherd);

        if (enc == null) {
            System.out.println("WARNING: getMatchingSetQuery() could not find Encounter for " +
                this);
            return null;
        }
        JSONObject query = new JSONObject(
            "{\"query\": {\"bool\": {\"filter\": [], \"must_not\": []} } }");
        JSONObject wrapper = new JSONObject();
        JSONObject arg = new JSONObject();
        String txStr = enc.getTaxonomyString();
        if (txStr != null) {
            useClauses = true;
            if (txStr.endsWith(" sp")) {
                arg.put("encounterTaxonomy", txStr.substring(0, txStr.length() - 2) + "*");
                wrapper.put("wildcard", arg);
            } else {
                arg.put("encounterTaxonomy", txStr);
                wrapper.put("match", arg);
            }
            query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
            "allowIdentificationWithoutTaxonomy"))) {
            System.out.println(
                "WARNING: getMatchingSetQuery() no taxonomy and allowIdentificationWithoutTaxonomy not set; returning empty set");
            return null;
        }
        // it seems like useClauses=false only ever was used when no taxonomy was present and basically
        // returned every annotation with matchAgainst=T and an acmId
        if (useClauses) {
            if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
                "ignoreViewpointMatching", this.getTaxonomy(myShepherd)))) {
                String[] viewpoints = this.getViewpointAndNeighbors();
                if (viewpoints != null) {
                    arg = new JSONObject();
                    arg.put("viewpoint", new JSONArray(viewpoints));
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    // query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);
                    // to handle allowing null viewpoint, opensearch query gets messy!
                    JSONArray should = new JSONArray(
                        "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"viewpoint\"}}}}]");
                    should.put(wrapper);
                    JSONObject bool = new JSONObject("{\"bool\": {}}");
                    bool.getJSONObject("bool").put("should", should);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        bool);
                }
            }
            // this does either/or part/iaClass - unsure if this is correct
            boolean usedPart = false;
            if (Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(),
                "usePartsForIdentification"))) {
                String part = this.getPartIfPresent();
                if (!Util.stringIsEmptyOrNull(part)) {
                    arg = new JSONObject();
                    arg.put("iaClass", "*" + part);
                    wrapper = new JSONObject();
                    wrapper.put("wildcard", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                    usedPart = true;
                }
            }
            if (!usedPart && (this.getIAClass() != null)) {
                arg = new JSONObject();
                arg.put("iaClass", this.getIAClass());
                wrapper = new JSONObject();
                wrapper.put("match", arg);
                query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                    wrapper);
            }
        }
        // matchAgainst true
        arg = new JSONObject();
        arg.put("matchAgainst", true);
        wrapper = new JSONObject();
        wrapper.put("term", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);

        // must have acmId
        arg = new JSONObject();
        arg.put("field", "acmId");
        wrapper = new JSONObject();
        wrapper.put("exists", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(wrapper);

        // exclude our encounter
        arg = new JSONObject();
        arg.put("encounterId", enc.getId());
        wrapper = new JSONObject();
        wrapper.put("match", arg);
        query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(wrapper);

        // skip dead animals
        Long dateMS = enc.getDateInMillisecondsFallback();
        if (dateMS != null) {
            wrapper = new JSONObject(
                "{\"range\": {\"encounterIndividualTimeOfDeath\": { \"lte\": " + dateMS + " } } }");
            query.getJSONObject("query").getJSONObject("bool").getJSONArray("must_not").put(
                wrapper);
        }
        // now process taskParams
        if (taskParams != null) {
            String userId = taskParams.optString("userId", null);
            JSONObject filt = taskParams.optJSONObject("matchingSetFilter");
            if (filt != null) {
                // locationId=FOO and locationIds=[FOO,BAR]
                boolean useNullLocation = false;
                List<String> rawLocationIds = new ArrayList<String>();
                String tmp = Util.basicSanitize(filt.optString("locationId", null));
                if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
                JSONArray larr = filt.optJSONArray("locationIds");
                if (larr != null) {
                    for (int i = 0; i < larr.length(); i++) {
                        tmp = Util.basicSanitize(larr.optString(i));
                        if ("__NULL__".equals(tmp)) {
                            useNullLocation = true;
                        } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
                            rawLocationIds.add(tmp);
                        }
                    }
                }
                List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
                if (expandedLocationIds.size() > 0) {
                    arg = new JSONObject();
                    arg.put("encounterLocationId", new JSONArray(expandedLocationIds));
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    if (useNullLocation) {
                        JSONArray should = new JSONArray(
                            "[{\"bool\": {\"must_not\": {\"exists\": {\"field\": \"encounterLocationId\"}}}}]");
                        should.put(wrapper);
                        JSONObject bool = new JSONObject("{\"bool\": {}}");
                        bool.getJSONObject("bool").put("should", should);
                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                            "filter").put(bool);
                    } else {
                        query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                            "filter").put(wrapper);
                    }
                }
                // owner ... which requires we have userId in the taskParams
                JSONArray owner = filt.optJSONArray("owner");
                JSONArray uids = new JSONArray();
                if ((owner != null) && (userId != null)) {
                    for (int i = 0; i < owner.length(); i++) {
                        String opt = owner.optString(i, null);
                        if (!Util.stringExists(opt)) continue;
                        if (opt.equals("me")) {
                            uids.put(userId);
                        } else {
                            uids.put(opt);
                        }
                    }
                }
                if (uids.length() > 0) {
                    arg = new JSONObject();
                    arg.put("encounterUserUuid", uids);
                    wrapper = new JSONObject();
                    wrapper.put("terms", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                }
                // projectId
                String projectId = filt.optString("projectId", null);
                if (Util.stringExists(projectId)) {
                    arg = new JSONObject();
                    arg.put("encounterProjectIds", projectId);
                    wrapper = new JSONObject();
                    wrapper.put("match", arg);
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray("filter").put(
                        wrapper);
                }
            }
        }
        /* saving this for possible future passing raw queries
           JSONArray arr = additionalQuery.optJSONArray("filter");
           if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject clause = arr.optJSONObject(i);
                if (clause != null)
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                        "filter").put(clause);
            }
           }
           arr = additionalQuery.optJSONArray("must_not");
           if (arr != null) {
            for (int i = 0; i < arr.length(); i++) {
                JSONObject clause = arr.optJSONObject(i);
                if (clause != null)
                    query.getJSONObject("query").getJSONObject("bool").getJSONArray(
                        "must_not").put(clause);
            }
           }
         */
        System.out.println("getMatchingSetQuery() returning query=" + query.toString(4));
        return query;
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
        return getMatchingSet(myShepherd, null, true);
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams) {
        return getMatchingSet(myShepherd, taskParams, true);
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject taskParams,
        boolean useClauses) {
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        JSONObject query = getMatchingSetQuery(myShepherd, taskParams, useClauses);
        OpenSearch os = new OpenSearch();
        long startTime = System.currentTimeMillis();

        if (query == null) return anns;
        JSONObject queryRes = null;
        int hitSize = -1;
        try {
            int pageSize = 10000;
            try {
                pageSize = os.getSettings("annotation").optInt("max_result_window", 10000);
            } catch (Exception ex) {}
            os.deletePit("annotation");
            queryRes = os.queryPit("annotation", query, 0, pageSize, null, null);
            hitSize = queryRes.optJSONObject("hits").optJSONObject("total").optInt("value");
        } catch (Exception ex) {
            System.out.println("getMatchingSet() exception: " + ex);
            ex.printStackTrace();
        }
        JSONArray hits = OpenSearch.getHits(queryRes);
        for (int i = 0; i < hits.length(); i++) {
            JSONObject hit = hits.optJSONObject(i);
            if (hit == null) continue;
            Annotation ann = myShepherd.getAnnotation(hit.optString("_id", null));
            if (ann != null) anns.add(ann);
        }
        System.out.println("getMatchingSet() results: hitSize=" + hitSize + "; hits length=" +
            hits.length() + "; anns size=" + anns.size() + "; " +
            (System.currentTimeMillis() - startTime) + "ms");
        return anns;
    }

    /*
        sorta weird to have this in here, but it is inherently linked with getMatchingSetXXX() above ...
        this is a string that uniquely identifies the matchingSet, dependent of content (e.g. cant be based on content uuids)
     */
    public String getCurvrankDailyTag(JSONObject taskParams) {
        if (taskParams == null) return null;
        String userId = taskParams.optString("userId", null);
        JSONObject j = taskParams.optJSONObject("matchingSetFilter");
        if (j == null) return null;
        String tag = "";

        // currently we have only owner=me, which requires a userId
        JSONArray owner = j.optJSONArray("owner");
        boolean mineOnly = false;
        if ((owner != null) && (userId != null)) {
            for (int i = 0; i < owner.length(); i++) {
                if ("me".equals(owner.optString(i, null))) mineOnly = true;
            }
        }
        if (mineOnly) {
            tag += "user:" + userId;
        } else {
            tag += "user:ANY";
        }
        // now locations, which we want sorted and lowercase, to ensure consistency in multi-value names
        List<String> locs = new ArrayList<String>();
        if (j.optString("locationId", null) != null) locs.add(j.getString("locationId"));
        JSONArray larr = j.optJSONArray("locationIds");
        if (larr != null) {
            for (int i = 0; i < larr.length(); i++) {
                String val = larr.optString(i, null);
                if ((val != null) && !locs.contains(val)) locs.add(val);
            }
        }
        if (locs.size() > 0) {
            Collections.sort(locs);
            tag += ";locs:" + String.join(",", locs);
        }
        int maxLength = 128;
        if (tag.length() > maxLength) {
            String orig = tag;
            tag = DigestUtils.md5Hex(tag) + tag.substring(0, maxLength - 32);
            System.out.println("INFO: getCurvrankDailyTag() using hashed " + tag + " for " + orig);
        }
        return tag;
    }

    public String findIndividualId(Shepherd myShepherd) {
        Encounter enc = this.findEncounter(myShepherd);

        if ((enc == null) || !enc.hasMarkedIndividual()) return null;
        return enc.getIndividualID(); // is this one of those things that can be "None" ?
    }

    // convenience!
    public Encounter findEncounter(Shepherd myShepherd) {
        return Encounter.findByAnnotation(this, myShepherd);
    }

    // this is a little tricky. the idea is the end result will get us an Encounter, which *may* be new
    // if it is new, its pretty straight forward (uses findEncounter) .. if not, creation is as follows:
    // look for "sibling" Annotations on same MediaAsset.  if one of them has an Encounter, we clone that.
    // additionally, if one is a trivial annotation, we drop it after.  if no siblings are found, we create
    // an Encounter based on this Annotation (which may get us something, e.g. species, date, loc)
    public Encounter toEncounterDEPRECATED(Shepherd myShepherd) {
        // fairly certain this will *never* happen as code currently stands.  this (Annotation) is always new, and
        // therefore unattached to any Encounter for sure.   so skipping this for now!
        ////Encounter enc = this.findEncounter(myShepherd);

        // rather, we straight up find sibling Annotations, and look at them...
        List<Annotation> sibs = this.getSiblings();

        if ((sibs == null) || (sibs.size() < 1)) { // no sibs, we make a new Encounter!
            Encounter enc = new Encounter(this);
            if (CommonConfiguration.getProperty("encounterState0",
                myShepherd.getContext()) != null) {
                enc.setState(CommonConfiguration.getProperty("encounterState0",
                    myShepherd.getContext()));
            }
            return enc;
        }
        /* TODO: evaluate what of these notes are necessary
            ok, we have sibling Annotations.  if one is trivial, we just go for it and replace that one.
            is this wise?   well, if it is the *only* sibling then probably the MediaAsset was attached to the Annotation using legacy (non-IA)
               methods, and we are "zooming in" on the actual animal.  or *one of* the actual animals -- if there are others, they should get added in
               subsequent iterations of toEncounter().
            in theory.

            the one case that is still to be considered is when (theoretically) detection *improves* and we will want a new detection to
               replace a *non-trivial* Annotation.  but we arent considering that just now!
         */

        // so now we look for a trivial annot to replace.
        // we currently now allow (via import!) there to be *more than one trivial annotation* on a media asset!
        // as such, this will find the first trivial available and use that.  since we have no way to know which annot
        // from detection lines up with which encounter (via the trivial annot), we just randomly replace basically.  alas!
        Encounter someEnc = null; // this is in case we fall thru (no trivial annot), we can clone some of this for new one
        for (Annotation ann : sibs) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (ann.isTrivial()) {
                ann.setMatchAgainst(false);
                if (enc == null) { // weird case, but yneverknow (trivial annot with no encounter?)
                    ann.detachFromMediaAsset(); // but this.annot is now on asset, so we are good: kill ann!
                } else {
                    // this also does the detachFromMediaAsset() for us
                    enc.replaceAnnotation(ann, this);
                    return enc; // our work is done here
                }
                break;
            }
            if (someEnc == null) someEnc = enc; // use the first one we find to base new one (below) off of, if necessary
        }
        // do we have an an encounter from the sibling?
        if (someEnc != null) {
            for (Annotation ann : sibs) {
                if ((ann.getIAClass() == null || this.getIAClass() == null) ||
                    ann.getIAClass().equals(this.getIAClass())) { break; }
                // if these two intersect and have a different detected class they are allowed to reside on the same encounter
                if (this.intersects(ann)) {
                    someEnc.addAnnotation(this);
                    someEnc.setDWCDateLastModified();
                    return someEnc;
                }
            }
        }
        // if we fall thru, we have no trivial annot, so just get a new Encounter for this Annotation
        Encounter newEnc = null;
        if (someEnc == null) {
            newEnc = new Encounter(this);
        } else { // copy some stuff from sibling
            try {
                newEnc = someEnc.cloneWithoutAnnotations(myShepherd);
                newEnc.addAnnotation(this);
                newEnc.setDWCDateAdded();
                newEnc.setDWCDateLastModified();
                newEnc.resetDateInMilliseconds();
                newEnc.setSpecificEpithet(someEnc.getSpecificEpithet());
                newEnc.setGenus(someEnc.getGenus());
                newEnc.setSex(null);
            } catch (Exception e) {
                e.printStackTrace();
            }
            try {
                myShepherd.beginDBTransaction();
                Occurrence occ = myShepherd.getOccurrence(someEnc);
                if (occ == null) {
                    occ = new Occurrence(Util.generateUUID(), someEnc);
                    myShepherd.storeNewOccurrence(occ);
                }
                occ.addEncounterAndUpdateIt(newEnc);
                occ.setDWCDateLastModified();
                myShepherd.commitDBTransaction();
            } catch (Exception e) {
                e.printStackTrace();
                myShepherd.rollbackDBTransaction();
            }
        }
        if (CommonConfiguration.getProperty("encounterState0", myShepherd.getContext()) != null) {
            newEnc.setState(CommonConfiguration.getProperty("encounterState0",
                myShepherd.getContext()));
        }
        return newEnc;
    }

    public Annotation revertToTrivial(Shepherd myShepherd)
    throws IOException {
        return this.revertToTrivial(myShepherd, false);
    }

    public Annotation revertToTrivial(Shepherd myShepherd, boolean force)
    throws IOException {
        if (this.isTrivial()) throw new IOException("Already a trivial Annotation: " + this);
        Encounter enc = this.findEncounter(myShepherd);
        if (enc == null)
            throw new IOException("Unable to find corresponding Encounter for " + this);
        MediaAsset ma = this.getMediaAsset();
        if (ma == null)
            throw new IOException("Unable to find corresponding MediaAsset for " + this);
        if (!force && (ma.getFeatures() != null) && (ma.getFeatures().size() > 1))
            throw new IOException("Sibling Annotations detected on " + ma +
                    "; cannot revert to trivial " + this);
        Annotation triv = new Annotation(this.species, ma); // not going to set IAClass or anything since starting fresh
        enc.removeAnnotation(this);
        this.setMatchAgainst(false);
        this.detachFromMediaAsset();
        enc.addAnnotation(triv);
        System.out.println("INFO: revertToTrivial() created annot=" + triv.getId() + " on enc=" +
            enc.getCatalogNumber() + ", replacing annot=" + this.getId());
        return triv;
    }

    // creates a new Annotation with the basic properties duplicated (but no "linked" objects, like Features etc)
    public Annotation shallowCopy() {
        Annotation ann = new Annotation();

        ann.id = Util.generateUUID();
        ann.species = this.species;
        ann.name = this.name;
        ann.isExemplar = this.isExemplar;
        ann.identificationStatus = this.identificationStatus;
        return ann;
    }

    public static Base createFromApi(JSONObject payload, List<File> files, Shepherd myShepherd)
    throws ApiException {
        if (payload == null) throw new ApiException("empty payload");
        User user = (User)payload.opt("_currentUser");
        int maId = (Integer)validateFieldValue("mediaAssetId", payload);
        int x = (Integer)validateFieldValue("x", payload);
        int y = (Integer)validateFieldValue("y", payload);
        int width = (Integer)validateFieldValue("width", payload);
        int height = (Integer)validateFieldValue("height", payload);
        double theta = (Double)validateFieldValue("theta", payload);
        String iaClass = (String)validateFieldValue("iaClass", payload);
        String viewpoint = (String)validateFieldValue("viewpoint", payload);
        // dont need to validate encId, as it is optional and we load encounter below which effectively validates if set
        // UPDATE: switching gears on this -- now requiring encounter; but leaving this code as-is in case we switch back
        // (see comments below as well)
        String encId = payload.optString("encounterId", null);
        JSONObject error = new JSONObject();
        MediaAsset ma = MediaAssetFactory.load(maId, myShepherd);
        if (ma == null) {
            error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
            error.put("fieldName", "mediaAssetId");
            error.put("value", maId);
            throw new ApiException("invalid MediaAsset id=" + maId, error);
        }
        Encounter enc = null;
        if (encId != null) {
            enc = myShepherd.getEncounter(encId);
            if (enc == null) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("fieldName", "encounterId");
                error.put("value", encId);
                throw new ApiException("invalid Encounter id=" + maId, error);
            }
            // TODO manualAnnotation.jsp did *not* restrict who can edit which encounter, as long as they had researcher role
            // should this be locked down tighter as to who can add an annotation to an encounter?
        }
        // as noted above, last-minute decision to make an encounter required:
        if (enc == null) {
            error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
            error.put("fieldName", "encounterId");
            throw new ApiException("Encounter required", error);
        }
        // validate iaClass; this is a little janky
        IAJsonProperties iaConf = IAJsonProperties.iaConfig();
        if (enc != null) {
            Taxonomy tx = enc.getTaxonomy(myShepherd);
            if (!iaConf.isValidIAClass(tx, iaClass)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("fieldName", "iaClass");
                error.put("value", iaClass);
                throw new ApiException("iaClass=" + iaClass + " invalid for taxonomy " + tx +
                        " on " + enc, error);
            }
        }
        // must have all we need now
        String context = myShepherd.getContext();
        List<Annotation> annots = ma.getAnnotations(); // get before we add ours
        FeatureType.initAll(myShepherd);
        JSONObject fparams = new JSONObject();
        fparams.put("x", x);
        fparams.put("y", y);
        fparams.put("width", width);
        fparams.put("height", height);
        fparams.put("theta", theta);
        fparams.put("viewpoint", viewpoint); // not sure when/how this is used, but seems here historically
        fparams.put("_manualAnnotationViaApiV3", System.currentTimeMillis());
        Feature ft = new Feature("org.ecocean.boundingBox", fparams);
        Annotation ann = new Annotation(null, ft, iaClass);
        ann.setViewpoint(viewpoint);
        ma.addFeature(ft);
        ma.setDetectionStatus("complete");
        myShepherd.getPM().makePersistent(ft);
        myShepherd.getPM().makePersistent(ann);
/*
        believe this is overly complicated, but saving it from manualAnnotation.jsp logic
        if (enc != null) {
            if (IBEISIA.validForIdentification(ann, context) && iaConf.isValidIAClass(enc.getTaxonomy(myShepherd), iaClass)) {
                ann.setMatchAgainst(true);
            }
        }
 */
        // NOTE: manualAnnotation.jsp once allowed featureId to be passed; that functionality is not handled here
        if (enc != null) { // note: we currently *require* enc, so this should always be true
            ann.setMatchAgainst(true);
            // !NOTE! this first set of logic to set cloneEncounter is copied from manualAnnotation.jsp
            // i believe this logic is flawed! it is left for reference/research/consideration
            // please see instead the block following this where new logic is applied  -jon 2025-10-17
            boolean cloneEncounter = false;
            // we would expect at least a trivial annotation, so if annots>=2, we know we need to clone
            if ((annots.size() > 1) && (iaClass != null)) {
                System.out.println("DEBUG Annotation.createFromApi(): cloneEncounter [0]");
                cloneEncounter = true;

                // also don't clone if this is a part
                // if the one annot isn't trivial, then we have to clone the encounter as well
            } else if ((annots.size() == 1) && !annots.get(0).isTrivial() && (iaClass != null) &&
                (iaClass.indexOf("+") == -1)) {
                System.out.println("DEBUG Annotation.createFromApi(): cloneEncounter [1]");
                cloneEncounter = true;
                // exception case - if there is only one annotation and it is a part
                Annotation annot1 = annots.get(0);
                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
                    System.out.println("DEBUG Annotation.createFromApi(): cloneEncounter [2]");
                    cloneEncounter = false;
                }
                // exception case - if there is only one annotation and it is a part
            } else if ((annots.size() == 1) && !annots.get(0).isTrivial() && (iaClass != null) &&
                (iaClass.indexOf("+") > -1)) {
                System.out.println("DEBUG Annotation.createFromApi(): cloneEncounter [3]");
                Annotation annot1 = annots.get(0);
                if ((annot1.getIAClass() != null) && (annot1.getIAClass().indexOf("+") != -1)) {
                    System.out.println("DEBUG Annotation.createFromApi(): cloneEncounter [4]");
                    cloneEncounter = true;
                }
            }
            // here is the new logic. this will hopefully side-step the problem where the enc was getting
            // cloned far more often than it should. i believe this was due to the fact that annots was
            // from the media asset and therefore could be pointing to all kinds of *other* encounters,
            // rather than just focusing on the connection between ma and enc
            cloneEncounter = false; // start over
            if (!ann.isPart()) { // we can skip this whole thing if we are adding a part
                List<Annotation> encAnnots = enc.getAnnotations(ma);
                System.out.println("DEBUG Annotation.createFromApi(): encAnnots = " + encAnnots);
                // we see if we have a non-part annot, which would force us to clone (parts we ignore)
                for (Annotation eann : encAnnots) {
                    if (eann.isPart()) continue;
                    // trivial *should* be replaced below (see foundTrivial) ... i guess there is a weird
                    // chance of more than one trivial being on this asset, but thats probably bad news anyway
                    // we dont clone encounter since we will drop this trivial annot (then add new one to enc)
                    if (eann.isTrivial()) continue;
                    System.out.println(
                        "DEBUG Annotation.createFromApi(): cloneEncounter [5] forcing cloneEncounter due to "
                        + eann);
                    cloneEncounter = true;
                }
            }
            //handle multiple parts case, such as a pre-existing elphant+head 
            // and a new elephant+head added in a single image
            else{
                List<Annotation> encAnnots = enc.getAnnotations(ma);
                System.out.println("DEBUG Annotation.createFromApi(): encAnnots = " + encAnnots);
                // we see if we have a non-part annot, which would force us to clone (parts we ignore)
                for (Annotation eann : encAnnots) {
                    if (!eann.isPart()) continue;
                    // trivial *should* be replaced below (see foundTrivial) ... i guess there is a weird
                    // chance of more than one trivial being on this asset, but thats probably bad news anyway
                    // we dont clone encounter since we will drop this trivial annot (then add new one to enc)
                    if (eann.isTrivial()) continue;
                    System.out.println(
                        "DEBUG Annotation.createFromApi(): cloneEncounter [5] forcing multiple partscloneEncounter due to "
                        + eann);
                    cloneEncounter = true;
                }
            }
            if (cloneEncounter) {
                try {
                    Encounter clone = enc.cloneWithoutAnnotations(myShepherd);
                    clone.addAnnotation(ann);
                    clone.addComments("<p data-annot-id=\"" + ann.getId() +
                        "\">Encounter cloned and <i>new Annotation</i> manually added by " +
                        user.getDisplayName() + "</p>");
                    myShepherd.getPM().makePersistent(clone);
                    Occurrence occ = myShepherd.getOccurrence(enc);
                    if (occ != null) {
                        occ.addEncounterAndUpdateIt(clone);
                        occ.setDWCDateLastModified();
                    } else {
                        // let's create an occurrence to link these two Encounters
                        occ = new Occurrence(Util.generateUUID(), clone);
                        occ.addEncounter(enc);
                        myShepherd.getPM().makePersistent(occ);
                    }
                    System.out.println("Annotation.createFromApi(): " + ann + " added to clone " +
                        clone + " in " + occ);
                    myShepherd.updateDBTransaction();
                } catch (Exception ex) {
                    throw new ApiException("cloning encounter " + enc.getId() + " failed: " +
                            ex.toString());
                }
            } else { // not cloned
                enc.addAnnotation(ann);
                enc.addComments("<p data-annot-id=\"" + ann.getId() +
                    "\"><i>new Annotation</i> manually added by " + user.getDisplayName() + "</p>");
                System.out.println("Annotation.createFromApi(): " + ann + " added to " + enc);
            }
        }
        // NOTE: manualAnnotation.jsp allowed 'removeTrivial' (boolean) to be set via url, but was default true
        Annotation foundTrivial = null; // note this will only remove (at most) ONE (but "should never" have > 1 anyway)
        for (Annotation a : ma.getAnnotations()) {
            if (a.isTrivial()) foundTrivial = a;
        }
        if (foundTrivial == null) {
            System.out.println(
                "Annotation.createFromApi(): no trivial annotation found to remove from " + ma);
        } else {
            foundTrivial.detachFromMediaAsset();
            if (enc == null) {
                System.out.println("Annotation.createFromApi(): removeTrivial detached " +
                    foundTrivial + " (and Feature) from " + ma);
            } else {
                enc.removeAnnotation(foundTrivial);
                System.out.println("Annotation.createFromApi(): removeTrivial detached " +
                    foundTrivial + " (and Feature) from " + ma + " and " + enc);
            }
        }
        // send to IA as needed
        try {
            if (ma.getAcmId() == null) {
                ArrayList<MediaAsset> mas = new ArrayList<MediaAsset>();
                mas.add(ma);
                IBEISIA.sendMediaAssetsNew(mas, context);
            }
            ArrayList<Annotation> anns = new ArrayList<Annotation>();
            anns.add(ann);
            IBEISIA.sendAnnotationsNew(anns, context, myShepherd);
        } catch (Exception ex) {} // silently fail; they will be synced up later
        return ann;
    }

    public static Object validateFieldValue(String fieldName, JSONObject data)
    throws ApiException {
        if (data == null) throw new ApiException("empty payload");
        JSONObject error = new JSONObject();
        error.put("fieldName", fieldName);
        String exMessage = "invalid value for " + fieldName;
        Object returnValue = null;
        switch (fieldName) {
        case "mediaAssetId":
            returnValue = data.optInt(fieldName, 0);
            if ((int)returnValue < 1) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            break;

        case "width":
        case "height":
            // value must be > 0 (also will catch unset)
            returnValue = data.optInt(fieldName, -1);
            if ((int)returnValue < 1) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "x":
        case "y":
            // x/y can be negative or zero, but they are required
            // little hacky as prevents this actual x/y value, but um...
            returnValue = data.optInt(fieldName, -999999);
            if ((int)returnValue == -999999) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            break;

        case "theta":
            returnValue = data.optDouble(fieldName, 9999.9);
            double dval = (double)returnValue;
            if (dval == 9999.9) {
                returnValue = 0.0d; // theta (passed-in) is optional, but results in 0.0
            } else if ((dval < -6.2832) || (dval > 6.2832)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", dval);
                throw new ApiException("invalid theta value in radians", error);
            }
            break;

        case "viewpoint":
            returnValue = data.optString(fieldName, null);
            if (returnValue == null) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            if (!isValidViewpoint((String)returnValue)) {
                error.put("code", ApiException.ERROR_RETURN_CODE_INVALID);
                error.put("value", returnValue);
                throw new ApiException(exMessage, error);
            }
            break;

        case "iaClass":
            // TODO is iaClass required???
            returnValue = data.optString(fieldName, null);
            if (returnValue == null) {
                error.put("code", ApiException.ERROR_RETURN_CODE_REQUIRED);
                throw new ApiException(exMessage, error);
            }
            // validity is checked in main createFromApi
            break;

        default:
            System.out.println("Encounter.validateFieldValue(): WARNING unsupported fieldName=" +
                fieldName);
        }
        return returnValue;
    }

    public List<Task> getRootIATasks(Shepherd myShepherd) { // convenience
        return Task.getRootTasksFor(this, myShepherd);
    }

    public int detachFromTasks(Shepherd myShepherd) {
        List<Task> tasks = Task.getTasksFor(this, myShepherd);

        if (Util.collectionIsEmptyOrNull(tasks)) return 0;
        for (Task task : tasks) {
            task.removeObject(this);
        }
        return tasks.size();
    }

    public static boolean isValidViewpoint(String vp) {
        if (vp == null) return true;
        return getAllValidViewpoints().contains(vp);
    }

    public static List<String> getAllValidViewpoints() {
        // add code to limit based on IA.properties viewpoints enabled switches
        List<String> all = new ArrayList<>();

        for (int i = 0; i < VALID_VIEWPOINTS.length; i++) {
            Collections.addAll(all, VALID_VIEWPOINTS[i]);
        }
        return all;
    }

    public static Set<String> getAllValidViewpointsSorted() {
        return new TreeSet<String>(getAllValidViewpoints());
    }

    public static ArrayList<Encounter> checkForConflictingIDsforAnnotation(Annotation annot,
        String proposedIndividualIDForEncounter, Shepherd myShepherd) {
        ArrayList<Encounter> conflictingEncs = new ArrayList<Encounter>();
        String filter =
            "SELECT FROM org.ecocean.Encounter WHERE individual!=null && individual.individualID != \""
            + proposedIndividualIDForEncounter +
            "\" && annotations.contains(annot1) && annot1.acmId == \"" + annot.getAcmId() +
            "\" VARIABLES org.ecocean.Annotation annot1";
        Query q = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection)(q.execute());

        conflictingEncs = new ArrayList<Encounter>(c);
        q.closeAll();
        return conflictingEncs;
    }

/*
    these will update(/create) AnnotationLite.cache for this Annotation. should only happen when (related) taxonomy or
       individual or validForIdentification changes
 */
    public void refreshLiteTaxonomy(String tax) {
        if (this.acmId == null) return;
        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
        if (annl == null) {
            annl = new AnnotationLite(null, tax); // indiv = null here, but it is new so its what we got. :/
        } else {
            annl.setTaxonomy(tax);
        }
        Util.mark("Annotation.refreshLiteTaxonomy() refreshing " + this.acmId);
        AnnotationLite.setCache(this.acmId, annl);
    }

    public void refreshLiteIndividual(String indiv) {
        if (this.acmId == null) return;
        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
        if (annl == null) {
            annl = new AnnotationLite(indiv);
        } else {
            annl.setIndividualId(indiv);
        }
        Util.mark("Annotation.refreshLiteIndividual() refreshing " + this.acmId);
        AnnotationLite.setCache(this.acmId, annl);
    }

    public void refreshLiteValid(Boolean validForId) {
        if (this.acmId == null) return;
        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
        if (annl == null) {
            annl = new AnnotationLite(validForId);
        } else {
            annl.setValidForIdentification(validForId);
        }
        Util.mark("Annotation.refreshLiteValid() refreshing " + this.acmId);
        AnnotationLite.setCache(this.acmId, annl);
    }

    public boolean contains(Annotation ann) {
        Rectangle myRect = getRect(this);
        Rectangle queryRect = getRect(ann);

        if ((myRect == null) || (queryRect == null)) return false;
        return myRect.contains(queryRect);
    }

    public boolean intersects(Annotation ann) {
        Rectangle myRect = getRect(this);
        Rectangle queryRect = getRect(ann);

        if ((myRect == null) || (queryRect == null)) return false;
        return myRect.intersects(queryRect);
    }

    public boolean intersectsAtLeastOne(List<Annotation> anns) {
        if (Util.collectionIsEmptyOrNull(anns)) return false;
        for (Annotation ann : anns) {
            if (intersects(ann)) return true;
        }
        return false;
    }

    // they all are chained together; basically no gap between any single *or cluster* of these annots
    // note: this skips all trivial annots, cuz those are always going to intersect everything
    public static boolean areContiguous(List<Annotation> anns) {
        if (Util.collectionIsEmptyOrNull(anns)) return false;
        List<Annotation> nonTrivial = new ArrayList<Annotation>();
        for (Annotation ann : anns) {
            if (!ann.isTrivial()) nonTrivial.add(ann);
        }
        System.out.println("areContiguous() has nonTrivial=" + nonTrivial);
        if (nonTrivial.size() < 1) return false;
        if (nonTrivial.size() == 1) return true;
        // if they're a body and a part, consider them contiguous
        if (nonTrivial.size() == 2) {
            String iaClass0 = nonTrivial.get(0).getIAClass();
            String iaClass1 = nonTrivial.get(1).getIAClass();
            if (iaClass0 != null && iaClass1 != null) {
                if (iaClass0.indexOf("+") > -1 && iaClass1.indexOf("+") == -1) return true;
                if (iaClass1.indexOf("+") > -1 && iaClass0.indexOf("+") == -1) return true;
            }
        }
        Annotation first = nonTrivial.remove(0);
        return (first.intersectsAtLeastOne(nonTrivial) && areContiguous(nonTrivial)); // yay recursion!
    }

    private Rectangle getRect(Annotation ann) {
        try {
            if (ann.getBbox() == null) return null;
            int[] bBox = ann.getBbox();
            int x = bBox[0];
            int y = bBox[1];
            int width = bBox[2];
            int height = bBox[3];
            return new Rectangle(x, y, width, height);
        } catch (NumberFormatException nfe) {
            nfe.printStackTrace();
        }
        return null;
    }

    public int hashCode() {
        if (id == null) return Util.generateUUID().hashCode(); // random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
    }
}
