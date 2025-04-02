package org.ecocean;

import org.json.JSONObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
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

    public Setting(String group, String id, List value) {
        SettingValidator sval = new SettingValidator(group, id, value);
        this.group = group;
        this.id = id;
        this.setValue(value);
    }

    public Setting(String group, String id, JSONObject value) {
        SettingValidator sval = new SettingValidator(group, id, value);
        this.group = group;
        this.id = id;
        this.setValueRaw(value);
    }

    public static boolean isValidGroupAndId(String group, String id) {
        return SettingValidator.isValidGroupAndId(group, id);
    }

    public String getGroup() {
        return group;
    }

    public String getId() {
        return id;
    }

    public JSONObject getValueRaw() { // only return as JSONObject!
        if (value == null) return null;
        return Util.stringToJSONObject(value);
    }

    public void setValueFromPayload(JSONObject payload) {
        if (payload == null) return;
        JSONObject value = new JSONObject();
        if (payload.has("data")) {
            value = new JSONObject(payload.toString());
        } else {
            value.put("data", payload);
        }
        if (value.optString("type", null) == null) value.put("type", typeFromData(value.get("data")));
        Object objValue = getValueFromJSONObject(value);
        SettingValidator sval = new SettingValidator(this.group, this.id, objValue);
        this.setValueRaw(value);
    }

    // currently used only for setValueFromPayload
    public String typeFromData(Object data) {
        if (data instanceof JSONArray) return "Array";
        if (data instanceof String) return "String";
        if (data instanceof Integer) return "Integer";
        if (data instanceof Double) return "Double";
        return "Unknown";
    }

    public void setValueRaw(String s) {
        // TODO probably should eventually use SettingValidator here
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

    private void setValue(String type, Object val) {
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

    public void setValue(List val) {
        JSONArray jarr = new JSONArray();
        if (!Util.collectionIsEmptyOrNull(val)) {
            for (Object el : val) {
                jarr.put(el);
            }
        }
        setValue("Array", jarr);
    }

/*
    this returns an Object, so to use this directly, you must cast to the expected
    class. not ideal. some casting convenience methods are below, but may not act
    graceful if casting fails. also, these are not (currently) accessible for the
    convenience all-in-one method: Shepherd.getSettingValue()

    getting List-types is a little sketchy now. you have to do something like:
         List<String> list = (List)setting.getValue();
    and trust that your values were cast correctly during conversion from JSONArray
    since for now its likely we will only be using an array of strings, lets start with
    this pattern and get more complicated when we need it
*/
    public Object getValue() {
        return getValueFromJSONObject(this.getValueRaw());
    }

    static public Object getValueFromJSONObject(JSONObject j) {
        if (j == null) return null;
        String type = j.optString("type", null);
        if (type == null) return null;
        Object rtn = null;
        if (type.equals("Array")) {
            JSONArray dataArr = j.optJSONArray("data");
            if (dataArr == null) return null;
            List arr = new ArrayList();
            for (int i = 0 ; i < dataArr.length() ; i++) {
                arr.add(dataArr.get(i));
            }
            return arr;

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

    public String getValueString() {
        return (String)getValue();
    }

    public Integer getValueInteger() {
        return (Integer)getValue();
    }

    public Double getValueDouble() {
        return (Double)getValue();
    }

    public static Map<String,String[]> getValidGroupsAndIds() {
        return SettingValidator.VALID_GROUPS_AND_IDS;
    }

    // generic way to set up things not already set up, e.g. available languages
    public static void initialize(String context) {
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("Setting.initialize");
        myShepherd.beginDBTransaction();
        try {
            Setting st = myShepherd.getSetting("language", "available");
            if (st == null) {
                st = new Setting("language", "available");
                List<String> langs = Arrays.asList(new String[]{"de", "en", "es", "fr", "it"});
                st.setValue(langs);
                myShepherd.storeSetting(st);
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            myShepherd.commitDBTransaction();
            myShepherd.closeDBTransaction();
        }
    }

    public JSONObject toJSONObject() {
        JSONObject j = new JSONObject();
        j.put("group", group);
        j.put("id", id);
        j.put("value", this.getValueRaw());
        j.put("created", created);
        j.put("modified", modified);
        return j;
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
        VALID_GROUPS_AND_IDS.put("language", new String[]{"site", "available"});
    }

    public static boolean isValidGroupAndId(String group, String id) {
        if ((group == null) || (id == null)) return false;
        if (!VALID_GROUPS_AND_IDS.containsKey(group)) return false;
        if (VALID_GROUPS_AND_IDS.get(group) == null) return false;
        return Arrays.asList(VALID_GROUPS_AND_IDS.get(group)).contains(id);
    }

/*
    right now this is going very light on any sort of validation of *value*
    TODO this is meant to be improved upon as various use cases pop up. back in codex
    world we had a complicated json structure of "definitions" that could be used to
    validate setting values. ended up feeling like way overkill. leaning toward doing
    it now just in code, here.
*/
    public SettingValidator(String group, String id) {
        this(group, id, null);
    }
    public SettingValidator(String group, String id, Object value) {
        if (!isValidGroupAndId(group, id)) throw new IllegalArgumentException("invalid group=" + group + " and/or id=" + id);
        String groupId = group + "." + id;
        switch (groupId) {
            case "language.site":
                if ((value == null) || !(value instanceof List)) throw new IllegalArgumentException("value must be a list");
                List<String> langs = new ArrayList<String>((List)value);
                if (langs.size() < 1) throw new IllegalArgumentException("value must have at least 1 value");

                Object alangs = null;
                Shepherd myShepherd = new Shepherd("context0"); // hacky but only need to read other Setting(s)
                myShepherd.setAction("SettingValidator");
                try {
                    myShepherd.beginDBTransaction();
                    alangs = myShepherd.getSettingValue("language", "available");
                } catch (Exception ex) {
                    ex.printStackTrace();
                } finally {
                    myShepherd.rollbackAndClose();
                }
                if ((alangs != null) && (alangs instanceof List)) {
                    List<String> availableLangs = new ArrayList<String>((List)alangs);
                    for (String lang : langs) {
                        if (!availableLangs.contains(lang)) throw new IllegalArgumentException(lang + " is not a valid value");
                    }
                }
                break;
        }
        // if we fall through, value is just allowed
    }
}

