/*
    aka "Tweet-a-Whale" .. implements listening for and processing tweets
*/
package org.ecocean;


import javax.jdo.Query;
import javax.servlet.http.HttpServletRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.lang.Runnable;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ScheduledFuture;
import java.text.Normalizer;
import io.prometheus.client.CollectorRegistry;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.ecocean.ia.IA;
import org.ecocean.metrics.Prometheus;
import org.json.JSONObject;

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
        long interval = 15; //number minutes between metrics refreshes of data in the CSV
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
      //System.out.println("-- Collecting metrics for: "+ name);
      //System.out.println("        "+ filter);
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
      //System.out.println("   -- Done: "+ line);
      return line;
    }
    
    public static void refreshMetrics(String context) {
      
      //clear old metrics
      CollectorRegistry.defaultRegistry.clear();
      
      
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
        
        String userLabelTemp=buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0", "*","Number of users",context);
        StringTokenizer str5=new StringTokenizer(userLabelTemp,",");
        userLabels+="login_"+str5.nextToken()+":"+str5.nextToken()+",";
        
        if(userLabels.equals(""))userLabels=null;
        else if(userLabels.endsWith(",")) {userLabels="\""+userLabels.substring(0,(userLabels.length()-1))+"\"";}
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.User WHERE username != null && lastLogin > 0", "wildbook_users_total","Number of users",context, userLabels));
       
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
        
        // Machine learning tasks
        addTasksToCsv(csvLines, context);

        //WBIA Metrics pull
        String metricsURL = IA.getProperty(context, "IBEISIARestUrlAddImages");
        if(metricsURL!=null) {
          metricsURL=metricsURL.replaceAll("/api/image/json/","")+"/metrics";
          String wbiaMetrics=httpGetRemoteText(metricsURL);

          //WBIA turnaround time all task types
          String regexTT="wbia_turnaround_seconds\\{endpoint=\"\\*\".*\\} \\d*\\.\\d*";
          String promValueTT = getWBIAPrometheusClientValue(wbiaMetrics, regexTT);
          if(promValueTT!=null) {
            csvLines.add("wildbook_wbia_turnaroundtime"+","+promValueTT+","+"gauge"+","+"WBIA job queue turnaround time");
          }
          
          //WBIA turnaround time detection (lightnet) tasks
          String regexTTdetect="wbia_turnaround_seconds\\{endpoint=\"/api/engine/detect/cnn/lightnet/\".*\\} \\d*\\.\\d*";
          String promValueDetect = getWBIAPrometheusClientValue(wbiaMetrics, regexTTdetect);
          if(promValueDetect!=null) {
            csvLines.add("wildbook_wbia_turnaroundtime_detection"+","+promValueDetect+","+"gauge"+","+"WBIA job queue turnaround time for detection tasks");
          }
          
          //WBIA turnaround time ID (graph) tasks
          String regexTTgraph="wbia_turnaround_seconds\\{endpoint=\"/api/engine/query/graph/\".*\\} \\d*\\.\\d*";
          String promValueID = getWBIAPrometheusClientValue(wbiaMetrics, regexTTgraph);
          if(promValueID!=null) {
            csvLines.add("wildbook_wbia_turnaroundtime_id"+","+promValueID+","+"gauge"+","+"WBIA job queue turnaround time for ID tasks");
          }
          
        }
                
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
        
        //now reload metrics
        Prometheus metricsExtractor = new Prometheus();
        metricsExtractor.getValues(); 
        
        
        
     }
     catch(Exception f) {
       System.out.println("Exception in MetricsBot!");
       f.printStackTrace();
     } 
    }

    /* 
    * addTasksToCsv
    *  
    * Helper method for adding machine learning tasks related metrics 
    * Written by 2022 Captstone team: Gabe Marcial, Joanna Hoang, Sarah Schibel
    */
    private static void addTasksToCsv(ArrayList<String> csvLines, String context) throws FileNotFoundException
    {
        // Total tasks
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task", "wildbook_tasks_total", "Number of machine learning tasks", context));

        // Detection tasks
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf('ibeis.detection') > -1  && (children == null || (children.contains(child) && child.parameters.indexOf('ibeis.detection') == -1)) VARIABLES org.ecocean.ia.Task child","wildbook_detection_tasks","Number of detection tasks", context));        
  
        // Identification tasks
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1)" , "wildbook_identification_tasks","Number of identification tasks", context));
      
        // Hotspotter, PieTwo, PieOne 
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where children == null && parameters.indexOf('\"sv_on\"')>-1", "wildbook_tasks_hotspotter", "Number of tasks using Hotspotter algorithm", context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('Pie')>-1 && parameters.indexOf('PieTwo')==-1", "wildbook_tasks_pieOne", "Number of tasks using PieOne algorithm", context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('PieTwo')>-1", "wildbook_tasks_pieTwo", "Number of tasks using PieTwo algorithm", context));
        
        // CurvRankTwoDorsal, CurveRankTwoFluke, OC_WDTW
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('CurvRankTwoDorsal')>-1", "wildbook_tasks_curveRankTwoDorsal", "Number of tasks using CurveRankTwoDorsal algorithm", context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('CurvRankTwoFluke')>-1", "wildbook_tasks_curveRankTwoFluke", "Number of tasks using CurveRankTwoFluke algorithm", context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('OC_WDTW')>-1", "wildbook_tasks_oc_wdtw", "Number of tasks using OC_WDTW algorithm", context));
        
        // Finfindr, Deepsense
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.indexOf('Finfindr')>-1", "wildbook_tasks_finFindr", "Number of tasks using FinFindr algorithm", context));
        csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where  children == null && parameters.toLowerCase().indexOf('deepsense')>-1", "wildbook_tasks_deepsense", "Number of tasks using Deepsense algorithm", context));
        
        // Species tasks
        Shepherd myShepherd = new Shepherd(context);
        myShepherd.setAction("MetricsBot_ML_Tasks");
        myShepherd.beginDBTransaction();
        try {
        
          IAJsonProperties iaConfig = new IAJsonProperties();
  	      List<Taxonomy> taxes = iaConfig.getAllTaxonomies(myShepherd);
          String filter3 = "SELECT count(this) FROM org.ecocean.ia.Task where (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1) ";
          String scientificName = "";
  
          // Iterate through our species
          for (Taxonomy tax:taxes)
          { 
            // Logic for extracting IA classes comes from taskQueryTests.jsp
            List<String> iaClasses = iaConfig.getValidIAClassesIgnoreRedirects(tax);
            if (iaClasses!=null && iaClasses.size()>0)
            {
              String allowedIAClasses = "&& ( ";
              for (String str:iaClasses)
              {
                if (allowedIAClasses.indexOf("iaClass") == -1)
                {
                  allowedIAClasses +=" annot.iaClass == '" + str + "' ";
                }
                else
                {
                  allowedIAClasses += " || annot.iaClass == '" + str + "' ";
                }
              }
              
              try
              {
                scientificName = tax.getScientificName();
              }
              catch (NullPointerException e)
              {
                System.out.println("Null Pointer Exception in Species Tasks");
              }
  
              // Replace space w/ underscore for prometheus syntax
              scientificName = scientificName.replaceAll("\\s+", "_"); 
              scientificName = scientificName.replaceAll("\\+", "_"); 
  
              allowedIAClasses += " )";
              String filter = filter3 + " && objectAnnotations.contains(annot) " + allowedIAClasses + " VARIABLES org.ecocean.Annotation annot";
              csvLines.add(buildGauge(filter, "wildbook_tasks_idSpecies_" + scientificName, "Number of ID tasks by species " + scientificName, context));
            }
          }
  
          // Tasks by users
          //WB-1968: filter to only users who have logged in
          //List<User> users = myShepherd.getAllUsers();
          String filterTasksUsers="SELECT FROM org.ecocean.User where lastLogin > 0";
          Query filterTasksUsersQuery = myShepherd.getPM().newQuery(filterTasksUsers);
          Collection c = (Collection)filterTasksUsersQuery.execute();
          List<User> users = new ArrayList<User>(c);
          filterTasksUsersQuery.closeAll();
          //end WB-1968
          String userFilter = "";
          String name = "";
          for (User user:users)
          {  
            // Try catch for nulls, because tasks executed by anonymous users don't have a name tied to them
            try
            {
              name = user.getFullName(); 
              userFilter = (String) user.getUsername();
  
              // Truncate user's full name to first name and last initial, and replace space w/ underscore 
              if (name.contains(" "))
              {
                String normalizedName = stripAccents(name);
                int spaceIndex = normalizedName.indexOf(" ");
                name = (normalizedName.substring(0,spaceIndex) + "_" + normalizedName.charAt(spaceIndex+1)).toLowerCase();
              }
              name+="_"+user.getUUID().substring(0,8);
              name=name.replaceAll("-", "_");
              System.out.println("NAME:" + name);
              csvLines.add(buildGauge("SELECT count(this) FROM org.ecocean.ia.Task where parameters.indexOf(" + "'" + userFilter + "'" + ") > -1 && (parameters.indexOf('ibeis.identification') > -1 || parameters.indexOf('pipeline_root') > -1 || parameters.indexOf('graph') > -1)","wildbook_user_tasks_"+name, "Number of tasks from user " + name, context)); 
            }
            catch (NullPointerException e) { }
          }
        }
        catch(Exception exy) {exy.printStackTrace();}
        finally {
          myShepherd.rollbackAndClose();
        }
    }

    //Helper method for normalizing characters
    public static String stripAccents(String input){
    return input == null ? null :
            Normalizer.normalize(input, Normalizer.Form.NFD)
                    .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
    }


    public static String httpGetRemoteText(String url) {
      String responseString = "";

      try {

          CloseableHttpClient httpClient = HttpClientBuilder.create().build();
  
          URIBuilder uriBuilder = new URIBuilder(url);
  
          URI uri = uriBuilder.build();
          HttpGet request = new HttpGet(uri);

          // Make the API call and get the response entity.
          HttpResponse response = httpClient.execute(request);
          HttpEntity entity = response.getEntity();
          if (entity != null) {
              responseString = EntityUtils.toString(entity);
          }

      }
      catch (Exception e) {
          System.out.println("Failed to get a response posting MediaAsset with URL: "+url);
          e.printStackTrace();
      }   
      return responseString;
    }
    
    public static String getWBIAPrometheusClientValue(String parseMe, String regex) {
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(parseMe);
      if(matcher.find()){
        String tt=matcher.group(0); 
        StringTokenizer str=new StringTokenizer(tt," ");
        if(str.countTokens()>1) {
          String definition = str.nextToken();
          String value = str.nextToken();
          return value;
        }
      }
      return null;
    }
    

}
