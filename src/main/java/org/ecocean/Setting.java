package org.ecocean;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;

public class Setting implements java.io.Serializable {
    private String group;
    private String id;
    private String value = null;
    private long created = System.currentTimeMillis();
    private long modified = System.currentTimeMillis();

    public Setting() {}

    public Setting(String group, String id) {
        SettingValidator sval = new SettingValidator(group, id);
        this.group = group;
        this.id = id;
    }

    public Setting(String group, String id, String value) {
        SettingValidator sval = new SettingValidator(group, id, value);
        this.group = group;
        this.id = id;
        this.setValueRaw(value);
    }

    public Setting(String group, String id, JSONObject value) {
        SettingValidator sval = new SettingValidator(group, id, value);
        this.group = group;
        this.id = id;
        this.setValueRaw(value);
    }

    public JSONObject getValueRaw() { // only return as JSONObject!
        if (value == null) return null;
        return Util.stringToJSONObject(value);
    }

    public void setValueRaw(String s) {
        JSONObject test = Util.stringToJSONObject(s); // bad json => null
        this.modified = System.currentTimeMillis();
        if (test == null) {
            value = null;
            return;
        }
        value = s;
    }

    public void setValueRaw(JSONObject j) {
        this.modified = System.currentTimeMillis();
        if (j == null) {
            value = null;
        } else {
            value = j.toString();
        }
    }

    public void setValue(String type, Object val) {
        JSONObject jv = new JSONObject();
        jv.put("type", type);
        jv.put("data", val);
        this.value = jv.toString();
    }

    public void setValue(String val) {
        setValue("String", val);
    }

    public void setValue(Integer val) {
        setValue("Integer", val);
    }

    public void setValue(Double val) {
        setValue("Double", val);
    }

    public void setValue(JSONObject val) {
        setValue("JSONObject", val);
    }

    public void setValue(boolean val) {
        setValue("Boolean", val);
    }

    public Object getValue() {
        JSONObject j = this.getValueRaw();
        if (j == null) return null;
        String type = j.optString("type", null);
        if (type == null) return null;
        Object rtn = null;
        if (type.startsWith("Array")) {
            JSONArray dataArr = j.optJSONArray("data");
            if (dataArr == null) return null;
        } else {
            try {
                switch (type) {
                    case "String":
                        rtn = j.optString("data");
                        break;
                    case "Integer":
                        rtn = j.getInt("data");
                        break;
                    case "Double":
                        rtn = j.getDouble("data");
                        break;
                    case "JSONObject":
                        rtn = j.optJSONObject("data");
                        break;
                    case "Boolean":
                        rtn = j.getBoolean("data");
                        break;
                    default:
                        System.out.println("unknown Setting type=" + type);
                }
            } catch (JSONException ex) {
                System.out.println("Setting.getValue() returning null due to " + ex);
            }
        }
        return rtn;
    }

    public String toString() {
        return new ToStringBuilder(this)
                   .append("group", group)
                   .append("id", id)
                   .append("value", value)
                   .toString();
    }
}

/*
    in codex we defined valid settings (and their values) by a complex set of json-based "definitions"
    going to try doing it as a class here instead to keep it simple, at least to start
*/

class SettingValidator {
    public static final Map<String,String[]> VALID_GROUPS_AND_IDS;
    static {
        VALID_GROUPS_AND_IDS = new HashMap<>();
        VALID_GROUPS_AND_IDS.put("language", new String[]{"site"});
    }

    public static boolean isValidGroupAndId(String group, String id) {
        if ((group == null) || (id == null)) return false;
        if (!VALID_GROUPS_AND_IDS.containsKey(group)) return false;
        if (VALID_GROUPS_AND_IDS.get(group) == null) return false;
        return Arrays.asList(VALID_GROUPS_AND_IDS.get(group)).contains(id);
    }

    public SettingValidator(String group, String id) {
        this(group, id, null);
    }
    public SettingValidator(String group, String id, Object value) {
        if (!isValidGroupAndId(group, id)) throw new IllegalArgumentException("invalid group=" + group + " and/or id=" + id);
    }
}

