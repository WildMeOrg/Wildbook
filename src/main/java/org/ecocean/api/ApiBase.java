package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;

import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.Collection;
import org.json.JSONObject;
import org.json.JSONArray;
import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;

public abstract class ApiBase implements java.io.Serializable {
    private String id = null;
    private long version = 0l;
    private User owner = null;
    private OrganizationSet organizationSet = null;


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
    public User getOwner() {
        return owner;
    }
    public void setOwner(User u) {
        owner = u;
    }
    public Set<Organization> getOrganizations() {
        if (organizationSet == null) return null;
        return organizationSet.getSet();
    }
    public void setOrganizations(Set<Organization> orgs) {
        if (organizationSet == null) {
            organizationSet = new OrganizationSet(orgs);
        } else {
            organizationSet.setSet(orgs);
        }
    }
    public void addOrganization(Organization org) {
        if (organizationSet == null) organizationSet = new OrganizationSet();
        organizationSet.addOrganization(org);
    }

    public abstract String description();


    //ignore these ones
    private static final List<String> skipGetters = Arrays.asList(new String[]{
            "getClass", "getGetters", "getSetters", "getProperties", "getApiValueForJSONObject"
        });
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
        int td = incrementTraversalDepth(opts);
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
                rtn.put(prop, getApiValueForJSONObject(mth, opts));
            } else if (debug != null) {
                noAccess.put(prop);
            }
        }

        if (debug != null) {
            debug.put("class", this.getClass().getName());
            debug.put("noAccess", noAccess);
            debug.put("opts", new JSONObject(opts));
            debug.put("traversalDepth", td);
            rtn.put("_debug", debug);
        }
        return rtn;
    }

    private static final List<Class> invokeAsIs = Arrays.asList(new Class[]{
            String.class, Integer.class, Integer.TYPE, Long.class, Long.TYPE
        });

    //ideally this would be a primitive, JSONObject, or JSONArray, but..... ymmv?
    //  TODO not sure how to really deal with traversalDepth ... !!!
    public Object getApiValueForJSONObject(Method mth, final Map<String,Object> opts) {
        Class rtnCls = mth.getReturnType();
System.out.println("=============== " + mth + " -> returnType = " + rtnCls);
        //if (rtnCls.equals(String.class) || rtnCls.equals(Integer.class) || rtnCls.equals(Long.class)) {
        if (invokeAsIs.contains(rtnCls)) {
            try {
                return mth.invoke(this);
            } catch (Exception ex) {
                System.out.println("failed to call " + mth + " on " + this + " --> " + ex.toString());
                return null;
            }
        } else if (ApiBase.class.isAssignableFrom(rtnCls)) {
            try {
                Object obj = mth.invoke(this);
                return ((ApiBase)obj).toApiJSONObject(opts);
            } catch (Exception ex) {
                System.out.println("failed to call " + mth + " on " + this + " --> " + ex.toString());
                return null;
            }
        } else if (Collection.class.isAssignableFrom(rtnCls)) {
            Collection coll = null;
            try {
                coll = (Collection)mth.invoke(this);
            } catch (Exception ex) {
                System.out.println("failed to call " + mth + " on " + this + " --> " + ex.toString());
                return null;
            }
            JSONArray arr = new JSONArray();
            if (coll == null) return arr;
            for (Object obj : coll) {
                if (obj instanceof ApiBase) {
                    arr.put(((ApiBase)obj).toApiJSONObject(opts));
                } else {
                    arr.put(obj);
                }
            }
            return arr;
        }

        System.out.println("WARNING: ApiBase.getApiValueForJSONObject() unknown " + mth + " on " + this);
        JSONObject jobj = new JSONObject();
        jobj.put("_class", rtnCls.toString());
        return jobj;
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
    private static int incrementTraversalDepth(Map<String,Object> opts) {
        Object td = opts.get("traversalDepth");
        int val = 0;
        if ((td != null) || (td instanceof Integer)) {
            Integer i = (Integer)td;
            val = i.intValue() + 1;
        }
        opts.put("traversalDepth", val);
        return val;
    }

    public String toString() {  return this.getClass().getName() + ":" + this.id; }
}

