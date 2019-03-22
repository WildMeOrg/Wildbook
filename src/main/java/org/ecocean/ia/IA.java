/*
    idea here is "simple": a gateway to all IA calls, mostly (now) cleaned up from identity/IBEISIA.java (guessing)

    THIS IS A WORK-IN-PROGRESS

    proposed key concepts:
    * can handle multiple IA frameworks (not just historic-IBEIS)
      - likely a base abstract class with a "isEnabled() / init()" concept
      - classes would allow for instances of each IA framework?
    
    * no idea how to handle crazy (and configurable!?) workflow!

    * probably should "leverage" Queue stuff where applicable?
      - possibly there is a NEED for both variations (as suggested by drew): an asynchronous (queued) and synchronous (not)

    * simply entry point for: MediaAsset and Annotation???
*/

package org.ecocean.ia;

import org.ecocean.Shepherd;
import org.ecocean.CommonConfiguration;
import org.ecocean.Annotation;
import org.ecocean.Util;
import org.ecocean.Taxonomy;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.ServletUtilities;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Properties;
import org.ecocean.ShepherdProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;

public class IA {
    private static final String PROP_FILE = "IA.properties";

    /*  NOTE: methods for both intaking a single element or a list.  thoughts:
        - these should be treated as different in that an IA framework might batch together the list in some way
          (i.e. difference between sending as list vs iterating over list with intake(each element)
        - you only get one task ID for the list/group, is this a bad idea?
    */

    public static Task intake(Shepherd myShepherd, MediaAsset ma) {
        return intakeMediaAssets(myShepherd, new ArrayList<MediaAsset>(Arrays.asList(ma)));
    }
    //Annotations *may or may not* already be on an Encounter  #neverforget
    public static Task intake(Shepherd myShepherd, Annotation ann) {
        return intakeAnnotations(myShepherd, new ArrayList<Annotation>(Arrays.asList(ann)));
    }

/*  these have same erasure types so cant co-exist. :( another reason for a common baseclass.. sigh?
    hence the overly-inclusive Object version below!
    public static Task intake(Shepherd myShepherd, List<MediaAsset> mas) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Task task = new Task();
        return task;
    }
    public static Task intake(Shepherd myShepherd, List<Annotation> anns) {
        if ((anns == null) || (anns.size() < 1)) return null;
        Task task = new Task();
        return task;
    }
*/

    //i think objects ingested here must(?) be persisted (and committed), as we have to assume (or we know)
    //  that these processes will use queues which operate in different (Shepherd) threads and will thus try
    //  to find the objects via the db.  :/
    //     parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        return intakeMediaAssets(myShepherd, mas, null);
    }
    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas, final Task parentTask) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Task task = new Task();
        if (parentTask != null) task.setParameters(parentTask.getParameters());
        task.setObjectMediaAssets(mas);
        myShepherd.storeNewTask(task);

        //what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONArray maArr = new JSONArray();
        for (MediaAsset ma : mas) {
            maArr.put(ma.getId());
        }
        JSONObject dj = new JSONObject();
        dj.put("mediaAssetIds", maArr);
        String context = myShepherd.getContext();
        JSONObject qjob = new JSONObject();
        qjob.put("detect", dj);
        qjob.put("taskId", task.getId());
        qjob.put("__context", context);
        qjob.put("__baseUrl", getBaseURL(context));
        boolean sent = false;
        try {
            sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " + iox.toString());
        }

System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() + " assets; queued? = " + sent + "; " + task);
        return task;
    }

    //similar behavior to above: basically fake /ia api call, but via queue
    //     parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        return intakeAnnotations(myShepherd, anns, null);
    }
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
        if ((anns == null) || (anns.size() < 1)) return null;

        Task topTask = new Task();
        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
        topTask.setObjectAnnotations(anns);
        String context = myShepherd.getContext();

        /*
            what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
            for this we use IBEISIA.identOpts to decide how many flavors of identification we need to do!   if have more than
            one we need to make a set of subtasks
        */
        List<JSONObject> opts = IBEISIA.identOpts(context);
        if ((opts == null) || (opts.size() < 1)) return null;  //"should never happen"
        List<Task> tasks = new ArrayList<Task>();
        JSONObject newTaskParams = new JSONObject();  //we merge parentTask.parameters in with opts from above
        if (parentTask != null && parentTask.getParameters()!=null) newTaskParams = parentTask.getParameters();
        if (opts.size() == 1) {
            newTaskParams.put("ibeis.identification", ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
            topTask.setParameters(newTaskParams);
            tasks.add(topTask);  //topTask will be used as *the*(only) task -- no children
        } else {
            for (int i = 0 ; i < opts.size() ; i++) {
                Task t = new Task();
                t.setObjectAnnotations(anns);
                newTaskParams.put("ibeis.identification", ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));  //overwrites each time
                t.setParameters(newTaskParams);
                topTask.addChild(t);
                tasks.add(t);
            }
        }
        myShepherd.storeNewTask(topTask);

        //these are re-used in every task
        JSONArray annArr = new JSONArray();
        for (Annotation ann : anns) {
            annArr.put(ann.getId());
        }
        JSONObject aj = new JSONObject();
        aj.put("annotationIds", annArr);
        String baseUrl = getBaseURL(context);

        for (int i = 0 ; i < opts.size() ; i++) {
            JSONObject qjob = new JSONObject();
            qjob.put("identify", aj);
            qjob.put("taskId", tasks.get(i).getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            if (opts.get(i) != null) qjob.put("opt", opts.get(i));
            boolean sent = false;
            try {
                sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
            } catch (java.io.IOException iox) {
                System.out.println("ERROR[" + i + "]: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
            }
System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " + anns.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
        }
System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
        return topTask;
    }


    //possibly (should?) have .taskId, and *definitely* should have .__context and .__baseUrl
    //  note: this is processed *from the queue* and as such does not have "output"
    public static void handleRest(JSONObject jin) {
System.out.println("JIN JIN JIN: " + jin);
        if (jin == null) return;
        String context = jin.optString("__context", null);
        if (context == null) throw new RuntimeException("IA.handleRest(): passed data has no __context");
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IA.handleRest");
        myShepherd.beginDBTransaction();
        String taskId = jin.optString("taskId", Util.generateUUID());
        Task topTask = Task.load(taskId, myShepherd);
        if (topTask == null) topTask = new Task(taskId);
        myShepherd.storeNewTask(topTask);
        JSONObject opt = jin.optJSONObject("opt");  // should use this to decide how to branch differently than "default"

        //for now (TODO) we just send MAs off to detection and annots off to identification

        JSONArray mlist = jin.optJSONArray("mediaAssetIds");
        if ((mlist != null) && (mlist.length() > 0)) {
System.out.println("MLIST: " + mlist);
            List<MediaAsset> mas = new ArrayList<MediaAsset>();
            for (int i = 0 ; i < mlist.length() ; i++) {
                int mid = mlist.optInt(i, -1);
                if (mid < 1) continue;
                MediaAsset ma = MediaAssetFactory.load(mid, myShepherd);
System.out.println(i + " -> " + ma);
                if (ma == null) continue;
                mas.add(ma);
            }
            Task mtask = intakeMediaAssets(myShepherd, mas, topTask);
            System.out.println("INFO: IA.handleRest() just intook MediaAssets as " + mtask + " for " + topTask);
            topTask.addChild(mtask);
        }
        JSONArray alist = jin.optJSONArray("annotationIds");
        if ((alist != null) && (alist.length() > 0)) {
            List<Annotation> anns = new ArrayList<Annotation>();
            for (int i = 0 ; i < alist.length() ; i++) {
                String aid = alist.optString(i, null);
                if (aid == null) continue;
                Annotation ann = ((Annotation) (myShepherd.getPM().getObjectById(myShepherd.getPM().newObjectIdInstance(Annotation.class, aid), true)));
                if (ann == null) continue;
                anns.add(ann);
            }
            Task atask = intakeAnnotations(myShepherd, anns, topTask);
            System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask + " for " + topTask);
            topTask.addChild(atask);
            myShepherd.getPM().refresh(topTask);
        }
    }

    //via IAGateway servlet, we handle the work
    public static void handleGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        //JSONObject rtn = queueCallback(request);
        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        String taskId = request.getParameter("taskId");

        if (taskId != null) {
            Task task = Task.load(taskId, myShepherd);
            if (task == null) {
                response.sendError(404, "Not found: taskId=" + taskId);
                return;
            }
            rtn.put("success", true);
            rtn.remove("error");
            rtn.put("task", task.toJSONObject(Util.requestParameterSet(request.getParameter("includeChildren"))));
        }

        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        out.println(rtn.toString());
        out.close();
        return;
    }

    public static String getBaseURL(String context) {
        String url = CommonConfiguration.getServerURL(context);
        String containerName = CommonConfiguration.getProperty("containerName","context0");
        if (containerName != null) containerName = containerName.trim();
        url = CommonConfiguration.getServerURL(context);
        if (containerName!=null&&!"".equals(containerName)) { 
            System.out.println("Wildbook is containerized: sending container name: "+containerName+" to IA instead of localhost.");
            url = url.replace("localhost", containerName);
        }
        return url;
    }


    //(optional!) Taxonomy will append "_Scientific_name" to label and try that.  if not available, then try just label.
    public static String getProperty(String context, String label, Taxonomy tax, String def) {
        if ((tax != null) && (tax.getScientificName() != null)) {
            String propKey = label + "_".concat(tax.getScientificName()).replaceAll(" ", "_");
            System.out.println("[INFO] IA.getProperty() using propKey=" + propKey + " based on " + tax);
            String val = getProperty(context, propKey, (String)null);
            if (val != null) return val;
        }
        return IA.getProperty(context, label, def);
    }
    public static String getProperty(String context, String label, Taxonomy tax) {  //no-default version
        return getProperty(context, label, tax, null);
    }

    public static String getProperty(String context, String label) {  //no-default, no-taxonomy
        return getProperty(context, label, (String)null);
    }
    public static String getProperty(String context, String label, String def) {
        Properties p = getProperties(context);
        if (p == null) {
            System.out.println("IA.getProperty(" + label + ") has no properties; IA.properties unavailable?");
            return null;
        }
        return p.getProperty(label, def);
    }
    private static Properties getProperties(String context) {
        try {
            return ShepherdProperties.getProperties(PROP_FILE, "", context);
        } catch (Exception ex) {
            return null;
        }
    }

    public static void log(String msg) {
        System.out.println(new org.joda.time.DateTime() + " " + msg);
    }

}
