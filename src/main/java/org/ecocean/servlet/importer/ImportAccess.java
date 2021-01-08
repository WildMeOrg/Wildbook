package org.ecocean.servlet.importer;

import org.ecocean.*;
import org.ecocean.genetics.BiologicalMeasurement;
import org.ecocean.genetics.SexAnalysis;
import org.ecocean.genetics.TissueSample;
import org.ecocean.servlet.*;
import org.ecocean.tag.SatelliteTag;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.ecocean.identity.*;
import org.ecocean.media.*;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;


import org.json.JSONObject;

import com.healthmarketscience.jackcess.Column;
import com.healthmarketscience.jackcess.Database; 
import com.healthmarketscience.jackcess.DatabaseBuilder;
import com.healthmarketscience.jackcess.Row;
import com.healthmarketscience.jackcess.Table;

import org.json.JSONArray;

import java.io.*;
import java.math.BigDecimal;

/// DEPRECATED!!! This importer is not compatible with the new naming scheme and must be updated
public class ImportAccess extends HttpServlet {
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static PrintWriter out;
  private static String context;

  // Hack. TODO: remove?
  private static boolean runOncePercompile = false;

  private static boolean committing = true; // for developing w/o mucking up database
  

  private String testIndId = "OM00-003";
  private Map<String,Integer> testIndEncounterCodes = new HashMap<String,Integer>();

  // Okay, we might need to build a hashmap out of every line in this table, so we can create multiple encounters 
  // for the date/sighting number pairs that occure multiple times. 
  HashMap<String,Integer> duplicatePairsMap = new HashMap<String,Integer>();
  ArrayList<String> failedEncs = new ArrayList<String>();

  // so we can bounce objects around between helper methods w/o worrying about shepherd permanence so much
  Map<String,MarkedIndividual> generatedIndividuals = new HashMap<String, MarkedIndividual>();
  Map<String,Encounter> generatedEncounters = new HashMap<String, Encounter>();
  Map<String,Occurrence> generatedOccurrences = new HashMap<String, Occurrence>();


  private String photoLocation;

  Taxonomy humpbackTax;

  private final String profileKwName = "ProfilePhoto"; // sadly a soft-standard used on wildbook
  private Keyword profilePicKeyword;

  private AssetStore astore;
  public static final String DEFAULT_ASSETSTORE_NAME = "Oman-Asset-Store";

  private List<String> missingPhotos = new ArrayList<String>();

  Integer rowLimitForTesting;

  private int numEncountersSharedByTables=0;
  private int numIndividualsSharedByTables=0;
  private int numOccurrencesSharedByTables=0;

  // this is to double-check that we're finding "left dorsal"-tagged annots/mediaassets
  private int numTaggedAnnotsFoundAgain=0;
  private int numTaggedRDAnnotsFoundAgain=0;
  private int numTaggedLDAnnotsFoundAgain=0;

  // myShepherd.getKeyword() is expensive so this saves some time
  private Keyword flukeKeyword;
  private Keyword rightFinKeyword;
  private Keyword leftFinKeyword;
  private Keyword backKeyword;
  private int numUniqueKeywords = 0;

  private void commitIndividuals(Shepherd myShepherd) {
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    for (MarkedIndividual indy : generatedIndividuals.values()) {
      indy.setSexFromEncounters();
      indy.setTaxonomyFromEncounters();
      if (!myShepherd.isMarkedIndividual(indy.getIndividualID())) {
        myShepherd.storeNewMarkedIndividual(indy);
      }
    }
  }
  private void commitEncounters(Shepherd myShepherd) {
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();

    for (Encounter enc : generatedEncounters.values()) {
      if (enc.getDateInMilliseconds()==null) enc.setDateFromAssets();
      if (enc.getDecimalLatitude()==null) enc.setLatLonFromAssets();

      for (Annotation ann: enc.getAnnotations()) {
        try {
          MediaAsset ma = ann.getMediaAsset();
          if (ma!=null) {
            myShepherd.storeNewAnnotation(ann);
            ma.setMetadata();
            ma.updateStandardChildren(myShepherd);
          }
        }
        catch (Exception e) {
          System.out.println("EXCEPTION on annot/ma persisting on enc "+enc.getCatalogNumber());
        }
      }

      if (!myShepherd.isEncounter(enc.getCatalogNumber())) {
        myShepherd.storeNewEncounter(enc);
      }
    }
  }
  private void commitOccurrences(Shepherd myShepherd) {
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    for (Occurrence indy : generatedOccurrences.values()) {
      indy.setLatLonFromEncs();
      if (indy.getDateTimeLong()==null) indy.setDateFromEncounters();
      if (!myShepherd.isOccurrence(indy.getOccurrenceID())) {
        myShepherd.storeNewOccurrence(indy);
      }
    }
  }


  // // I find AssetStores confusing, hence the convenience method
  // private LocalAssetStore  getLocalAssetStore(Shepherd myShepherd) {
  //   return getLocalAssetStore(DEFAULT_ASSETSTORE_NAME, myShepherd);
  // }

  private AssetStore  getAssetStore(Shepherd myShepherd) {

    //return AssetStore.getDefault(myShepherd);
    return AssetStore.get(myShepherd, 1);

    // String assetStorePath="/var/lib/tomcat7/webapps/wildbook_data_dir";
    // // TODO: fix this for flukebook
    // // String assetStoreURL="http://flukebook.wildbook.org/wildbook_data_dir";
    // String assetStoreURL="http://54.71.122.188/wildbook_data_dir";

    // AssetStore as = new LocalAssetStore("Oman Import", new File(assetStorePath).toPath(), assetStoreURL, true);


    // if (committing) {
    //   myShepherd.beginDBTransaction();
    //   myShepherd.getPM().makePersistent(as);
    //   myShepherd.commitDBTransaction();
    //   myShepherd.beginDBTransaction();
    // }

    // return as;
    
  }

  private static <T> void addToCount(Map<T,Integer> countMap, T elem) {
    if (countMap.containsKey(elem)) countMap.put(elem, (countMap.get(elem)+1));
    else countMap.put(elem, 1);
  }

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }


  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

    if (runOncePercompile) return;
    //else runOncePercompile = true;

    rowLimitForTesting = 20000;
    missingPhotos = new ArrayList<String>();


    context = ServletUtilities.getContext(request);
    out = response.getWriter();

    committing =  (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false")); //false by default

    boolean skipSightingsHistory = false;
    boolean skipIDPhotos = false;

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.beginDBTransaction();
    myShepherd.setAction("AccessImport.class");
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      out.println("-- Wildbook not initialized. Starting Wildbook. --");
      StartupWildbook.initializeWildbook(request, myShepherd);
    }
    myShepherd.commitDBTransaction();
    myShepherd.closeDBTransaction();
    myShepherd.beginDBTransaction();

    profilePicKeyword = myShepherd.getOrCreateKeyword("ProfilePhoto");
    humpbackTax = myShepherd.getOrCreateTaxonomy("Megaptera novaeangliae");


    //String dbName = "omanData2017.07.04.mdb";
    String dbName = "OmanHumpbackPhotoID-2018-06-04-OLD-VersionNewData.mdb";    
    if (request.getParameter("file") != null) dbName = request.getParameter("file");
    
    String dbLocation = "/data/oman_import/";
    if (request.getParameter("location") != null) dbLocation = request.getParameter("location");

    photoLocation = "/data/oman_import/photos";

    if ((request.getParameter("committing")!=null) && !request.getParameter("committing").equals("false")) committing = true; // unsets default false value

    Database db = null;  
    try {
      db =  DatabaseBuilder.open(new File(dbLocation + dbName));
    } catch (Exception e) {
      e.printStackTrace();
      System.out.println("Error grabbing the .mdb file to process.");
    }
    
    
    // Displaying full html with some jsp templating
    ServletUtilities.importJsp("header.jsp", request, response);
    // for alignment after header
    out.println("<div class=\"container\"><div class=\"row\" style=\"margin-top:50px\">");

    out.println("***** Beginning Access Database Import. DB name = "+dbName+"*****\n");
    
    Set<String> tables = db.getTableNames();
    out.println("********************* Here's the tables : "+tables.toString()+"\n");
    

    numEncountersSharedByTables=0;

    flukeKeyword = myShepherd.getOrCreateKeyword("Tail Fluke");
    leftFinKeyword = myShepherd.getOrCreateKeyword("Left Dorsal Fin");
    rightFinKeyword = myShepherd.getOrCreateKeyword("Right Dorsal Fin");
    backKeyword = myShepherd.getOrCreateKeyword("Back");
    out.println("-- Got three keywords: "+flukeKeyword+", "+leftFinKeyword+", "+rightFinKeyword);
    numUniqueKeywords = 0;

    // possibly committing the newly created keywords above
    if (committing) {
    	myShepherd.commitDBTransaction();
    	myShepherd.beginDBTransaction();
    }


    if (!skipIDPhotos) {
      out.println("<div>");
      out.println("<h2> Committing = "+committing+"</h2>");
      out.println("<h3> Processing IDPhotos table</h3>");
      out.println("<p>");
      if (!tables.contains("IDPhotos")) throw new IOException("Formatting Exception: No IDphotos table!");
      Table idPhotos = db.getTable("IDPhotos");

      out.println("Table present: "+tables.contains("IDPhotos")+" about to call processIDPhotosTable.");
      out.println("</p>");
      boolean idPhotosDone = processIDPhotosTable(idPhotos, out, myShepherd);
      out.println("<p>Done with idPhotos. Result = "+idPhotosDone+"</p>");
      out.println("</div>");
    }

    if (!skipSightingsHistory) {
      out.println("<div>");
      out.println("<p>");
      if (!tables.contains("SightingHistory")) throw new IOException("Formatting Exception: No SightingHistory table!");
      out.println("<h3> Processing SightingHistory table</h3>");
      out.println("Table present: "+tables.contains("SightingHistory"));
      Table sightingHistory = db.getTable("SightingHistory");
      boolean sightingHistoryDone = processSightingHistoryTable(sightingHistory, out, myShepherd);
      out.println("</p>");
      out.println("</div>");
    }

    out.println("<div>");    
    out.println("<p> Number encounters shared by tables: "+numEncountersSharedByTables+"</p>");
    out.println("<p> Number individuals shared by tables: "+numIndividualsSharedByTables+"</p>");
    out.println("<p> Number occurrences shared by tables: "+numOccurrencesSharedByTables+"</p>");
    out.println("</div>");    

    out.println("<div>");
    out.println("<p> Number of fluke-tagged annots given quality scores: "+numTaggedAnnotsFoundAgain+"</p>");
    out.println("<p> Number of Right-dorsal-tagged annots given quality scores: "+numTaggedRDAnnotsFoundAgain+"</p>");
    out.println("<p> Number of Left-dorsal-tagged annots given quality scores: "+numTaggedLDAnnotsFoundAgain+"</p>");


    out.println("<p> Number of unique, fresh Keywords created: "+numUniqueKeywords+"</p>");

    out.println("</div>");    



    out.println("<div>");    
    out.println("<h3>Looking at the Encounter codes for individual "+testIndId+"</h3><ul>");
    int totalEncsOfTestInd = 0;
    List<String> testIndEncCodesSorted = new ArrayList<String>(testIndEncounterCodes.keySet());
    for (String entry: testIndEncounterCodes.keySet()) {
      out.println("<li>"+entry+": "+testIndEncounterCodes.get(entry)+"</li>");
      totalEncsOfTestInd += testIndEncounterCodes.get(entry);
    }
    out.println("</ul><p>"+totalEncsOfTestInd+" total rows covering "+testIndEncounterCodes.size()+" total encounters</p></div>");    



    if (committing) {

      out.println("<div>");
      out.println("<p> Whoah! We're going to commit!</p>");

      out.println("<p> Committing encs...</p>");
      commitEncounters(myShepherd);
      out.println("<p> Committing inds...</p>");
      commitIndividuals(myShepherd);
      out.println("<p> Committing occs...</p>");
      commitOccurrences(myShepherd);  

      myShepherd.commitDBTransaction();


      out.println("<p> Donezo spumoni</p>");
      out.println("</div>");
    


      out.println("<div>");
      out.println("<p> Because we've committed, going to print out some sample objects. spumoni</p>");
      String exEncId = null;
      String exEncId2 = null;

      for (Encounter enc: generatedEncounters.values()) {
        if (enc.getAnnotations()!=null && enc.getAnnotations().size()>0) {
          if (exEncId==null) exEncId = enc.getCatalogNumber();
          else {
            exEncId2 = enc.getCatalogNumber();
            break;
          }
        }
      }
      out.println("<div><a href=encounters/encounter.jsp?number="+exEncId+" > Example encounter page</a>");
      out.println("<a href=obrowse.jsp?type=Encounter&id="+exEncId+" > Example obrowse page</a></div>");


      out.println("<div><a href=encounters/encounter.jsp?number="+exEncId2+" > Example encounter page</a>");
      out.println("<a href=obrowse.jsp?type=Encounter&id="+exEncId2+" > Example obrowse page</a></div>");


      out.println("</div>");
    



    }
    myShepherd.closeDBTransaction();
    db.close(); 
    // for alignment after header
    out.println("</div></div>");
    ServletUtilities.importJsp("footer.jsp", request, response);
  }  
  

  private boolean processIDPhotosTable(Table table, java.io.PrintWriter out, Shepherd myShepherd) throws IOException {

  	out.println("<p>IDPhotosTable: processing IDPhotos table.</p>");
    ArrayList<String> columnMasterList = getColumnMasterList(table);
    int success = 0;
    out.println("<p>IDPhotos Table has "+table.getRowCount()+" Rows!</p>");
    printTable(table, out);
    out.println("<p>IDPhotos Table is done printing now</p>");

   System.out.println("    PROCIDPHOTOS: getting asset store");
    if (astore==null) astore = getAssetStore(myShepherd);
   System.out.println("    PROCIDPHOTOS: got asset store "+astore);
   out.println("  <p> PROCIDPHOTOS: got asset store "+astore+"</p>");

    Row thisRow = null;
    int printPeriod = 1; // how often to print certain log statements

    boolean doOnceOnlyForTesting = true;
    int rowNum = 0;
   System.out.println("    PROCIDPHOTOS: entering row loop");
   out.println("  <p> entering row loop</p>");

    for (;rowNum<table.getRowCount()&&rowLimitForTesting!=null&&rowNum<rowLimitForTesting;rowNum++) {
      boolean printing = true;//((rowNum%printPeriod)==0);
      if (printing) {
        System.out.println("    PROCIDPHOTOS: beginning row "+rowNum+".");
      }

      // forgive me, god
      // if (i==1188) {
      //   System.out.println("Skipping sighting history row "+getStringSafe(thisRow,"RecID"));
      //   continue
      //forgive me, god
      // if (rowNum>562&&rowNum<600) {
      //   System.out.println("Skipping IDPhoto row "+rowNum);
      //   continue;
      // }

      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("\n!!!!!!!!!!!!!! Could not get next Row in IDPhotos table on row "+rowNum+"...\n");
      }
      if (printing) System.out.println("---------PROCIDPHOTOS: about to process row "+rowNum);
            if (thisRow == null) {
        System.out.println("WTF!? A null row is in the table. Row #"+rowNum);
        continue;
      }


      processIDPhotosRow(thisRow, myShepherd, columnMasterList, astore, printing, out);
      if (printing) System.out.println("---------PROCIDPHOTOS: Done with row "+rowNum+".");
    } 
    System.out.println("    PROCIDPHOTOS: Done with row Processing");
    int numMissingPhotos = missingPhotos.size();
    out.println("<p>IDPhotos Table is done processing <strong>"+rowNum+" rows</strong>. <strong>"+(rowNum-numMissingPhotos)+" photos found</strong> and <strong>"+numMissingPhotos+" missing photos</strong>.</p>");
    System.out.println("IDPhotos Table is done processing "+rowNum+" rows. "+(rowNum-numMissingPhotos)+" photos found and "+numMissingPhotos+" missing photos.");

    out.println("<p>We now have <strong>"+generatedEncounters.size()+" Encounters,. "+generatedIndividuals.size()+" Individuals, and "+generatedOccurrences.size()+" Occurrences.</strong></p>");


    out.println("<p>Printing missing photos! <ul>");
    for (String missingPhoto : missingPhotos) {
      out.println("<li>"+missingPhoto+"</li>");
    }
    out.println("</ul></p>!");

  	return true;
  }


  private boolean processIDPhotosRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList, AssetStore astore, boolean print, PrintWriter out) {
    // have to manually enable comitting (ie no committing arg = no committing)
    return processIDPhotosRow(thisRow,myShepherd,columnMasterList,astore,false,print,out);
  }
  
  private boolean processIDPhotosRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList, AssetStore astore, boolean commit, boolean print, PrintWriter out) {

    String occID = occurrenceCodeForIDPhotoRow(thisRow);

    Annotation ann = getAnnotationForIDPhotoRow(thisRow, astore, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got Annotation "+ann);

    Encounter enc = getEncounterForIDPhotoRow(thisRow, ann, occID, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got Encounter "+enc);

    Occurrence occ = getOccurrenceForIDPhotoRow(thisRow, occID, enc, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got Occurrence "+occ);

    MarkedIndividual indy = getIndividualForIDPhotoRow(thisRow, enc, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got individual "+indy);

    if (committing) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

    if (print && committing) {
      out.println("<li> processIDPhotosRow got Encounter "+Encounter.getWebUrl(enc.getCatalogNumber(),"http://34.208.139.53")+"</li>");
    }

    return true;
  }

  // the following getters are helper functions for the table processors
  private MarkedIndividual getIndividualForIDPhotoRow(Row thisRow, Encounter enc, Shepherd myShepherd) {


    MarkedIndividual indy = null;
    String indID = thisRow.getString("Individual_id");
    if (indID == null) return indy;

    System.out.println("    PROCIDPHOTOS got indID " +indID);
    if (generatedIndividuals.containsKey(indID)) {
      System.out.println("    PROCIDPHOTOS generatedIndividuals contains our boy");
      indy = generatedIndividuals.get(indID);
      System.out.println("    PROCIDPHOTOS got our boy "+indy+" ("+indy.totalEncounters()+" encs).");
      if (enc!=null) indy.addEncounterNoCommit(enc);
      System.out.println("    PROCIDPHOTOS ... and added our boy's encounter ("+indy.totalEncounters()+" encs).");

    } else {
      System.out.println("    PROCIDPHOTOS making a new individual");
      indy = new MarkedIndividual(indID, enc);
      generatedIndividuals.put(indID, indy);
    }

    // TODO: process indy?

    return indy;
  }
  
  private Encounter getEncounterForIDPhotoRow(Row thisRow, Annotation ann, String occID, Shepherd myShepherd) {
    
    System.out.println("    PROCIDPHOTOS starting getEncounter ");

    String encCode = getEncounterCodeForIDPhotoRow(thisRow);
    Encounter enc = null;
    String indID = thisRow.get("Individual_id").toString();
    boolean newObj = false;

    System.out.println("    PROCIDPHOTOS got encCode " +encCode);
    if (generatedEncounters.containsKey(encCode)) {
      System.out.println("    PROCIDPHOTOS generatedEncounters contains our boy");
      enc = generatedEncounters.get(encCode);
      System.out.println("    PROCIDPHOTOS got our enc "+enc);

    } else {
      System.out.println("    PROCIDPHOTOS making a new encounter");
      // commented out bc of mystifying bug on 565th row. Shouldn't matter if this runs *before* StandardImport
      // enc = myShepherd.getEncounterByIndividualAndOccurrence(indID, occID);
      // if (enc!=null) {
      //   System.out.println("    PROCIDPHOTOS wait! we got the enc from shep!");
      // } else {
        enc = new Encounter(false);
        System.out.println(" we're adding to generatedEncounters!");
        generatedEncounters.put(encCode, enc);
        newObj = true;
      //}
    }
    if (ann!=null) {
      enc.addAnnotation(ann);
      System.out.println("    PROCIDPHOTOS ... and loaded our boy's annotation");
    } else {
      System.out.println("    Nothing to add!");
    }
      // System.out.println("    PROCIDPHOTOS making a new encounter");
      // enc = myShepherd.getEncounterByIndividualAndOccurrence(indID, occID);
      // if (enc!=null) {
      //   System.out.println("    PROCIDPHOTOS wait! we got the enc from shep!");
      //   if (ann!=null) {
      //     System.out.println("    Adding annotation!");
      //     enc.addAnnotation(ann);
      //     System.out.println("    We added annotation!");
      //   } else {
      //     System.out.println("    Nothing to add!");
      //   }
      // } else {
      //   if (ann!=null) {
      //     enc = new Encounter(ann);
      //   } else {
      //     enc = new Encounter(false);
      //   }
      // }

    System.out.println(" we're continuing onward!");

    // Parse the various columns
    System.out.println("    PROCIDPHOTOS Now to load the columns of the table into our boy");
    

    System.out.println("PROCIDPHOTOS about to set date");
    Date encTime = null;
    try {encTime = thisRow.getDate("Date");}
    catch (Exception e) {
      try {encTime = new Date(thisRow.get("Date").toString());}
      catch (Exception f) {System.out.println("PROCIDPHOTOS about to set date");}
    }
    if (encTime!=null) {
      enc.setDateInMilliseconds(encTime.getTime());
    }
    System.out.println("    PROCIDPHOTOS set date to "+encTime);

    System.out.println("    ** PROCIDPHOTOS about to set verbatim date");
    String dateString = getStringSafe(thisRow, "Date");
    if (dateString!=null) enc.setVerbatimEventDate(dateString);


    enc.setCountry("Oman");
    enc.setSubmitterName("Oman Photo ID Catalog Bulk Import");
    enc.setSubmitterID("ESO");


    String project = thisRow.get("Project_code").toString();
    if (project!=null) enc.setSubmitterProject(project);
    System.out.println("    PROCIDPHOTOS set project "+project);


    // DEPRECATED
    //if (individualID!=null) enc.setIndividualID(individualID);
    System.out.println("    PROCIDPHOTOS set indID "+indID);


    String otherId = thisRow.get("Individual_designation").toString();
    if (otherId!=null) enc.setDynamicProperty("Field ID", otherId);
    System.out.println("    PROCIDPHOTOS set otherId "+otherId);

    enc.setOccurrenceID(occID);
    System.out.println("    PROCIDPHOTOS set occID "+occID);


    boolean needsPhotogName = (enc.getPhotographerName()==null || "".equals(enc.getPhotographerName()));
    System.out.println("    PROCIDPHOTOS needsPhotogName "+needsPhotogName);

    boolean photogCheck = isIDPhotoRowExemplar(thisRow) || needsPhotogName;
    System.out.println("    PROCIDPHOTOS photogCheck "+photogCheck);

    if (photogCheck) {
      enc.setPhotographerName(thisRow.getString("Photographer"));
      System.out.println("    PROCIDPHOTOS setPhotographerName "+thisRow.getString("Photographer"));
    }
    else {System.out.println("    PROCIDPHOTOS did not need to set photog name ");}

    // System.out.println("    PROCIDPHOTOS: encCode is done loading");


    // if (committing && newObj) {
    //   System.out.println("    Committing PROCIDPHOTOS: about to commit new enc "+enc);
    //   myShepherd.storeNewEncounter(enc);
    // }


    enc.setGenus("Megaptera");
    enc.setSpecificEpithet("novaeangliae");

    return enc;
  }
  // each row in idphotos is an annotation, but you can deduce which encounter it is by the individual_id and date fields
  private String getEncounterCodeForIDPhotoRow(Row thisRow) {
    try {
      //String indivDayCode = getDailyIndivNameForIDPhotoRow(thisRow);
      String indID = thisRow.get("Individual_id").toString();
      String occID = occurrenceCodeForIDPhotoRow(thisRow);
      String ans = occID+"-"+indID;
      if (indID.equals(testIndId)) addToCount(testIndEncounterCodes, ans);
      return ans;
    } catch (Exception e) {
      System.out.println("    PROCIDPHOTOS: getEncounterCodeForIDPhotoRow: no encounter code found for row "+loggingRefForIDPhotoRow(thisRow));
    }
    return null;
  }

  private boolean isIDPhotoRowExemplar(Row thisRow) {
    System.out.println("    PROCIDPHOTOS: isRowExemplar starting");
    Boolean bit = thisRow.getBoolean("Representative_photo");
    System.out.println("    PROCIDPHOTOS: isRowExemplar got bit "+bit);
    if (bit == null) return false;
    return bit.booleanValue();
  }

  private String getDailyGroupNameForIDPhotoRow(Row thisRow) {
    // since "Individual_designation" of A2^2 means the second member of group A2 on that day...
    String individualDesignation = thisRow.get("Individual_designation").toString();
    System.out.println("    PROCIDPHOTOS: getDailyGroupNameForIDPhotoRow got full string "+individualDesignation);
    if (individualDesignation == null || individualDesignation.equals("")) return null;
    System.out.println("    PROCIDPHOTOS: getDailyGroupNameForIDPhotoRow returning "+(individualDesignation.split("\\^")[0]));
    return (individualDesignation.split("\\^")[0]);
  }

  private String getDailyIndivNameForIDPhotoRow(Row thisRow) {
    // since "Individual_designation" of A2^2 means the second member of group A2 on that day...
    String individualDesignation = thisRow.get("Individual_designation").toString();
    return individualDesignation;
  }

  private Date getDateForIDPhotoRow(Row thisRow) {
    return thisRow.getDate("Date");
  }

  private DateTime getDateTimeForIDPhotoRow(Row thisRow) {
    Date date = getDateForIDPhotoRow(thisRow);
    if (date==null) return null;
    return new DateTime(date);
  }

  private Occurrence getOccurrenceForIDPhotoRow(Row thisRow, String occCode, Encounter enc, Shepherd myShepherd) {
    Occurrence occ;
    boolean newObj = false;
    if (generatedOccurrences.containsKey(occCode)) {
      occ = generatedOccurrences.get(occCode);
      occ.addEncounterAndUpdateIt(enc);
    }
    else if (myShepherd.isOccurrence(occCode)) {
      occ = myShepherd.getOccurrence(occCode);
      occ.addEncounterAndUpdateIt(enc);
      generatedOccurrences.put(occCode, occ);
    }
    else {
      occ = new Occurrence(occCode, enc);
      generatedOccurrences.put(occCode, occ);
      newObj = true;
    }

    //occ.addSpecies("Megaptera novaeangliae", myShepherd);
    occ.addTaxonomy(humpbackTax);
    return occ;

  }
  private String occurrenceCodeForIDPhotoRow(Row thisRow) {
    try {
      Object omcdObj = thisRow.get("OMCD Sighting ID");
      String omcd = omcdObj.toString();
      if (omcd!=null) return "OMCD"+omcd; // label the omcd row
    } catch (Exception e) {
      try {
        return (getDateForIDPhotoRow(thisRow).toString() +"-group"+ getDailyGroupNameForIDPhotoRow(thisRow));
      }
      catch (Exception f) {
        System.out.println("    +getOccurrenceCodeForIDPhotoRow: no occurrence code found for row "+loggingRefForIDPhotoRow(thisRow)+"; returning a UUID");
      }
    }
    return Util.generateUUID();
  }

  private Annotation getAnnotationForIDPhotoRow(Row thisRow, AssetStore astore, Shepherd myShepherd) {
    MediaAsset ma = getMediaAssetForIDPhotoRow(thisRow, astore, myShepherd);
    if (ma==null) return null;
    Annotation ann = new Annotation("Megaptera novaeangliae", ma);
    ann.setIsExemplar(true);
    return ann;
  }

  // since Access rows aren't numbered, this is useful for logging
  private String loggingRefForIDPhotoRow(Row thisRow) {
    String refKey = "RecID";
    try {
      return thisRow.get("RecID").toString();
    } catch (Exception e) {
      return "NOT_PARSED";
    }
  }

  // the filenames specified in the data have "thumbnail" or "Thumbnail" in their name.
  // this looks for the same photo but in the "medium res" or "Medium Res" or "raw" folders
  private String getBestExistingFilename(String fileName) {

    String name = fixWeirdFilenames(fileName);

    String replaceString = fileName.contains("Thumbnail") ? "Thumbnail" : "thumbnail";

    String lowerMedium = withExistingJpgExtension(name.replace(replaceString, "medium res"));
    if (Util.fileExists(lowerMedium)) return lowerMedium;

    String upperMedium = withExistingJpgExtension(name.replace(replaceString, "Medium Res"));
    if (Util.fileExists(upperMedium)) return upperMedium;

    String lowerRaw = withExistingJpgExtension(name.replace(replaceString, "raw"));
    if (Util.fileExists(lowerMedium)) return lowerMedium;

    String upperRaw = withExistingJpgExtension(name.replace(replaceString, "Raw"));
    if (Util.fileExists(upperMedium)) return upperMedium;

    return fileName;
  }

  // sometimes the ".jpg" in filenames doesn't line up with the ".JPG" that exists on-disk
  private String withExistingJpgExtension(String fileName) {
    if (Util.fileExists(fileName)) return fileName;
    if (Util.fileExists(fileName.replace(".JPG",".jpg"))) return fileName.replace(".JPG",".jpg");
    if (Util.fileExists(fileName.replace(".jpg",".JPG"))) return fileName.replace(".jpg",".JPG");
    return fileName;
  }

  private String fixWeirdFilenames(String fileName) {
    String newName = fileName.replace("THUMBNAIL","Thumbnail");
    newName = newName.replace("AWILLSON", "AWillson");
    newName = newName.replace("DMACDONALD", "DMacDonald");
    newName = newName.replace("RBALDWIN", "RBaldwin");
    newName = newName.replace("RBALDWIN", "RBaldwin");
    return newName;
  }

  private MediaAsset getMediaAssetForIDPhotoRow(Row thisRow, AssetStore astore, Shepherd myShepherd) {
    String localFileLocation;
    try {
      localFileLocation = thisRow.getString("Photo_location");
    } catch (NullPointerException npe) {
      System.out.println("    PROCIDPHOTOS: getMediaAssetForIDPhotoRow: no Photo_location column found for row "+loggingRefForIDPhotoRow(thisRow));
      return null;
    }
    try {
      if (localFileLocation==null || "".equals(localFileLocation)) return null;
      localFileLocation = Util.windowsFileStringToLinux(localFileLocation);
      localFileLocation = photoLocation+"/"+localFileLocation;
      localFileLocation = getBestExistingFilename(localFileLocation);
      boolean fileExists = Util.fileExists(localFileLocation);
      // System.out.println("    PROCIDPHOTOS: getIDPhotosMediaAsset got localFileLocation "+localFileLocation+" this file exists="+fileExists);
      if (!fileExists) {
        missingPhotos.add(localFileLocation);
        return null;
      }

      File f = new File(localFileLocation);

      // create MediaAsset and return it
      JSONObject assetParams = astore.createParameters(f);
      assetParams.put("_localDirect", f.toString());
      MediaAsset ma = astore.copyIn(f, assetParams);

      // MA processing
      ma.setUserDateTime(getDateTimeForIDPhotoRow(thisRow));

      // now committing in the annotation logic on commitEncounters()
      // if (committing) {
      //   ma.setMetadata();
      //   ma.updateStandardChildren(myShepherd);
      // }

      if (isIDPhotoRowExemplar(thisRow)) ma.addKeyword(profilePicKeyword);

      String keywordName = thisRow.getString("Photo_category");
      Keyword keyword;
      if ("Tail Fluke".equals(keywordName)) keyword = flukeKeyword;
      else if ("Left Dorsal Fin".equals(keywordName)) keyword = leftFinKeyword;
      else if ("Right Dorsal Fin".equals(keywordName)) keyword = rightFinKeyword;
      else if ("Back".equals(keywordName)) keyword = backKeyword;
      else {
      	keyword = new Keyword(keywordName);
      	numUniqueKeywords++;
      }

      if (keyword != null) ma.addKeyword(keyword);

      String frameKeyword = thisRow.getString("Frame");
      if (frameKeyword != null) {
        ma.addToMetadata("Film frame no.", frameKeyword);
        System.out.println("    PROCIDPHOTOS: added metadata to mediaAsset: (Film frame no.: "+frameKeyword+")");

      }
      String rollKeyword = thisRow.getString("Roll");
      if (rollKeyword != null) {
        ma.addToMetadata("Film roll", rollKeyword);
        System.out.println("    PROCIDPHOTOS: added metadata to mediaAsset: (Film roll: "+rollKeyword+")");
      }

      String filmTypeKeyword = thisRow.getString("Film_type");
      if (filmTypeKeyword != null) {
        ma.addToMetadata("Film type", filmTypeKeyword);
        System.out.println("    PROCIDPHOTOS: added metadata to mediaAsset: (Film type: "+filmTypeKeyword+")");
      }

      if (ma!=null) System.out.println("MEDIA ASSET height parsed at "+ma.getHeight());

      return ma;
    } catch (Exception e) {
      System.out.println("Exception on getIDPhotosMediaAsset! Returning a null asset.");
      e.printStackTrace();
    }
    return null;
  }



  private boolean processSightingHistoryTable(Table table, java.io.PrintWriter out, Shepherd myShepherd) {
  	out.println("    processing SightingHistory table");
    ArrayList<String> columnMasterList = getColumnMasterList(table);
    int success = 0;
    out.println("Sighting History Table has "+table.getRowCount()+" Rows!");
    printTable(table, out);

    if (astore==null) astore = getAssetStore(myShepherd);
    System.out.println("    SHROW-Proc: beginning");


    Row thisRow = null;
    
    int printPeriod = 1; // how often to print certain log statements
    for (int i=0;i<table.getRowCount();i++) {
      boolean printing = true;//((rowNum%printPeriod)==0);

      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("\n!!!!!!!!!!!!!! Could not get next Row in SightingHistory table on row "+i+"...\n");
      }


      processSightingHistoryRow(thisRow, myShepherd, columnMasterList, astore, printing);
       
    } 
    out.println("<p>We now have a running <strong>"+generatedEncounters.size()+" Encounters,. "+generatedIndividuals.size()+" Individuals, and "+generatedOccurrences.size()+" Occurrences.</strong></p>");


  	return true;
  }

  private boolean processSightingHistoryRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList, AssetStore astore, boolean print) {

    // note, both sighting history and idphotos tables have same occ code
    String occID = occurrenceCodeForIDPhotoRow(thisRow);
    if (occID==null) occID = Util.generateUUID(); // there's only one row per occurrence

    Encounter enc = getEncounterForSHRow(thisRow, occID, myShepherd);
    if (print) System.out.println("    SHROW-Proc got Encounter "+enc);

    Occurrence occ = getOccurrenceForSHRow(thisRow, occID, enc, myShepherd);
    if (print) System.out.println("    SHROW-Proc got Occurrence "+occ);

    MarkedIndividual indy = getIndividualForSHRow(thisRow, enc, myShepherd);
    if (print) System.out.println("    SHROW-Proc got individual "+indy);

    if (committing) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

    return true;
  }

  private Encounter getEncounterForSHRow(Row thisRow, String occID, Shepherd myShepherd) {
    System.out.println("    SHROW-Proc: starting getEncounter ");

    // NOTE this works b/c these tables use same column headings
    String encCode = getEncounterCodeForIDPhotoRow(thisRow);
    Encounter enc = null;





    System.out.println("    SHROW-Proc got encCode " +encCode);
    // NOTE if we have to flush the generatedEncounters map for ram reasons, we'll need another way to grab the encounter (ie keep a map of encCode->encID and use myShepherd.getEncounter)
    if (generatedEncounters.containsKey(encCode)) {
      numEncountersSharedByTables++;
      System.out.println("    SHROW-Proc generatedEncounters contains our enc");
      enc = generatedEncounters.get(encCode);
      System.out.println("    SHROW-Proc got our enc "+enc);
    } else {
      System.out.println("    SHROW-Proc making a new encounter");

    // TODO: try to load encounter by indID and occID, make a new one if it doesn't exist.
      // nvm: there are no indIDs on the SHRows, so we couldn't resolve this


      enc = new Encounter((Annotation) null);
      
      enc.setCountry("Oman");
      enc.setSubmitterName("Oman Photo ID Catalog Bulk Import");
      enc.setSubmitterID("ESO");

      generatedEncounters.put(encCode, enc);
    }

    String location = thisRow.getString("Location");
    if (location!=null) enc.setLocationID(location);
    System.out.println("    SHROW-Proc set location "+location);

    String otherId = getStringSafe(thisRow, "Matching_designation");
    // if (otherId!=null) enc.setAlternateID(otherId);
    System.out.println("    SHROW-Proc set otherId "+otherId);

    enc.setCountry("Oman");
    enc.setSubmitterName("Oman Photo ID Catalog Bulk Import");
    String latStr = thisRow.get("Latitude").toString();
    try {enc.setDecimalLatitude(new Double(latStr));}
    catch (Exception e) {}
    String lonStr = thisRow.get("Longitude").toString();
    try {enc.setDecimalLongitude(new Double(lonStr));}
    catch (Exception e) {}
    System.out.println("    SHROW-Proc got lat,lon "+enc.getDecimalLatitude()+","+enc.getDecimalLongitude());


    System.out.println("    ** SHROW-Proc about to set date");
    Date encTime = null;
    try {encTime = thisRow.getDate("Date");}
    catch (Exception e) {
      try {encTime = new Date(thisRow.get("Date").toString());}
      catch (Exception f) {}
    }
    if (encTime!=null) {
      enc.setDateInMilliseconds(encTime.getTime());
    }
    System.out.println("    SHROW-Proc set date to "+encTime);

    System.out.println("    ** SHROW-Proc about to set verbatim date");
    if (encTime!=null) enc.setVerbatimEventDate(encTime.toString());

    String biopsy = thisRow.get("Biopsy").toString();
    if (biopsy !=null) enc.setDynamicProperty("Biopsy collected",biopsy);
    System.out.println("    SHROW-Proc set biopsy "+biopsy);

    String skin = thisRow.get("Sloughed skin").toString();
    if (skin !=null) enc.setDynamicProperty("Sloughed skin",skin);
    System.out.println("    SHROW-Proc set sloughed skin "+skin);

    Annotation flukeAnn = enc.getAnnotationWithKeyword("Tail Fluke");
    System.out.println("    SHROW-Proc got flukeAnn "+flukeAnn);
    Double flukeQuality = null;
    try {flukeQuality = new Double(thisRow.get("Fluke photo score").toString());}
    catch (Exception e) {}
    System.out.println("    SHROW-Proc got flukeQuality "+flukeQuality);
    if (flukeAnn != null && flukeQuality != null) {
      flukeAnn.setQuality(flukeQuality);
      numTaggedAnnotsFoundAgain++;
      System.out.println("ANOQUAL ALIGNED: Tail Fluke="+flukeQuality );
    } else System.out.println("    couldn't find tail fluke quality for ann="+flukeAnn+" and qual="+flukeQuality);


    Annotation rightDorsalAnn = enc.getAnnotationWithKeyword("Right Dorsal Fin");
    System.out.println("    SHROW-Proc got rightDorsalAnn "+rightDorsalAnn);
    Double rightDorsalQuality = null;
    try {rightDorsalQuality = new Double(thisRow.get("Right Dorsal Fin score").toString());}
    catch (Exception e) {}
    System.out.println("    SHROW-Proc got rightDorsalQuality "+rightDorsalQuality);
    if (rightDorsalAnn != null && rightDorsalQuality != null) {
      rightDorsalAnn.setQuality(rightDorsalQuality);
      numTaggedRDAnnotsFoundAgain++;
      System.out.println("ANOQUAL ALIGNED: Tail rightDorsal="+rightDorsalQuality );
    } else System.out.println("    couldn't find tail rightDorsal quality for ann="+rightDorsalAnn+" and qual="+rightDorsalQuality);

    Annotation leftDorsalAnn = enc.getAnnotationWithKeyword("Left Dorsal Fin");
    System.out.println("    SHROW-Proc got leftDorsalAnn "+leftDorsalAnn);
    Double leftDorsalQuality = null;
    // note non-capitalization of fin as opposed to Right Dors. Fin
    try {leftDorsalQuality = new Double(thisRow.get("Left Dorsal fin score").toString());}
    catch (Exception e) {}
    System.out.println("    SHROW-Proc got leftDorsalQuality "+leftDorsalQuality);
    if (leftDorsalAnn != null && leftDorsalQuality != null) {
      leftDorsalAnn.setQuality(leftDorsalQuality);
      numTaggedLDAnnotsFoundAgain++;
      System.out.println("ANOQUAL ALIGNED: Tail leftDorsal="+leftDorsalQuality );
    } else System.out.println("    couldn't find tail leftDorsal quality for ann="+leftDorsalAnn+" and qual="+leftDorsalQuality);




    String sex = thisRow.get("Sex").toString();
    if (sex !=null) {
      if      (sex.indexOf("M") >= 0) enc.setSex("male");
      else if (sex.indexOf("F") >= 0) enc.setSex("female");
      else if (sex.indexOf("U") >= 0) enc.setSex("unknown");
    }
    System.out.println("    SHROW-Proc set sex "+sex);

    String satelliteTag = thisRow.get("Satellite Tag").toString();
    if (satelliteTag != null && !satelliteTag.toLowerCase().equals("No")) enc.setDynamicProperty("Satellite tag",satelliteTag);
    System.out.println("    SHROW-Proc asdf set satelliteTag dyprop "+satelliteTag);

    System.out.println("    SHROW-Proc about to try for nickname ");
    Object nickObj = thisRow.get("Nickname");
    String nickname = (nickObj==null) ? null : nickObj.toString();
    System.out.println("    SHROW-Proc got nickname "+nickname);
    if (nickname != null) enc.setAlternateID(nickname);
    System.out.println("    SHROW-Proc set nickname "+nickname);

    System.out.println("    SHROW-Proc done with method. Why you not printing?");

    if (enc.getOccurrenceID()==null && occID!=null) enc.setOccurrenceID(occID);
    System.out.println("    SHROW-Proc done setting occID. About to set genus.");

    enc.setGenusOnly("Megaptera");
    System.out.println("    SHROW-Proc done setting genus. About to set species.");
    enc.setSpeciesOnly("novaeangliae");    

    System.out.println("    SHROW-Proc REALLY done with method. Why you not printing?");


    return enc;
  }

  private Occurrence getOccurrenceForSHRow(Row thisRow, String occCode, Encounter enc, Shepherd myShepherd) {
    Occurrence occ;
    System.out.println("    SHROW-Proc starting getOccurrence "+occCode);
    if (generatedOccurrences.containsKey(occCode)) {
      numOccurrencesSharedByTables++;
      occ = generatedOccurrences.get(occCode);
      occ.addEncounterAndUpdateIt(enc);
    }
    else if (myShepherd.isOccurrence(occCode)) {
      occ = myShepherd.getOccurrence(occCode);
      occ.addEncounterAndUpdateIt(enc);
      generatedOccurrences.put(occCode, occ);
    }
    else {
      occ = new Occurrence(occCode, enc);
      generatedOccurrences.put(occCode, occ);
    }
    System.out.println("    SHROW-Proc got "+occ);


    String recID = thisRow.get("RecID").toString();
    System.out.println("    SHROW-Proc recID "+recID);  
    if (recID!=null) occ.setFieldSurveyCode(recID);
    System.out.println("    SHROW-Proc setFieldSurveyCode "+recID);

    String groupDesignation = thisRow.getString("Group_number");
    if (groupDesignation!=null) occ.addComments("Daily group number: "+groupDesignation);
    System.out.println("    SHROW-Proc set group number "+groupDesignation);

    String location = thisRow.getString("Location");
    if (location!=null) occ.setFieldStudySite(location);
    System.out.println("    SHROW-Proc setFieldStudySite "+location);

    String boat = thisRow.getString("Boat");
    if (boat!=null) occ.setSightingPlatform(boat);
    System.out.println("    SHROW-Proc set boat "+boat);

    occ.addSpecies("Megaptera novaeangliae", myShepherd);

    return occ;
  }

  private MarkedIndividual getIndividualForSHRow(Row thisRow, Encounter enc, Shepherd myShepherd) {

    MarkedIndividual indy = null;
    String indID = thisRow.getString("Individual_id");
    if (indID == null) return indy;

    System.out.println("    SHROW-Proc got indID " +indID);
    if (generatedIndividuals.containsKey(indID)) {
      numIndividualsSharedByTables++;
      System.out.println("    SHROW-Proc generatedIndividuals contains our boy");
      indy = generatedIndividuals.get(indID);
      System.out.println("    SHROW-Proc got our boy "+indy+" ("+indy.totalEncounters()+" encs).");
      if (enc!=null) indy.addEncounterNoCommit(enc);
      System.out.println("    SHROW-Proc ... and added our boy's encounter ("+indy.totalEncounters()+" encs).");

    } else {
      System.out.println("    SHROW-Proc making a new individual");
      indy = new MarkedIndividual(indID, enc);
      generatedIndividuals.put(indID, indy);
    }

    Object nickObj = thisRow.get("Nickname");
    String nickname = (nickObj==null) ? null : nickObj.toString();
    if (nickname !=null) indy.setNickName(nickname);
    System.out.println("    SHROW-Proc set nickname "+nickname);


    // TODO: process indy?

    return indy;

  }


  
  private DateTime dateStringToDateTime(String verbatim, String format) {
    DateFormat fm = new SimpleDateFormat(format);
    Date d = null;
    try {
      d = (Date)fm.parse(verbatim);    
    } catch (ParseException pe) {
      pe.printStackTrace();
      out.println("Barfed Parsing a Datestring... Format : "+format);
    }
    DateTime dt = new DateTime(d);
    
    return dt;
  }
  
  private String processDateString(String date, String startTime) {
    String justDate = date.substring(0,11);
    String years = date.substring(date.length() - 5);
    String formattedStartTime = formatMilitaryTime(startTime);
    String finalDateTimeString = justDate + formattedStartTime + years;
    
    return finalDateTimeString;
  }
  
  private String formatMilitaryTime(String mt) {
    // The parsing breaks on military time formatted like "745" instead of "0745"
    // Stupid timey stuff. Sometimes there are colons, sometimes not. Hence all the if's.
    try {
      if (mt.contains(":")) {
        mt = mt.replace(":", "");
      }
      if (mt.length() < 3 || mt.equals(null) || mt.equals("") || mt == null || Integer.parseInt(mt) > 2400) {
        mt = "0000";
      }
      if (mt.length() < 4) {
        mt = "0" + mt;
      }      
    } catch (Exception e) {
      // Is it weird and malformed? Lets just auto set it. 
      System.out.println("Couldn't find startTime : "+mt+", setting to midnight.");
      mt = "0000";
      //e.printStackTrace();
    }
    DateTimeFormatter in = DateTimeFormat.forPattern("HHmm"); 
    DateTimeFormatter out = DateTimeFormat.forPattern("hh:mm a"); 
    DateTime mtFormatted = in.parseDateTime(mt); 
    String standard = out.print(mtFormatted.getMillis());
    
    return standard;
  }
    
  public static Object deepCopy(Object orig) {
    Object obj = null;
    try {
        // Write the object out to a byte array
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(bos);
        out.writeObject(orig);
        out.flush();
        out.close();

        ObjectInputStream in = new ObjectInputStream(
            new ByteArrayInputStream(bos.toByteArray()));
        obj = in.readObject();
    }
    catch(IOException e) {
      e.printStackTrace();
      System.out.println("Failed to clone this object.");
    }
    catch(ClassNotFoundException cnfe) {
      System.out.println("Failed to clone this object - Class Not Found.");
      cnfe.printStackTrace();
    }
    return obj;
  }

  public static ArrayList<String> getColumnMasterList(Table table) {
    // This is a list of column names. We are gonna take them out as we process them so we know if we missed any at the end. 
    ArrayList<String> columnMasterList = new ArrayList<String>();
    List<? extends Column> columns = table.getColumns();
    for (int i=0;i<columns.size();i++) {
      columnMasterList.add(columns.get(i).getName());
    }
    //out.println("All of the columns in this Table : "+columnMasterList.toString()+"\n");
    return columnMasterList;
  }

  public static void printTable(Table table, java.io.PrintWriter out) {
    List<String> colNames = getColumnMasterList(table);
    System.out.println("beginning to printTable "+table.getName());
    out.println("<table>");
    for (String colName : colNames) {
      out.println("<tr><td>");

      try {out.print(colName);}
       catch (Exception e) {}
      out.print("</td></tr>");

    } 
    out.println("</table>");
    System.out.println("Done printing table "+table.getName());

  }

  public static String getStringSafe(Row row, String column) {
    if (!row.containsKey(column)) return null;
    Object obj = row.get("column");

    if (obj == null) return null;

    return obj.toString();
  }

  private LocalAssetStore initFeatureTypeAndAssetStore(Shepherd myShepherd) {
    FeatureType.initAll(myShepherd);
    String rootDir = getServletContext().getRealPath("/");
    String baseDir = ServletUtilities.dataDir(myShepherd.getContext(), rootDir);
    String assetStorePath="/data/wildbook_data_dir/encounters";
    //String rootURL="http://localhost:8080";
    String rootURL="flukebook.org";
    String assetStoreURL=rootURL+"/wildbook_data_dir/encounters";
    //////////////// begin local //////////////
    LocalAssetStore as = new LocalAssetStore("Oman-Asset-Store", new File(assetStorePath).toPath(), assetStoreURL, true);
    myShepherd.getPM().makePersistent(as);
    return as;
  }




}