package org.ecocean.metrics;

import java.io.BufferedWriter;
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

import org.ecocean.Shepherd;
import org.ecocean.Encounter;
import org.ecocean.User;
import org.ecocean.media.MediaAsset;
import org.ecocean.media.MediaAssetSet;
import org.ecocean.MarkedIndividual;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

public class Prometheus
{
    /*Initialize variables*/
    private boolean pageVisited = false;  
    
    //Encounters by Wildbook Counter
    public Counter encs;

    //Encounter Species Counters
    public Counter encsSpecies;
    public Counter encountersForSpecieEquusQuagga;
    public Counter encountersForSpecieEquusGrevyi;
    public Counter encountersForSpeciePzGzHybrid;

    public Counter encsSubDate;

    //Encounter Location Counters
    public Counter encountersForLocationKenya;
    public Counter encountersForLocationMpala;
    public Counter encountersForLocationMpalacentral;
    public Counter encountersForLocationMpala_Central;
    public Counter encountersForLocationMpala_North;
    public Counter encountersForLocationMpala_South;
    public Counter encountersForLocationMpala_central;
    public Counter encountersForLocation01Pejeta_East;

    //Users Gauges
    public Gauge numUsersInWildbook; 
    public Gauge numUsersWithLogin;
    public Gauge numUsersWithoutLogin;

    //Media assets Gauge
    public Gauge numMediaAssetsWildbook;
    public Gauge numMediaAssetsSpecieEquusQuagga;
    public Gauge numMediaAssetsSpecieEquusGrevyi;
    public Gauge numMediaAssetsSpeciePzGzHybrid;
    
    
    //individuals Gauge
    public Gauge indiv;
    
    //Default constructor
    public Prometheus()
    {
      //register all metrics
      encsSpecies = Counter.build().name("wildbook_encounters_by_specie")
        .help("Number encounters by Specie").register();
      encsSubDate = Counter.build().name("wildbook_encounters_by_date")
        .help("Number encounters by Submission Date").register();

      //Specie Counters
      encountersForSpecieEquusQuagga = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_Quagga")
        .help("Number encounters by Specie type Equus Quagga").register();
      encountersForSpecieEquusGrevyi = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_Grevyi")
        .help("Number encounters by Specie type Equus Grevyi").register();
      encountersForSpeciePzGzHybrid = Counter.build().name("wildbook_encounters_by_Specie_Type_Equus_PzGz_Hybrid")
        .help("Number encounters by Specie type PzGz Hybrid").register();

      //Location Counters
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
      
      indiv = Gauge.build().name("wildbook_individual_wildbook")
        .help("Number individuals by Wildbook").register();
      encs = Counter.build().name("wildbook_encounters")
        .help("Number encounters").register();
      numUsersInWildbook = Gauge.build().name("wildbook_users")
        .help("Number users").register();
      numUsersWithLogin = Gauge.build().name("wildbook_users_w_login")
        .help("Number users with Login").register();
      numUsersWithoutLogin = Gauge.build().name("wildbook_users_wout_login")
        .help("Number users without Login").register();
      numMediaAssetsWildbook = Gauge.build().name("wildbook_mediaassets_wild")
        .help("Number of Media Assets by Wildbook").register();

      numMediaAssetsSpecieEquusQuagga = Gauge.build().name("equusQuagga_mediaassets")
        .help("Number of Media Assets by Specie Equus Quagga").register();
      numMediaAssetsSpecieEquusGrevyi = Gauge.build().name("equusGrevyi_mediaassets")
        .help("Number of Media Assets by Specie Equus Grevyi").register();
      numMediaAssetsSpeciePzGzHybrid = Gauge.build().name("pzgzHybrid_mediaassets")
        .help("Number of Media Assets by Specie PzGz Hyrbid").register();
    }

    //Unit test constructor
    public Prometheus(boolean isTesting)
    {
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
      numMediaAssetsWildbook = Gauge.build().name("wildbook_mediaassets_wild")
        .help("Number of Media Assets by Wildbook").create();
      
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
    public void setNumberOfUsers(Shepherd ms)
    {
      //Getting number of users by wildbook
      int numUsers = ms.getNumUsers();
      this.numUsersInWildbook.set((double)numUsers);

      //get number of users w/ login privileges
      List<User> numUsersUsername = ms.getUsersWithUsername();
      int totalNumUsersUsername = numUsersUsername.size();
      this.numUsersWithLogin.set((double)totalNumUsersUsername);

      //get number of users w/out login privileges
      int totalNumUserNoLogin = (numUsers-totalNumUsersUsername);
      this.numUsersWithoutLogin.set((double)totalNumUserNoLogin);

      //TODO: Set number of active users
    }
    
    /** setNumberOfEncounters
     * Sets the counters/gauges for metrics related to number of encounters
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberOfEncounters(Shepherd ms)
    {
      int i;
      int j;
      /*Number of encounters */
      int numEncounters = ms.getNumEncounters(); //in aggregate
      this.encs.inc((double)numEncounters);

      //Num of Encounters by Specie
      List<Encounter> speciesEquusQuagga = ms.getAllEncountersForSpecies("Equus", "quagga");
      int specEquusQuagga = speciesEquusQuagga.size();
      this.encountersForSpecieEquusQuagga.inc((double)specEquusQuagga);

      List<Encounter> speciesEquusGrevyi = ms.getAllEncountersForSpecies("Equus", "grevyi");
      int specEquusGrevyi = speciesEquusGrevyi.size();
      this.encountersForSpecieEquusGrevyi.inc((double)specEquusGrevyi);

      ArrayList<Encounter> speciesPzGzHybrid = ms.getAllEncountersForSpecies("PzGz", "hybrid");
      int specPzGzHybrid = speciesPzGzHybrid.size();
      this.encountersForSpeciePzGzHybrid.inc((double)specPzGzHybrid);


      //Number of Encounters by Submission Dates
      //TODO: Time Series Item, this method is not complete
      List<String> numEncountersSub = ms.getAllRecordedBy();
      int totalNumEncSub = numEncountersSub.size();

      //Number of Encounters by Location ID
      //Getting location ID
      List<String> numEncountersLoc = ms.getAllLocationIDs();

      int totalNumEncsByLocKenya = ms.getNumEncounters(numEncountersLoc.get(1));
            this.encountersForLocationKenya.inc((double)totalNumEncsByLocKenya);

      int totalNumEncsByLocMpala = ms.getNumEncounters(numEncountersLoc.get(2));
            this.encountersForLocationMpala.inc((double)totalNumEncsByLocMpala);

      int totalNumEncsByLocMpalacentral = ms.getNumEncounters(numEncountersLoc.get(3));
            this.encountersForLocationMpalacentral.inc((double)totalNumEncsByLocMpalacentral);

      int totalNumEncsByLocMpala_Central = ms.getNumEncounters(numEncountersLoc.get(4));
            this.encountersForLocationMpala_Central.inc((double)totalNumEncsByLocMpala_Central);

      int totalNumEncsByLocMpala_North = ms.getNumEncounters(numEncountersLoc.get(5));
            this.encountersForLocationMpala_North.inc((double)totalNumEncsByLocMpala_North);

      int totalNumEncsByLocMpala_South = ms.getNumEncounters(numEncountersLoc.get(6));
            this.encountersForLocationMpala_South.inc((double)totalNumEncsByLocMpala_South);

      int totalNumEncsByLoc_central = ms.getNumEncounters(numEncountersLoc.get(7));
            this.encountersForLocationMpala_central.inc((double)totalNumEncsByLoc_central);

      int totalNumEncsByLoc01Pejeta_East = ms.getNumEncounters(numEncountersLoc.get(8));
            this.encountersForLocation01Pejeta_East.inc((double)totalNumEncsByLoc01Pejeta_East);

    }
    
    /** setNumberOfIndividuals
     * Sets the counters/gauges for metrics related to number of individuals
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberOfIndividuals(Shepherd ms)
    {
      //Get num of Individuals by wildbook
      int numIndividuals = ms.getNumMarkedIndividuals();
      this.indiv.inc((double)numIndividuals);
    }
    
    /** setNumberOfMediaAssets
     * Sets the counters/gauges for metrics related to number of media assets
     * Parameters
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberofMediaAssets(Shepherd ms)
    {
      //Media Assets by WildBook
      ArrayList<MediaAsset> numMediaAssetsWild = ms.getAllMediaAssetsAsArray();
      int totalNumMediaAssests = numMediaAssetsWild.size();
      this.numMediaAssetsWildbook.inc((double)totalNumMediaAssests);

      //Media Assets by Specie
      Long mediaAssetsEquusQuagga = ms.countMediaAssetsBySpecies("Equus", "quagga", ms);
      this.numMediaAssetsSpecieEquusQuagga.inc((double)mediaAssetsEquusQuagga);

      Long mediaAssetsEquusGrevyi = ms.countMediaAssetsBySpecies("Equus", "grevyi", ms);
      this.numMediaAssetsSpecieEquusGrevyi.inc((double)mediaAssetsEquusGrevyi);

      Long mediaAssetsPzGzHybrid = ms.countMediaAssetsBySpecies("PzGz", "hybrid", ms);
      this.numMediaAssetsSpeciePzGzHybrid.inc((double)mediaAssetsPzGzHybrid);

    }      
    
}


