/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
*/
package org.ecocean;


import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.time.Duration;
import java.time.ZonedDateTime;
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
                
               refreshMetrics(context); 
               
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

    
    public static String buildGauge(String filter, String name, String help, String context) {
      return buildGauge(filter, name, help, context, null);
    }
    
    
    public static String buildGauge(String filter, String name, String help, String context, String label) {
      System.out.println("-- Collecting metrics for: "+ name);
      System.out.println("        "+ filter);
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
        if(label!=null)line+=","+label;
        
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
    
    public static void refreshMetrics(String context) {
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
        
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.media.MediaAsset", "wildbook_mediaassets_total","Number of media assets",context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Occurrence", "wildbook_sightings_total","Number of sightings",context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Annotation", "wildbook_annotations_total","Number of annotations",context));
        
        //User-related
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime oldDay = now.minusDays(1);
        long oneDayAgo = oldDay.toInstant().toEpochMilli();
        ZonedDateTime oldWeek = now.minusDays(7);
        long oneWeekAgo = oldWeek.toInstant().toEpochMilli();
        ZonedDateTime oldMonth = now.minusMonths(1);
        long oneMonthAgo = oldMonth.toInstant().toEpochMilli();
        ZonedDateTime oldYear = now.minusYears(1);
        long oneYearAgo = oldYear.toInstant().toEpochMilli();
        
       
        //Taxonomy has to be treated differently because of past data pollution from Spotter app
        List<String> taxa=CommonConfiguration.getIndexedPropertyValues("genusSpecies", context);
        if(taxa!=null) {
          System.out.println("-- Collecting metrics for: wildbook_taxonomies_total");
          csvLines.add("wildbook_taxonomies_total"+","+taxa.size()+","+"gauge"+","+"Number of species");
          System.out.println("   -- Done");
        }
        String encLabels="";
        String indyLabels="";
        for(String tax:taxa) {
          StringTokenizer str=new StringTokenizer(tax," ");
          if(str.countTokens()>1) {
            String genus = str.nextToken();
            String specificEpithet=str.nextToken();
            if(str.hasMoreTokens())specificEpithet+=" "+str.nextToken();
            
            String indyLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual where encounters.contains(enc) && enc.specificEpithet == '"+specificEpithet+"'", (genus+"_"+specificEpithet.replaceAll(" ","_")),"Number of marked individuals ("+genus+" "+specificEpithet+")",context);
            StringTokenizer strIndy=new StringTokenizer(indyLabelTemp,",");
            indyLabels+="species_"+strIndy.nextToken()+":"+strIndy.nextToken()+",";
            
            String encLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.Encounter where specificEpithet == '"+specificEpithet+"'", (genus+"_"+specificEpithet.replaceAll(" ","_")),"Number of encounters ("+genus+" "+specificEpithet+")",context);
            StringTokenizer encIndy=new StringTokenizer(encLabelTemp,",");
            encLabels+="species_"+encIndy.nextToken()+":"+encIndy.nextToken()+",";
            
            
          }
        }
        
        String indyLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual","*","Number of marked individuals",context);
        StringTokenizer strIndy=new StringTokenizer(indyLabelTemp,",");
        indyLabels+="species_"+strIndy.nextToken()+":"+strIndy.nextToken()+",";
        
        String encLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.Encounter", "*","Number of encounters",context);
        StringTokenizer encIndy=new StringTokenizer(encLabelTemp,",");
        encLabels+="species_"+encIndy.nextToken()+":"+encIndy.nextToken()+",";
        
        if(encLabels.equals(""))encLabels=null;
        else if(encLabels.endsWith(",")) {encLabels="\""+encLabels.substring(0,(encLabels.length()-1))+"\"";}
        if(indyLabels.equals(""))indyLabels=null;
        else if(indyLabels.endsWith(",")) {indyLabels="\""+indyLabels.substring(0,(indyLabels.length()-1))+"\"";}
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.Encounter", "wildbook_encounters_total","Number of encounters",context,encLabels));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.MarkedIndividual", "wildbook_individuals_total","Number of marked individuals",context,indyLabels));
        
        
        //User analysis
        String userLabels="";
        String dayLabel=buildGauge("SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > "+oneDayAgo, "lastDayLogin","Number of users logging in (24 hours)",context);
        StringTokenizer str1=new StringTokenizer(dayLabel,",");
        userLabels+="login_"+str1.nextToken()+":"+str1.nextToken()+",";
        
        String weekLabel = buildGauge("SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > "+oneWeekAgo, "lastWeekLogin","Number of users logging in (Last 7 days)",context);
        StringTokenizer str2=new StringTokenizer(weekLabel,",");
        userLabels+="login_"+str2.nextToken()+":"+str2.nextToken()+",";
        
        String monthLabel = buildGauge("SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > "+oneMonthAgo, "lastMonthLogin","Number of users logging in (Last 30 days)",context);
        StringTokenizer str3=new StringTokenizer(monthLabel,",");
        userLabels+="login_"+str3.nextToken()+":"+str3.nextToken()+",";
        
        String yearLabel = buildGauge("SELECT count(this) FROM org.ecocean.User where username != null && lastLogin > "+oneYearAgo, "lastYearLogin","Number of users logging in (Last 365 days)",context);
        StringTokenizer str4=new StringTokenizer(yearLabel,",");
        userLabels+="login_"+str4.nextToken()+":"+str4.nextToken()+",";
        
        String userLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username != null", "*","Number of users",context);
        StringTokenizer str5=new StringTokenizer(userLabelTemp,",");
        userLabels+="login_"+str5.nextToken()+":"+str5.nextToken()+",";
        
        if(userLabels.equals(""))userLabels=null;
        else if(userLabels.endsWith(",")) {userLabels="\""+userLabels.substring(0,(userLabels.length()-1))+"\"";}
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username != null", "wildbook_users_total","Number of users",context, userLabels));
       
        //data contributors
        String contributorsLabels="";
        String allContribLabel=buildGauge("SELECT count(this) FROM org.ecocean.User", "*","Number of data contributors",context);
        StringTokenizer str6=new StringTokenizer(allContribLabel,",");
        contributorsLabels+="contributor_"+str6.nextToken()+":"+str6.nextToken()+",";
        
        String publicContribLabel=buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username == null", "public","Number of public data contributors",context);
        StringTokenizer str7=new StringTokenizer(publicContribLabel,",");
        contributorsLabels+="contributor_"+str7.nextToken()+":"+str7.nextToken()+",";
        
        if(contributorsLabels.equals(""))contributorsLabels=null;
        else if(contributorsLabels.endsWith(",")) {contributorsLabels="\""+contributorsLabels.substring(0,(contributorsLabels.length()-1))+"\"";}
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username == null", "wildbook_datacontributors_total","Number of public data contributors",context,contributorsLabels));
        
        
        
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



}
