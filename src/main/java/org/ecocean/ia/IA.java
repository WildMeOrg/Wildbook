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
import org.ecocean.media.MediaAsset;
import org.ecocean.identity.IBEISIA;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import org.json.JSONObject;
import org.json.JSONArray;

public class IA {

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
    public static Task intakeMediaAssets(Shepherd myShepherd, List<MediaAsset> mas) {
        if ((mas == null) || (mas.size() < 1)) return null;
        Task task = new Task();
        task.setObjectMediaAssets(mas);

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
    public static Task intakeAnnotations(Shepherd myShepherd, List<Annotation> anns) {
        if ((anns == null) || (anns.size() < 1)) return null;
        Task task = new Task();
        task.setObjectAnnotations(anns);

        //what we do *for now* is punt to "legacy" IBEISIA queue stuff... but obviously this should be expanded as needed
        JSONArray annArr = new JSONArray();
        for (Annotation ann : anns) {
            annArr.put(ann.getId());
        }
        JSONObject aj = new JSONObject();
        aj.put("annotationIds", annArr);
        String context = myShepherd.getContext();
        JSONObject qjob = new JSONObject();
        qjob.put("identify", aj);
        qjob.put("taskId", task.getId());
        qjob.put("__context", context);
        qjob.put("__baseUrl", getBaseURL(context));
        boolean sent = false;
        try {
            sent = org.ecocean.servlet.IAGateway.addToQueue(context, qjob.toString());
        } catch (java.io.IOException iox) {
            System.out.println("ERROR: IA.intakeAnnotations() addToQueue() threw " + iox.toString());
        }
System.out.println("INFO: IA.intakeAnnotations() accepted " + anns.size() + " annots; queued? = " + sent + "; " + task);
        return task;
    }


    public static String getBaseURL(String context) {
        String url = CommonConfiguration.getServerURL(context);
        String containerName = CommonConfiguration.getProperty("containerName","context0").trim();
        url = CommonConfiguration.getServerURL(context);
        if (containerName!=null&&!"".equals(containerName)) { 
            System.out.println("Wildbook is containerized: sending container name: "+containerName+" to IA instead of localhost.");
            url = url.replace("localhost", containerName);
        }
        return url;
    }


}
