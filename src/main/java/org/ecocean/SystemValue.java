package org.ecocean;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;

/**
 * SystemValue is a catchall storage of name/value(s) pairs
 *  value is stored as JSONObject (as string in db), but retrieval masks
 *  this underlying structure.  e.g.:
 *     SystemValue.getInteger(myShepherd, "fubar") will return Integer (null if not found / not integer)
 */

public class SystemValue implements java.io.Serializable {
    private static final long serialVersionUID = -2398728440056255450L;
    private String key;
    private String value;
    private long version;

    public SystemValue() {}

    public SystemValue(String k, JSONObject v) {
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

    public static SystemValue load(Shepherd myShepherd, String key) {
        try {
            return ((SystemValue) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(SystemValue.class, key), true)));
        } catch (Exception ex) {
            return null;
        }
    }

    //load if exists, create if not
    public static SystemValue obtain(Shepherd myShepherd, String key) {
        SystemValue sv = load(myShepherd, key);
        if (sv != null) return sv;
        return new SystemValue(key, null);
    }

    public void store(Shepherd myShepherd) {
        this.version = System.currentTimeMillis();
        myShepherd.getPM().makePersistent(this);
    }

    private static SystemValue _set(Shepherd myShepherd, String key, String type, Object val) {
        JSONObject jv = new JSONObject();
        jv.put("type", type);
        jv.put("value", val);
        SystemValue sv = obtain(myShepherd, key);
        sv.setValue(jv);
        sv.store(myShepherd);
        return sv;
    }

    public static SystemValue set(Shepherd myShepherd, String key, Integer val) {
        return _set(myShepherd, key, "Integer", val);
    }
    public static SystemValue set(Shepherd myShepherd, String key, Long val) {
        return _set(myShepherd, key, "Long", val);
    }
    public static SystemValue set(Shepherd myShepherd, String key, Double val) {
        return _set(myShepherd, key, "Double", val);
    }
    public static SystemValue set(Shepherd myShepherd, String key, String val) {
        return _set(myShepherd, key, "String", val);
    }
    public static SystemValue set(Shepherd myShepherd, String key, JSONObject val) {
        return _set(myShepherd, key, "JSONObject", val);
    }

    public static JSONObject getValue(Shepherd myShepherd, String key) {
        SystemValue sv = load(myShepherd, key);
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
            System.out.println("WARNING: parse error on " + v.toString() + ": " + ex.toString());
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
            System.out.println("WARNING: parse error on " + v.toString() + ": " + ex.toString());
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
            System.out.println("WARNING: parse error on " + v.toString() + ": " + ex.toString());
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
            System.out.println("WARNING: parse error on " + v.toString() + ": " + ex.toString());
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
            System.out.println("WARNING: parse error on " + v.toString() + ": " + ex.toString());
            return null;
        }
    }


    public String toString() {
        return new ToStringBuilder(this)
                .append("key", key)
                .append("valueObj", (this.getValue() == null) ? null : this.getValue().opt("value"))
                .append("rawValueContent", value)
                .append("version", version)
                .append("versionDateTime", new DateTime(version))
                .toString();
    }
}
