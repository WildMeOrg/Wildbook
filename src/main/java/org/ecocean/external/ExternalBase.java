package org.ecocean.external;

import org.json.JSONObject;
import org.apache.commons.lang3.builder.ToStringBuilder;


public abstract class ExternalBase implements java.io.Serializable {
    private String id = null;  //assumed to be set, um, externally!
    private long version = 0l;

    public ExternalBase() {
        version = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public long getVersion() {
        return version;
    }
    public void setVersion(long v) {
        version = v;
    }
    public void setVersion() {
        version = System.currentTimeMillis();
    }

    public JSONObject toJSONObject() {
        JSONObject j = new JSONObject();
        j.put("class", this.getClass().getName());
        j.put("id", this.id);
        j.put("version", this.version);
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", this.id)
                .append("version", this.version)
                .toString();
    }
}

