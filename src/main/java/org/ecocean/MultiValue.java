package org.ecocean;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import javax.servlet.http.HttpServletRequest;
import org.json.JSONObject;
import org.json.JSONArray;
import org.apache.commons.lang3.builder.ToStringBuilder;

public class MultiValue implements java.io.Serializable {
    private int id;
    private Map<String,List<String>> values = new HashMap<String,List<String>>();
    private static final String DEFAULT_KEY_VALUE = "*";

    public MultiValue() {
    }
  
    /*
        a note on 'keyHint':

        this should be used carefully, especially when *setting* using this, as it will (likely?) include
        the DEFAULT key.  this means you will be setting the value for default as well.  if you wish to avoid
        this behavior, access (set or get) by a specific list of keys.  in particular, check out
        generateKeys() with a second arg ('includeDefault')
    */

    public MultiValue(Object keyHint, String initialValue) {
        this();
        this.setValuesByKeys(generateKeys(keyHint), initialValue);
    }

    public void setValuesByKeys(Set<String> keys, String value) {
        if (value == null) return;  //??
        for (String key : keys) {
            if (values.get(key) == null) values.put(key, new ArrayList<String>());
            if (!values.get(key).contains(value)) values.get(key).add(value);
        }
    }
    public void setValuesByKey(String key, String value) {  //convenience method
        Set<String> keys = new HashSet<String>();
        keys.add(key);
        setValuesByKeys(keys, value);
    }
    public void setValues(Object keyHint, String value) {
        setValuesByKeys(generateKeys(keyHint), value);
    }
    public void setValuesDefault(String value) {
        setValuesByKey(DEFAULT_KEY_VALUE, value);
    }

    //this could get values across multiple keys, but wont get duplicates
    public List<String> getValues(Object keyHint) {
        return getValuesByKeys(generateKeys(keyHint));
    }
    public List<String> getValuesByKeys(Set<String> keys) {
        List<String> rtn = new ArrayList<String>();
        for (String key : keys) {
            if (values.get(key) == null) continue;
            for (String val : values.get(key)) {
                if (!rtn.contains(val)) rtn.add(val);
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
        return values.get(DEFAULT_KEY_VALUE);
    }
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

    //TODO? getAllValues()

    public Set<String> getKeys() {
        return values.keySet();
    }

    public JSONObject toJSONObject(Object keyHint) {
        JSONObject rtn = new JSONObject();
        rtn.put("values", new JSONArray(getValues(keyHint)));
        rtn.put("map", new JSONObject(getValuesMap(keyHint)));
        return rtn;
    }
    public JSONObject toJSONObject() {  //default only
        JSONObject rtn = new JSONObject();
        if (values.get(DEFAULT_KEY_VALUE) == null) return rtn;
        JSONObject jmap = new JSONObject();
        jmap.put(DEFAULT_KEY_VALUE, new JSONArray(values.get(DEFAULT_KEY_VALUE)));
        rtn.put("map", jmap);
        rtn.put("values", new JSONArray(values.get(DEFAULT_KEY_VALUE)));
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

}

