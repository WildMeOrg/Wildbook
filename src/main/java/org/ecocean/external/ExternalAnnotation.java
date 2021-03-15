package org.ecocean.external;

import org.ecocean.Shepherd;
import org.ecocean.KeywordNG;
import org.ecocean.Util;
import java.util.Set;
import java.util.HashSet;
import org.json.JSONObject;
import org.json.JSONArray;

public class ExternalAnnotation extends org.ecocean.external.ExternalBase {
    private Set<KeywordNG> keywords;


    public void setKeywords(Set<KeywordNG> kw) {
        keywords = keywords;
    }
    public Set<KeywordNG> getKeywords() {
        return keywords;
    }

    public void addKeyword(KeywordNG kw) {
        if (keywords == null) keywords = new HashSet<KeywordNG>();
        keywords.add(kw);
        this.setVersion();
    }
    public KeywordNG addKeyword(Shepherd myShepherd, String val, String lang) {
        if (lang == null) lang = "";
        KeywordNG kw = KeywordNG.obtain(myShepherd, val, lang);
        this.addKeyword(kw);
        return kw;
    }
    public KeywordNG addKeyword(Shepherd myShepherd, String val) {
        return this.addKeyword(myShepherd, val, null);
    }


    public void removeKeyword(KeywordNG kw) {
        if (keywords == null) return;
        keywords.remove(kw);
        this.setVersion();
    }
    public void removeKeyword(String val, String lang) {
    }
    //note: this will not _just_ remove default-lang, but *all* values matching
    //  if you want to explicitly remove default-lang value, you have to use above with lang=""
    public void removeKeyword(String val) {
    }


    public JSONObject toJSONObject() {
        return this.toJSONObject(false);
    }
    public JSONObject toJSONObject(boolean full) {
        JSONObject j = super.toJSONObject();
        if (!Util.collectionIsEmptyOrNull(this.keywords)) {
            JSONArray jarr = new JSONArray();
            for (KeywordNG kw : this.keywords) {
                jarr.put(kw.toJSONObject(full));
            }
            j.put("keywords", jarr);
        }
        return j;
    }


}

