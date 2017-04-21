package org.ecocean;

/*
  TODO note: this is very ibeis-specific concept of "Annotation"
     we should probably consider a general version which can be manipulated into an ibeis one somehow
*/

import org.ecocean.ImageAttributes;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.json.JSONObject;
import org.apache.commons.lang3.builder.ToStringBuilder;
import javax.jdo.Query;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Arrays;
import javax.servlet.http.HttpServletRequest;

//import java.time.LocalDateTime;

public class Annotation implements java.io.Serializable {
    public Annotation() {}  //empty for jdo
    private String id;  //TODO java.util.UUID ?
    private String species;
    private String name;
    private boolean isExemplar = false;
    protected String identificationStatus;
    private ArrayList<Feature> features;

////// these will go away after transition to Features
    private int x;
    private int y;
    private int width;
    private int height;
    private float[] transformMatrix;
    private double theta;
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

    //returns null if not MediaAsset (whaaa??), otherwise a list (possibly empty) of siblings on the MediaAsset
    public List<Annotation> getMediaAssetSiblings() {
        if (this.getMediaAsset() == null) return null;
        List<Annotation> sibs = new ArrayList<Annotation>();
        for (Annotation ann : this.getMediaAsset().getAnnotations()) {  //fyi .getAnnotations() doesnt return null
            if (!ann.getId().equals(this.getId())) sibs.add(ann);
        }
        return sibs;
    }

    public String getSpecies() {
        return species;
    }
    public void setSpecies(String s) {
        species = s;
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
            return bbox;
        }
        //guess we derive from feature!
        if (found.getParameters() == null) return null;
        bbox[0] = found.getParameters().optInt("x", 0);
        bbox[1] = found.getParameters().optInt("y", 0);
        bbox[2] = found.getParameters().optInt("width", 0);
        bbox[3] = found.getParameters().optInt("height", 0);
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


    static public ArrayList<Annotation> getExemplars(String species, Shepherd myShepherd) {
//species = "Balaenoptera acutorostrata";  //for springbreak testing only!!!!!!!  FIXME
        String filter = "SELECT FROM org.ecocean.Annotation WHERE this.isExemplar && species == \"" + species + "\"";
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            anns.add((Annotation)it.next());
        }
        query.closeAll();
        return anns;
    }

    //for *any/all* species
    static public ArrayList<Annotation> getExemplars(Shepherd myShepherd) {
        String filter = "SELECT FROM org.ecocean.Annotation WHERE this.isExemplar";
        ArrayList<Annotation> anns = new ArrayList<Annotation>();
        Query query = myShepherd.getPM().newQuery(filter);
        Collection c = (Collection) (query.execute());
        Iterator it = c.iterator();
        while (it.hasNext()) {
            anns.add((Annotation)it.next());
        }
        query.closeAll();
        return anns;
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
        Encounter enc = this.findEncounter(myShepherd);
        if (enc != null) return enc;
/*   NOTE: for now i am snipping out this sibling stuff!  youtube-sourced frames used this but now doesnt... here for prosperity...
System.out.println(".toEncounter() on " + this + " found no Encounter.... trying to find one on siblings or make one....");
        List<Annotation> sibs = this.getMediaAssetSiblings();
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
        return new Encounter(this);
    }

/*  deprecated, maybe?
    public String toHtmlElement(HttpServletRequest request, Shepherd myShepherd) {
        if (mediaAsset == null) return "<!-- Annotation.toHtmlElement(): " + this + " has no MediaAsset -->";
        return mediaAsset.toHtmlElement(request, myShepherd, this);
    }
*/

}
