package org.ecocean.configuration;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.io.File;
import org.ecocean.Util;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class Configuration implements java.io.Serializable {
    private String id;
    private String content = null;
    private long created;

    public Configuration() {
        this.created = System.currentTimeMillis();
    }
    public Configuration(String id) {
        this();
        this.id = id;
    }

    public JSONObject getContent() {
        return Util.stringToJSONObject(content);
    }

    public Object getValue() {
        if (!this.isValid()) return null;
        return "OKAY";
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
        return true;
    }

    public JSONObject toJSONObject() {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("idPath", new JSONArray(this.getIdPath()));
        j.put("isRootLevel", this.isRootLevel());
        j.put("isValid", this.isValid());
        j.put("validRoot", this.hasValidRoot());
        j.put("content", this.getContent());
        j.put("value", this.getValue());
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("isRootLevel", this.isRootLevel())
                .append("isValid", this.isValid())
                .append("validRoot", this.hasValidRoot())
                .toString();
    }
}
