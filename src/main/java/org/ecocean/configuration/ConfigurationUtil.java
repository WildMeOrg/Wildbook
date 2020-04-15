package org.ecocean.configuration;

import org.ecocean.Util;
import org.ecocean.Shepherd;
import org.ecocean.ContextConfiguration;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.File;
import org.json.JSONObject;
import org.json.JSONArray;

public class ConfigurationUtil {
    public static final String KEY_DELIM = "_";
    public static final String ID_DELIM = ".";
    public static final String META_KEY = "__meta";

    private static Map<String,JSONObject> meta = new HashMap<String,JSONObject>();
    private static Map<String,JSONObject> value = new HashMap<String,JSONObject>();
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

    public static Configuration getConfiguration(Shepherd myShepherd, String id) {
        if (!idHasValidRoot(id)) return new Configuration(id);
        //TODO something with 'value' cache??????
        List<String> path = idPath(id);
        String root = path.remove(0);
        Configuration conf = myShepherd.getConfiguration(root);
        if (conf == null) conf = new Configuration(id, new JSONObject());  //no root in db yet
        if (id.equals(root)) return conf;  //we want the whole thing
        //we must be a sub-branch (or leaf) of the main json
        conf = new Configuration(id, _traverse(conf.getContent(), path));
        return conf;
    }
    public static Object getConfigurationValue(Shepherd myShepherd, String id) {
        Configuration conf = getConfiguration(myShepherd, id);
        if (conf == null) return null;
        return conf.getValue();
    }

    public static Configuration setConfigurationValue(Shepherd myShepherd, String id, Object value) throws ConfigurationException {
        if (!idHasValidRoot(id)) return null;
        List<String> path = idPath(id);
        String root = path.remove(0);
        JSONObject content = null;
        Configuration conf = getConfiguration(myShepherd, root);
        if (conf == null) {
            conf = new Configuration(root, new JSONObject());
            myShepherd.getPM().makePersistent(conf);
        } else {
            content = conf.getContent();
        }
        if (content == null) content = new JSONObject();
        content = setDeepJSONObject(content, path, value);
        conf.setContent(content);
        conf.setModified();
        myShepherd.getPM().makePersistent(conf);
        return conf;
    }

    public static JSONObject setDeepJSONObject(final JSONObject jobj, final List<String> path, final Object value) {
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

}

