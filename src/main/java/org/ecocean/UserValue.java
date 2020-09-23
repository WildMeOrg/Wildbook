package org.ecocean;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.external.ExternalUser;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/**
 *  basically a rip-off of SystemValue, but with an owner field
 *   not inheriting for future munging
 */

public class UserValue implements java.io.Serializable {
    private ExternalUser owner;
    private String key;
    private String value;
    private long version;

    public UserValue() {}

    public UserValue(String k, JSONObject v) {
        if (k == null) return;
        key = k;
        if (v != null) value = v.toString();
        version = System.currentTimeMillis();
    }

    public JSONObject getValue() {
        return Util.stringToJSONObject(value);
    }

    public void setValue(JSONObject v) {
        if (v == null) {
            value = null;
        } else {
            value = v.toString();
        }
    }

    public static UserValue load(Shepherd myShepherd, String key) {
        try {
            return ((UserValue) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(UserValue.class, key), true)));
        } catch (Exception ex) {
            return null;
        }
    }

    //load if exists, create if not
    public static UserValue obtain(Shepherd myShepherd, String key) {
        UserValue sv = load(myShepherd, key);
        if (sv != null) return sv;
        return new UserValue(key, null);
    }

    public void store(Shepherd myShepherd) {
        this.version = System.currentTimeMillis();
        myShepherd.getPM().makePersistent(this);
    }

    private static UserValue _set(Shepherd myShepherd, String key, String type, Object val) {
        JSONObject jv = new JSONObject();
        jv.put("type", type);
        jv.put("value", val);
        UserValue sv = obtain(myShepherd, key);
        sv.setValue(jv);
        sv.store(myShepherd);
        return sv;
    }

    public static UserValue set(Shepherd myShepherd, String key, Integer val) {
        return _set(myShepherd, key, "Integer", val);
    }
    public static UserValue set(Shepherd myShepherd, String key, Long val) {
        return _set(myShepherd, key, "Long", val);
    }
    public static UserValue set(Shepherd myShepherd, String key, Double val) {
        return _set(myShepherd, key, "Double", val);
    }
    public static UserValue set(Shepherd myShepherd, String key, String val) {
        return _set(myShepherd, key, "String", val);
    }
    public static UserValue set(Shepherd myShepherd, String key, JSONObject val) {
        return _set(myShepherd, key, "JSONObject", val);
    }

    public static JSONObject getValue(Shepherd myShepherd, String key) {
        UserValue sv = load(myShepherd, key);
        if (sv == null) return null;
        return sv.getValue();
    }

    public static Integer getInteger(Shepherd myShepherd, String key) {
        JSONObject v = getValue(myShepherd, key);
        if (v == null) return null;
        if (v.isNull("value")) return null;
        try {
            return v.getInt("value");
        } catch (JSONException ex) {
            SystemLog.warn("WARNING: parse error on {}: {}", v.toString(), ex.toString());
            return null;
        }
    }
    public static Long getLong(Shepherd myShepherd, String key) {
        JSONObject v = getValue(myShepherd, key);
        if (v == null) return null;
        if (v.isNull("value")) return null;
        try {
            return v.getLong("value");
        } catch (JSONException ex) {
            SystemLog.warn("WARNING: parse error on {}: {}", v.toString(), ex.toString());
            return null;
        }
    }
    public static Double getDouble(Shepherd myShepherd, String key) {
        JSONObject v = getValue(myShepherd, key);
        if (v == null) return null;
        if (v.isNull("value")) return null;
        try {
            return v.getDouble("value");
        } catch (JSONException ex) {
            SystemLog.warn("WARNING: parse error on {}: {}", v.toString(), ex.toString());
            return null;
        }
    }
    public static String getString(Shepherd myShepherd, String key) {
        JSONObject v = getValue(myShepherd, key);
        if (v == null) return null;
        if (v.isNull("value")) return null;
        try {
            return v.getString("value");
        } catch (JSONException ex) {
            SystemLog.warn("WARNING: parse error on {}: {}", v.toString(), ex.toString());
            return null;
        }
    }
    public static JSONObject getJSONObject(Shepherd myShepherd, String key) {
        JSONObject v = getValue(myShepherd, key);
        if (v == null) return null;
        if (v.isNull("value")) return null;
        try {
            return v.getJSONObject("value");
        } catch (JSONException ex) {
            SystemLog.warn("WARNING: parse error on {}: {}", v.toString(), ex.toString());
            return null;
        }
    }


    public String toString() {
        return new ToStringBuilder(this)
                .append("key", key)
                .append("valueObj", (this.getValue() == null) ? null : this.getValue().opt("value"))
                .append("owner", owner)
                .append("rawValueContent", value)
                .append("version", version)
                .append("versionDateTime", new DateTime(version))
                .toString();
    }
}
