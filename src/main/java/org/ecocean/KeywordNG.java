package org.ecocean;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.external.ExternalUser;
import org.json.JSONObject;
import org.json.JSONException;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.joda.time.DateTime;
import javax.jdo.Query;
import java.util.List;
import java.util.Iterator;

/**
 *  Keywords, for next-gen
 */

public class KeywordNG implements java.io.Serializable {
    private String id;
    private String value;
    private long version;

    public KeywordNG() {}

    public KeywordNG(JSONObject v) {
        id = Util.generateUUID();
        if (v != null) value = v.toString();
        this.setVersion();
    }

    public KeywordNG(String value) {
        this(value, null);
    }
    public KeywordNG(String value, String lang) {
        this((JSONObject)null);
        if (lang == null) lang = "";
        JSONObject jv = new JSONObject();
        jv.put(lang, value);
        this.setValue(jv);
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

    public void setVersion() {
        this.version = System.currentTimeMillis();
    }

    public static KeywordNG load(Shepherd myShepherd, String id) {
        try {
            return ((KeywordNG) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(KeywordNG.class, id), true)));
        } catch (Exception ex) {
            return null;
        }
    }

    /*
        this is a little hacky in order to be somewhat efficient in finding matches, but ultimately must return an exact match
        against value + language; otherwise return a new one
    */
    public static KeywordNG obtain(Shepherd myShepherd, String value, String lang) {
        if (lang == null) lang = "";
        // TODO will this work with unicode???
        String sql = "SELECT * FROM \"KEYWORDNG\" WHERE \"VALUE\" like ?;";
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        q.setClass(KeywordNG.class);
        q.setParameters("%" + lang + "%:%" + value + "%");
        KeywordNG found = null;
        List<KeywordNG> results = q.executeList();
        if (results.size() > 0) {
            found = results.get(0);
        } else {
            found = new KeywordNG(value, lang);
            found.store(myShepherd);
System.out.println("made? " + found);
        }
        q.closeAll();
System.out.println("found => " + found);
        return found;
    }

    public void store(Shepherd myShepherd) {
        this.version = System.currentTimeMillis();
        myShepherd.getPM().makePersistent(this);
    }

    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }
    public JSONObject toJSONObject(boolean full) {
        JSONObject j = new JSONObject();
        j.put("id", this.id);
        j.put("version", this.version);
        if (full) j.put("value", this.getValue());
        return j;
    }

    public String toString() {
        return new ToStringBuilder(this)
                .append("id", this.id)
                .append("value", this.getValue())
                .append("version", version)
                .append("versionDateTime", new DateTime(version))
                .toString();
    }
}
