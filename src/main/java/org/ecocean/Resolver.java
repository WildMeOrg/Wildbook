/* note: the first iteration of Resolver will *intentionally* make attempts to auto-resolve any choices during splits and merges.
   good luck with that!   later we will investigate review workflows etc.  */

package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;
import javax.servlet.http.HttpServletRequest;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.FeatureType;

public class Resolver implements java.io.Serializable {
    public static String STATUS_PENDING = "pending";  //needs review
    public static String STATUS_APPROVED = "approved";  //approved, but not complete
    public static String STATUS_AUTO = "auto";  //approval not necessary; but not complete
    public static String STATUS_COMPLETE = "complete";
    public static String STATUS_ERROR = "error";

    public static String TYPE_RETIRE = "retire";
    public static String TYPE_SPLIT = "split";
    public static String TYPE_MERGE = "merge";
    public static String TYPE_NEW = "new";

    private int id;
    private long modified;
    private String parameters;
    private String results;
    private String status;
    private String type;

    private Resolver parent;
    private List<Resolver> children;

    private List<Object> resultObjects;



    public Resolver() { }

    public Resolver(String type) {
        if (!validType(type)) throw new RuntimeException("Invalid Resolver type " + type);
        this.type = type;
        this.modified = System.currentTimeMillis();
    }

    public int getId() {
        return id;
    }
    public void setId(int i) {
        id = i;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String s) {
        status = s;
    }
    public void setParameters(String p) {
        parameters = p;
    }
    public void setParameters(JSONObject jp) {
        if (jp == null) {
            parameters = null;
        } else {
            parameters = jp.toString();
        }
    }
    public JSONObject getParameters() {
        return Util.stringToJSONObject(this.parameters);
    }

    public void setResults(String r) {
        results = r;
    }
    public void setResults(JSONObject jr) {
        if (jr == null) {
            results = null;
        } else {
            results = jr.toString();
        }
    }
    public JSONObject getResults() {
        return Util.stringToJSONObject(this.results);
    }

    public Resolver getParent() {
        return parent;
    }
    public void setParent(Resolver r) {
        parent = r;
    }
    public List<Resolver> getChildren() {
        return children;
    }
    public void setChildren(List<Resolver> c) {
        children = c;
    }
    public List<Resolver> addChild(Resolver c) {
        if (children == null) children = new ArrayList<Resolver>();
        if (!children.contains(c)) {
            children.add(c);
            c.setParent(this);
        }
        return children;
    }

    public List<Object> addResultObject(Object obj) {
        if (resultObjects == null) resultObjects = new ArrayList<Object>();
        resultObjects.add(obj);
        return resultObjects;
    }
/*   will we ever want this?
    public List<Object> getResultObjects() {
        return resultObjects;
    }
*/
    public List<MarkedIndividual> getResultMarkedIndividuals() {
        List<MarkedIndividual> list = new ArrayList<MarkedIndividual>();
        if ((resultObjects == null) || (resultObjects.size() < 1)) return list;
        for (Object obj : resultObjects) {
            if (obj instanceof MarkedIndividual) list.add((MarkedIndividual)obj);
        }
        return list;
    }
    public List<Encounter> getResultEncounters() {
        List<Encounter> list = new ArrayList<Encounter>();
        if ((resultObjects == null) || (resultObjects.size() < 1)) return list;
        for (Object obj : resultObjects) {
            if (obj instanceof Encounter) list.add((Encounter)obj);
        }
        return list;
    }

    public static boolean validType(String t) {
        if (t == null) return false;
        return (t.equals(TYPE_RETIRE) || t.equals(TYPE_SPLIT) || t.equals(TYPE_MERGE) || t.equals(TYPE_NEW));
    }

    public static Resolver retireMarkedIndividual(MarkedIndividual indiv, HttpServletRequest request, Shepherd myShepherd) {
        if (indiv == null) throw new RuntimeException("null MarkedIndividual passed to Resolver.retireMarkedIndividual()");
        Resolver res = new Resolver(TYPE_RETIRE);

        JSONObject jp = new JSONObject();
        jp.put("individualId", indiv.getIndividualID());
        jp.put("user", AccessControl.userAsJSONObject(request));
        res.setParameters(jp);

        JSONObject jr = new JSONObject();
        jr.put("snapshot", snapshotMarkedIndividual(indiv));
        res.setResults(jr);

        myShepherd.getPM().makePersistent(res);
        return res;
    }

    public static Resolver mergeMarkedIndividuals(List<MarkedIndividual> indivs, HttpServletRequest request, Shepherd myShepherd) {
        Resolver res = new Resolver(TYPE_MERGE);
        return res;
    }

    public static Resolver splitMarkedIndividuals(List<MarkedIndividual> indivs, HttpServletRequest request, Shepherd myShepherd) {
        Resolver res = new Resolver(TYPE_SPLIT);
        return res;
    }

    public static Resolver addNewMarkedIndividuals(List<Annotation> anns, HttpServletRequest request, Shepherd myShepherd) {
        Resolver res = new Resolver(TYPE_NEW);

        JSONObject jp = new JSONObject();
        JSONArray aarr = new JSONArray();
        for (Annotation ann : anns) {
            aarr.put(ann.getId());
        }
        jp.put("annotations", aarr);
        jp.put("user", AccessControl.userAsJSONObject(request));
        res.setParameters(jp);

        boolean needsReview = false;
        JSONArray issues = new JSONArray();

        ArrayList<Encounter> encs = new ArrayList<Encounter>();
        for (Annotation ann : anns) {
            Encounter enc = Encounter.findByAnnotation(ann, myShepherd);
            if (encs.contains(enc)) continue;
            if (enc == null) {  //we need an encounter to hold this annotation
                enc = new Encounter(ann);
                System.out.println("INFO: new encounter " + enc.getCatalogNumber());

            } else if (enc.hasMarkedIndividual()) {
                needsReview = true;
                JSONObject iss = new JSONObject();
                iss.put("message", "Encounter " + enc.getCatalogNumber() + " is already assigned " + enc.getIndividualID());
                iss.put("encId", enc.getCatalogNumber());
                issues.put(iss);
            }
            encs.add(enc);
        }

        JSONObject jr = new JSONObject();
/*  this should never happen now, since we create encounters
        if (encs.size() < 1) {
            JSONArray e = new JSONArray();
            e.put("no Encounters found containing any of the Annotations");
            jr.put("errors", e);
            res.setResults(jr);
            res.setStatus(STATUS_ERROR);
            return res;
        }
*/

        if (issues.length() > 0) jr.put("reviewIssues", issues);
needsReview = false;  // we are only full-auto now!
        if (needsReview) {
            res.setStatus(STATUS_PENDING);
        } else {
            res.setStatus(STATUS_AUTO);
        }

        //res.setResults(jr);
        //myShepherd.getPM().makePersistent(res);  //save it in this state before execute....

        MarkedIndividual indiv = new MarkedIndividual(Util.generateUUID(), encs.get(0));
        for (int i = 1 ; i < encs.size() ; i++) {
            indiv.addEncounter(encs.get(i), "context0");
        }

        res.addResultObject(indiv);
        jr.put("newMarkedIndividual", indiv.getIndividualID());
        res.setResults(jr);
        res.setStatus(STATUS_COMPLETE);
        myShepherd.getPM().makePersistent(res);

        return res;
    }


    public static JSONObject processAPIJSONObject(JSONObject jin, Shepherd myShepherd) {
        JSONObject rtn = new JSONObject("{\"success\": true}");
        Iterator it = jin.keys();
        while (it.hasNext()) {
            String type = (String)it.next();
            if (type.equals("fromIAImageSet")){
                FeatureType.initAll(myShepherd);
                try {
                    rtn = IBEISIA.mergeIAImageSet(jin.optString(type, null), myShepherd);
                } catch (Exception ex) {  //pokemon!
                    ex.printStackTrace();
                    throw new RuntimeException("mergeIAImageSet() failed! " + ex.toString());
                }
            } else if (type.equals("assignNameToAnnotations")){
                FeatureType.initAll(myShepherd);
                rtn = IBEISIA.assignFromIAAPI(jin.optJSONObject(type), myShepherd, true);
            } else if (type.equals("annotations")){
                FeatureType.initAll(myShepherd);
                rtn = IBEISIA.arbitraryAnnotationsFromIA(jin.optJSONArray(type), myShepherd);
            } else if (!validType(type)) {
                rtn.put("success", false);
                rtn.put(type, "error: invalid type " + type);
            } else {
                rtn.put(type, "not yet supported");
            }
        }
        return rtn;
    }


    public static JSONObject snapshotMarkedIndividual(MarkedIndividual indiv) {
        if (indiv == null) return null;
        JSONObject snap = new JSONObject();
        snap.put("timestamp", System.currentTimeMillis());
        return snap;
    }



    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("type", type)
                .append("parent", ((parent == null) ? "" : parent.getId()))
                .append("status", status)
                .append("modified", modified)
                .toString();
    }

}
