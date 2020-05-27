package org.ecocean;

import java.util.List;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.DateTimeException;
/*
import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ContextConfiguration;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.Iterator;
import java.io.File;
import javax.jdo.Query;
*/

public class DataDefinition {
    private JSONObject meta;
    //public static final String META_KEY = "__meta";
    public static final String VALUE_KEY = "__value";
    public static final String[] TYPES = new String[]{
        "string",
        "integer",
        "double",
        "long",
        "date",
        "url",
        "uuid",
        "image",
        "color",
        "geo",
        "json",
        "customFields",  //data is actually json
        "locationIds",   //  same
        "taxonomy"
    };

    public DataDefinition() {
    }
    public DataDefinition(JSONObject m) {
        this();
        if (m == null) throw new RuntimeException("DataDefinition passed null meta");
        meta = m;
    }

    public static boolean isValidType(String t) {
        if (t == null) return false;
        for (int i = 0 ; i < TYPES.length ; i++) {
            if (t.equals(TYPES[i])) return true;
        }
        return false;
    }

    //a whole bunch of these based on diff incoming types
    // TODO make these actually verify against meta!!!
    /// TODO support .allowEmpty as option *when require=T*
    public boolean checkValidity(Boolean b) throws DataDefinitionException {
        return true;
    }
    public boolean checkValidity(Integer i) throws DataDefinitionException {
        return true;
    }
    public boolean checkValidity(Double d) throws DataDefinitionException {
        return true;
    }
    public boolean checkValidity(Long l) throws DataDefinitionException {
        return true;
    }
    public boolean checkValidity(String s) throws DataDefinitionException {
        return true;
    }
    public boolean checkValidity(ZonedDateTime dt) throws DataDefinitionException {
        return true;
    }

    public String getType() {
        return getType(this.meta);
    }
    public static String getType(JSONObject meta) {
        if (meta == null) return null;
        String t = meta.optString("type", null);
        if (t == null) return null;
        if (!isValidType(t)) {
            System.out.println("WARNING: invalid type found in " + meta.toString());  //worth noting, i guess
            return null;
        }
        return t;
    }

    //this does a whole bunch of magic to get a json-settable value from any kind of nonsense that comes in
    //  it also does validity checks!
    public Object handleValue(Object inVal) throws DataDefinitionException {
        String type = this.getType();
        if (type == null) {
            System.out.println("WARNING: DataDefinition.handleValue() missing meta/type for " + this.toString());
            return inVal;
        }
        switch (type) {
            case "string":
                String s = null;
                if (inVal instanceof String) {
                    s = (String)inVal;
                }
                checkValidity(s);
                return s;
            case "boolean":
                Boolean b = null;
                if (inVal instanceof String) {
                    b = !((String)inVal).toLowerCase().startsWith("f");
                } else if (inVal instanceof Integer) {
                    b = ((Integer)inVal != 0);
                } else if (inVal instanceof Boolean) {
                    b = (Boolean)inVal;
                }
                checkValidity(b);
                return b;
            case "integer":
                Integer i = null;
                if (inVal instanceof Integer) {
                    i = (Integer)inVal;
                } else if (inVal instanceof String) {
                    try {
                        i = Integer.parseInt((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new DataDefinitionException("could not parse Integer from " + (String)inVal);
                    }
                }
                checkValidity(i);
                return i;
            case "long":
                Long l = null;
                if (inVal instanceof Long) {
                    l = (Long)inVal;
                } else if (inVal instanceof String) {
                    try {
                        l = Long.parseLong((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new DataDefinitionException("could not parse Long from " + (String)inVal);
                    }
                }
                checkValidity(l);
                return l;
            case "double":
                Double d = null;
                if (inVal instanceof Double) {
                    d = (Double)inVal;
                } else if (inVal instanceof String) {
                    try {
                        d = Double.parseDouble((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new DataDefinitionException("could not parse Double from " + (String)inVal);
                    }
                }
                checkValidity(d);
                return d;
            case "date":
                ZonedDateTime dt = null;
                if (inVal instanceof ZonedDateTime) {
                    dt = (ZonedDateTime)inVal;
                } else if (inVal instanceof Long) {
                    try {
                        dt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((Long)inVal), ZoneOffset.UTC);
                    } catch (DateTimeException ex) {
                        throw new DataDefinitionException("could not parse ZonedDateTime from " + (Long)inVal + " - " + ex.toString());
                    }
                } else if (inVal instanceof String) {
                    try {
                        dt = ZonedDateTime.parse((String)inVal);
                    } catch (DateTimeException ex) {
                        throw new DataDefinitionException("could not parse ZonedDateTime from " + (String)inVal + " - " + ex.toString());
                    }
                }
                checkValidity(dt);
                return dt;
        }
        return inVal;
    }

/*
    there are also public static to use them directly (without having to create DataDefinition first
*/

    public String coerceString(JSONObject content) throws DataDefinitionException {
        return coerceString(content, this.meta);
    }
    public static String coerceString(JSONObject content, JSONObject meta) throws DataDefinitionException {
        if ((content == null) || (meta == null)) throw new DataDefinitionException("invalid content/meta arguments");
        _precheckSingle(meta);
        String type = getType(meta);
        //all these json values seem to cast just fine to string
        if ((type == null) || type.equals("string") || type.equals("date") || type.equals("integer") || type.equals("boolean") || type.equals("double") || type.equals("color") || type.matches("uuid")) {
            String s = content.optString(VALUE_KEY, null);
            if ((s != null) || content.has(VALUE_KEY)) return s;
        }
        throw new DataDefinitionException("could not coerce String from " + content.toString());
    }
    public Integer coerceInteger(JSONObject content) throws DataDefinitionException {
        return coerceInteger(content, this.meta);
    }
    public static Integer coerceInteger(JSONObject content, JSONObject meta) throws DataDefinitionException {
        if ((content == null) || (meta == null)) throw new DataDefinitionException("invalid content/meta arguments");
        _precheckSingle(meta);
        String type = getType(meta);
        if ((type == null) || !type.equals("integer")) throw new DataDefinitionException("not type=integer");
        if (content.has(VALUE_KEY) && content.isNull(VALUE_KEY)) return null;  //legit null
        int i = content.optInt(VALUE_KEY, 0);
        //this wonky second fallback is to prove no int there (vs actual 0)
        if ((i == 0) && (content.optInt(VALUE_KEY, 1) == 1)) throw new DataDefinitionException("could not coerce Integer from " + content.toString());
        return i;
    }
    public Double coerceDouble(JSONObject content) throws DataDefinitionException {
        return coerceDouble(content, this.meta);
    }
    public static Double coerceDouble(JSONObject content, JSONObject meta) throws DataDefinitionException {
        if ((content == null) || (meta == null)) throw new DataDefinitionException("invalid content/meta arguments");
        _precheckSingle(meta);
        String type = getType(meta);
        if ((type == null) || !type.equals("double")) throw new DataDefinitionException("not type=double");
        if (content.has(VALUE_KEY) && content.isNull(VALUE_KEY)) return null;  //legit null
        double d = content.optDouble(VALUE_KEY, 0d);
        //this wonky second fallback is to prove no int there (vs actual 0)
        if ((d == 0d) && (content.optDouble(VALUE_KEY, 1d) == 1d)) throw new DataDefinitionException("could not coerce Double from " + content.toString());
        return d;
    }
    public Boolean coerceBoolean(JSONObject content) throws DataDefinitionException {
        return coerceBoolean(content, this.meta);
    }
    public static Boolean coerceBoolean(JSONObject content, JSONObject meta) throws DataDefinitionException {
        if ((content == null) || (meta == null)) throw new DataDefinitionException("invalid content/meta arguments");
        _precheckSingle(meta);
        String type = getType(meta);
        if ((type == null) || !type.equals("boolean")) throw new DataDefinitionException("not type=double");
        if (content.has(VALUE_KEY) && content.isNull(VALUE_KEY)) return null;  //legit null
        boolean b = content.optBoolean(VALUE_KEY, false);
        //this wonky second fallback is to prove no int there (vs actual 0)
        if (!b && content.optBoolean(VALUE_KEY, true)) throw new DataDefinitionException("could not coerce Boolean from " + content.toString());
        return b;
    }

    public List<String> coerceStringList(JSONObject content) throws DataDefinitionException {
        return coerceStringList(content, this.meta);
    }
    public static List<String> coerceStringList(JSONObject content, JSONObject meta) throws DataDefinitionException {
        if ((content == null) || (meta == null)) throw new DataDefinitionException("invalid content/meta arguments");
        String type = getType(meta);
        if ((type == null) || type.equals("string") || type.equals("date") || type.equals("integer") || type.equals("boolean") || type.equals("double")) {
            List<String> list = new ArrayList<String>();
            JSONArray arr = content.optJSONArray(VALUE_KEY);
            if (arr == null) {  //we allow a single value to be promoted to array here
                list.add(coerceString(content, meta));
            } else {
                for (int i = 0 ; i < arr.length() ; i++) {
                    if (arr.isNull(i)) {
                        list.add(null);
                    } else {
                        String s = arr.optString(i);
                        if (s == null) throw new DataDefinitionException("unable to coerce String from i=" + i + " in " + arr.toString());
                        list.add(s);
                    }
                }
            }
            return list;
        }
        throw new DataDefinitionException("could not coerce String from " + content.toString());
    }

    private static JSONObject _precheckSingle(JSONObject meta) throws DataDefinitionException {
        if (meta == null) return null;  //out of scope here? let the caller handle!
        if (isMultiple(meta)) throw new DataDefinitionException("calling single value on multiple for meta=" + meta);
        return meta;
    }

    public boolean isMultiple() {
        return isMultiple(this.meta);
    }
    public static boolean isMultiple(JSONObject meta) {
        if (meta == null) return false;
        if (meta.optBoolean("multiple", false)) return true;  //vanilla
        int min = meta.optInt("multipleMin", -1);
        int max = meta.optInt("multipleMax", -1);
        if ((min > 1) || (max > 1)) return true;
        return false;
    }
    public boolean isReadOnly() {
        return isReadOnly(this.meta);
    }
    public static boolean isReadOnly(JSONObject meta) {
        if (meta == null) return true;  //kinda weird?
        return meta.optBoolean("readOnly", false);
    }
    public boolean isPrivate() {
        return isPrivate(this.meta);
    }
    public static boolean isPrivate(JSONObject meta) {
        if (meta == null) return true;  //kinda weird?
        return meta.optBoolean("private", false);
    }
/*
    // this is the "inverse" of handleValue() above, in that it tries to get the right kind of object out of this
    public static Object coerceValue(Configuration conf) throws DataDefinitionException {
System.out.println("*** coerceValue() conf=" + conf);
        if ((conf == null) || !conf.isValid()) throw new DataDefinitionException("coerceValue() given invalid conf=" + conf);
        JSONObject meta = conf.getMeta();
        String type = getType(meta);
        JSONObject cont = conf.getContent();
        String key = VALUE_KEY;  //this is only valid one for now: content.__value = (?)
System.out.println("*** coerceValue(key=" + key + ";type=" + type + ") content=" + cont);
        if ((cont == null) || cont.isNull(key)) return null;
        if (type == null) return cont.opt(key); //good luck, part 1
        switch (type) {
            case "string":
                return (String)cont.optString(key, null);
            case "boolean":
                return (Boolean)cont.optBoolean(key, false);
            case "integer":
                return (Integer)cont.optInt(key, 0);
            case "double":
                return (Double)cont.optDouble(key, 0.0d);
        }
        return cont.opt(key); //good luck, part 2
    }
*/

}

