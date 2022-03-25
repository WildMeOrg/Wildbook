package org.ecocean.ia;

import org.datanucleus.api.rest.orgjson.JSONObject;
import org.ecocean.cache.CachedQuery;
import org.ecocean.cache.QueryCache;
import org.ecocean.cache.QueryCacheFactory;
import org.ecocean.Util;
import org.ecocean.RestClient;
import org.ecocean.identity.IBEISIA;
import java.net.URL;
import java.util.Iterator;

public class WbiaQueueUtil {

  //Measurement static values
  private static JSONObject wbiaQueue = new JSONObject();
  private static String cacheName="wbiaQueue";
  
  private static int numJobs=0;
  private static int numCompletedJobs = 0;
  private static int numWorkingJobs = 0;
  private static int numQueuedJobs = 0;
  private static int numErrorJobs = 0;
  private static int numDetectionJobs = 0;
  private static int numIDJobs = 0;
  private static int sizeIDJobQueue=0;
  private static int sizeDetectionJobQueue=0;
  
  private static void reloadIfNeeded(boolean refresh) {
    String context="context0";
    try {
      QueryCache qc=QueryCacheFactory.getQueryCache(context);
      if(qc.getQueryByName(cacheName)!=null && System.currentTimeMillis()<qc.getQueryByName(cacheName).getNextExpirationTimeout() && !refresh){
        wbiaQueue=Util.toggleJSONObject(qc.getQueryByName(cacheName).getJSONSerializedQueryResult());
      }
      else{
          URL wbiaQueueUrl = IBEISIA.iaURL(context, "api/engine/job/status/");
          wbiaQueue = Util.toggleJSONObject(RestClient.get(wbiaQueueUrl,5000));
          CachedQuery cq=new CachedQuery(cacheName,Util.toggleJSONObject(wbiaQueue));
          cq.nextExpirationTimeout=System.currentTimeMillis()+30000;
          qc.addCachedQuery(cq);
          
          //reset our vars
          numJobs=0;
          numCompletedJobs = 0;
          numWorkingJobs = 0;
          numQueuedJobs = 0;
          numErrorJobs = 0;
          numDetectionJobs = 0;
          numIDJobs = 0;
          sizeIDJobQueue=0;
          sizeDetectionJobQueue=0;
          
          JSONObject inspectMe =wbiaQueue.getJSONObject("response").getJSONObject("json_result");
          Iterator<String> keys=inspectMe.keys();
          while(keys.hasNext()){
            String jobID=keys.next();
            numJobs++;
            JSONObject job=inspectMe.getJSONObject(jobID);
            boolean working = false;
            boolean queued = false;
            if(job.getString("status").equals("completed"))numCompletedJobs++;
            if(job.getString("status").equals("working")){
              numWorkingJobs++;
              working = true;
            }
            if(job.getString("status").equals("queued")){
              numQueuedJobs++;
              queued=true;
            }
            if(job.getString("status").equals("error"))numErrorJobs++;
            if(job.getString("function").startsWith("start_detect")){
              numDetectionJobs++;
              if(working||queued)sizeDetectionJobQueue++;
            }
            if(job.getString("function").startsWith("start_identify")){
              numIDJobs++;
              if(working||queued)sizeIDJobQueue++;
            }
          }
      }
    }
    catch(Exception e) {e.printStackTrace();}
  }
  
  public static synchronized int getSizeIDJobQueue(boolean refresh) {
    reloadIfNeeded(refresh);
    return sizeIDJobQueue;
  }
  
  public static synchronized int getSizeDetectionJobQueue(boolean refresh) {
    reloadIfNeeded(refresh);
    return sizeDetectionJobQueue;
  }
  
  public static synchronized String getStatusWBIAJob(String id,boolean refresh) {
    if(id==null)return null;
    reloadIfNeeded(refresh);
    try {
      JSONObject inspectMe =wbiaQueue.getJSONObject("response").getJSONObject("json_result");
      Iterator<String> keys=inspectMe.keys();
      while(keys.hasNext()){
        String jobID=keys.next();
        if(id.equals(jobID)) {
          JSONObject job=inspectMe.getJSONObject(jobID);
          if(job.getString("status")!=null)return job.getString("status");
        }
      }
    }
    catch(Exception e) {e.printStackTrace();}
    return null;
  }
  
  public static synchronized int getNumWorkingJobs(boolean refresh) {
    reloadIfNeeded(refresh);
    return numWorkingJobs;
  }
  
  public static synchronized int getNumQueuedJobs(boolean refresh) {
    reloadIfNeeded(refresh);
    return numQueuedJobs;
  }
  


}
