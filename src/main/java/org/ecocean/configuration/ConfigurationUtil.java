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

    private static Map<String,JSONObject> config = new HashMap<String,JSONObject>();
    private static Map<String,JSONObject> value = new HashMap<String,JSONObject>();
    private static long lastRead = 0l;

    public static boolean isValidRoot(String root) {
        checkCache();
        return config.containsKey(root);
    }
    public static boolean idHasValidRoot(String id) {
        List<String> p = idPath(id);
        if (Util.collectionSize(p) < 1) return false;
        return isValidRoot(p.get(0));
    }
    public static List<String> idPath(String id) {
        if (id == null) return null;
        return new ArrayList<String>(Arrays.asList(id.split("\\.")));
    }

    public static Configuration getConfiguration(Shepherd myShepherd, String id) {
        if (!idHasValidRoot(id)) return new Configuration(id);
        //TODO something with 'value' cache??????
        Configuration conf = myShepherd.getConfiguration(id);
        if (conf == null) {
            conf = new Configuration(id);
        }
        return conf;
    }

    //this is the Configuration config!  :/
    public static Map<String,JSONObject> getConfig() {
        checkCache();
        return config;
    }
    public static JSONObject getConfigAsJSONObject() {
        checkCache();
        return new JSONObject(config);
    }

    public static void checkCache() {
        if (config.size() < 1) init();
    }

    public static void init() {
        config = new HashMap<String,JSONObject>();
        //value = new HashMap<String,JSONObject>();
        for (File conf : allFiles(dirOverride())) {
            JSONObject j = readJson(conf);
            if (j == null) continue;
            j.put("_fromOverride", true);
            j.put("_timeRead", System.currentTimeMillis());
            config.put(getRootName(conf), j);
        }
        for (File conf : allFiles(dir())) {
            String rn = getRootName(conf);
            if (config.containsKey(rn)) continue;
            JSONObject j = readJson(conf);
            if (j == null) continue;
            j.put("_timeRead", System.currentTimeMillis());
            config.put(rn, j);
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
