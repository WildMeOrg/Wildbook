


/*
  TODO note: this is very ibeis-specific concept of "Annotation"
     we should probably consider a general version which can be manipulated into an ibeis one somehow
*/

package org.ecocean;

import org.ecocean.ImageAttributes;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.acm.AcmBase;
import org.ecocean.identity.IBEISIA;
import org.ecocean.ia.Task;
import org.json.JSONArray;
import org.ecocean.ia.IA;
import org.json.JSONObject;
import org.apache.commons.lang3.builder.ToStringBuilder;
import javax.jdo.Query;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.collections.CollectionUtils;


//import java.time.LocalDateTime;

public class Annotation implements java.io.Serializable {
    public Annotation() {}  //empty for jdo
    private String id;  //TODO java.util.UUID ?
    private static final String[][] VALID_VIEWPOINTS = new String[][] {
        {"up",        "up",            "up",        "up",            "up",       "up",           "up",        "up",          },
        {"upfront",   "upfrontright",  "upright",   "upbackright",   "upback",   "upbackleft",   "upleft",    "upfrontleft"  },
        {"front",     "frontright",    "right",     "backright",     "back",     "backleft",     "left",      "frontleft"    },
        {"downfront", "downfrontright","downright", "downbackright", "downback", "downbackleft", "downleft",  "downfrontleft"},
        {"down",      "down",          "down",      "down",          "down",     "down",         "down",      "down"         }
};
    private String species; 

    private String iaClass; // This is just how it gonna be for now. Swap the methods to draw from Taxonomy later if ya like?

    private String name;
    private boolean isExemplar = false;
    private Boolean isOfInterest = null;  //aka AoI (Annotation of Interest)
    protected String identificationStatus;
    private ArrayList<Feature> features;
    protected String acmId;

    //this is used to decide "should we match against this"  problem is: that is not very (IA-)algorithm agnostic
    //  hoping this will be obsoleted by ACM and friends
    private boolean matchAgainst = false;

////// these will go away after transition to Features
    private int x;
    private int y;
    private int width;
    private int height;
    private float[] transformMatrix;
    private double theta;

    // quality indicates the fidelity of the annotation, e.g. the overall image quality of a picture.
    // This is useful e.g. for researchers who want to account for a bias where "better" images are 
    // more likely to produce matches.
    private Double quality;
    // distinctiveness indicates the real-wold distinctiveness of the feature *being recorded*, independent
    // of the recording medium. Useful e.g. for researchers who want to account for a bias where more distinct
    // animals like one with a large scar are easier to re-sight (match).
    private Double distinctiveness;
    private String viewpoint;
    //*'annot_yaw': 'REAL',
    //~'annot_detect_confidence': 'REAL',
    //~'annot_exemplar_flag': 'INTEGER',
    //~'annot_note': 'TEXT',
    //~'annot_visual_uuid': 'UUID',
    //~'annot_semantic_uuid': 'UUID',
    //*'annot_quality': 'INTEGER',
    //~'annot_tags': 'TEXT',

    private MediaAsset mediaAsset = null;
////// end of what will go away


    //the "trivial" Annotation - will have a single feature which references the total MediaAsset
    public Annotation(String species, MediaAsset ma) {
        this(species, ma.generateUnityFeature());
    }

    //single feature convenience constructor
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

    //For setting the iaClass returned from detection... No more mangled species names sent to identification
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

/*
    public Annotation(MediaAsset ma, String species, int x, int y, int w, int h, float[] tm) {
        this.id = org.ecocean.Util.generateUUID();
        this.x = x;
        this.y = y;
        this.width = w;
        this.height = h;
        this.transformMatrix = tm;
        this.theta = 0.0;
        this.species = species;
        this.mediaAsset = ma;
    }
*/

    //this is for use *only* to migrate old-world Annotations to new-world
    public Feature migrateToFeatures() {
        Feature f;
        if (isTrivial()) { //this gets special "unity" feature, which means the whole thing basically
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
    public void setFeatures(ArrayList<Feature> f) {
        features = f;
    }
    public void addFeature(Feature f) {
        if (features == null) features = new ArrayList<Feature>();
        if (!features.contains(f)) features.add(f);
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
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

    //transform is not empty or "useless" (e.g. identity)
    public boolean needsTransform() {
        return Util.isIdentityMatrix(transformMatrix);
    }

    public float[] getTransformMatrixClean() {
        if (!needsTransform()) return new float[]{1,0,0,1,0,0};
        return transformMatrix;
    }

    public Taxonomy getTaxonomy(Shepherd myShepherd) {
        Encounter enc = findEncounter(myShepherd);
        if (enc == null) return null;
        return enc.getTaxonomy(myShepherd);
    }

    //TODO this needs to be fixed to mean "has the unity feature"... i think(!?) -- but migrating to features needs a legacy-compatible version!  ouch
    //       good luck on that one, jon
    public boolean isTrivial() {
        MediaAsset ma = this.getMediaAsset();
        if (ma == null) return false;
        for (Feature ft : getFeatures()) {
            if (ft.isUnity()) return true;  //TODO what *really* of multiple features?? does "just one unity" make sense?
        }
        //see note above. this is to attempt to be backwards-compatible.  :/  "untested"
        return (!needsTransform() && (getWidth() == (int)ma.getWidth()) && (getHeight() == (int)ma.getHeight()));
    }

    public double getTheta() {
        return theta;
    }
    public void setTheta(double t) {
        theta = t;
    }
    public boolean isIAReady() {
        MediaAsset ma = this.getMediaAsset();
        if (ma==null) return false;
        if (ma.getHeight()==0 && ma.getWidth()==0) return false;
        return true;
    }

    public String getViewpoint() {
        return viewpoint;
    }
    //this returns this viewpoint, plus one to either side
    //  (will return null if unset or invalid)
    public String[] getViewpointAndNeighbors() {
        return getViewpointAndNeighbors(this.viewpoint);
    }

    private boolean isViewpointPrimary() {
        List<String> primaryList = new ArrayList<>();
        // I guess primary vp's should never be something that changes?
        Collections.addAll(primaryList, new String[]{ "front" , "right" ,"back" , "left" , "up" , "down" });
        if (primaryList.contains(this.getViewpoint())) {
            return true;
        }
        return false;
    }

    //(viewpoint == null || viewpoint == 'up' || viewpoint == 'upfront' || viewpoint == 'upfrontright'
    // || viewpoint == 'upright' || viewpoint == 'upbackright' || viewpoint == 'upback' 
    // || viewpoint == 'upbackleft' || viewpoint == 'upfront' || viewpoint == 'upfrontleft')

    public static String[] getViewpointAndNeighbors(String vp) {
        List<String> rtn = new ArrayList<>();
        try { 
            System.out.println("Input vp to getViewpointAndNeighbors: "+vp);
            if ((vp == null) || !isValidViewpoint(vp)) return null;
            for (int i=0;i<VALID_VIEWPOINTS.length;i++) {
                String[] innerArr = VALID_VIEWPOINTS[i];
                for (int j=0;j<innerArr.length;j++) {
                    if (vp.equals(VALID_VIEWPOINTS[i][j])) {
                        //cases: up, down, side edge, lower or upper. 
                        // always want the center viewpoint    
                        rtn.add(vp);
                        //start with top & bottom EZ cases..
                        if (i==0) {
                            rtn.addAll(Arrays.asList(VALID_VIEWPOINTS[1]));
                            break;
                        } else if (i==VALID_VIEWPOINTS.length-1) {
                            rtn.addAll(Arrays.asList(VALID_VIEWPOINTS[VALID_VIEWPOINTS.length-2]));
                            break;
                        }
                        for (int h=-1;h<2;h++) {
                            for (int w=-1;w<2;w++) {
                                // gettin trixy.. wrap indexes around 
                                int horizontal = j+w;
                                if (horizontal==-1) {
                                    horizontal=VALID_VIEWPOINTS[1].length-1;
                                } else if (horizontal==8) {
                                    horizontal=0;
                                }
                                if (!rtn.contains(VALID_VIEWPOINTS[i+h][horizontal])) {
                                    rtn.add(VALID_VIEWPOINTS[i+h][horizontal]);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        System.out.println("Found these Viewpoints in getViewpointAndNeighbors: "+rtn.toString());
        if (rtn.size()==0) return null;
        String[] rtnArr = new String[rtn.size()];
        return rtn.toArray(rtnArr);
    }

    public void setViewpoint(String v) {
        viewpoint = v;
    }
    //  note!  this can block and take a while if IA has yet to compute the viewpoint!
    public String setViewpointFromIA(String context) throws IOException {
        if (acmId == null) throw new IOException(this + " does not have acmId set; cannot get viewpoint from IA");
        try {
            JSONObject resp = IBEISIA.iaViewpointFromAnnotUUID(acmId, context);
            if (resp == null) return null;
            viewpoint = resp.optString("viewpoint", null);
            System.out.println("INFO: setViewpointFromIA() got '" + viewpoint + "' (score " + resp.optDouble("score", -1.0) + ") for " + this);
            return viewpoint;
        } catch (RuntimeException | IOException | java.security.NoSuchAlgorithmException | java.security.InvalidKeyException ex) {
            throw new IOException("setViewpointFromIA() on " + this + " failed: " + ex.toString());
        }
    }
/*
    //  response comes from ia thus: "response": [{"score": 0.9783339699109396, "species": "giraffe_reticulated", "viewpoint": "right"}]
    public static JSONObject iaViewpointFromAnnotUUID(String uuid, String context) throws RuntimeException, MalformedURLException, IOException, NoSuchAlgorithmException, InvalidKeyException {
*/

//FIXME this all needs to be deprecated once deployed sites are migrated
    public MediaAsset __getMediaAsset() {
        return mediaAsset;
    }
    //TODO what should we do for multiple features that point to more than one MediaAsset ?
    public MediaAsset getMediaAsset() {
        ArrayList<Feature> fts = getFeatures();
        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) {
            System.out.println("WARNING: annotation " + this.getId() + " is featureless, falling back to deprecated __getMediaAsset().  please fix!");
            return __getMediaAsset();
        }
        return fts.get(0).getMediaAsset();  //should this instead return first feature *that has a MediaAsset* ??
    }
/*  deprecated
    public void setMediaAsset(MediaAsset ma) {
        mediaAsset = ma;
    }
*/

    // get the MediaAsset created using this Annotation  TODO make this happen
    public MediaAsset getDerivedMediaAsset() {
        return null;
    }

/*
    public void setMediaAsset(MediaAsset ma) {
        mediaAsset = ma;
        if ((ma.getAnnotationCount() == 0) || !ma.getAnnotations().contains(this)) {
            ma.getAnnotations().add(this);
        }
    }
*/


    //detaches this Annotation from MediaAsset by removing the corresponding feature *from the MediaAsset*
    // (the Feature is deleted forever, tho!)
    public MediaAsset detachFromMediaAsset() {
        ArrayList<Feature> fts = getFeatures();
        if ((fts == null) || (fts.size() < 1) || (fts.get(0) == null)) return null;
        MediaAsset ma = fts.get(0).getMediaAsset();
        if (ma == null) return null;
        ma.removeFeature(fts.get(0));
        return ma;
    }

    //returns null if not MediaAsset (whaaa??), otherwise a list (possibly empty) of siblings on the MediaAsset
    public List<Annotation> getSiblings() {
        if (this.getMediaAsset() == null) return null;
        List<Annotation> sibs = new ArrayList<Annotation>();
        for (Annotation ann : this.getMediaAsset().getAnnotations()) {  //fyi .getAnnotations() doesnt return null
            if (!ann.getId().equals(this.getId())) sibs.add(ann);
        }
        return sibs;
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
    }

    public String getIdentificationStatus() {
      return this.identificationStatus;
    }
    public void setIdentificationStatus(String status) {
      this.identificationStatus = status;
    }

    //if this cannot determine a bounding box, then we return null
    public int[] getBbox() {
        if (getMediaAsset() == null) return null;
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
            bbox[2] = (int)getMediaAsset().getWidth();
            bbox[3] = (int)getMediaAsset().getHeight();
        } else {
            //guess we derive from feature!
            if (found.getParameters() == null) return null;
            bbox[0] = found.getParameters().optInt("x", 0);
            bbox[1] = found.getParameters().optInt("y", 0);
            bbox[2] = found.getParameters().optInt("width", 0);
            bbox[3] = found.getParameters().optInt("height", 0);
        }

        if ((bbox[2] < 1) || (bbox[3] < 1)) {
            //note: do NOT use toString() in here!  it references .getBbox() !!  see: recursion
            System.out.println("WARNING: Annotation.getBbox() found invalid width/height for id=" + this.getId());
            return null;
        }
        return bbox;
    }


/*  TODO should this use the IBEIS-IA attribute names or what?
    public JSONObject toJSONObject() {
        JSONObject obj = new JSONObject();
        obj.put("annot_uuid", annot_uuid);
        obj.put("annot_xtl", annot_xtl);
        obj.put("annot_ytl", annot_ytl);
        obj.put("annot_width", annot_width);
        obj.put("annot_height", annot_height);
        obj.put("annot_theta", annot_theta);
        obj.put("species_text", species_text);
        obj.put("image_uuid", this.mediaAsset.getUUID());
        obj.put("name_text", name_text);
        return obj;
    }
*/

/*  we no longer make a MediaAsset "from an Annotation", but rather from its associated Feature(s)
    public MediaAsset createMediaAsset() throws IOException {
        if (mediaAsset == null) return null;
        if (this.isTrivial()) return null;  //we shouldnt make a new MA that is identical, right?
        HashMap<String,Object> hmap = new HashMap<String,Object>();
        hmap.put("annotation", this);
        return mediaAsset.updateChild("annotation", hmap);
    }
*/

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("species", species)
                .append("bbox", getBbox())
/*
                //.append("transform", ((getTransformMatrix == null) ? null : Arrays.toString(getTransformMatrix())))
                .append("transform", Arrays.toString(getTransformMatrix()))
                .append("asset", mediaAsset)
*/
                .toString();
    }

/*
    public MediaAsset getCorrespondingMediaAsset(Shepherd myShepherd) {
        return MediaAsset.findByAnnotation(this, myShepherd);
    }
*/

        //*for now* this will only(?) be called from an Encounter, which means that Encounter must be sanitized
        //  so we assume this *must* be sanitized too.
	public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request,
                                                                        boolean fullAccess) throws org.datanucleus.api.rest.orgjson.JSONException {
            org.datanucleus.api.rest.orgjson.JSONObject jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
            jobj.put("id", id);
            jobj.put("isExemplar", this.getIsExemplar());
            jobj.put("species", this.getIAClass());
            jobj.put("annotationIsOfInterest", this.getIsOfInterest());
            if (this.getFeatures() != null) {
                org.datanucleus.api.rest.orgjson.JSONArray feats = new org.datanucleus.api.rest.orgjson.JSONArray();
                for (Feature f : this.getFeatures()) {
                    if (f == null) continue;
                    feats.put(f.sanitizeJson(request, fullAccess));
                }
                jobj.put("features", feats);
            }
            jobj.put("identificationStatus", this.getIdentificationStatus());
            return jobj;
        }

        //default behavior is limited access
	public org.datanucleus.api.rest.orgjson.JSONObject sanitizeJson(HttpServletRequest request) throws org.datanucleus.api.rest.orgjson.JSONException {
            return this.sanitizeJson(request, false);
    }

///////////////////// TODO fix this for Feature upgrade ////////////////////////
        /**
        * returns only the MediaAsset sanitized JSON, because whenever UI queries our DB (regardless of class query),
        * all they want in return are MediaAssets
        * TODO: add metadata?
        **/
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request, boolean fullAccess) throws org.datanucleus.api.rest.orgjson.JSONException {
        org.datanucleus.api.rest.orgjson.JSONObject jobj;
        if (this.getMediaAsset() != null) {
        jobj = this.getMediaAsset().sanitizeJson(request, new org.datanucleus.api.rest.orgjson.JSONObject(), fullAccess);
        }
        else {
        jobj = new org.datanucleus.api.rest.orgjson.JSONObject();
        }
        return jobj;
    }
    public org.datanucleus.api.rest.orgjson.JSONObject sanitizeMedia(HttpServletRequest request) throws org.datanucleus.api.rest.orgjson.JSONException {
        return this.sanitizeMedia(request, false);
    }

    public String getPartIfPresent() {
        String thisPart = "";
        if (this.iaClass!=null&&this.iaClass.contains("+")) {
            String[] arr = this.iaClass.split("\\+");
            thisPart = arr[arr.length-1];
        }
        return thisPart;
    }

    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd) {
        return getMatchingSet(myShepherd, null);
    }
    //params (usually?) come from task.parameters
    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject params) {
        return getMatchingSet(myShepherd, params, true);
    }
    
    public ArrayList<Annotation> getMatchingSet(Shepherd myShepherd, JSONObject params, boolean useClauses) {
System.out.println("[1] getMatchingSet params=" + params);
        // Make sure we don't include any 'siblings' no matter how we return..
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        Encounter myEnc = this.findEncounter(myShepherd);
        if (myEnc == null) {
            System.out.println("WARNING: getMatchingSet() could not find Encounter for " + this);
            return anns;
        }
        System.out.println("Getting matching set for annotation. Retrieved encounter = "+myEnc.getCatalogNumber());
        String myGenus = myEnc.getGenus();
        String mySpecificEpithet = myEnc.getSpecificEpithet();
        if (Util.stringExists(mySpecificEpithet) && Util.stringExists(myGenus)) {
        	anns = getMatchingSetForTaxonomyExcludingAnnotation(myShepherd, myEnc, params);
        } else if (!Util.booleanNotFalse(IA.getProperty(myShepherd.getContext(), "allowIdentificationWithoutTaxonomy"))) {
            System.out.println("INFO: No taxonomy found on encounter and IA property 'allowIdentificationWithoutTaxonomy' not set; empty matchingSet returned");
            return anns;
        } else if (useClauses) {
            System.out.println("MATCHING ALL SPECIES : Filter for Annotation id="+this.id+" is using viewpoint neighbors and matching parts.");
            anns = getMatchingSetForAnnotationAllSpeciesUseClauses(myShepherd);

        } else {
            System.out.println("MATCHING ALL SPECIES : The parent encounter for query Annotation id="+this.id+" has not specified specificEpithet and genus, and is not using clauses.");
            anns = getMatchingSetAllSpecies(myShepherd);
        }
        System.out.println("Did the query return any encounters? It got: "+anns.size()); 
        return anns;
    }

    //note: this also excludes "sibling annots" (in same encounter)
    public ArrayList<Annotation> getMatchingSetForTaxonomyExcludingAnnotation(Shepherd myShepherd, Encounter enc, JSONObject params) {
        if ((enc == null) || !Util.stringExists(enc.getGenus()) || !Util.stringExists(enc.getSpecificEpithet())) return null;
        //do we need to worry about our annot living in another encounter?  i hope not!
        String filter = "SELECT FROM org.ecocean.Annotation WHERE matchAgainst " + this.getMatchingSetFilterFromParameters(params) + this.getMatchingSetFilterViewpointClause(myShepherd) + this.getPartClause(myShepherd) + " && acmId != null && enc.catalogNumber != '" + enc.getCatalogNumber() + "' && enc.annotations.contains(this) && enc.genus == '" + enc.getGenus() + "' && enc.specificEpithet == '" + enc.getSpecificEpithet() + "' VARIABLES org.ecocean.Encounter enc";
        if (filter.matches(".*\\buser\\b.*")) filter += "; org.ecocean.User user";  //need another VARIABLE declaration
        return getMatchingSetForFilter(myShepherd, filter);
    }

    // the figure-it-out-yourself version
    public ArrayList<Annotation> getMatchingSetForTaxonomyExcludingAnnotation(Shepherd myShepherd, JSONObject params) {
        return getMatchingSetForTaxonomyExcludingAnnotation(myShepherd, this.findEncounter(myShepherd), params);
    }

    //gets everything, no exclusions (e.g. for cacheing)
    public ArrayList<Annotation> getMatchingSetForTaxonomy(Shepherd myShepherd, String genus, String specificEpithet, JSONObject params) {
        if (!Util.stringExists(genus) || !Util.stringExists(specificEpithet)) return null;
        String filter = "SELECT FROM org.ecocean.Annotation WHERE matchAgainst && acmId != null && enc.annotations.contains(this) && enc.genus == '" + genus + "' && enc.specificEpithet == '" + specificEpithet + "' VARIABLES org.ecocean.Encounter enc";
        return getMatchingSetForFilter(myShepherd, filter);
    }
    //figgeritout
    public ArrayList<Annotation> getMatchingSetForTaxonomy(Shepherd myShepherd, JSONObject params) {
        Encounter enc = this.findEncounter(myShepherd);
        if (enc == null) return null;
        return getMatchingSetForTaxonomy(myShepherd, enc.getGenus(), enc.getSpecificEpithet(), params);
    }

    //pass in a generic SELECT filter query string and get back Annotations
    //  currently not taking params, but we can add later if we find useful (for now just trusting filter!)
    static public ArrayList<Annotation> getMatchingSetForFilter(Shepherd myShepherd, String filter) {
        if (filter == null) return null;
        long t = System.currentTimeMillis();
        System.out.println("INFO: getMatchingSetForFilter filter = " + filter);
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection)query.execute();
        Iterator it = c.iterator();
        ArrayList<Annotation> anns = new ArrayList<Annotation>(c.size());
        while (it.hasNext()) {
            Annotation ann = (Annotation)it.next();
            if (!IBEISIA.validForIdentification(ann)) continue;
            anns.add(ann);
        }
        query.closeAll();
        System.out.println("INFO: getMatchingSetForFilter found " + anns.size() + " annots (" + (System.currentTimeMillis() - t) + "ms)");
        return anns;
    }

    // If you don't specify a species, still take into account viewpoint and parts  
    public ArrayList<Annotation> getMatchingSetForAnnotationAllSpeciesUseClauses(Shepherd myShepherd) {
        return getMatchingSetForFilter(myShepherd, "SELECT FROM org.ecocean.Annotation WHERE matchAgainst " + this.getMatchingSetFilterViewpointClause(myShepherd) + this.getPartClause(myShepherd) + " && acmId != null");
    }

    static public ArrayList<Annotation> getMatchingSetAllSpecies(Shepherd myShepherd) {
        return getMatchingSetForFilter(myShepherd, "SELECT FROM org.ecocean.Annotation WHERE matchAgainst && acmId != null");
    }

    // will construnct "&& (viewpoint == null || viewpoint == 'x' || viewpoint == 'y')" for use above
    //   note: will return "" when this annot has no (valid) viewpoint
    private String getMatchingSetFilterViewpointClause(Shepherd myShepherd) {
        String[] viewpoints = this.getViewpointAndNeighbors();
        if (viewpoints == null || (getSpecies(myShepherd)!=null && getSpecies(myShepherd).equals("Tursiops truncatus"))) return "";
        String clause = "&& (viewpoint == null || viewpoint == '" + String.join("' || viewpoint == '", Arrays.asList(viewpoints)) + "')";
        System.out.println("VIEWPOINT CLAUSE: "+clause);
        return clause;
    }

    private String getPartClause(Shepherd myShepherd) {
        String clause = "";
        String useParts =  IA.getProperty(myShepherd.getContext(), "usePartsForIdentification");
        System.out.println("PART CLAUSE: usePartsForIdentification="+useParts);
        if ("true".equals(useParts)) {
            String part = this.getPartIfPresent();
            if (!"".equals(part)&&part!=null) {
                clause = " && iaClass.endsWith('"+part+"') ";
                System.out.println("PART CLAUSE: "+clause);
                return clause;
            }
        }
        return clause;
    }

    //note, we are give *full* task.parameters; by convention, we only act on task.parameters.matchingSetFilter
    // > > > ATTENTION!  if you change this method, please also adjust accordingly getCurvrankDailyTag() below!! < < <
    private String getMatchingSetFilterFromParameters(JSONObject taskParams) {
        if (taskParams == null) return "";
        String userId = taskParams.optString("userId", null);
        JSONObject j = taskParams.optJSONObject("matchingSetFilter");
        if (j == null) return "";
        String f = "";

        // locationId=FOO and locationIds=[FOO,BAR]
        boolean useNullLocation = false;
        List<String> rawLocationIds = new ArrayList<String>();
        String tmp = Util.basicSanitize(j.optString("locationId", null));
        if (Util.stringExists(tmp)) rawLocationIds.add(tmp);
        JSONArray larr = j.optJSONArray("locationIds");
        if (larr != null) {
            for (int i = 0 ; i < larr.length() ; i++) {
                tmp = Util.basicSanitize(larr.optString(i));
                if ("__NULL__".equals(tmp)) {
                    useNullLocation = true;
                } else if (Util.stringExists(tmp) && !rawLocationIds.contains(tmp)) {
                    rawLocationIds.add(tmp);
                }
            }
        }

        //TODO we could have an option to skip expansion (i.e. not include children)
        List<String> expandedLocationIds = LocationID.expandIDs(rawLocationIds);
        String locFilter = "";

        if (expandedLocationIds.size() > 0) {
            locFilter += "enc.locationID == ''";
            // loc ID's were breaking for Hawaiian names with apostrophe(s) and stuff, so overkill now
            List<String> unsavoryCharacters = Arrays.asList(new String[] {"'", ")", "(", "=", ":", ";", "\""});
            for (int i=0;i<expandedLocationIds.size();i++) {
                String expandedLoc = expandedLocationIds.get(i);
                if (CollectionUtils.containsAny(Arrays.asList(expandedLoc.split("")),unsavoryCharacters)) {
                    for (String badChar : unsavoryCharacters) {
                        expandedLoc = expandedLoc.replace(badChar, ".*");
                    } 
                    locFilter += " || enc.locationID.matches(\'"+expandedLoc+"\') ";
                } else {
                    locFilter +=  " || enc.locationID == '"+expandedLoc+"'";
                } 
            }
        }
        
        if (useNullLocation) {
            if (!locFilter.equals("")) locFilter += " || ";
            locFilter += "enc.locationID == null";
        }
        if (!locFilter.equals("")) f += " && (" + locFilter + ") ";

        // "owner" ... which requires we have userId in the taskParams
        JSONArray owner = j.optJSONArray("owner");
        if ((owner != null) && (userId != null)) {
            for (int i = 0 ; i < owner.length() ; i++) {
                String opt = owner.optString(i, null);
                if (!Util.stringExists(opt)) continue;
                if (opt.equals("me")) f += " && enc.submitters.contains(user) && user.uuid == '" + userId + "' ";
                ///TODO also handle "collab" (users you collab with)   :/
            }
        }

        return f;
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

        //currently we have only owner=me, which requires a userId
        JSONArray owner = j.optJSONArray("owner");
        boolean mineOnly = false;
        if ((owner != null) && (userId != null)) {
            for (int i = 0 ; i < owner.length() ; i++) {
                if ("me".equals(owner.optString(i, null))) mineOnly = true;
            }
        }
        if (mineOnly) {
            tag += "user:" + userId;
        } else {
            tag += "user:ANY";
        }

        //now locations, which we want sorted and lowercase, to ensure consistency in multi-value names
        List<String> locs = new ArrayList<String>();
        if (j.optString("locationId", null) != null) locs.add(j.getString("locationId"));
        JSONArray larr = j.optJSONArray("locationIds");
        if (larr != null) {
            for (int i = 0 ; i < larr.length() ; i++) {
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
        return enc.getIndividualID();  //is this one of those things that can be "None" ?
    }

    //convenience!
    public Encounter findEncounter(Shepherd myShepherd) {
        return Encounter.findByAnnotation(this, myShepherd);
    }



/* untested!
    public Encounter findEncounterDeep(Shepherd myShepherd) {
        Encounter enc = this.findEncounter(myShepherd);
System.out.println(">>>> findEncounterDeep(" + this + ") -> enc1 = " + enc);
        if (enc != null) return enc;
        MediaAsset ma = this.getMediaAsset();
System.out.println("  >> findEncounterDeep() -> ma = " + ma + " .... getting Annotations:");
        if (ma == null) return null;
        ArrayList<Annotation> anns = ma.getAnnotations();
        for (Annotation ann : anns) {
System.out.println("  >> findEncounterDeep() -> ann = " + ann);
            //question: do we *only* look for trivial here? seems like we would want that... cuz we crawl hierarchy only in weird video cases etc
            if (ann.isTrivial()) return ann.findEncounterDeep(myShepherd); //recurse! (and effectively bottom-out here... do or die
        }
        return null;  //fall thru, no luck!
    }
*/

    //this is a little tricky. the idea is the end result will get us an Encounter, which *may* be new
    // if it is new, its pretty straight forward (uses findEncounter) .. if not, creation is as follows:
    // look for "sibling" Annotations on same MediaAsset.  if one of them has an Encounter, we clone that.
    // additionally, if one is a trivial annotation, we drop it after.  if no siblings are found, we create
    // an Encounter based on this Annotation (which may get us something, e.g. species, date, loc)
    public Encounter toEncounter(Shepherd myShepherd) {
        // fairly certain this will *never* happen as code currently stands.  this (Annotation) is always new, and
        //  therefore unattached to any Encounter for sure.   so skipping this for now!
        ////Encounter enc = this.findEncounter(myShepherd);

        //rather, we straight up find sibling Annotations, and look at them...
        List<Annotation> sibs = this.getSiblings();
        if ((sibs == null) || (sibs.size() < 1)) {  //no sibs, we make a new Encounter!
            Encounter enc = new Encounter(this);
            //this taxonomy only works when its twitter-sourced data cuz otherwise this is just null 
            enc.setTaxonomy(IBEISIA.taxonomyFromMediaAsset(myShepherd, TwitterUtil.parentTweet(myShepherd, this.getMediaAsset())));
            return enc;
        }
        /*
            ok, we have sibling Annotations.  if one is trivial, we just go for it and replace that one.
            is this wise?   well, if it is the *only* sibling then probably the MediaAsset was attached to the
            Annotation using legacy (non-IA) methods, and we are "zooming in" on the actual animal.  or *one of* the
            actual animals -- if there are others, they should get added in subsequent iterations of toEncounter().
            in theory.

            the one case that is still to be considered ( TODO ) is when (theoretically) detection *improves* and we will
            want a new detection to replace a *non-trivial* Annotation.  but we arent considering that just now!
        */

        //so now we look for a trivial annot to replace.
        //  we currently now allow (via import!) there to be *more than one trivial annotation* on a media asset!
        //  as such, this will find the first trivial available and use that.  since we have no way to know which annot
        //  from detection lines up with which encounter (via the trivial annot), we just randomly replace basically.  alas!
        Encounter someEnc = null;  //this is in case we fall thru (no trivial annot), we can clone some of this for new one
        for (Annotation ann : sibs) {
            Encounter enc = ann.findEncounter(myShepherd);
            if (ann.isTrivial()) {
                ann.setMatchAgainst(false);
                if (enc == null) {  //weird case, but yneverknow (trivial annot with no encounter?)
                    ann.detachFromMediaAsset();  //but this.annot is now on asset, so we are good: kill ann!
                } else {
                    //this also does the detachFromMediaAsset() for us
                    enc.replaceAnnotation(ann, this);
                    return enc;  //our work is done here
                }
                break;
            }
            if (someEnc == null) someEnc = enc;  //use the first one we find to base new one (below) off of, if necessary
        }
        //if we fall thru, we have no trivial annot, so just get a new Encounter for this Annotation
        Encounter newEnc = null;
        if (someEnc == null) {
            newEnc = new Encounter(this);
        } else {  //copy some stuff from sibling
            newEnc = someEnc.cloneWithoutAnnotations();
            newEnc.addAnnotation(this);
            newEnc.setDWCDateAdded();
            newEnc.setDWCDateLastModified();
            newEnc.resetDateInMilliseconds();
            newEnc.setSpecificEpithet(someEnc.getSpecificEpithet());
            newEnc.setGenus(someEnc.getGenus());
        }
        return newEnc;

/*   NOTE: for now i am snipping out this sibling stuff!  youtube-sourced frames used this but now doesnt... here for prosperity...
System.out.println(".toEncounter() on " + this + " found no Encounter.... trying to find one on siblings or make one....");
        List<Annotation> sibs = this.getSiblings();
        Annotation sourceSib = null;
        Encounter sourceEnc = null;
        if (sibs != null) {
            //we look for one that has an Encounter, favoring the trivial one (so we can replace it) otherwise any will do
            for (Annotation sib : sibs) {
                sourceEnc = sib.findEncounter(myShepherd);
                if (sourceEnc == null) continue;
                sourceSib = sib;
                if (sib.isTrivial()) break;  //we have a winner
            }
        }

System.out.println(" * sourceSib = " + sourceSib + "; sourceEnc = " + sourceEnc);
        if (sourceSib == null) return new Encounter(this);  //from scratch it is then!

        if (sourceSib.isTrivial()) {
            System.out.println("INFO: annot.toEncounter() replaced trivial " + sourceSib + " with " + this + " on " + sourceEnc);
            sourceEnc.addAnnotationReplacingUnityFeature(this);
            sourceEnc.setSpeciesFromAnnotations();
            return sourceEnc;
        }

        enc = sourceEnc.cloneWithoutAnnotations();
        enc.addAnnotation(this);
        enc.setSpeciesFromAnnotations();
        return enc;
*/
    }

/*  deprecated, maybe?
    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd) {
        if (mediaAsset == null) return "<!-- Annotation.toHtmlElement(): " + this + " has no MediaAsset -->";
        return mediaAsset.toHtmlElement(request, myShepherd, this);
    }
*/

    public Annotation revertToTrivial(Shepherd myShepherd) throws IOException {
        if (this.isTrivial()) throw new IOException("Already a trivial Annotation: " + this);
        Encounter enc = this.findEncounter(myShepherd);
        if (enc == null) throw new IOException("Unable to find corresponding Encounter for " + this);
        MediaAsset ma = this.getMediaAsset();
        if (ma == null) throw new IOException("Unable to find corresponding MediaAsset for " + this);
        if ((ma.getFeatures() != null) && (ma.getFeatures().size() > 1)) throw new IOException("Sibling Annotations detected on " + ma + "; cannot revert to trivial " + this);
        Annotation triv = new Annotation(this.species, ma);  //not going to set IAClass or anything since starting fresh
        enc.removeAnnotation(this);
        this.setMatchAgainst(false);
        this.detachFromMediaAsset();
        enc.addAnnotation(triv);
        System.out.println("INFO: revertToTrivial() created annot=" + triv.getId() + " on enc=" + enc.getCatalogNumber() + ", replacing annot=" + this.getId());
        return triv;
    }

    //creates a new Annotation with the basic properties duplicated (but no "linked" objects, like Features etc)
    public Annotation shallowCopy() {
        Annotation ann = new Annotation();
        ann.id = Util.generateUUID();
        ann.species = this.species;
        ann.name = this.name;
        ann.isExemplar = this.isExemplar;
        ann.identificationStatus = this.identificationStatus;
        return ann;
    }

    public List<Task> getRootIATasks(Shepherd myShepherd) {  //convenience
        return Task.getRootTasksFor(this, myShepherd);
    }

    public static boolean isValidViewpoint(String vp) {
        if (vp == null) return true;  //?? is this desired behavior?
        return getAllValidViewpoints().contains(vp);
    }
    public static List<String> getAllValidViewpoints() {
        //add code to limit based on IA.properties viewpoints enabled switches if you want i guess
        List<String> all = new ArrayList<>();
        for (int i=0;i<VALID_VIEWPOINTS.length;i++) {
            Collections.addAll(all, VALID_VIEWPOINTS[i]);
        }
        return all;
    }
    
    public static ArrayList<Encounter> checkForConflictingIDsforAnnotation(Annotation annot, String proposedIndividualIDForEncounter, Shepherd myShepherd){
      ArrayList<Encounter> conflictingEncs=new ArrayList<Encounter>();
      String filter="SELECT FROM org.ecocean.Encounter WHERE individual!=null && individual.individualID != \""+proposedIndividualIDForEncounter+"\" && annotations.contains(annot1) && annot1.acmId == \""+annot.getAcmId()+"\" VARIABLES org.ecocean.Annotation annot1";
      Query q=myShepherd.getPM().newQuery(filter);
      Collection c = (Collection) (q.execute());
      conflictingEncs=new ArrayList<Encounter>(c);
      q.closeAll();
      return conflictingEncs;
    }

/*
    these will update(/create) AnnotationLite.cache for this Annotation
      note: use sparingly?  i.e. should only happen when (related) taxonomy or individual or validForIdentification changes
*/
    public void refreshLiteTaxonomy(String tax) {
        if (this.acmId == null) return;
        AnnotationLite annl = AnnotationLite.getCache(this.acmId);
        if (annl == null) {
            annl = new AnnotationLite(null, tax);    //indiv = null here, but it is new so its what we got. :/
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
    //TODO ... other permutations?
}
