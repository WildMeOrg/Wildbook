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
import org.ecocean.IAJsonProperties;
import org.ecocean.media.AssetStore;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetFactory;
import org.ecocean.identity.IBEISIA;
import org.ecocean.servlet.ServletUtilities;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Iterator;
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

    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas, Task parentTask) {
        List<List<MediaAsset>> assetsBySpecies = binAssetsBySpecies(mas, myShepherd);
        int numSpecies = assetsBySpecies.size();
        // in one-species case we don't need to create an extra layer of tasks
        if (numSpecies == 1) return intakeMediaAssetsOneSpecies(myShepherd, assetsBySpecies.get(0), parentTask);
        // in multi-species case we make sure we have a parent task and add each species task as a child
        if (parentTask == null) parentTask = new Task();
        for (List<MediaAsset> masOneSpecies: assetsBySpecies) {
            Task thisTask = intakeMediaAssetsOneSpecies(myShepherd, masOneSpecies, parentTask);
            parentTask.addChild(thisTask);
        }
        return parentTask;
    }

    public static List<List<MediaAsset>> binAssetsBySpecies(List<MediaAsset> mas, Shepherd myShepherd) {
        Map<Taxonomy, List<MediaAsset>> assetsBySpecies = new HashMap<Taxonomy, List<MediaAsset>>();
        for (MediaAsset ma: mas) {
            Taxonomy taxy = ma.getTaxonomy(myShepherd);
            if (!assetsBySpecies.containsKey(taxy)) assetsBySpecies.put(taxy, new ArrayList<MediaAsset>());
            assetsBySpecies.get(taxy).add(ma);
        }
        return new ArrayList<List<MediaAsset>>(assetsBySpecies.values());
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas, final Task parentTask) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Taxonomy taxy = mas.get(0).getTaxonomy(myShepherd);
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas, Taxonomy taxy, final Task parentTask) {
        return intakeMediaAssetsOneSpecies(myShepherd, mas, taxy, parentTask, -1);
    }

    public static Task intakeMediaAssetsOneSpecies(Shepherd myShepherd, List<MediaAsset> mas, Taxonomy taxy, final Task parentTask, int tweetAssetId) {
        System.out.println("intakeMediaAssetsOneSpecies called for "+mas.size()+" media assets:");
        handleMissingAcmids(mas, myShepherd);
        for (MediaAsset ma: mas) {
            System.out.println("intakeMediaAssetsOneSpecies incl. ma "+ma.getId());
            System.out.println("acmid is: " + ma.getAcmId());
        }

        JSONArray maArr = new JSONArray();
        for (MediaAsset ma : mas) {
            maArr.put(ma.getId());
        }
        System.out.println("intakeMediaAssetsOneSpecies constructed maArr "+maArr.toString());

        Task topTask = new Task();
        if (parentTask != null) topTask.setParameters(parentTask.getParameters());
        topTask.setObjectMediaAssets(mas);
        myShepherd.storeNewTask(topTask);

        //what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONObject dj = new JSONObject();
        dj.put("mediaAssetIds", maArr);
        String context = myShepherd.getContext();
        String baseUrl = getBaseURL(context);

        // Ia configs are keyed off taxonomies
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        // mimicking intakeAnnotations, we assume the first mediaAsset is representative of all of them wrt Taxonomies, configs etc.
        int numDetectAlgos = iaConfig.numDetectionAlgos(taxy);
        Boolean[] sent = new Boolean[numDetectAlgos];
        for (int i=0; i<numDetectAlgos; i++) {
            // task for this job (only create new (child) tasks if multiple detect algos)
            Task task = (numDetectAlgos==1) ? topTask : new Task();
            task.setObjectMediaAssets(mas);
            task.setParameters(topTask.getParameters());

            JSONObject detectArgs = iaConfig.getDetectionArgs(taxy, baseUrl, i);
            task.addParameter("detectArgs", detectArgs);

            String detectionUrl = iaConfig.getDetectionUrl(taxy, i);
            task.addParameter("__detect_url", detectionUrl);

            JSONObject qjob = new JSONObject();
            qjob.put("detect", dj);
            qjob.put("__detect_args", detectArgs);
            qjob.put("__detect_url", detectionUrl);
            // task is queued here
            qjob.put("taskId", topTask.getId());
            qjob.put("__context", context);
            qjob.put("__baseUrl", baseUrl);
            System.out.println("intakeMediaAssetsOneSpecies about to add additionalArgs to query");
            if (tweetAssetId!=-1) {
                qjob.put("tweetAssetId", tweetAssetId);
            }
            System.out.println("intakeMediaAssetsOneSpecies successfully added additionalArgs to query");
            sent[i] = false;
            try {
                // job is queued here
                sent[i] = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
            } catch (java.io.IOException iox) {
                System.out.println("ERROR: IA.intakeMediaAssets() hit exception on taxonomy "+taxy.toString()+", detectArgs = "+detectArgs.toString());
                System.out.println("ERROR: IA.intakeMediaAssets() addToQueue() threw " + iox.toString());
            }
        }

        System.out.println("INFO: IA.intakeMediaAssets() accepted " + mas.size() + " assets; queued? = " + sent + "; " + topTask);
        return topTask;
    }

    public static void handleMissingAcmids(List<MediaAsset>mediaAssets, Shepherd myShepherd){
        int count = 0;
        int stopAfter = 200000;
        int batchThreshold = 50;
        int batchesSoFar = 0;
        ArrayList<MediaAsset> assetsWithMissingAcmids = new ArrayList<MediaAsset>();
        try{
            for (MediaAsset ma: mediaAssets) {
                count ++;
                if(count > stopAfter){
                  break;
                }
                if (ma != null && !ma.hasAcmId()) {
                    assetsWithMissingAcmids.add(ma);
                }
                if ((assetsWithMissingAcmids.size()>=batchThreshold)|| count == mediaAssets.size()){
                    if(assetsWithMissingAcmids.size() > 0){ // if count gets to the end and assetsWithMissingAcmids is still empty, no need to do any of this
                        try{
                            IBEISIA.sendMediaAssetsNew(assetsWithMissingAcmids, myShepherd.getContext());
                        }catch(Exception e){
                            System.out.println("Error sending media asset to IA in handleMissingAcmids method in IA.java");
                            e.printStackTrace();
                        }
                        try {
                            Thread.sleep(30000);
                        } catch (java.lang.InterruptedException ex) {
                            System.out.println("You’re not the only one who didn’t sleep well. Neither did the thread in handleMissingAcmids in IA.java");
                            ex.printStackTrace();
                        }
                    }
                    batchesSoFar++;
                    assetsWithMissingAcmids = new ArrayList<MediaAsset>();
                    myShepherd.updateDBTransaction();
                }
            }
        } catch(Exception e){
            System.out.println("Error in handleMissingAcmids in IA.java");
            e.printStackTrace();
            myShepherd.rollbackDBTransaction();
        }
    }


    //similar behavior to above: basically fake /ia api call, but via queue
    //     parentTask is optional, but *will NOT* set task as child automatically. is used only for inheriting params
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        return intakeAnnotations(myShepherd, anns, null);
    }
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
    //     List<List<Annotation>> annses = binAnnotsByIaClass(anns);
    //     // slightly complicated bc we need to create child tasks only if there are multiple iaClasses
    //     if (annses.size() == 1) return intakeAnnotationsOneIAClass(myShepherd, annses.get(0), parentTask);

    //     // here we make child tasks
    //     Task topTask = (parentTask==null) ? new Task() : parentTask;
    //     for (List<Annotation> annsOneIaClass: annses) {
    //         topTask.addChild(intakeAnnotationsOneIAClass(myShepherd, anns, parentTask));
    //     }
    //     return topTask;
    // }
    // public static Task intakeAnnotationsOneIAClass(Shepherd myShepherd, List<Annotation> anns, final Task parentTask) {
        //System.out.println("Starting intakeAnnotations");
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

/*
        String iaClass = anns.get(0).getIAClass(); //IAClass is a standard with image analysis that identifies the featuretype used for identification
        List<JSONObject> opts = null;
        // below gets it working for dolphins but can be generalized easily from IA.properties
        String inferredIaClass = IBEISIA.inferIaClass(anns.get(0), myShepherd);
        String bottlenose = "dolphin_bottlenose_fin";
        if (bottlenose.equals(iaClass) || bottlenose.equals(inferredIaClass)) {
            System.out.println("IA.java is sending a Tursiops truncatus job");
            opts = IBEISIA.identOpts(context, bottlenose);
        } else { // defaults to the default ia.properties IBEISIdentOpt, in our case humpback flukes
            opts = IBEISIA.identOpts(context);
        }
*/
        //List<JSONObject> opts = IBEISIA.identOpts(myShepherd, anns.get(0));
        IAJsonProperties iaConfig = IAJsonProperties.iaConfig();
        List<List<Annotation>> annotsByIaClass = binAnnotsByIaClass(anns);

        for (List<Annotation> annsOneIAClass: annotsByIaClass) {

            List<JSONObject> opts = iaConfig.identOpts(myShepherd, annsOneIAClass.get(0));
            //now we remove ones with default=false (they may get added in below via matchingAlgorithms param (via newOpts)
            if (opts != null) {
                Iterator<JSONObject> itr = opts.iterator();
                while (itr.hasNext()) {
                    if (!itr.next().optBoolean("default", true)) itr.remove();
                }
            }

            System.out.println("identOpts: "+opts);
            List<Task> tasks = new ArrayList<Task>();
            JSONObject newTaskParams = new JSONObject();  //we merge parentTask.parameters in with opts from above
            if (parentTask != null && parentTask.getParameters()!=null) {
              newTaskParams = parentTask.getParameters();
              System.out.println("newTaskParams: "+newTaskParams.toString());
              if(newTaskParams.optJSONArray("matchingAlgorithms")!=null) {
                JSONArray matchingAlgorithms=newTaskParams.optJSONArray("matchingAlgorithms");
                System.out.println("matchingAlgorithms1: "+matchingAlgorithms.toString());
                ArrayList<JSONObject> newOpts=new ArrayList<JSONObject>();
                int maLength=matchingAlgorithms.length();
                for(int y=0;y<maLength;y++) {
                  newOpts.add(matchingAlgorithms.getJSONObject(y));
                }
                System.out.println("matchingAlgorithms2: "+newOpts.toString());
                if(newOpts.size()>0) {
                  opts=newOpts;
                  System.out.println("Swapping opts for newOpts!!");
                }


              }
            }
            if ((opts == null) || (opts.size() < 1)) continue;  // no ID for this iaClass.

            // just one IA class, one algorithm case
            if (opts.size() == 1 && annotsByIaClass.size() == 1) {
                newTaskParams.put("ibeis.identification", ((opts.get(0) == null) ? "DEFAULT" : opts.get(0)));
                topTask.setParameters(newTaskParams);
                tasks.add(topTask);  //topTask will be used as *the*(only) task -- no children
            } else {
                for (int i = 0 ; i < opts.size() ; i++) {
                    Task t = new Task();
                    t.setObjectAnnotations(annsOneIAClass);
                    newTaskParams.put("ibeis.identification", ((opts.get(i) == null) ? "DEFAULT" : opts.get(i)));  //overwrites each time
                    t.setParameters(newTaskParams);
                    topTask.addChild(t);
                    tasks.add(t);
                }
            }
            myShepherd.storeNewTask(topTask);

            //these are re-used in every task
            JSONArray annArr = new JSONArray();
            for (Annotation ann : annsOneIAClass) {
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
    System.out.println("INFO: IA.intakeAnnotations() [opt " + i + "] accepted " + annsOneIAClass.size() + " annots; queued? = " + sent + "; " + tasks.get(i));
            }
        } // end for each iaClass
System.out.println("INFO: IA.intakeAnnotations() finished as " + topTask);
        return topTask;
    }

    public static List<List<Annotation>> binAnnotsByIaClass(List<Annotation> anns) {
        System.out.println("binAnnotsByIaClass called on "+anns.size()+" annots");
        Map<String,List<Annotation>> iaClassToAnns = new HashMap<String, List<Annotation>>();
        for (Annotation ann: anns) {
            String iaClass = ann.getIAClass();
            List<Annotation> iaClassList = iaClassToAnns.getOrDefault(iaClass, new ArrayList<Annotation>());
            iaClassList.add(ann);
            iaClassToAnns.put(iaClass, iaClassList);
        }
        System.out.println("binAnnotsByIaClass binned them into "+iaClassToAnns.keySet().size()+" bins: "+iaClassToAnns.keySet());
        return new ArrayList<List<Annotation>>(iaClassToAnns.values());
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
        try {
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

              // okay, if we are sending another ID job from the hburger menu, the media asset needs to be added to your top level 'root' task,
              // or else you will link to the original root task
              List<MediaAsset> masForNewRoot = new ArrayList<>();
              for (Annotation ann : anns) {
                  MediaAsset ma = ann.getMediaAsset();
                  if (ma!=null && !masForNewRoot.contains(ma)) {
                      masForNewRoot.add(ma);
                  }
              }
              // i cant think of a scenario where we would get here and accidently double-add mas... but jic
              for (MediaAsset ma : masForNewRoot) {
                  if (!topTask.getObjectMediaAssets().contains(ma)) {
                      topTask.addObject(ma);
                  }
              }

              Task atask = intakeAnnotations(myShepherd, anns, topTask);
              System.out.println("INFO: IA.handleRest() just intook Annotations as " + atask + " for " + topTask);
              topTask.addChild(atask);
              myShepherd.getPM().refresh(topTask);
          }
          myShepherd.commitDBTransaction();
        }
        catch(Exception e) {
          e.printStackTrace();
          myShepherd.rollbackDBTransaction();
        }
        finally {
          myShepherd.closeDBTransaction();
        }
    }

    //via IAGateway servlet, we handle the work
    public static void handleGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        //JSONObject rtn = queueCallback(request);
        JSONObject rtn = new JSONObject("{\"success\": false, \"error\": \"unknown\"}");
        String context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("IA.handleGet");
        myShepherd.beginDBTransaction();
        String taskId = request.getParameter("taskId");

        if (taskId != null) {
            Task task = Task.load(taskId, myShepherd);
            if (task == null) {
                response.sendError(404, "Not found: taskId=" + taskId);
                myShepherd.rollbackDBTransaction();
                myShepherd.closeDBTransaction();
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
        myShepherd.rollbackDBTransaction();
        myShepherd.closeDBTransaction();
        return;
    }

    public static String getBaseURL(String context) {
        String url = CommonConfiguration.getServerURL(context);
        String containerName = CommonConfiguration.getProperty("containerName",context);
        if (containerName!=null&&!"".equals(containerName)) {
            containerName = containerName.trim();
            System.out.println("INFO: Wildbook is containerized: Server getBaseURL is returning: "+containerName+"");
            url = url.replace("localhost", containerName);
        }
        System.out.println("INFO: Server getBaseURL is returning "+url);
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
