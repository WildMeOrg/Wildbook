package org.ecocean.api;

import java.util.Map;
import org.json.JSONObject;

public class TestObject2 extends org.ecocean.api.ApiBase {

    private String foo = "this is the value of .foo";

    public String description() {
        return "yes, this is my description";
    }

    public String getFoo() {
        return foo;
    }

/*
    //this is to test overriding this
    public JSONObject toApiJSONObject(Map<String,Object> opts) {
        JSONObject j = new JSONObject();
        j.put("__opts__", opts);
        j.put("fu", System.currentTimeMillis());
        return j;
    }
*/
}


