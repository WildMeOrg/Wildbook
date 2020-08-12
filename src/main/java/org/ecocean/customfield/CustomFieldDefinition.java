package org.ecocean.customfield;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.DataDefinition;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.List;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class CustomFieldDefinition implements java.io.Serializable {
    private String id = null;
    private String className = null;
    private String name = null;  //this is a human-readable name which is required and needs to be unique
    private String type = null;
    private boolean multiple = false;
    private String parameters = null;

    public CustomFieldDefinition() {
        id = Util.generateUUID();
    }
    public CustomFieldDefinition(String className, String type, String name) throws CustomFieldException {
        this();
        if (!validClassName(className)) throw new CustomFieldException("CustomFieldDefinition() invalid className in constructor");
        if (!DataDefinition.isValidType(type)) throw new CustomFieldException("CustomFieldDefinition() invalid type in constructor");
        this.className = className;
        this.name = name;
        this.type = type;
    }
    public CustomFieldDefinition(String className, String type, String name, boolean mult) throws CustomFieldException {
        this();
        if (!validClassName(className)) throw new CustomFieldException("CustomFieldDefinition() invalid className in constructor");
        if (!DataDefinition.isValidType(type)) throw new CustomFieldException("CustomFieldDefinition() invalid type in constructor");
        this.className = className;
        this.name = name;
        this.type = type;
        this.multiple = mult;
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public String getType() {
        return type;
    }
    public void setType(String t) throws CustomFieldException {
        if (!DataDefinition.isValidType(t)) throw new CustomFieldException("CustomFieldDefinition.setType() passed invalid type");
        type = t;
    }
    public String getClassName() {
        return className;
    }
    public void setClassName(String c) throws CustomFieldException {
        if (!validClassName(c)) throw new CustomFieldException("CustomFieldDefinition.setClassName() passed invalid className");
        className = c;
    }
    public String getName() {
        return name;
    }
    public void setName(String n) {
        name = n;
    }
    public boolean getMultiple() {
        return multiple;
    }
    public void setMultiple(boolean m) {
        multiple = m;
    }

    public static boolean validClassName(String cn) {
        if (cn == null) return false;
        return true;  //FIXME
    }

    //this can create a new one and/or modify existing, depending on defn.id
    public static CustomFieldDefinition updateCustomFieldDefinition(Shepherd myShepherd, JSONObject defn) throws CustomFieldException {
        CustomFieldDefinition cfd = fromJSONObject(defn);
        String id = defn.optString("id", null);
        if (Util.isUUID(id)) {  //lets see if we are updating....
            List<CustomFieldDefinition> all = myShepherd.getAllCustomFieldDefinitions();
            if (!Util.collectionIsEmptyOrNull(all)) for (CustomFieldDefinition c : all) {
                if (id.equals(c.getId())) return c.modify(myShepherd, defn);
            }
            cfd.setId(id);  //if we made it this far, is a new cfd but wants to set the id
        }
        //must be a new one, lets save it
        myShepherd.getPM().makePersistent(cfd);
        return cfd;
    }
/*
    we are going to have to make some tough decisions here because we may be modifying things that affect *existing data*.
    for now we are going to punt on a lot of this tough stuff (and throw exceptions!) but TODO this will eventually be addressed.
*/
    public CustomFieldDefinition modify(Shepherd myShepherd, JSONObject defn) throws CustomFieldException {
        if (defn == null) throw new CustomFieldException("modify() passed null");
        JSONObject orig = this.toJSONObject();
        if (Util.compareJSONObjects(defn, orig)) {
            System.out.println("INFO: ignoring modify() called with identical defintion for " + this.toString());
            return this;
        }
        if (!defn.optString("type", "_FAIL_").equals(this.getType())) throw new CustomFieldException("modification of definition type currently not supported");
        if (!defn.optString("id", "_FAIL_").equals(this.getId())) throw new CustomFieldException("modification of definition id forbidden");
        if (!defn.optString("className", "_FAIL_").equals(this.getClassName())) throw new CustomFieldException("modification of definition className forbidden");
        int changed = 0;
        String name = defn.optString("name", null);
        if ((name != null) && !name.equals(this.getName())) {
            changed++;
            this.setName(name);
        }
        Boolean mult = Util.optBoolean(defn, "multiple");
        if (mult != null) {
            if (!mult && this.getMultiple()) {  //cannot undo multiple at this time!  TODO
                throw new CustomFieldException("disabling definition multiple boolean not supported");
            } else if (mult && !this.getMultiple()) {  //but we can enable it
                this.setMultiple(true);
                changed++;
            }
        }
        if (defn.has("options") && !Util.compareJSONArrays(orig.optJSONArray("options"), defn.optJSONArray("options"))) throw new CustomFieldException("modifying options not yet supported");
        //at this point, we kinda blindly trust that other values (e.g. displayType) are fine for the sake of parameters
        this.setParameters(defn);
        System.out.println("INFO: modify() altered " + changed + " non-parameter value(s) in definition: " + orig.toString() + " => " + defn.toString());
        return this;
    }

    //note: this ignores 'id' passed in!  (it will set own)
    public static CustomFieldDefinition fromJSONObject(JSONObject defn) throws CustomFieldException {
        if (defn == null) throw new CustomFieldException("fromJSONObject() passed null");
        CustomFieldDefinition cfd = new CustomFieldDefinition(defn.optString("className", null), defn.optString("type", null), defn.optString("name", null), defn.optBoolean("multiple", false));
        cfd.setParameters(defn);
        return cfd;
    }

    //this is basically to sanity-check the parameters as well
    public void setParameters(JSONObject param) throws CustomFieldException {
        if (param == null) {
            this.parameters = null;
            return;
        }
        if (param.has("options") && (param.optJSONArray("options") == null)) throw new CustomFieldException("options parameter must be array");
        //TODO check this.type against contents of array
        this.parameters = param.toString();
    }

    public boolean equals(final Object d2) {
        if (d2 == null) return false;
        if (!(d2 instanceof CustomFieldDefinition)) return false;
        CustomFieldDefinition two = (CustomFieldDefinition)d2;
        if ((this.id == null) || (two == null) || (two.getId() == null)) return false;
        return this.id.equals(two.getId());
    }
    public int hashCode() {  //we need this along with equals() for collections methods (contains etc) to work!!
        if (id == null) return Util.generateUUID().hashCode();  //random(ish) so we dont get two users with no uuid equals! :/
        return id.hashCode();
    }

    public static JSONObject getDefinitionsAsJSONObject(Shepherd myShepherd, String className) throws CustomFieldException {
        if (!validClassName(className)) throw new CustomFieldException("getDefinitionsAsJSONObject() passed invalid className=" + className);
        List<CustomFieldDefinition> all = myShepherd.getCustomFieldDefinitionsForClassName(className);
        JSONObject rtn = new JSONObject();
        JSONArray defns = new JSONArray();
        if (Util.collectionIsEmptyOrNull(all)) {
            rtn.put("_message", "no definitions");
        } else {
            for (CustomFieldDefinition cfd : all) {
                defns.put(cfd.toJSONObject());
            }
        }
        rtn.put("definitions", defns);
        return rtn;
    }

    public JSONObject toJSONObject() {
        JSONObject j = Util.stringToJSONObject(this.parameters);
        if (j == null) j = new JSONObject();
        //these "overwrite" any that may happen to be in parameters, so tough
        j.put("id", id);
        j.put("name", name);
        j.put("className", className);
        j.put("type", type);
        j.put("multiple", multiple);
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("name", name)
                .append("className", className)
                .append("type", type)
                .append("multiple", multiple)
                .toString();
    }
}

