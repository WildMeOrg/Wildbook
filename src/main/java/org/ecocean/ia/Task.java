/*
    an instance of an ia.Task can be persisted and represents the state of that task
    ... replacement (and improvement upon, hopefully) messy identity/IdentityServiceLog.java
*/

package org.ecocean.ia;

import org.ecocean.Shepherd;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.ecocean.identity.IBEISIA;
//import java.util.UUID;   :(
import java.util.List;
import java.util.ArrayList;
import org.joda.time.DateTime;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.jdo.Query;

public class Task implements java.io.Serializable {

    private String id = null;
    private long created = -1;
    private long modified = -1;
    //private List<Object> objects = null;  //in some perfect world i could figure out how to persist this.  :/  oh, for a wb base class.
    private List<MediaAsset> objectMediaAssets = null;
    private List<Annotation> objectAnnotations = null;
    private Task parent = null;
    private List<Task> children = null;
    private String parameters = null;

    public Task() {
        this(Util.generateUUID());
    }
    public Task(String id) {
        this.id = id;
        created = System.currentTimeMillis();
        modified = System.currentTimeMillis();
    }
    //makes a child of the passed Task (and inherits the parameters!!)
    public Task(Task p) {
        this();
        this.setParameters(p.getParameters());
        this.setParent(p);
    }

    public String getId() {
        return id;
    }

    public long getCreatedLong() {
        return created;
    }

    public long getModifiedLong() {
        return modified;
    }

    public int countObjectMediaAssets() {
        return (objectMediaAssets == null) ? 0 : objectMediaAssets.size();
    }
    public int countObjectAnnotations() {
        return (objectAnnotations == null) ? 0 : objectAnnotations.size();
    }
    public int countObjects() {
        return countObjectMediaAssets() + countObjectAnnotations();
    }

    //not sure if these two are mutually exclusive by definition, but lets assume not (wtf would that even mean? i dunno)
    public boolean hasObjectMediaAssets() {
        return (countObjectMediaAssets() > 0);
    }
    public boolean hasObjectAnnotations() {
        return (countObjectAnnotations() > 0);
    }
    public boolean hasObjects() {
        return (countObjects() > 0);
    }

    public void setObjectMediaAssets(List<MediaAsset> mas) {
        objectMediaAssets = mas;
    }
    public void setObjectAnnotations(List<Annotation> anns) {
        objectAnnotations = anns;
    }
    public List<MediaAsset> getObjectMediaAssets() {
        return objectMediaAssets;
    }
    public List<Annotation> getObjectAnnotations() {
        return objectAnnotations;
    }
    //kinda for convenience?
    public boolean addObject(MediaAsset ma) {
        if (ma == null) return false;
        if (objectMediaAssets == null) objectMediaAssets = new ArrayList<MediaAsset>();
        if (!objectMediaAssets.contains(ma)) {
            objectMediaAssets.add(ma);
            return true;
        }
        return false;
    }
    public boolean addObject(Annotation ann) {
        if (ann == null) return false;
        if (objectAnnotations == null) objectAnnotations = new ArrayList<Annotation>();
        if (!objectAnnotations.contains(ann)) {
            objectAnnotations.add(ann);
            return true;
        }
        return false;
    }

    public List<Task> getChildren() {
        return children;
    }
    public void setChildren(List<Task> kids) {
        children = kids;
    }
    public List<Task> addChild(Task kid) {
        if (children == null) children = new ArrayList<Task>();
        if (kid == null) return children;
        if (!children.contains(kid)) children.add(kid);
        return children;
    }

    public void setParent(Task t) {
        parent = t;
        t.addChild(this);
    }
    public Task getParent() {
        return parent;
    }
    public int numChildren() {
        return (children == null) ? 0 : children.size();
    }
    public boolean hasChildren() {
        return (this.numChildren() > 0);
    }

    //omg i am going to assume no looping
    public List<Task> getLeafTasks() {
        List<Task> leaves = new ArrayList<Task>();
        if (!this.hasChildren()) {
            leaves.add(this);
            return leaves;
        }
        for (Task kid : children) {
            leaves.addAll(kid.getLeafTasks());
        }
        return leaves;
    }

    public Task getRootTask() {
        if (parent == null) return this;
        return parent.getRootTask();
    }

    public JSONObject getParameters() {  //only return as JSONObject!  TODO probably validate content below?
        if (parameters == null) return null;
        return Util.stringToJSONObject(parameters);
    }
    // see comment above: should this even be public?  (or exist)
    public void setParameters(String s) {  //best be json, yo
        parameters = s;
    }
    public void setParameters(JSONObject j) {
        if (j == null) {
            parameters = null;
        } else {
            parameters = j.toString();
        }
    }
    //convenience method to construct the JSONObject from key/value
    public void setParameters(String key, Object value) {
        if (key == null) return;  //nope
        JSONObject j = new JSONObject();
        j.put(key, value);  //value object type better be kosher for JSONObject.  :/
        parameters = j.toString();
    }
    //like above, but doesnt (re)set .parameters, will only append/alter the key'ed one
    public void addParameter(String key, Object value) {
        if (key == null) return;
        JSONObject j = this.getParameters();
        if (j == null) j = new JSONObject();
        j.put(key, value);
        parameters = j.toString();
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }
    public JSONObject toJSONObject(boolean includeChildren) {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("parameters", this.getParameters());
        j.put("created", created);
        j.put("modified", modified);
        j.put("createdDate", new DateTime(created));
        j.put("modifiedDate", new DateTime(modified));
        if ((objectMediaAssets != null) && (objectMediaAssets.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (MediaAsset ma : this.objectMediaAssets) {
                jo.put(ma.getId());
            }
            j.put("mediaAssetIds", jo);
        }
        if ((objectAnnotations != null) && (objectAnnotations.size() > 0)) {
            JSONArray jo = new JSONArray();
            for (Annotation ann : this.objectAnnotations) {
                jo.put(ann.getId());
            }
            j.put("annotationIds", jo);
        }
        if (includeChildren && this.hasChildren()) {
            JSONArray jc = new JSONArray();
            for (Task kid : this.children) {
                jc.put(kid.toJSONObject(true));  //we once again assume no looping!  bon chance.
            }
            j.put("children", jc);
        }
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append(id)
                .append("(" + new DateTime(created) + "|" + new DateTime(modified) + ")")
                .append(numChildren() + "Kids")
                .append(countObjectMediaAssets() + "MA")
                .append(countObjectAnnotations() + "Ann")
                .append("params=" + ((this.getParameters() == null) ? "(none)" : this.getParameters().toString()))
                .toString();
    }

    public static Task load(String taskId, Shepherd myShepherd) {
        Task t = null;
        try {
            t = ((Task) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Task.class, taskId), true)));
        } catch (Exception ex) {};  //swallow jdo not found noise
        return t;
    }

    //TODO versions for multiple objects (when needed)
    public static List<Task> getTasksFor(Annotation ann, Shepherd myShepherd) {
        String qstr = "SELECT FROM org.ecocean.ia.Task WHERE objectAnnotations.contains(obj) && obj.id == \"" + ann.getId() + "\" VARIABLES org.ecocean.Annotation obj";
        Query query = myShepherd.getPM().newQuery(qstr);
        query.setIgnoreCache(true);
        query.setOrdering("created");
        return (List<Task>) query.execute();
    }
    public static List<Task> getRootTasksFor(Annotation ann, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ann, myShepherd));
    }

    public static List<Task> getTasksFor(MediaAsset ma, Shepherd myShepherd) {
        String qstr = "SELECT FROM org.ecocean.ia.Task WHERE objectMediaAssets.contains(obj) && obj.id == " + ma.getId() + " VARIABLES org.ecocean.media.MediaAsset obj";
        Query query = myShepherd.getPM().newQuery(qstr);
        query.setIgnoreCache(true);
        query.setOrdering("created");
        return (List<Task>) query.execute();
    }
    public static List<Task> getRootTasksFor(MediaAsset ma, Shepherd myShepherd) {
        return onlyRoots(getTasksFor(ma, myShepherd));
    }

    //takes a bunch of tasks and returns only roots (without duplication)
    public static List<Task> onlyRoots(List<Task> all) {
        List<Task> roots = new ArrayList<Task>();
        for (Task t : all) {
            Task r = t.getRootTask();
            if (!roots.contains(r)) roots.add(r);
        }
        return roots;
    }
}

