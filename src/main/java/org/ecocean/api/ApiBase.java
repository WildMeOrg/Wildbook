package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

import java.util.List;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public abstract class ApiBase implements java.io.Serializable {
    private String id = null;
    private long version = 0l;
    private User creator = null;
    private List<Organization> organizations = null;


    public ApiBase() {
        id = Util.generateUUID();
        version = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }
    public long getVersion() {
        return version;
    }
    public User getCreator() {
        return creator;
    }
    public void setCreator(User u) {
        creator = u;
    }
    public List<Organization> getOrganizations() {
        return organizations;
    }
    public void setOrganizations(List<Organization> orgs) {
        organizations = orgs;
    }

    public abstract String description();


    public JSONObject toApiJSONObject() {
        return toApiJSONObject(null);
    }
    public JSONObject toApiJSONObject(JSONObject opts) {
        return null;
    }

    public JSONObject toApiDefinitionJSONObject() {
        JSONObject defn = new JSONObject();
        JSONObject refl = new JSONObject();
        Class cls = this.getClass();
        refl.put("className", cls.getName());
        JSONArray marr = new JSONArray();
        for (Method m : cls.getMethods()) {
            marr.put(m.getName());
        }
        refl.put("methods", marr);
        defn.put("_reflect", refl);
        return defn;
    }
}

