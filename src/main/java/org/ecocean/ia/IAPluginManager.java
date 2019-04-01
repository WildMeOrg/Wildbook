/*
    handles communication to "all" (applicable) plugins via common single calls
*/

package org.ecocean.ia;

import org.ecocean.Shepherd;
import org.ecocean.ia.plugin.*;
import org.ecocean.ia.Task;
import org.ecocean.Annotation;
import org.ecocean.media.MediaAsset;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import javax.servlet.ServletContextEvent;

/*
import org.ecocean.CommonConfiguration;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.ServletUtilities;
import java.util.Arrays;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Properties;
import org.ecocean.ShepherdProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
*/

public class IAPluginManager {
    private static Map<String,List<IAPlugin>> plugins = new HashMap<String,List<IAPlugin>>();


    //StartupWildbook calls this
    public static void startup(ServletContextEvent sce) {
        System.out.println("INFO: IAPluginManager.startup() called");
//TODO do we only call this in ONE context based on sce?????  FIXME
        for (String context : plugins.keySet()) {
            for (IAPlugin p : plugins.get(context)) {
                p.startup(sce);
            }
        }
    }

    //creates static instances of each (enabled) plugin (and initialized them), for use here
    public static List<IAPlugin> initPlugins(String context) {
        if (plugins.get(context) != null) return plugins.get(context);
        List<IAPlugin> list = new ArrayList<IAPlugin>();
        for (Class c : getAllPluginClasses(context)) {
            IAPlugin p = getIAPluginInstanceFromClass(c, context);
            if (p.isEnabled()) list.add(p);
            p.init(context);
        }
        plugins.put(context, list);
        return list;
    }


    //someday this may be done with reflection etc, but too lazy to do that now
    public static List<Class> getAllPluginClasses(String context) {
        List<Class> all = new ArrayList<Class>();
        all.add(org.ecocean.ia.plugin.WildbookIAM.class);
        all.add(org.ecocean.ia.plugin.TestPlugin.class);
        return all;
    }

    public static IAPlugin getIAPluginInstanceFromClass(Class c, String context) {
        Object p;
        try {
            p = c.getDeclaredConstructor(String.class).newInstance(context);
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("ERROR: IAPluginManager.getIAPluginInstanceFromClass() broke -- " + ex.toString());
        }
        return (IAPlugin)p;
    }


//currently rootTask gets created here.  this could change in the future, to accommodate passing params around etc.

    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        String context = myShepherd.getContext();
        if (plugins.get(context) == null) {
            IA.log("WARNING: IAPluginManager.intakeMediaAssets() had no plugin(s) for context " + context);
            return null;
        }
        Task rootTask = new Task();
        rootTask.setObjectMediaAssets(mas);
        for (IAPlugin p : plugins.get(context)) {
            Task subTask = p.intakeMediaAssets(myShepherd, mas, rootTask);
            if (subTask == null) {
                IA.log("WARNING: IAPluginManager.intakeMediaAssets() got NULL for " + p);
            } else {
                IA.log("INFO: IAPluginManager.intakeMediaAssets() got " + subTask + " for " + p);
                rootTask.addChild(subTask);
            }
        }
        return rootTask;
    }

    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        String context = myShepherd.getContext();
        if (plugins.get(context) == null) {
            IA.log("WARNING: IAPluginManager.intakeAnnotations() had no plugin(s) for context " + context);
            return null;
        }
        Task rootTask = new Task();
        rootTask.setObjectAnnotations(anns);
        for (IAPlugin p : plugins.get(context)) {
            Task subTask = p.intakeAnnotations(myShepherd, anns, rootTask);
            if (subTask == null) {
                IA.log("WARNING: IAPluginManager.intakeAnnotations() got NULL for " + p);
            } else {
                IA.log("INFO: IAPluginManager.intakeAnnotations() got " + subTask + " for " + p);
                rootTask.addChild(subTask);
            }
        }
        return rootTask;
    }

/*
    public static List<Class> getAllEnabledPluginClasses(String context) {
        List<Class> all = getAllPluginClasses(context);
        List<Class> enabled = new ArrayList<Class>();
        for (Class c : all) {
            Object p;
            try {
                p = c.getDeclaredConstructor(String.class).newInstance(context);
            } catch (Exception ex) {
                throw new RuntimeException("ERROR: IAPluginManager.getAllEnabledPlugins() broke -- " + ex.toString());
            }
            IAPlugin plugin = (IAPlugin)p;
            if (plugin.isEnabled()) enabled.add(c);
        }
        return enabled;
    }
*/
}

