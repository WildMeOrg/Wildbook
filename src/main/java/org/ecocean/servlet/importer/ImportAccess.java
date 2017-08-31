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

public class ImportAccess extends HttpServlet {
  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  private static PrintWriter out;
  private static String context;

  // Hack. TODO: remove?
  private static boolean runOncePercompile = false;

  private static boolean committing = false; // for developing w/o mucking up database
  
  // Okay, we might need to build a hashmap out of every line in this table, so we can create multiple encounters 
  // for the date/sighting number pairs that occure multiple times. 
  HashMap<String,Integer> duplicatePairsMap = new HashMap<String,Integer>();
  ArrayList<String> failedEncs = new ArrayList<String>();

  // so we can bounce objects around between helper methods w/o worrying about shepherd permanence so much
  Map<String,MarkedIndividual> generatedIndividuals = new HashMap<String, MarkedIndividual>();
  Map<String,Encounter> generatedEncounters = new HashMap<String, Encounter>();
  Map<String,Occurrence> generatedOccurrences = new HashMap<String, Occurrence>();

  private String photoLocation;

  private AssetStore astore;
  public static final String DEFAULT_ASSETSTORE_NAME = "Oman-Asset-Store";

  private List<String> missingPhotos = new ArrayList<String>();

  Integer rowLimitForTesting;

  private void commitIndividuals(Shepherd myShepherd) {
    for (MarkedIndividual indy : generatedIndividuals.values()) {
      if (!myShepherd.isMarkedIndividual(indy.getIndividualID())) {
        myShepherd.storeNewMarkedIndividual(indy);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
  }
  private void commitEncounters(Shepherd myShepherd) {
    for (Encounter indy : generatedEncounters.values()) {
      if (!myShepherd.isEncounter(indy.getIndividualID())) {
        myShepherd.storeNewEncounter(indy);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
  }
  private void commitOccurrence(Shepherd myShepherd) {
    for (Occurrence indy : generatedOccurrences.values()) {
      if (!myShepherd.isOccurrence(indy.getOccurrenceID())) {
        myShepherd.storeNewOccurrence(indy);
        myShepherd.commitDBTransaction();
        myShepherd.beginDBTransaction();
      }
    }
  }

  // // I find AssetStores confusing, hence the convenience method
  // private LocalAssetStore  getLocalAssetStore(Shepherd myShepherd) {
  //   return getLocalAssetStore(DEFAULT_ASSETSTORE_NAME, myShepherd);
  // }

  private AssetStore  getAssetStore(Shepherd myShepherd) {

    return AssetStore.getDefault(myShepherd);

    //AssetStore store = AssetStore.get(storeName);
    //if (store != null) return store;
    
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

    rowLimitForTesting = 200;
    missingPhotos = new ArrayList<String>();

    context = ServletUtilities.getContext(request);
    out = response.getWriter();

    boolean skipSightingsHistory = true;
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
      
    
    String dbName = "omanData2017.07.04.mdb";
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
    out.println("<div class=\"container\"><div class=\"row\">");

    out.println("***** Beginning Access Database Import. *****\n");
    
    Set<String> tables = db.getTableNames();
    out.println("********************* Here's the tables : "+tables.toString()+"\n");
    
    myShepherd.beginDBTransaction();



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


    if (!skipIDPhotos) {

      out.println("<div>");
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

    if (committing) myShepherd.commitDBTransaction();
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
    AssetStore astore = getAssetStore(myShepherd);
   System.out.println("    PROCIDPHOTOS: got asset store "+astore);

    Row thisRow = null;
    int printPeriod = 1; // how often to print certain log statements

    boolean doOnceOnlyForTesting = true;
    int rowNum = 0;
   System.out.println("    PROCIDPHOTOS: entering row loop");

    for (;rowNum<table.getRowCount()&&rowLimitForTesting!=null&&rowNum<rowLimitForTesting;rowNum++) {
      boolean printing = true;//((rowNum%printPeriod)==0);
      if (printing) System.out.println("    PROCIDPHOTOS: beginning row "+rowNum+".");
      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("\n!!!!!!!!!!!!!! Could not get next Row in IDPhotos table on row "+rowNum+"...\n");
      }
      processIDPhotosRow(thisRow, myShepherd, columnMasterList, astore, printing);
      if (printing) System.out.println("    PROCIDPHOTOS: Done with row "+rowNum+".");
    } 
    System.out.println("    PROCIDPHOTOS: Done with row Processing");
    int numMissingPhotos = missingPhotos.size();
    out.println("<p>IDPhotos Table is done processing <strong>"+rowNum+" rows</strong>. <strong>"+(rowNum-numMissingPhotos)+" photos found</strong> and <strong>"+numMissingPhotos+" missing photos</strong>.</p>");
    System.out.println("<p>IDPhotos Table is done processing "+rowNum+" rows. "+(rowNum-numMissingPhotos)+" photos found and "+numMissingPhotos+" missing photos.</p>");

    System.out.println("<p>We made <strong>"+generatedEncounters.size()+" Encounters,. "+generatedIndividuals.size()+" Individuals, and "+generatedOccurrences.size()+" Occurrences.</strong></p>");


  	return true;
  }


  private boolean processIDPhotosRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList, AssetStore astore, boolean print) {
    // have to manually enable comitting (ie no committing arg = no committing)
    return processIDPhotosRow(thisRow,myShepherd,columnMasterList,astore,false,print);
  }
  
  private boolean processIDPhotosRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList, AssetStore astore, boolean commit, boolean print) {

    String occID = occurrenceCodeForIDPhotoRow(thisRow);

    Annotation ann = getAnnotationForIDPhotoRow(thisRow, astore, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got Annotation "+ann);

    Encounter enc = getEncounterForIDPhotoRow(thisRow, ann, occID, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got Encounter "+enc);

    Occurrence occ = getOccurrenceForIDPhotoRow(thisRow, enc, myShepherd);
    if (enc!=null && occ!=null) enc.setOccurrenceID(occ.getOccurrenceID());
    if (print) System.out.println("    PROCIDPHOTOS got Occurrence "+occ);

    MarkedIndividual indy = getMarkedIndividualForIDPhotoRow(thisRow, enc, myShepherd);
    if (print) System.out.println("    PROCIDPHOTOS got individual "+indy);

    if (committing) {
      myShepherd.commitDBTransaction();
      myShepherd.beginDBTransaction();
    }

    return true;
  }


  // the following getters are helper functions for the table processors
  private MarkedIndividual getMarkedIndividualForIDPhotoRow(Row thisRow, Encounter enc, Shepherd myShepherd) {


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

    System.out.println("    PROCIDPHOTOS got encCode " +encCode);
    if (generatedEncounters.containsKey(encCode)) {
      System.out.println("    PROCIDPHOTOS generatedEncounters contains our boy");
      enc = generatedEncounters.get(encCode);
      System.out.println("    PROCIDPHOTOS got our enc "+enc);

      if (ann!=null) enc.addAnnotation(ann);
      System.out.println("    PROCIDPHOTOS ... and loaded our boy's annotation");

    } else {
      System.out.println("    PROCIDPHOTOS making a new encounter");
      enc = new Encounter(ann);
      generatedEncounters.put(encCode, enc);
    }

    // Parse the various columns
    System.out.println("    PROCIDPHOTOS Now to load the columns of the table into our boy");
    
    System.out.println("    ** PROCIDPHOTOS about to set date");
    Date encTime = thisRow.getDate("Date");
    System.out.println("    ** PROCIDPHOTOS about to set date to "+encTime);
    if (encTime!=null) {
      enc.setDateInMilliseconds(encTime.getTime());
      System.out.println("    PROCIDPHOTOS set date to "+encTime);
    } else System.out.println("    PROCIDPHOTOS set date to "+encTime);

    String project = thisRow.getString("Project_code");
    if (project!=null) enc.setSubmitterProject(project);
    System.out.println("    PROCIDPHOTOS set project "+project);


    String indID = thisRow.getString("Individual_id");
    if (indID!=null) enc.setIndividualID(indID);
    System.out.println("    PROCIDPHOTOS set indID "+indID);

    String otherId = thisRow.getString("Individual_designation");
    if (otherId!=null) enc.setAlternateID(otherId);
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
    return enc;
  }
  // each row in idphotos is an annotation, but you can deduce which encounter it is by the individual_id and date fields
  private String getEncounterCodeForIDPhotoRow(Row thisRow) {
    try {
      String indivDayCode = getDailyGroupNameForIDPhotoRow(thisRow);
      Date rowDate = getDateForIDPhotoRow(thisRow);
      if ((indivDayCode == null || "".equals(indivDayCode)) && rowDate == null) return null;
      return indivDayCode + rowDate.toString();
    } catch (Exception e) {
      System.out.println("    PROCIDPHOTOS: getMediaAssetForIDPhotoRow: no encounter code found for row "+loggingRefForIDPhotoRow(thisRow));
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
    if (individualDesignation == null || individualDesignation.equals("") || (individualDesignation.split("^").length==0)) return null;
    return individualDesignation.split("^")[1];
  }

  private Date getDateForIDPhotoRow(Row thisRow) {
    return thisRow.getDate("Date");
  }

  private DateTime getDateTimeForIDPhotoRow(Row thisRow) {
    Date date = getDateForIDPhotoRow(thisRow);
    if (date==null) return null;
    return new DateTime(date);
  }

  private Occurrence getOccurrenceForIDPhotoRow(Row thisRow, Encounter enc, Shepherd myShepherd) {
    String occCode = occurrenceCodeForIDPhotoRow(thisRow);
    Occurrence occ;
    if (generatedOccurrences.containsKey(occCode)) occ = generatedOccurrences.get(occCode);
    else if (myShepherd.isOccurrence(occCode)) return myShepherd.getOccurrence(occCode);
    else occ = new Occurrence(occCode, enc);

    // TODO: process occ
    String groupDesignation = getDailyGroupNameForIDPhotoRow(thisRow);
    if (groupDesignation!=null) occ.addComments("Daily group code="+groupDesignation);
    System.out.println("    PROCIDPHOTOS set group designation "+groupDesignation);

    return occ;

  }
  private String occurrenceCodeForIDPhotoRow(Row thisRow) {
    try {
      String omcd = thisRow.getString("OMCD Sighting ID");
      return "OMCD"+omcd; // label the omcd row
    } catch (Exception e) {
      try {
        return getDateForIDPhotoRow(thisRow).toString() + getDailyGroupNameForIDPhotoRow(thisRow);
      }
      catch (Exception f) {
        System.out.println("    PROCIDPHOTOS: getOccurrenceCodeForIDPhotoRow: no occurrence code found for row "+loggingRefForIDPhotoRow(thisRow)+"; returning a UUID");
      }
    }
    return Util.generateUUID();
  }


  private Annotation getAnnotationForIDPhotoRow(Row thisRow, AssetStore astore, Shepherd myShepherd) {
    MediaAsset ma = getMediaAssetForIDPhotoRow(thisRow, astore, myShepherd);
    if (ma==null) return null;
    return new Annotation("Megaptera novaeangliae", ma);
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
      boolean fileExists = Util.fileExists(localFileLocation);
      // System.out.println("    PROCIDPHOTOS: getIDPhotosMediaAsset got localFileLocation "+localFileLocation+" this file exists="+fileExists);
      if (!fileExists) {
        missingPhotos.add(localFileLocation);
        return null;
      }
      // create MediaAsset and return it
      JSONObject assetParams = new JSONObject();
      assetParams.put("path",localFileLocation);
      MediaAsset ma = astore.create(assetParams);

      // MA processing
      ma.setUserDateTime(getDateTimeForIDPhotoRow(thisRow));

      String keyword = thisRow.getString("Photo_category");
      if (keyword != null) ma.addKeyword(new Keyword(keyword));

      String frameKeyword = thisRow.getString("Frame");
      if (frameKeyword != null) ma.addKeyword(new Keyword(frameKeyword));

      String rollKeyword = thisRow.getString("Roll");
      if (rollKeyword != null) ma.addKeyword(new Keyword(rollKeyword));

      String filmTypeKeyword = thisRow.getString("Film_type");
      if (filmTypeKeyword != null) ma.addKeyword(new Keyword(filmTypeKeyword));

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
    out.println("IDPhotos Table has "+table.getRowCount()+" Rows!");
    printTable(table, out);

    Row thisRow = null;
    
    int printPeriod = 0; // how often to print certain log statements
    for (int i=0;i<table.getRowCount();i++) {
      
      try {
        thisRow = table.getNextRow();
      } catch (IOException io) {
        io.printStackTrace();
        out.println("\n!!!!!!!!!!!!!! Could not get next Row in SightingHistory table on row "+i+"...\n");
      }

      processSightingHistoryRow(thisRow, myShepherd, columnMasterList);
       
    } 

  	return true;
  }

  // This gets the list of Occurrence names from the idPhotosTable.
  // Each day Oman gives individuals names like "A4^2" to denote the 2nd individual in group A4
  // of that day. So, we would get the string "A4:<datestring>"" for that entry.
  // private Set<String> getOccurrenceNames(Table idPhotosTable) {

  // }

  // in ASWN Oman MS Access Data, "OMCD Sighting ID" is a column heading referencing what map to occurrences
  private Occurrence getOrMakeOMCDOccurrence(Row thisRow, Shepherd myShepherd, Encounter enc) {

    String omcdCode;
    Occurrence occ;
    try {
      omcdCode = thisRow.get("OMCD Sighting ID").toString();
      occ = myShepherd.getOccurrence(omcdCode);
      if (occ == null) occ = new Occurrence(omcdCode, enc);
    } catch (Exception e) {
      System.out.println("getOrMakeOMCDOccurrence: no OMCD Sighting ID on this row");
      throw e;
    }
    return occ;
  }

  // null (no-) encounter version of func
  private Occurrence getOrMakeOMCDOccurrence(Row thisRow, Shepherd myShepherd) {
    return getOrMakeOMCDOccurrence(thisRow, myShepherd, null);
  }

  private boolean processSightingHistoryRow(Row thisRow, Shepherd myShepherd, ArrayList<String> columnMasterList) {


    return false;
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