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
     *    out: For debugging purposes, in case we want to another way of
     *         printing the value in the page. 
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberOfUsers(PrintWriter out, Shepherd ms)
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
     *    out: For debugging purposes, in case we want to another way of
     *         printing the value in the page. 
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberOfEncounters(PrintWriter out, Shepherd ms)
    {
      int i;
      int j;
      /*Number of encounters */
      int numEncounters = ms.getNumEncounters(); //in aggregate
      this.encs.inc((double)numEncounters);

      //Num of Encounters by Specie
      //Epithet (specie) calling
      List<String> specieNames = ms.getAllTaxonomyNames();
      out.println("<p> Species List: "+specieNames+"</p>");

      //Genus call
      List<String> genuesNames = ms.getAllGenuses();
      out.println("<p> Genus List: "+genuesNames+"</p>");
      //Tokenizes Taxonomy to get genus and Epithet(specie)
      //Look at Taxonmomy object, getting list of Taxonomy getGenus getEpithet
      
      // for(i = 0; i< genuesNames.size(); i++){
      //   out.println("<p> All genues types: "+genuesNames.get(i)+"</p>");
      //     for(j = 0; j < specieNames.size(); j++){
      //       out.println("<p> All specie types: "+specieNames.get(j)+"</p>");
      //       ArrayList<Encounter> specieEncounterNums = ms.getAllEncountersForSpecies(genuesNames.get(i), specieNames.get(j));
      //       out.println("<p> Encounters for species: "+specieNames.get(j)+ ": " + specieEncounterNums + "</p>");
      //     }

      //       // ArrayList<Encounter> speciesEquusGrevyi = ms.getAllEncountersForSpecies(genuesNames.get(1), specieNames.get(1));
      //       // int specGrevyi = speciesEquusGrevyi.size();
      //       // ArrayList<Encounter> speciesPzGz = ms.getAllEncountersForSpecies(genuesNames.get(2), specieNames.get(2));
      //       // int specPzGz = speciesPzGz.size();
      //       // out.println("<p> Encounters for species: "+specieNames.get(0)+ ": " + specQuagga + "</p>");
      //       // out.println("<p> Encounters for species: "+specieNames.get(1)+ ": " + specQuagga + "</p>");
      //       // out.println("<p> Encounters for species: "+specieNames.get(2)+ ": " + specQuagga + "</p>");
      //       // int totalEncsSpecies = allEncSpecies.size();
      //       // this.encsSpecies.inc((double)totalEncsSpecies);
      //       // out.println("<p> Number of encounters by Species, for Species" +specieNames.get(j)+ "is: "+this.encsSpecies.get()+"</p>");
      // }

            // //Testing Code
            // List<Encounter> speciesEquusQuagga1 = ms.getAllEncountersForSpecies("Equus", "quagga");
            // int specQuagga = speciesEquusQuagga1.size();
            // out.println("<p> Species Equus Quagga Encounters Try 1: "+speciesEquusQuagga1+"</p>");
            // ArrayList<Encounter> speciesEquusQuagga2 = ms.getAllEncountersForSpecies("Equus", "grevyi");
            // out.println("<p> Species Equus Quagga Encounters Try 2: "+speciesEquusQuagga2+"</p>");
            // ArrayList<Encounter> speciesEquusQuagga3 = ms.getAllEncountersForSpecies("PzGz", "Hybrid");
            // out.println("<p> Species Equus Quagga Encounters Try 3: "+speciesEquusQuagga3+"</p>");

            //Actual Metrics
            List<Encounter> speciesEquusQuagga = ms.getAllEncountersForSpecies('Equus', 'quagga');
            int specEquusQuagga = speciesEquusQuagga.size();
            out.println("<p> Species Equus Quagga Encounters: "+specEquusQuagga+"</p>");
            this.encountersForSpecieEquusQuagga.inc((double)specEquusQuagga);

            List<Encounter> speciesEquusGrevyi = ms.getAllEncountersForSpecies('Equus', 'grevyi');
            int specEquusGrevyi = speciesEquusGrevyi.size();
            out.println("<p> Species Equus Grevyi Encounters: "+specEquusGrevyi+"</p>");
            this.encountersForSpecieEquusGrevyi.inc((double)specEquusGrevyi);

            ArrayList<Encounter> speciesPzGzHybrid = ms.getAllEncountersForSpecies('PzGz', 'hybrid');
            int specPzGzHybrid = speciesPzGzHybrid.size();
            out.println("<p> Species PzGz Hybrid Encounters: "+specPzGzHybrid+"</p>");
            this.encountersForSpeciePzGzHybrid.inc((double)specPzGzHybrid);


      //Number of Encounters by Submission Dates
      List<String> numEncountersSub = ms.getAllRecordedBy();
      int totalNumEncSub = numEncountersSub.size();

      //Number of Encounters by Location ID
      List<String> numEncountersLoc = ms.getAllLocationIDs();
      
      out.println("<p> Location List: "+numEncountersLoc+"</p>");

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
     *    out: For debugging purposes, in case we want to another way of
     *         printing the value in the page. 
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberOfIndividuals(PrintWriter out, Shepherd ms)
    {
      //Get num of Individuals by wildbook
      int numIndividuals = ms.getNumMarkedIndividuals();
      this.indiv.inc((double)numIndividuals);
    }
    
    /** setNumberOfMediaAssets
     * Sets the counters/gauges for metrics related to number of media assets
     * Parameters
     *    out: For debugging purposes, in case we want to another way of
     *         printing the value in the page. 
     *    ms: shepherd object for creating database transactions.
     */
    public void setNumberofMediaAssets(PrintWriter out, Shepherd ms)
    {
      //Media Assets by WildBook
      ArrayList<MediaAsset> numMediaAssetsWild = ms.getAllMediaAssetsAsArray();
      int totalNumMediaAssests = numMediaAssetsWild.size();
      this.numMediaAssetsWildbook.inc((double)totalNumMediaAssests);

      //TODO: Media Assets by Species

    }
    
    //Method for printing prometheus objects standardly 
    public void printMetrics(PrintWriter out)
    {
    out.println("<p>User Metrics</p>");
      out.println("<p> Number of users is: "+ (this.numUsersInWildbook.get())+"</p>"); 
      out.println("<p> Number of users with login is: "+(this.numUsersWithLogin.get())+"</p>");     
      out.println("<p> Number of users without login is: "+(this.numUsersWithoutLogin.get())+"</p>"); 
     
     out.println("<p>Encounter Metrics</p>");
      out.println("<p> Number of encounters by Wildbook is: "+(this.encs.get())+"</p>");

      out.println("<p> Number of encounters by Species Equus Quagga Encounters: " + (this.encountersForSpecieEquusQuagga.get()) + "<p>");
      out.println("<p> Number of encounters by Species Equus Grevyi Encounters: " + (this.encountersForSpecieEquusGrevyi.get()) + "<p>");
      out.println("<p> Number of encounters by Species PzGz Hybrid Encounters: " + (this.encountersForSpeciePzGzHybrid.get()) + "<p>");
   
      out.println("<p> Number of encounters by Location ID Kenya: " + (this.encountersForLocationKenya.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala: " + (this.encountersForLocationMpala.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala central: " + (this.encountersForLocationMpalacentral.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala.Central: " + (this.encountersForLocationMpala_Central.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala.North: " + (this.encountersForLocationMpala_North.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala.South: " + (this.encountersForLocationMpala_South.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID Mpala.central: " + (this.encountersForLocationMpala_central.get()) + "<p>");
      out.println("<p> Number of encounters by Location ID 01 Pejeta East: " + (this.encountersForLocation01Pejeta_East.get()) + "<p>");

    out.println("<p>Individual Metrics</p>");
      out.println("<p> Number of Individuals by Wildbook is: "+ (this.indiv.get())+"</p>"); 

    out.println("<p>Media Asset Metrics</p>");
      out.println("<p> Number of Media Assets by Wildbook: "+ (this.numMediaAssetsWildbook.get())+"</p>");
    }
    
    
    
}


