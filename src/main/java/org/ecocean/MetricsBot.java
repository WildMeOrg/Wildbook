/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
*/
package org.ecocean;


import javax.jdo.Query;


import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ScheduledFuture;

public class MetricsBot {
    private static long collectorStartTime = 0l;

    public static String csvFile = "/data/metrics/metrics.csv";
    
    static String context="context0";


    private static RateLimitation outgoingRL = new RateLimitation(48 * 60 * 60 * 1000);  //only care about last 48 hrs


    public static String rateLimitationInfo() {
        return outgoingRL.toString();
    }

  

    //background workers
    public static boolean startServices(String context) {
        startCollector(context);
        return true;
    }





    //basically our "listener" daemon; but is more pull (poll?) than push so to speak.
    private static void startCollector(final String context) { //throws IOException {
        collectorStartTime = System.currentTimeMillis();  //TODO should really be keyed off context!
        long interval = 60; //number minutes between metrics refreshes of data in the CSV
        long initialDelay = 1; //number minutes before first execution occurs
        System.out.println("+ MetricsBot.startCollector(" + context + ") starting.");
        final ScheduledExecutorService schedExec = Executors.newScheduledThreadPool(1);
        final ScheduledFuture schedFuture = schedExec.scheduleWithFixedDelay(new Runnable() {
            int count = 0;
            
            
            //DO METRICS WORK HERE
            public void run() {
                ++count;
                if (new java.io.File("/tmp/WB_METRICSBOT_SHUTDOWN").exists()) {
                    System.out.println("INFO: MetricsBot.startCollection(" + context + ") shutting down due to file signal");
                    schedExec.shutdown();
                    return;
                }
                
                
                //first, make sure metrics file exists
                //if not, create it
                File metricsFile=new File(csvFile);
                File metricsDir=metricsFile.getParentFile();
                try {
                  if(!metricsDir.exists()) {
                    boolean created=metricsDir.mkdirs();
                    if(!created) throw new Exception("Could not create directory: "+metricsDir.getAbsolutePath());
                  }
                
                  //store our CSV lines for writing
                  ArrayList<String> csvLines=new ArrayList<String>();

                  //execute queries to get metrics
                  
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.media.MediaAsset", "wildbook_mediaassets","Number of media assets"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Encounter", "wildbook_encounters","Number of encounters"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual", "wildbook_individuals","Number of marked individuals"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User", "wildbook_data_contributors","Number of data contributors"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username != null", "wildbook_users","Number of users"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username == null", "wildbook_data_contributors_public","Number of public data contributors"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Occurrence", "wildbook_sightings","Number of sightings"));
                  csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Taxonomy", "wildbook_taxonomies","Number of species"));
                  
                  
                  
                  //write the file
                  //set up the output stream
                  FileOutputStream fos = new FileOutputStream(csvFile);
                  OutputStreamWriter outp = new OutputStreamWriter(fos);
                  for(String line:csvLines) {
                    outp.write(line+"\n");
                  }
                  outp.close();
                  fos.close();
                  outp=null;
                  
                  
               }
               catch(Exception f) {
                 System.out.println("Exception in MetricsBot!");
                 f.printStackTrace();
               } 
            }
            
            
            
            
        },
        initialDelay,  //initial delay
        interval,  //period delay *after* execution finishes
        TimeUnit.MINUTES);  //unit of delays above

        System.out.println("Let's get MetricsBot's time running.");
        try {
          schedExec.awaitTermination(5000, TimeUnit.MILLISECONDS);
        } catch (java.lang.InterruptedException ex) {
            System.out.println("WARNING: MetricsBot.startCollector(" + context + ") interrupted: " + ex.toString());
        }
        System.out.println("+ MetricsBot.startCollector(" + context + ") backgrounded");
    }



    // mostly for ContextDestroyed in StartupWildbook..... i think?
    public static void cleanup() {

        System.out.println("================ = = = = = = ===================== MetricsBot.cleanup() finished.");
    }

    public static String buildGauge(String filter, String name, String help) {
      System.out.println("-- Collecting metrics for: "+ name);
      String line=null;
      
      Shepherd myShepherd=new Shepherd(context);
      myShepherd.setAction("MetricsBot_buildGauge_"+name);
      myShepherd.beginDBTransaction();
      try {
        Long myValue=null;
        Query q=myShepherd.getPM().newQuery(filter);
        myValue=(Long) q.execute();
        q.closeAll();
        if(myValue!=null) {line=name+","+myValue.toString()+","+"gauge"+","+help;}
        
      }
      catch(Exception e) {
        e.printStackTrace();
      }
      finally {
        
        myShepherd.rollbackAndClose();
        
      }
      System.out.println("   -- Done: "+ line);
      return line;
    }



}
