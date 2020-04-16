package org.ecocean.configuration;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Configuration implements java.io.Serializable {
    private String id;
    private String content = null;
    private long created;
    private long modified;

    public Configuration() {
        this.created = System.currentTimeMillis();
        this.setModified();
    }
    public Configuration(String id) {
        this();
        this.id = id;
    }
    public Configuration(String id, JSONObject cont) {
        this();
        if (cont != null) this.content = cont.toString();
        this.id = id;
    }

    public JSONObject getContent() {
        return Util.stringToJSONObject(content);
    }

    //do NOT setContent directly, instead use ConfigurationUtil.setConfigurationValue()
    public void setContent(JSONObject j) {
        if (j == null) {
            content = null;
        } else {
            content = j.toString();
        }
        this.setModified();
    }
    public void setValue(Shepherd myShepherd, Object value) throws ConfigurationException {  //but, convenience
        ConfigurationUtil.setConfigurationValue(myShepherd, this.id, value);
    }

//////////// TODO need something like .hasValue() to know when set but null (as opposed to .getValue() returning null for other reasons like never set)

    public Object getValue() throws ConfigurationException {  //convenience
        return ConfigurationUtil.coerceValue(this);
    }

    public Object getDefaultValue() {
        if (!this.hasValidRoot()) return null;
        JSONObject meta = this.getMeta();
        String type = ConfigurationUtil.getType(meta);
return null; ///FIXME
        //return _coerce(meta, "defaultValue", type);
    }

    public List<String> getIdPath() {
        return ConfigurationUtil.idPath(this.id);
    }
    public boolean hasValidRoot() {
        return ConfigurationUtil.idHasValidRoot(this.id);
    }

    //the only kind that should be persisted to db!!!
    public boolean isRootLevel() {
        return ConfigurationUtil.isValidRoot(this.id);
    }

    //this means it "can" or "should" have a value (e.g. path is good, meta defined etc)
    //  note that this is false you cannot read or set value on it
    public boolean isValid() {
        if (!this.hasValidRoot()) return false;
        JSONObject meta = this.getMeta();
        if (meta == null) return false;
        if (ConfigurationUtil.getType(meta) == null) return false;
        return true;
    }

    public JSONObject getMeta() {
        return ConfigurationUtil.getMeta(this.id);
    }
    public JSONObject getNode() {
        return ConfigurationUtil.getNode(this.id);
    }
    public String getType() {
        return ConfigurationUtil.getType(this.id);
    }
    public Set<String> getChildKeys() {
        Set<String> ck = new HashSet<String>();
        JSONObject node = this.getNode();
        if (node == null) return ck;
        for (Object k : node.keySet()) {
            String ks = (String)k;
            if (!ks.startsWith("_")) ck.add(ks);
        }
        return ck;
    }
    public boolean hasChildren() {
        return (this.getChildKeys().size() > 0);
    }

    public void setModified() {
        modified = System.currentTimeMillis();
    }
    public long getModified() {
        return modified;
    }
    public long getCreated() {
        return created;
    }

    public JSONObject toJSONObject() {
        JSONObject m = this.getMeta();
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("idPath", new JSONArray(this.getIdPath()));
        j.put("meta", m);
        j.put("type", ConfigurationUtil.getType(m));
        j.put("isRootLevel", this.isRootLevel());
        j.put("isValid", this.isValid());
        j.put("validRoot", this.hasValidRoot());
        j.put("content", this.getContent());
        try {
            j.put("value", this.getValue());
        } catch (ConfigurationException ex) {}
        j.put("created", this.getCreated());
        j.put("modified", this.getModified());
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("type", this.getType())
                .append("isRootLevel", this.isRootLevel())
                .append("isValid", this.isValid())
                .append("validRoot", this.hasValidRoot())
                .toString();
    }
}
