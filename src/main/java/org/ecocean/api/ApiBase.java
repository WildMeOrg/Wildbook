package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
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


    //ignore these ones
    private static final List<String> skipGetters = Arrays.asList(new String[]{"getClass", "getGetters", "getSetters", "getProperties"});
    //this effectively exposes all getters and setters
    // so consider overriding if desired
    public List<Method> getGetters() {
        Class cls = this.getClass();
        List<Method> g = new ArrayList<Method>();
        for (Method m : cls.getMethods()) {
            if (!skipGetters.contains(m.getName()) && m.getName().matches("^get[A-Z].+")) g.add(m);
        }
        return g;
    }
    public List<Method> getSetters() {
        Class cls = this.getClass();
        List<Method> g = new ArrayList<Method>();
        for (Method m : cls.getMethods()) {
            if (!m.getName().equals("getClass") && m.getName().matches("^get[A-Z].+")) g.add(m);
        }
        return g;
    }

    /*
        kinda winging it... maybe value is optional?
        definitely worth considering overriding?
    */
    public boolean hasAccess(User user, String property, int access, Object value) {
        if (user == null) {
            System.out.println("WARNING: .hasAccess() on " + this + " has null user; allowing via ApiBase; please override if needed");
        }
        if (!ApiAccess.validAccessValue(access)) {
            System.out.println("WARNING: .hasAccess() on " + this + " given invalid access=" + access);
            return false;
        }
        if (!this.validProperty(property)) {
            System.out.println("WARNING: .hasAccess() on " + this + " given invalid propert=" + property);
            return false;
        }
        return true;
    }
    public boolean hasAccess(User user, String property, int access) {
        return hasAccess(user, property, access, null);
    }

    //base on getters... i think?  but overridable
    // .getDeclaredFields is another possibility but it seems .. wrong, we would rather look at
    //  exposed getters/setters for this purpos
    public List<String> getProperties() {
        List<String> p = new ArrayList<String>();
        for (Method m : this.getSetters()) {
            p.add(propertyFromGetter(m.getName()));
        }
        return p;
    }
    public static String propertyFromGetter(String getterName) {
        if (getterName == null) return null;
        if (getterName.length() < 4) return "_GETTERNAMETOOSHORT_";
        char[] c = getterName.substring(3).toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }
    public boolean validProperty(String prop) {
        if (prop == null) return false;
        for (String p : this.getProperties()) {
            if (prop.equals(p)) return true;
        }
        return false;
    }

    public JSONObject toApiJSONObject() {
        return toApiJSONObject(null);
    }
    public JSONObject toApiJSONObject(Map<String,Object> opts) {
        if (opts == null) opts = new HashMap<String,Object>();
        JSONObject rtn = new JSONObject();

        if (optsBoolean(opts.get("includeClass"))) {
            Class cls = this.getClass();
            JSONObject jc = new JSONObject();
            jc.put("name", cls.getName());
            rtn.put("_class", jc);
        }

        JSONObject debug = null;
        if (optsBoolean(opts.get("debug"))) debug = new JSONObject();

        JSONArray noAccess = new JSONArray();  //really only for debug
        for (Method mth : this.getGetters()) {
            String prop = propertyFromGetter(mth.getName());
            User user = optsUser(opts.get("user"));
            if (hasAccess(user, prop, ApiAccess.READ)) {
                rtn.put(prop, "SOME VALUE HERE!!!");
            } else if (debug != null) {
                noAccess.put(prop);
            }
        }

        if (debug != null) {
            debug.put("noAccess", noAccess);
            rtn.put("_debug", debug);
        }
        return rtn;
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


    //kinda utility/convenience thing for opts
    private static boolean optsBoolean(Object val) {
        if ((val == null) || !(val instanceof Boolean)) return false;
        if ((Boolean)val) return true;
        return false;  //covers potential of null Boolean
    }
    private static User optsUser(Object val) {
        if ((val == null) || !(val instanceof User)) return null;
        return (User)val;
    }
}

