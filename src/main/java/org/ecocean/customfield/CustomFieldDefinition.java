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
        className = n;
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

    public static CustomFieldDefinition updateCustomFieldDefinition(Shepherd myShepherd, JSONObject defn) throws CustomFieldException {
        CustomFieldDefinition cfd = fromJSONObject(defn);
        String id = defn.optString("id", null);
        //FIXME???: modifying existing cfd is... not easy. we should check if it has been *used at all first* i guess???  and if it is... gulp?
        if (Util.isUUID(id)) {  //lets see if we are updating....
            List<CustomFieldDefinition> all = myShepherd.getAllCustomFieldDefinitions();
            if (!Util.collectionIsEmptyOrNull(all)) for (CustomFieldDefinition c : all) {
                if (id.equals(c.getId())) throw new CustomFieldException("modification of existing CustomFieldDefinition currently not supported");
            }
            cfd.setId(id);  //if we made it this far, is a new cfd but wants to set the id
        }
        myShepherd.getPM().makePersistent(cfd);
        return cfd;
    }
    //note: this ignores 'id' passed in!  (it will set own)
    public static CustomFieldDefinition fromJSONObject(JSONObject defn) throws CustomFieldException {
        if (defn == null) throw new CustomFieldException("fromJSONObject() passed null");
        CustomFieldDefinition cfd = new CustomFieldDefinition(defn.optString("className", null), defn.optString("type", null), defn.optString("name", null), defn.optBoolean("multiple", false));
        cfd.parameters = defn.toString();
        return cfd;
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

