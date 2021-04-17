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

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Counter;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.common.TextFormat;

public class Prometheus
{
    /*Initialize variables*/
    private boolean pageVisited = false;  
    
    //Encounters
    public Counter encs;
    public Counter encsSpecies;
    public Counter encsSubDate;
    public Counter encsLocation;
    public Counter encsWildBook;

    //Users
    public Gauge numUsersInWildbook; 
    public Gauge numUsersWithLogin;
    public Gauge numUsersWithoutLogin;

    //Media assets
    public Gauge numMediaAssetsWildbook;
    
    //individuals
    public Gauge indiv;
    
    //Default constructor
    public Prometheus()
    {
      //register all metrics
      encsSpecies = Counter.build().name("wildbook_encounters_by_specie")
        .help("Number encounters by Specie").register();
      encsSubDate = Counter.build().name("wildbook_encounters_by_date")
        .help("Number encounters by Submission Date").register();
      encsLocation = Counter.build().name("wildbook_encounters_by_Location")
        .help("Number encounters by Location ID").register();
      indiv = Gauge.build().name("wildbook_individual_wildbook")
        .help("Number individuals by Wildbook").register();
      encs = Counter.build().name("wildbook_encounters")
        .help("Number encounters").register();
      encsWildBook = Counter.build().name("wildbook_encounters_wildbook")
        .help("Number encounters by Wildbook").register();
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
      encsLocation = Counter.build().name("wildbook_encounters_by_Location")
        .help("Number encounters by Location ID").create();
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
      //get the data from the database
      /*Number of encounters */
      int numEncounters = ms.getNumEncounters(); //in aggregate
      this.encs.inc((double)numEncounters);

      //Num of Encounters by Wildbook
      Vector numEncoutnersTotal = ms.getAllEncountersNoFilterAsVector();
      int numEncountersWild = numEncoutnersTotal.size();
      this.encsWildBook.inc((double)numEncountersWild);

      //Num of Encounters by Specie
      //Epithet (specie) calling
      List<String> specieNames = ms.getAllTaxonomyNames();
      out.println("<p> Species List: "+specieNames+"</p>");

      //Genus call
      List<String> genuesNames = ms.getAllGenuses();
      out.println("<p> Genus List: "+genuesNames+"</p>");
      //Tokenizes Taxonomy to get genus and Epithet(specie)
      //Look at Taxonmomy object, getting list of Taxonomy getGenus getEpithet
      
      for(i = 0; i< genuesNames.size(); i++){
        out.println("<p> All genues types: "+genuesNames.get(i)+"</p>");
          for(j = 0; j < specieNames.size(); j++){
            out.println("<p> All specie types: "+specieNames.get(j)+"</p>");
            ArrayList<Encounter> speciesEquusQuagga1 = ms.getAllEncountersForSpecies(genuesNames.get(1), specieNames.get(0));
            // int specQuagga = speciesEquusQuagga.size();
            out.println("<p> Species Equus Quagga Encounters Try 1: "+speciesEquusQuagga1+"</p>");
            ArrayList<Encounter> speciesEquusQuagga2 = ms.getAllEncountersForSpecies(genuesNames.get(1), specieNames.get(1));
            out.println("<p> Species Equus Quagga Encounters Try 2: "+speciesEquusQuagga2+"</p>");
            ArrayList<Encounter> speciesEquusQuagga3 = ms.getAllEncountersForSpecies(genuesNames.get(1), specieNames.get(2));
            out.println("<p> Species Equus Quagga Encounters Try 3: "+speciesEquusQuagga3+"</p>");
            // ArrayList<Encounter> speciesEquusGrevyi = ms.getAllEncountersForSpecies(genuesNames.get(1), specieNames.get(1));
            // int specGrevyi = speciesEquusGrevyi.size();
            // ArrayList<Encounter> speciesPzGz = ms.getAllEncountersForSpecies(genuesNames.get(2), specieNames.get(2));
            // int specPzGz = speciesPzGz.size();
            // out.println("<p> Encounters for species: "+specieNames.get(0)+ ": " + specQuagga + "</p>");
            // out.println("<p> Encounters for species: "+specieNames.get(1)+ ": " + specQuagga + "</p>");
            // out.println("<p> Encounters for species: "+specieNames.get(2)+ ": " + specQuagga + "</p>");
            // int totalEncsSpecies = allEncSpecies.size();
            // this.encsSpecies.inc((double)totalEncsSpecies);
            // out.println("<p> Number of encounters by Species, for Species" +specieNames.get(j)+ "is: "+this.encsSpecies.get()+"</p>");
          }
      }

      //Number of Encounters by Submission Dates
      List<String> numEncountersSub = ms.getAllRecordedBy();
      int totalNumEncSub = numEncountersSub.size();
      // for(String dataSub : numEncountersSub){
      //     ArrayList<Encounter> numOfEncounters = ms.getMostRecentIdentifiedEncountersByDate(dataSub);
      //     for(i = 0; i < totalNumEncSub; i++){  
      //         this.encsSubDate.inc((double)numOfEncounters);
      //          out.println("<p> Number of encounters by Submission Date is: "+this.encsSubDate.get(i)+"</p>");
      //     }
      // }
      // for(i = 0; i < totalNumEncSub; i++){
      //   ArrayList<Encounter> totalNumSub = ms.getMostRecentIdentifiedEncountersByDate(numEncountersSub.get(i));
      //   this.encsSubDate.inc((double)totalNumEncSub);
      //   out.println("<p> Number of encounters by Submission Date: " +numEncountersSub.get(i)+ "is: "+this.encsSubDate.get()+"</p>");
      // }

      //Number of Encounters by Location ID
      List<String> numEncountersLoc = ms.getAllLocationIDs();
      int totalNumLoc = numEncountersLoc.size();
      // this.encsLocation.inc((double));
      // PrintWriter output;
      for(i = 0; i < totalNumLoc; i++){
          int totalNumByLoc = ms.getNumEncounters(numEncountersLoc.get(i));
          this.encsLocation.inc((double)totalNumByLoc);
          out.println("<p> Number of encounters by Location ID" +numEncountersLoc.get(i)+ "is: "+this.encsLocation.get()+"</p>");
      }
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

      //Media Assets by Specie
      // int i;
      // MediaAssetSet numMediaAssetsSpecie = ms.getMediaAssetSet();
      // int sizeOfSets = numMediaAssetsSpecie.size();
      // int[] numSpeciesAssetsArray = new int[sizeOfSets];

      // for(i = 0; i < sizeOfSets; i++){
      //   numSpeciesAssetsArray = Integer.parseInt(numMediaAssetsSpecie);
      //    // int numSpeciesAssets = Integer.parseInt(numMediaAssetsSpecie);
      // }
            // List<String> specieNamesMedia = ms.getAllTaxonomyNames();
            // for(string mediaNames : specieNamesMedia){
            //   ArrayList<MediaAsset> mediaAssestBySpeciesList = ms.getAllMediAssetsWithKeyword(mediaNames);
            // }

    }
    
    //Method for printing prometheus objects standardly 
    public void printMetrics(PrintWriter out)
    {
    out.println("<p>User Metrics</p>");
      out.println("<p> Number of users is: "+ (this.numUsersInWildbook.get())+"</p>"); 
      out.println("<p> Number of users with login is: "+(this.numUsersWithLogin.get())+"</p>");     
      out.println("<p> Number of users without login is: "+(this.numUsersWithoutLogin.get())+"</p>"); 
     
     out.println("<p>Encounter Metrics</p>");
      out.println("<p> Number of encounters is: "+(this.encs.get())+"</p>");
      out.println("<p> Number of encounters by wildbook is: "+(this.encsWildBook.get())+"</p>");
      // out.println("<p> Number of encounters by Submission Date is: "+this.encsSubDate.get()+"</p>");
      // out.println("<p> Number of encounters by Location ID is: "+this.encsLocation.get()+"</p>");

    out.println("<p>Individual Metrics</p>");
      out.println("<p> Number of Individuals by Wildbook is: "+ (this.indiv.get())+"</p>"); 

    out.println("<p>Media Asset Metrics</p>");
      out.println("<p> Number of Media Assets by Wildbook: "+ (this.numMediaAssetsWildbook.get())+"</p>");
    }
    
    
    
}


