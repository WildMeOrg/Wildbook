package org.ecocean.metrics;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/*
import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetSet;
import org.ecocean.MarkedIndividual;
*/

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

public class Prometheus
{
    /*Initialize variables*/
    private boolean pageVisited = false;  
    
    //Encounters by Wildbook Counter
    //public Counter encs;

    //Encounter Species Counters
    //public Counter encsSpecies;
    
    /*
    public Counter encountersForSpecieEquusQuagga;
    public Counter encountersForSpecieEquusGrevyi;
    public Counter encountersForSpeciePzGzHybrid;
    */

    //public Counter encsSubDate;

    //Encounter Location Counters
    /*
    public Counter encountersForLocationKenya;
    public Counter encountersForLocationMpala;
    public Counter encountersForLocationMpalacentral;
    public Counter encountersForLocationMpala_Central;
    public Counter encountersForLocationMpala_North;
    public Counter encountersForLocationMpala_South;
    public Counter encountersForLocationMpala_central;
    public Counter encountersForLocation01Pejeta_East;
     */
    //Users Gauges
    //public Gauge numUsersInWildbook; 
    //public Gauge numUsersWithLogin;
    //public Gauge numUsersWithoutLogin;

    //Media assets Gauge
    //public Gauge numMediaAssetsWildbook;
    
    /*
    public Gauge numMediaAssetsSpecieEquusQuagga;
    public Gauge numMediaAssetsSpecieEquusGrevyi;
    public Gauge numMediaAssetsSpeciePzGzHybrid;
    */
    
    private String context="context0";
    
    
    //individuals Gauge
    //public Gauge indiv;
    
    String csvFile = "/data/metrics/metrics.csv";
    String cvsSplitBy = ",";
    
    //Default constructor
    public Prometheus()
    {
      this.context=context;
      //register all metrics
     // encsSpecies = Counter.build().name("wildbook_encounters")
     //   .help("Number encounters by Specie").register();
      //encsSubDate = Counter.build().name("wildbook_encounters_by_date")
      //  .help("Number encounters by Submission Date").register();

      //Specie Counters
      /*
      encountersForSpecieEquusQuagga = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_Quagga")
        .help("Number encounters by Specie type Equus Quagga").register();
      encountersForSpecieEquusGrevyi = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_Grevyi")
        .help("Number encounters by Specie type Equus Grevyi").register();
      encountersForSpeciePzGzHybrid = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_PzGz_Hybrid")
        .help("Number encounters by Specie type PzGz Hybrid").register();
      */
      
      //Location Counters
      /*
      encountersForLocationKenya = Counter.build().name("wildbook_encounters_by_Location_Kenya")
        .help("Number encounters by Location ID Kenya").register();
      encountersForLocationMpala = Counter.build().name("wildbook_encounters_by_Location_Mpala")
        .help("Number encounters by Location ID Mpala").register();
      encountersForLocationMpalacentral = Counter.build().name("wildbook_encounters_by_Location_Mpalacentral")
        .help("Number encounters by Location ID Mpala central").register();
      encountersForLocationMpala_Central = Counter.build().name("wildbook_encounters_by_Location_Mpala_Central")
        .help("Number encounters by Location ID Mpala.Central").register();
      encountersForLocationMpala_North = Counter.build().name("wildbook_encounters_by_Location_Mpala_North")
        .help("Number encounters by Location ID Mpala.North").register();
      encountersForLocationMpala_South = Counter.build().name("wildbook_encounters_by_Location_Mpala_South")
        .help("Number encounters by Location ID Mpala.South").register();
      encountersForLocationMpala_central = Counter.build().name("wildbook_encounters_by_Location_Mpala_central")
        .help("Number encounters by Location ID Mpala.central").register();
      encountersForLocation01Pejeta_East = Counter.build().name("wildbook_encounters_by_Location_01Pejeta_East")
        .help("Number encounters by Location ID 01 Pejeta.East").register();
      */
      
      
      /*
      indiv = Gauge.build().name("wildbook_individuals")
        .help("Number individuals by Wildbook").register();
      encs = Counter.build().name("wildbook_encounters")
        .help("Number encounters").register();
      numUsersInWildbook = Gauge.build().name("wildbook_users")
        .help("Number users").register();
      numUsersWithLogin = Gauge.build().name("wildbook_users_w_login")
        .help("Number users with Login").register();
      numUsersWithoutLogin = Gauge.build().name("wildbook_users_wout_login")
        .help("Number users without Login").register();
      numMediaAssetsWildbook = Gauge.build().name("wildbook_mediaassets")
        .help("Number of Media Assets by Wildbook").register();

      */
      
      /*
      numMediaAssetsSpecieEquusQuagga = Gauge.build().name("equusQuagga_mediaassets")
        .help("Number of Media Assets by Specie Equus Quagga").register();
      numMediaAssetsSpecieEquusGrevyi = Gauge.build().name("equusGrevyi_mediaassets")
        .help("Number of Media Assets by Specie Equus Grevyi").register();
      numMediaAssetsSpeciePzGzHybrid = Gauge.build().name("pzgzHybrid_mediaassets")
        .help("Number of Media Assets by Specie PzGz Hyrbid").register();
        */
    }

    //Unit test constructor
    public Prometheus(boolean isTesting)
    {
      
      /*
      //initialize but do not register metrics.
      encsSubDate = Counter.build().name("wildbook_encounters_by_date")
        .help("Number encounters by Submission Date").create();
      indiv = Gauge.build().name("wildbook_individual_wildbook")
        .help("Number individuals by Wildbook").create();
      encs = Counter.build().name("wildbook_encounters")
        .help("Number encounters").create();
      numUsersInWildbook = Gauge.build().name("wildbook_users")
        .help("Number users").create();
      numUsersWithLogin = Gauge.build().name("wildbook_users_w_login")
        .help("Number users with Login").create();
      numUsersWithoutLogin = Gauge.build().name("wildbook_users_wout_login")
        .help("Number users without Login").create();
      numMediaAssetsWildbook = Gauge.build().name("wildbook_mediaassets")
        .help("Number of Media Assets by Wildbook").create();
      */
      
    }
    
    /** Implementation borrowed from MetricsServlet class
    * Parses the default collector registery into the kind of 
    * output that prometheus likes
    * Visit https://github.com/prometheus/client_java/blob/master/simpleclient_servlet/src/main/java/io/prometheus/client/exporter/MetricsServlet.java
    */
    public void metrics(HttpServletRequest request, HttpServletResponse response) throws IOException
    {
      Writer writer = new BufferedWriter(response.getWriter());
      response.setStatus(HttpServletResponse.SC_OK);
      String contentType = TextFormat.chooseContentType(request.getHeader("Accept"));
      response.setContentType(contentType);
      try
      {
        TextFormat.writeFormat(contentType, writer, CollectorRegistry.defaultRegistry.filteredMetricFamilySamples(parse(request)));
        writer.flush();
      }
      finally
      {
        writer.close();
      }
    }
    
    //Helper method for metrics() also borrowed from MetricsServlet.java
    private Set<String> parse(HttpServletRequest req)
    {
      String[] includedParam = req.getParameterValues("name[]");
      if(includedParam == null)
      {
        return Collections.emptySet();
      }
      else
      {
        return new HashSet<String>(Arrays.asList(includedParam));
      }
    }
    
    /** setNumberOfUsers
     * Sets the counters/gauges for metrics related to number of users
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    /*
    public void setNumberOfUsers()
    {
      //Shepherd ms=new Shepherd(context);
      //ms.beginDBTransaction();
      try {
        //Getting number of users by wildbook
        //int numUsers = ms.getNumUsers();
        //this.numUsersInWildbook.set((double)numUsers);
        //numUsersInWildbook = Gauge.build().name("wildbook_users")
        String value=getValue("wildbook_users");
        this.numUsersInWildbook.inc(new Double(value));
        
        String value2=getValue("wildbook_users_wout_login");
        this.numUsersWithoutLogin.inc(new Double(value2));
        
        String value3=getValue("wildbook_users_w_login");
        this.numUsersWithLogin.inc(new Double(value3));
  
        
  
        //TODO: Set number of active users
      
      }
      catch(Exception e) {e.printStackTrace();}
      finally {
        //ms.rollbackAndClose();
      }
    }
    */
    
    /** setNumberOfEncounters
     * Sets the counters/gauges for metrics related to number of encounters
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    /*
    public void setNumberOfEncounters()
    {
      
      //Shepherd ms=new Shepherd(context);
      //ms.beginDBTransaction();
      try {
        int i;
        int j;
       
        //int numEncounters = ms.getNumEncounters(); //in aggregate
        //this.encs.inc((double)numEncounters);
  
        String value=getValue("wildbook_encounters");
        this.encs.inc(new Double(value));
        

  
        //Number of Encounters by Submission Dates
        //TODO: Time Series Item, this method is not complete
        //List<String> numEncountersSub = ms.getAllRecordedBy();
        //int totalNumEncSub = numEncountersSub.size();
  
        //Number of Encounters by Location ID
        //Getting location ID
        //List<String> numEncountersLoc = ms.getAllLocationIDs();
  
    
            
      }
      catch(Exception e) {e.printStackTrace();}
      finally {
        //ms.rollbackAndClose();
      }

    }
    */
    
    /** setNumberOfIndividuals
     * Sets the counters/gauges for metrics related to number of individuals
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    /*
    public void setNumberOfIndividuals()
    {

      try {
        //Get num of Individuals by wildbook
        //int numIndividuals = ms.getNumMarkedIndividuals();
        //this.indiv.inc((double)numIndividuals);
        
        String value=getValue("wildbook_individuals");
        this.indiv.inc(new Double(value));
        
      }
      catch(Exception e) {e.printStackTrace();}
      finally {
   
      }
    }
    */
    /** setNumberOfMediaAssets
     * Sets the counters/gauges for metrics related to number of media assets
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    /*
    public void setNumberofMediaAssets()
    {
      //Shepherd ms=new Shepherd(context);
      //ms.beginDBTransaction();
      try {
        //Media Assets by WildBook
        //ArrayList<MediaAsset> numMediaAssetsWild = ms.getAllMediaAssetsAsArray();
        //int totalNumMediaAssests = numMediaAssetsWild.size();
        String value=getValue("wildbook_mediaassets");
        this.numMediaAssetsWildbook.inc(new Double(value));
  
    
      }
      catch(Exception e) {e.printStackTrace();}
      finally {
        //ms.rollbackAndClose();
      }

    }    
    */
    
    public String getValue(String key) {
      if(key==null)return null;
      String value=null;
      String csvFile = "/data/metrics/metrics.csv";
      BufferedReader br = null;
      String line = "";
      
      try {

        br = new BufferedReader(new FileReader(csvFile));
        while ((line = br.readLine()) != null) {

            // use comma as separator
            String[] vals = line.split(cvsSplitBy);
            if(vals[0]!=null && vals[1]!=null){
              String m_key=vals[0];
              String m_value=vals[1];

              if(m_key.trim().equals(key)) {value=m_value;} 
            }
        }        
      } 
      catch (FileNotFoundException e) {
          e.printStackTrace();
      } 
      catch (IOException e) {
          e.printStackTrace();
      } 
      finally {
          if (br != null) {
              try {
                  br.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
      return value;
    }
    
    public void getValues() {
      
      //setNumberofMediaAssets();
      //setNumberOfEncounters();
      //setNumberOfIndividuals();
      //setNumberofMediaAssets();
      //setNumberOfUsers();
      
      
      ArrayList<String> metrics=new ArrayList<String>();
      
      BufferedReader br = null;
      String line = "";
      try {

        br = new BufferedReader(new FileReader(csvFile));
        while ((line = br.readLine()) != null) {

          metrics.add(line);
            
        }        
      } 
      catch (FileNotFoundException e) {
          e.printStackTrace();
      } 
      catch (IOException e) {
          e.printStackTrace();
      } 
      finally {
          if (br != null) {
              try {
                  br.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }
      
      for(String metric:metrics) {
        
        
        String[] vals = metric.split(cvsSplitBy);
        
        if(vals.length>3) {
        
          try {
              String m_key=vals[0];
              String m_value=vals[1];
              String m_type=vals[2];
              String m_help=vals[3];
              System.out.println("Loading: "+m_key+","+m_value+","+m_type+","+m_help);
              if(m_type.trim().equals("gauge")) {
                System.out.println("Loading gauge: "+m_key);
                Gauge.build().name(m_key.trim())
                .help(m_help.trim()).register().inc(new Double(m_value.trim()));
              }
              else if(m_type.trim().equals("counter")) {
                System.out.println("Loading counter: "+m_key);
                Counter.build().name(m_key.trim())
                .help(m_help.trim()).register().inc(new Double(m_value.trim()));
              }
          }
          catch(Exception e) {
            e.printStackTrace();
          }
          
        
        }
          
        
      }
      
      
      //return metrics;
    }
    
    
    
    
    
    
    
}


