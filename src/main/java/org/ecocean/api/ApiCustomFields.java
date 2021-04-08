package org.ecocean.api;

import org.ecocean.Util;
import org.ecocean.User;
import org.ecocean.Organization;
import org.ecocean.Shepherd;
import org.ecocean.Taxonomy;
import org.ecocean.customfield.*;
import org.ecocean.SystemLog;

import java.util.Set;
import java.util.Iterator;
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
import java.io.IOException;

public class ApiCustomFields {
    public static final String DETAIL_LEVEL_MIN = "min";
    public static final String DETAIL_LEVEL_MAX = "max";

    private String id = null;
    //private long version = 0l;
    //private User owner = null;
    //private OrganizationSet organizationSet = null;
    private List<CustomFieldValue> customFieldValues = null;

    public String getId() {
        return id;
    }
    public void setId(String id) {
        this.id = id;
    }

    public void setVersion() {
        SystemLog.warn("setVersion() called on " + this + "; should be overridden");
    }
/*
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
*/

    //note: you probably really want getCustomFieldValues(foo) below
    public List<CustomFieldValue> getCustomFieldValues() {
        return customFieldValues;
    }
    public void setCustomFieldValues(List<CustomFieldValue> vals) {
        customFieldValues = vals;
    }
// note: this will **replace** a value in the list of the same CustomFieldDefinition, if it is non-multiple
    public void addCustomFieldValue(CustomFieldValue val) {
        if (customFieldValues == null) customFieldValues = new ArrayList<CustomFieldValue>();
        if ((customFieldValues.size() < 1) || val.getDefinition().getMultiple()) {  //empty or val is multiple, we just safely add
            customFieldValues.add(val);
            return;
        }
        int found = -1;
        for (int i = 0 ; i < customFieldValues.size() ; i++) {
            if (customFieldValues.get(i).getDefinition().equals(val.getDefinition())) {
                found = i;
                break;
            }
        }
        if (found < 0) {
            customFieldValues.add(val);
        } else {
            customFieldValues.set(found, val);
        }
    }
    public void resetCustomFieldValues() {
        customFieldValues = null;
    }
    public void resetCustomFieldValues(String cfdId) {
        if (cfdId == null) return;
        if (Util.collectionSize(customFieldValues) < 1) return;
        Iterator<CustomFieldValue> it = customFieldValues.iterator();
        while (it.hasNext()) {
            if (it.next().getDefinition().getId().equals(cfdId)) it.remove();
        }
        if (customFieldValues.size() < 1) customFieldValues = null;
    }
    public void resetCustomFieldValues(CustomFieldDefinition cfd) {
        if (cfd == null) return;
        this.resetCustomFieldValues(cfd.getId());
    }
/*
    TODO if needed?
    replaceCustomFieldValues(List) could check if single then do add [auto-replace], otherwise do a resetCustomFieldValues() then add()
    replaceCustomFieldValue(obj) could be singleton flavor
*/
    public List<Object> getCustomFieldValues(String cfdId) {
        List<Object> rtn = new ArrayList<Object>();
        if (cfdId == null) return rtn;
        if (Util.collectionIsEmptyOrNull(customFieldValues)) return rtn;
        for (CustomFieldValue cfv : customFieldValues) {
            if ((cfv.getDefinition() != null) && cfdId.equals(cfv.getDefinition().getId())) rtn.add(cfv.getValue());
        }
        return rtn;
    }
    public List<Object> getCustomFieldValues(CustomFieldDefinition cfd) {
        return getCustomFieldValues((cfd == null) ? (String)null : cfd.getId());
    }
    //this sorts them by definitions
    public Map<CustomFieldDefinition,List<Object>> getCustomFieldValuesMap() {
        Map<CustomFieldDefinition,List<Object>> map = new HashMap<CustomFieldDefinition,List<Object>>();
        if (Util.collectionIsEmptyOrNull(customFieldValues)) return map;
        for (CustomFieldValue cfv : customFieldValues) {
            if (cfv.getDefinition() == null) continue;  //snh
            if (map.get(cfv.getDefinition()) == null) map.put(cfv.getDefinition(), new ArrayList<Object>());
            map.get(cfv.getDefinition()).add(cfv.getValue());
        }
        return map;
    }

/*
    //ignore these ones
    private static final List<String> skipGetters = Arrays.asList(new String[]{
            "getClass", "getGetters", "getSetters", "getProperties", "getApiValueForJSONObject",
            "getCustomFieldValues", "getCustomFieldValuesMap"
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
*/

    /*
        kinda winging it... maybe value is optional?
        definitely worth considering overriding?
    */
/*
    public boolean hasAccess(User user, String property, int access, Object value) {
        if (user == null) {
            System.out.println("WARNING: .hasAccess() on " + this + " has null user; allowing via ApiCustomFields; please override if needed");
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
*/

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

/*
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
*/

        rtn.put("customFields", this.getCustomFieldJSONObject());

        if (debug != null) {
            debug.put("class", this.getClass().getName());
            //debug.put("noAccess", noAccess);
            debug.put("opts", new JSONObject(opts));
            debug.put("traversalDepth", td);
            rtn.put("_debug", debug);
        }
        return rtn;
    }


    //this is strictly mean to be name/value
    public JSONObject getCustomFieldJSONObject() {
        Map<CustomFieldDefinition,List<Object>> cmap = this.getCustomFieldValuesMap();
        JSONObject cust = new JSONObject();
        for (CustomFieldDefinition cfd : cmap.keySet()) {
            if (Util.collectionIsEmptyOrNull(cmap.get(cfd))) {
                ///
            } else if (cfd.getMultiple()) {
                cust.put(cfd.getId(), new JSONArray(cmap.get(cfd)));
            } else {  //single value
                cust.put(cfd.getId(), cmap.get(cfd).get(0));
            }
        }
        return cust;
    }

    //this will iterate over multiple custom fields
    public int trySetting(Shepherd myShepherd, JSONObject all) throws IOException {
        int ct = 0;
        if (all == null) return ct;
        Iterator<String> it = all.keys();
        while (it.hasNext()) {
            String id = it.next();
            trySetting(myShepherd, id, all.get(id));
            ct++;
        }
        return ct;
    }
    //this just tries one
    public void trySetting(Shepherd myShepherd, String id, Object value) throws IOException {
        CustomFieldDefinition cfd = CustomFieldDefinition.load(myShepherd, id);
        if (cfd == null) throw new IOException("trySetting() cannot load id=" + id);
        if (!this.getClass().getName().equals(cfd.getClassName())) throw new IOException("definition id=" + id + " not valid for this class");
//////////// FIXME how do we decide *flavor* of Value to set here?
        CustomFieldValue cfv = CustomFieldValue.makeSpecific(cfd, value);
        addCustomFieldValue(cfv);
    }

    //convenience method
    public String getDetailLevel(JSONObject arg) {
        if (arg == null) return DETAIL_LEVEL_MIN;
        return arg.optString(this.getClass().getName(), DETAIL_LEVEL_MIN);
    }

/*
    private static final List<Class> invokeAsIs = Arrays.asList(new Class[]{
            String.class, Integer.class, Integer.TYPE, Long.class, Long.TYPE
        });

    //ideally this would be a primitive, JSONObject, or JSONArray, but..... ymmv?
    //  TODO not sure how to really deal with traversalDepth ... !!!
    public Object getApiValueForJSONObject(Method mth, final Map<String,Object> opts) {
        Class rtnCls = mth.getReturnType();
        Object obj = null;
        try {
            obj = mth.invoke(this);
        } catch (Exception ex) {
            System.out.println("ERROR: ApiCustomFields.getApiValueForJSONObject() failed to call " + mth + " on " + this + " --> " + ex.toString());
            return null;
        }
System.out.println("=============== " + mth + " -> returnType = " + rtnCls + " yielded: " + obj);
        if (invokeAsIs.contains(rtnCls)) {
            return obj;
        } else if (Collection.class.isAssignableFrom(rtnCls)) {
            Collection coll = (Collection)obj;
            JSONArray arr = new JSONArray();
            if (coll == null) return arr;
            for (Object cobj : coll) {
                arr.put(attemptGetValue(cobj, opts));
            }
            return arr;
        }
        return attemptGetValue(obj, opts);  //try our luck
    }

    private static JSONObject attemptGetValue(Object obj, Map<String,Object> opts) {
        if (obj == null) return null;
        Class cls = obj.getClass();
        if (ApiCustomFields.class.isAssignableFrom(cls)) return ((ApiCustomFields)obj).toApiJSONObject(opts);
        for (Method mth : cls.getMethods()) {  //see if our object has .toApiJSONObject()
            if (mth.getName().equals("toApiJSONObject") && (mth.getParameterCount() == 1)) {  //kinda cheat on checking param *type*
                try {
                    return (JSONObject)mth.invoke(obj, opts);
                } catch (Exception ex) {
                    System.out.println("ERROR: ApiCustomFields.attemptGetValue() failed to call toApiJSONObject() on " + obj + " --> " + ex.toString());
                    return null;
                }
            }
        }
        //fell thru here, well... you get what you get and you dont throw a fit
        JSONObject jobj = new JSONObject();
        jobj.put("_class", obj.getClass().toString());
        jobj.put("_toString", obj.toString());
        jobj.put("value", obj);  //godspeed
        return jobj;
    }
*/

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


    // input should be { "id": "taxonomy-uuid-here" } .... may later support other options?
    //  throws IllegalArgumentException if badness
    public static Taxonomy resolveTaxonomyJSONObject(Shepherd myShepherd, JSONObject tj, Set<Taxonomy> validTxs) {
        if (tj == null) throw new IllegalArgumentException("passed null JSONObject");
        Taxonomy tx = myShepherd.getTaxonomyById(tj.optString("id", "__FAIL__"));
        if (tx == null) throw new IllegalArgumentException("invalid taxonomy at " + tj);
        if ((validTxs != null) && !validTxs.contains(tx)) throw new IllegalArgumentException("non-site taxonomy " + tx);
        return tx;
    }
    public static Taxonomy resolveTaxonomyJSONObject(Shepherd myShepherd, JSONObject tj) {
        return resolveTaxonomyJSONObject(myShepherd, tj, Taxonomy.siteTaxonomies(myShepherd));
    }

/*
    this is for fromApiJSONObject() calls on sub-classes.   its just to get around the boring setFoo redundancy now.  optimize later!
    NOTE: this is ugly and uses reflection.   optimize/change later???
*/
    public boolean setFromJSONObject(String key, Class cls, org.json.JSONObject json, boolean required) throws IOException {
        SystemLog.debug("trying key=" + key + " with json=" + json);
        if (key == null) return false;
        if (required && ((json == null) || !json.has(key) || json.isNull(key))) throw new ApiValueException("value is required for " + key, key);
        if ((json == null) || !json.has(key)) return false;
        String setterName = "set" + key.substring(0,1).toUpperCase() + key.substring(1);
        try {
            Object val = null;
            if (!json.isNull(key)) val = json.get(key);
            Method setter = this.getClass().getMethod(setterName, cls);
            setter.invoke(this, cls.cast(val));
        } catch (java.lang.reflect.InvocationTargetException ex) {
            if (ex.getCause() instanceof ApiValueException) {
                ApiValueException newEx = (ApiValueException)ex.getCause();
                throw newEx;
            } else {
                throw new IOException("setter woes: " + ex.getCause().toString());
            }
        } catch (java.lang.NoSuchMethodException | java.lang.IllegalAccessException ex) {
            throw new IOException("setter woes: " + ex.toString());
        }
        this.setVersion();
        return true;
    }
    public boolean setFromJSONObject(String key, Class cls, org.json.JSONObject json) throws IOException {
        return setFromJSONObject(key, cls, json, false);
    }


    public JSONArray apiPatch(Shepherd myShepherd, org.json.JSONObject jsonIn) throws IOException {
        if (jsonIn == null) throw new IOException("apiPatch has null json");
        JSONArray opArr = jsonIn.optJSONArray("_value");
        if (opArr == null) throw new IOException("apiPatch requires an ARRAY of op-objects");
        JSONArray rtn = new JSONArray();
        for (int i = 0 ; i < opArr.length() ; i++) {
            JSONObject jsonOp = opArr.optJSONObject(i);
            if (jsonOp == null) throw new IOException("apiPatch got non-object at offset=" + i);
            String op = jsonOp.optString("op", null);
            if (op == null) throw new IOException("apiPatch got null op at offset=" + i);
            try {
                switch (op) {
                    case "add":
                        rtn.put(this.apiPatchAdd(myShepherd, jsonOp));
                        break;
                    case "replace":
                        rtn.put(this.apiPatchReplace(myShepherd, jsonOp));
                        break;
                    case "remove":
                        rtn.put(this.apiPatchRemove(myShepherd, jsonOp));
                        break;
                    case "move":
                        rtn.put(this.apiPatchMove(myShepherd, jsonOp));
                        break;
                    case "copy":
                        rtn.put(this.apiPatchCopy(myShepherd, jsonOp));
                        break;
                    case "test":
                        rtn.put(this.apiPatchTest(myShepherd, jsonOp));
                        break;
                    default:
                        throw new IOException("apiPatch op=" + op + " not supported (yet)");
                }
            } catch (ApiValueException valex) {
                throw valex;
            } catch (Exception ex) {
                throw ex;
            }
        }
        this.setVersion();
        return rtn;
    }

    //these should all be overridden... perhaps?
    public JSONObject apiPatchAdd(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchAdd");
    }
    //NOTE:  both add and replace will act like setter if it is not an array value (i.e. most things)
    public JSONObject apiPatchReplace(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchReplace");
    }
    public JSONObject apiPatchRemove(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchRemove");
    }
    public JSONObject apiPatchMove(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchMove");
    }
    public JSONObject apiPatchCopy(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchCopy");
    }
    public JSONObject apiPatchTest(Shepherd myShepherd, JSONObject jsonIn) throws IOException {
        throw new IOException("must override apiPatchTest");
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

    //public String toString() {  return this.getClass().getName() + ":" + this.id; }

    // this works fine for encounters presently but we override for occurrencesightings
    public void delete(Shepherd myShepherd) throws IOException {
        myShepherd.getPM().deletePersistent(this);
    }
}

