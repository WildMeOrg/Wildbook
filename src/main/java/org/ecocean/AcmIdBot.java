package org.ecocean;


import javax.jdo.Query;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;
import org.ecocean.identity.IBEISIA;
import org.ecocean.media.Feature;
import org.ecocean.media.MediaAsset;

/*
 * Wildbook requires shared UUIDs (a.k.a. acmID) between MediaAsset objects in the Wildbook database and images in WBIA.
 * AcmIDs are a prerequisite for detection and therefore can be a blocker in the IA pipeline if for any reason
 * WBIA times out or is otherwise unavailable to provide an acmId to Wildbook when new data is submitted.
 * This bot provides some automated backend healing to get images registered if for any reason acmId registration fails.
 * It first checks bulk ImportTasks for appropriate images that may be missing an acmId, and then it checks
 * Encounters submitted within the past 24 hours.
 * 
 */
public class AcmIdBot {

   
    static String context="context0";

    private static void fixFeats(List<Feature> feats, Shepherd myShepherd) {
      if(feats!=null && feats.size()>0) {
        for(Feature feat:feats){
          MediaAsset asset = feat.getMediaAsset();
          try {  
              if(asset!=null && asset.isValidImageForIA()==null) {
                asset.validateSourceImage();
                myShepherd.updateDBTransaction();
              }
              if(asset!=null && asset.isValidImageForIA()) {
                ArrayList<MediaAsset> fixMe=new ArrayList<MediaAsset>();
                fixMe.add(asset);
                IBEISIA.sendMediaAssetsNew(fixMe, context);
              }
            }
            catch(Exception ec) {
              System.out.println("Exception in AcmIdBot.fixFeats");
              ec.printStackTrace();
            }

        }
      }
    }
  

    //background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }


    //basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { //throws IOException {
        long interval = 15; //number minutes between metrics refreshes of data in the CSV
        long initialDelay = 1; //number minutes before first execution occurs
        System.out.println("+ AcmIdBot.startCollector(" + context + ") starting.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            
            //DO METRICS WORK HERE
            public void run() {
                if (new java.io.File("/tmp/WB_AcmIdBot_SHUTDOWN").exists()) {
                    System.out.println("INFO: AcmIdBot.startCollection(" + context + ") shutting down due to file signal");
                    schedExec.shutdown();
                    return;
                }
                
               fixAcmIds(context); 
               
            }

        },
        initialDelay,  //initial delay
        interval,  //period delay *after* execution finishes
        TimeUnit.MINUTES);  //unit of delays above

        System.out.println("Let's get AcmIdBot's time running.");
        try {
          schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: AcmIdBot.startCollector(" + context + ") interrupted: " + ex.toString());
        }
        System.out.println("+ AcmIdBot.startCollector(" + context + ") backgrounded");
    }



    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {
        System.out.println("================ = = = = = = ===================== AcmIdBot.cleanup() finished.");
    }

    

    
    public static void fixAcmIds(String context) {

      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("AcmIdBot.java");
      myShepherd.beginDBTransaction();

      try {
        
        
        System.out.println("Looking for complete import tasks with media assets with missing acmIds");

        
        String filter2="select from org.ecocean.media.Feature where itask.status == 'complete' && itask.encounters.contains(enc) && enc.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null && asset.validImageForIA != false VARIABLES org.ecocean.Encounter enc;org.ecocean.servlet.importer.ImportTask itask;org.ecocean.Annotation annot";
        Query query2 = myShepherd.getPM().newQuery(filter2);
        Collection c2 = (Collection) (query2.execute());
        List<Feature> feats = new ArrayList<Feature>(c2);
        query2.closeAll();
        
        fixFeats(feats,myShepherd);

        
        
        //check recent Encounter submissions in last 24 hours for missing acmIds
        long currentTimeInMillis = System.currentTimeMillis();
        long twenyFourHoursAgo = currentTimeInMillis-1000*60*60*24;
        System.out.println("Looking for recent Encounters (24 hours) with media assets with missing acmIds");
        //dwcDateAddedLong >=
        String filter3="select from org.ecocean.media.Feature where enc45.dwcDateAddedLong >= "+twenyFourHoursAgo+" && !itask.encounters.contains(enc45) && enc45.annotations.contains(annot) && annot.features.contains(this) && asset.acmId == null VARIABLES org.ecocean.Encounter enc45;org.ecocean.Annotation annot;org.ecocean.servlet.importer.ImportTask itask";
        Query query3 = myShepherd.getPM().newQuery(filter3);
        Collection c3 = (Collection) (query3.execute());
        List<Feature> feats2 = new ArrayList<Feature>(c3);
        query3.closeAll();
        
        fixFeats(feats2,myShepherd);
        
     }
     catch(Exception f) {
       System.out.println("Exception in AcmIdBot!");
       f.printStackTrace();
     }
     finally {
       myShepherd.rollbackAndClose();
      }
    }


    

}
