package org.ecocean;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class MultiValue implements java.io.Serializable {
    static final long serialVersionUID = 8831423450447974780L;
    private int id;
    protected JSONObject values;
    protected String valuesAsString;
    private static final String DEFAULT_KEY_VALUE = "*";

    public MultiValue() {
    }

/*
    public MultiValue(JSONObject values) {
System.out.println("ZZZZZZZ values=" + values);
System.out.println("ZZZZZZZ valuesAsString=" + valuesAsString);
        this.values = values;
        if (values != null) this.valuesAsString = values.toString();
    }
*/

    /*
        a note on 'keyHint':

        this should be used carefully, especially when *setting* using this, as it will (likely?) include
        the DEFAULT key.  this means you will be setting the value for default as well.  if you wish to avoid
        this behavior, access (set or get) by a specific list of keys.  in particular, check out
        generateKeys() with a second arg ('includeDefault')
    */

    public MultiValue(Object keyHint, String initialValue) {
        super();
        this.setValuesByKeys(generateKeys(keyHint), initialValue);
    }

    public int getId() {
        return id;
    }

    public JSONObject getValues() {
        if (values != null) return values;
        JSONObject j = Util.stringToJSONObject(valuesAsString);
        values = j;
//System.out.println("DEBUG: getValues values=" + values);
        return j;
    }
    public void setValues(JSONObject j) {
        if (j == null) return;
//System.out.println("DEBUG: setValues values=" + j);
        values = j;
        valuesAsString = j.toString();
    }
/*
    public void updateValuesAsString() {
System.out.println("DEBUG: updateValuesAsString() ???? values ---> " + values);
        if (values == null) {
            valuesAsString = null;
        } else {
            valuesAsString = values.toString();
        }
        setValuesAsString(valuesAsString);
System.out.println("DEBUG: updateValuesAsString() ???? valuesAsString ---> " + valuesAsString);
    }
*/

    public String getValuesAsString() {
        if (valuesAsString != null) return valuesAsString;
        if (values == null) return null;
        valuesAsString = values.toString();
//System.out.println("DEBUG: getValuesAsString valuesAsString=" + valuesAsString);
        return valuesAsString;
    }

    public void setValuesAsString(String s) {
//System.out.println("DEBUG: setValuesAsString s=" + s);
        valuesAsString = s;
        values = Util.stringToJSONObject(s);
    }


    public void setValuesByKeys(Set<String> keys, String value) {
        if (keys == null) return;
        if (value == null) return;
        for (String key : keys) {
            setValuesByKey(key, value);
        }
    }
    public void setValuesByKey(String key, String value) {
//System.out.println("key=>[" + key + "] value=" + value);
        if (key == null) return;
        if (value == null) return;
        JSONObject clone = getValues();
        if (clone == null) clone = new JSONObject();
//System.out.println("????? 1.CLONE -> " + clone);
        //if (clone != null) clone = new JSONObject(values, JSONObject.getNames(values));
        if (clone.optJSONArray(key) == null) clone.put(key, new JSONArray());
        if (!getValuesByKey(key).contains(value)) clone.getJSONArray(key).put(value);  //getValuesByKey is fine working on orig values
//JSONObject j = new JSONObject(values.toString());
//System.out.println("????? 2.CLONE -> " + clone);
        setValues(clone);
    }
    public void setValues(Object keyHint, String value) {
        setValuesByKeys(generateKeys(keyHint), value);
    }
    public void setValuesDefault(String value) {
        setValuesByKey(DEFAULT_KEY_VALUE, value);
    }

    //this could get values across multiple keys, but wont get duplicates
    public List<String> getValuesAsList(Object keyHint) {
        return getValuesByKeys(generateKeys(keyHint));
    }
    public List<String> getValuesByKeys(Set<String> keys) {
        List<String> rtn = new ArrayList<String>();
        if (getValues() == null) return rtn;
        for (String key : keys) {
            JSONArray v = values.optJSONArray(key);
            if (v == null) continue;
            for (int i = 0 ; i < v.length() ; i++) {
                String val = v.optString(i, null);
                if ((val != null) && !rtn.contains(val)) rtn.add(val);
            }
        }
        return rtn;
    }
    public List<String> getValuesByKey(String key) {  //convenience singular key
        Set<String> keys = new HashSet<String>();
        keys.add(key);
        return getValuesByKeys(keys);
    }
    public List<String> getValuesDefault() {
        return getValuesByKey(DEFAULT_KEY_VALUE);
    }
/* dont really think we need these?
    //returns a map from keys to values (for only passed keys)
    public Map<String,List<String>> getValuesMap(Object keyHint) {
        return getValuesMapByKeys(generateKeys(keyHint));
    }
    public Map<String,List<String>> getValuesMapByKeys(Set<String> keys) {
        Map<String,List<String>> rtn = new HashMap<String,List<String>>();
        for (String key : keys) {
            if (values.get(key) == null) continue;
            rtn.put(key, values.get(key));
        }
        return rtn;
    }
*/

    //TODO FIXME
    public boolean removeAllValues(String value) {  //regardless of key
        System.out.println("removeAllValues() FEATURE NOT YET SUPPORTED");
        return false;
    }
    public boolean removeValuesByKeys(Set<String> keys, String value) {
        System.out.println("removeAllValuesByKeys() FEATURE NOT YET SUPPORTED");
        return false;
    }
    public boolean removeValues(Object keyHint, String value) {
        return removeValuesByKeys(generateKeys(keyHint), value);
    }

    //this is made contain only one of each (in the event of duplicates)
    public Set<String> getAllValues() {
        Set<String> rtn = new HashSet<String>();
        if (values == null) return rtn;
        Iterator it = values.keys();
        while (it.hasNext()) {
            String key = (String)it.next();
            JSONArray v = values.optJSONArray(key);
            if (v == null) continue;
            for (int i = 0 ; i < v.length() ; i++) {
                String val = v.optString(i, null);
                if ((val != null) && !rtn.contains(val)) rtn.add(val);
            }
        }
        return rtn;
    }

    public Set<String> getKeys() {
        if (values == null) return null;
        Set<String> rtn = new HashSet<String>();
        Iterator it = values.keys();
        while (it.hasNext()) {
            rtn.add((String)it.next());
        }
        return rtn;
    }

    public JSONObject toJSONObject(Object keyHint) {
        JSONObject rtn = new JSONObject();
        if (values == null) return rtn;
        for (String key : generateKeys(keyHint)) {
            rtn.put(key, values.optJSONArray(key));
        }
        return rtn;
    }
    public JSONObject toJSONObject() {  //default only
        JSONObject rtn = new JSONObject();
        if (values == null) return rtn;
        rtn.put(DEFAULT_KEY_VALUE, values.optJSONArray(DEFAULT_KEY_VALUE));
        return rtn;
    }

    //this does a bunch of magic to get a "key" that matches the "user context" (whatever that means!)
    public static Set<String> generateKeys(Object keyHint) {
        return generateKeys(keyHint, true);  //default includes DEFAULT_KEY_VALUE -- you have been warned!
    }
    public static Set<String> generateKeys(Object keyHint, boolean includeDefault) {
        Set<String> rtn = new HashSet();
        if (includeDefault) rtn.add(DEFAULT_KEY_VALUE);
        if (keyHint == null) return rtn;  //gets us just default (or empty if !includeDefault !!)
        if (keyHint instanceof HttpServletRequest) {
            Shepherd myShepherd = new Shepherd("context0");  //ok cuz all users live in context0, no????
            User u = myShepherd.getUser((HttpServletRequest)keyHint);
            if (u != null) rtn.addAll(u.getMultiValueKeys());
        //if possible, supply Organization rather than User, as User will set on all their Organizations
        } else if (keyHint instanceof Organization) {
            Organization org = (Organization)keyHint;
            rtn.addAll(org.getMultiValueKeys());
        } else if (keyHint instanceof User) {
            User u = (User)keyHint;
            rtn.addAll(u.getMultiValueKeys());
        } else if (keyHint instanceof String) {  //this may(?) only be for testing purposes
            rtn.add("_RAW_:" + (String)keyHint);
        }
        return rtn;
    }



    public String toString() {
        return new ToStringBuilder(this)
                .append("id", id)
                .append("keys", this.getKeys())
                .toString();
    }

    public JSONObject debug() {
        JSONObject j = new JSONObject();
        j.put("values_raw", getValues());
        j.put("id", id);
        return j;
    }
}

