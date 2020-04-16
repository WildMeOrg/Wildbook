package org.ecocean.configuration;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ContextConfiguration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.io.File;
import org.json.JSONObject;
import org.json.JSONArray;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.time.DateTimeException;
import javax.jdo.Query;

public class ConfigurationUtil {
    public static final String KEY_DELIM = "_";
    public static final String KEY_PREFIX = "configuration";  //for frontend, like configuration_foo_path_blah
    public static final String ID_DELIM = ".";
    public static final String META_KEY = "__meta";

    private static Map<String,JSONObject> meta = new HashMap<String,JSONObject>();
    private static Map<String,JSONObject> valueCache = new HashMap<String,JSONObject>();
    private static long lastRead = 0l;

    public static boolean isValidRoot(String root) {
        if (root == null) return false;
        checkCache();
        return meta.containsKey(root);
    }
    public static boolean idHasValidRoot(String id) {
        return isValidRoot(idGetRoot(id));
    }
    public static String idGetRoot(String id) {
        if (id == null) return null;
        List<String> p = idPath(id);
        if (Util.collectionSize(p) < 1) return null;
        return p.get(0);
    }
    public static List<String> idPath(String id) {
        if (id == null) return null;
        return new ArrayList<String>(Arrays.asList(id.split("\\.")));
    }

    //not really(?) for public consumption
    private static Configuration _loadConfiguration(Shepherd myShepherd, String id) {
        if (!idHasValidRoot(id)) return null;
        String root = idGetRoot(id);
        //TODO other cache checks?  expires? etc
        Configuration conf = null;
        if (valueCache.get(root) != null) {
            System.out.println("INFO: _loadConfiguration(" + root + ") read from cache");
            conf = new Configuration(root, valueCache.get(root));
        } else {
            conf = myShepherd.getConfiguration(root);
            System.out.println("INFO: _loadConfiguration(" + root + ") loaded from db");
            valueCache.put(root, conf.getContent());
        }
        return conf;
    }
    public static Configuration getConfiguration(Shepherd myShepherd, String id) {
        if (!idHasValidRoot(id)) return new Configuration(id);
        //TODO something with 'value' cache??????
        List<String> path = idPath(id);
        String root = path.remove(0);
        Configuration conf = _loadConfiguration(myShepherd, id);  //this is root
        if (conf == null) conf = new Configuration(id, new JSONObject());  //no root in db yet
        if (id.equals(root)) return conf;  //we want the whole thing
        //we must be a sub-branch (or leaf) of the main json
        conf = new Configuration(id, _traverse(conf.getContent(), path));
        return conf;
    }
    public static Object getConfigurationValue(Shepherd myShepherd, String id) throws ConfigurationException {
        return coerceValue(getConfiguration(myShepherd, id));
    }

    public static Configuration setConfigurationValue(Shepherd myShepherd, String id, Object value) throws ConfigurationException {
        if (!idHasValidRoot(id)) throw new ConfigurationException("setConfiguration() passed invalid id=" + id);
        Object cvalue = handleValue(id, value);
        List<String> path = idPath(id);
        String root = path.remove(0);
        JSONObject content = null;
        Configuration conf = getConfiguration(myShepherd, root);
        if (conf == null) {
            conf = new Configuration(root, new JSONObject());
            //myShepherd.getPM().makePersistent(conf);
        } else {
            content = conf.getContent();
        }
        if (content == null) content = new JSONObject();
        content = setDeepJSONObject(content, path, cvalue);
        conf.setContent(content);
        conf.setModified();
        myShepherd.getPM().makePersistent(conf);
        System.out.println("INFO: setConfigurationValue(" + root + ") persisted and inserted into cache");
        valueCache.put(root, content);
        return conf;
    }

    //not really for public consumption!
    private static JSONObject setDeepJSONObject(final JSONObject jobj, final List<String> path, final Object value) {
        if (jobj == null) return null;

        if (path.size() == 1) {  //last one, we set the value
            JSONObject jval = new JSONObject();
            jval.put("value", value);
            String top = path.remove(0);
            jobj.put(top, jval);
            return jobj;

        } else if (path.size() > 1) {  //more to go down...
            String top = path.remove(0);
            JSONObject next = jobj.optJSONObject(top);
            if (next == null) next = new JSONObject();
            next = setDeepJSONObject(next, path, value);
            jobj.put(top, next);
            return jobj;
        } else {
System.out.println("setDeepJSONObject() ELSE??? " + jobj + " -> " + path);
        }
        return null;
    }

    public static Map<String,JSONObject> getMeta() {
        checkCache();
        return meta;
    }
    public static JSONObject getMetaAsJSONObject() {
        checkCache();
        return new JSONObject(meta);
    }
    public static JSONObject getMeta(String id) {
        JSONObject node = getNode(id);
        if (node == null) return null;
        return node.optJSONObject(META_KEY);
    }
    public static JSONObject getNode(String id) {
        if (!idHasValidRoot(id)) return null;
        List<String> path = idPath(id);
        if (Util.collectionIsEmptyOrNull(path)) return null;
        checkCache();
        if (path.size() == 1) return meta.get(path.get(0));  //want the whole tree
        return _traverse(meta.get(path.remove(0)), path);
    }

    public static String getType(JSONObject meta) {
        if (meta == null) return null;
        return meta.optString("type", null);
/*  for now we dont have any .type on formSchema
        if (meta.optString("type", null) != null) return meta.getString("type");
        JSONObject fs = meta.optJSONObject("formSchema");
        if (fs == null) return null;
        return fs.optString("type", null);
*/
    }
    public static String getType(String id) {
        return getType(getMeta(id));
    }

    private static JSONObject _traverse(final JSONObject j, final List<String> path) {
        if (j == null) return null;
        int psize = Util.collectionSize(path);
        if (psize < 1) return j;
        String key = path.remove(0);
        JSONObject next = j.optJSONObject(key);
        if ((path.size() < 1) || (next == null)) return next;  //terminal case!  nowhere to go!
        return _traverse(next, path);
    }

    public static void checkCache() {
        if (meta.size() < 1) init();
    }

    public static void init() {
        meta = new HashMap<String,JSONObject>();
        //value = new HashMap<String,JSONObject>();
        for (File conf : allFiles(dirOverride())) {
            JSONObject j = readJson(conf);
            if (j == null) continue;
            j.put("_fromOverride", true);
            j.put("_timeRead", System.currentTimeMillis());
            meta.put(getRootName(conf), j);
        }
        for (File conf : allFiles(dir())) {
            String rn = getRootName(conf);
            if (meta.containsKey(rn)) continue;
            JSONObject j = readJson(conf);
            if (j == null) continue;
            j.put("_timeRead", System.currentTimeMillis());
            meta.put(rn, j);
        }
        lastRead = System.currentTimeMillis();
    }
    public static String dir() {
        //EFF THIS  FIXME
        return "webapps/wildbook/WEB-INF/classes/bundles/config-json";
    }
    public static String dirOverride() {
        return "webapps/" + ContextConfiguration.getDataDirForContext("context0") + "/WEB-INF/classes/bundles/config-json";
    }
    public static List<File> allFiles(String dir) {
        List<File> all = new ArrayList<File>();
        File d = new File(dir);
        if (!d.exists()) return all;
        for (File f : d.listFiles()) {
            if (f.getName().endsWith(".json")) all.add(f);
        }
        return all;
    }
    public static JSONObject readJson(File f) {
        String c = null;
        try {
            c = Util.readFromFile(f.toString());
        } catch (java.io.IOException ex) {
            System.out.println("ERROR: Configuration.readJson() failed on " + f + ": " + ex.toString());
        }
        return Util.stringToJSONObject(c);
    }
    public static String getRootName(File f) {
        if (f == null) return null;
        String n = f.getName();
        return clean(n.substring(0, n.length() - 5));
    }

    public static String clean(String n) {
        if (n == null) return "";
        return n.replaceAll("\\-", KEY_DELIM).replaceAll("\\.", KEY_DELIM);
    }

    //of the form foo_bar_etc (for front end mostly?)
    public static String idToKey(String id) {
        if (id == null) return null;
        List<String> p = idPath(id);
        p.add(0, KEY_PREFIX);
        return String.join(KEY_DELIM, p);
    }
    //like FOO_BAR_BLAH (for front end i18n)
    public static String idToLang(String id) {
        if (id == null) return null;
        return idToKey(id).toUpperCase();
    }

    //a whole bunch of these based on diff incoming types
    public static boolean checkValidity(Boolean b, JSONObject meta) throws ConfigurationException {
        return true;
    }
    public static boolean checkValidity(Integer i, JSONObject meta) throws ConfigurationException {
        return true;
    }
    public static boolean checkValidity(Double d, JSONObject meta) throws ConfigurationException {
        return true;
    }
    public static boolean checkValidity(Long l, JSONObject meta) throws ConfigurationException {
        return true;
    }
    public static boolean checkValidity(String s, JSONObject meta) throws ConfigurationException {
        return true;
    }
    public static boolean checkValidity(ZonedDateTime dt, JSONObject meta) throws ConfigurationException {
        return true;
    }

    //this does a whole bunch of magic to get a json-settable value from any kind of nonsense that comes in
    //  it also does validity checks!
    public static Object handleValue(String id, Object inVal) throws ConfigurationException {
        JSONObject meta = getMeta(id);
        String type = ConfigurationUtil.getType(meta);
        if ((meta == null) || (type == null)) {
            System.out.println("WARNING: ConfigurationUtil.handleValue() missing meta/type for id=" + id);
            return inVal;
        }
        switch (type) {
            case "string":
                String s = null;
                if (inVal instanceof String) {
                    s = (String)inVal;
                }
                checkValidity(s, meta);
            case "boolean":
                Boolean b = null;
                if (inVal instanceof String) {
                    b = !((String)inVal).toLowerCase().startsWith("f");
                } else if (inVal instanceof Integer) {
                    b = ((Integer)inVal != 0);
                } else if (inVal instanceof Boolean) {
                    b = (Boolean)inVal;
                }
                checkValidity(b, meta);
                return b;
            case "integer":
                Integer i = null;
                if (inVal instanceof Integer) {
                    i = (Integer)inVal;
                } else if (inVal instanceof String) {
                    try {
                        i = Integer.parseInt((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new ConfigurationException("could not parse Integer from " + (String)inVal);
                    }
                }
                checkValidity(i, meta);
                return i;
            case "long":
                Long l = null;
                if (inVal instanceof Long) {
                    l = (Long)inVal;
                } else if (inVal instanceof String) {
                    try {
                        l = Long.parseLong((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new ConfigurationException("could not parse Long from " + (String)inVal);
                    }
                }
                checkValidity(l, meta);
                return l;
            case "double":
                Double d = null;
                if (inVal instanceof Double) {
                    d = (Double)inVal;
                } else if (inVal instanceof String) {
                    try {
                        d = Double.parseDouble((String)inVal);
                    } catch (NumberFormatException ex) {
                        throw new ConfigurationException("could not parse Double from " + (String)inVal);
                    }
                }
                checkValidity(d, meta);
                return d;
            case "date":
                ZonedDateTime dt = null;
                if (inVal instanceof ZonedDateTime) {
                    dt = (ZonedDateTime)inVal;
                } else if (inVal instanceof Long) {
                    try {
                        dt = ZonedDateTime.ofInstant(Instant.ofEpochSecond((Long)inVal), ZoneOffset.UTC);
                    } catch (DateTimeException ex) {
                        throw new ConfigurationException("could not parse ZonedDateTime from " + (Long)inVal + " - " + ex.toString());
                    }
                } else if (inVal instanceof String) {
                    try {
                        dt = ZonedDateTime.parse((String)inVal);
                    } catch (DateTimeException ex) {
                        throw new ConfigurationException("could not parse ZonedDateTime from " + (String)inVal + " - " + ex.toString());
                    }
                }
                checkValidity(dt, meta);
                return dt;
        }
        return inVal;
    }

    // this is the "inverse" of handleValue() above, in that it tries to get the right kind of object out of this
    public static Object coerceValue(Configuration conf) throws ConfigurationException {
        if ((conf == null) || !conf.isValid()) throw new ConfigurationException("coerceValue() given invalid conf=" + conf);
        JSONObject meta = conf.getMeta();
        String type = ConfigurationUtil.getType(meta);
        JSONObject cont = conf.getContent();
        String key = "value";  //this is only valid one for now: content.value = (?)
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


    //this requires that only the 0th element is returned, and builds a list of that... casting should be done elsewhere
    public static List<Object> sqlLookup(Shepherd myShepherd, String sql) {
        List<Object> list = new ArrayList<Object>();
        Query q = myShepherd.getPM().newQuery("javax.jdo.query.SQL", sql);
        List results = (List)q.execute();
        Iterator it = results.iterator();
        while (it.hasNext()) {
            Object row = (Object)it.next();  //is a single column
            if (row != null) list.add(row);
        }
        return list;
    }

}

