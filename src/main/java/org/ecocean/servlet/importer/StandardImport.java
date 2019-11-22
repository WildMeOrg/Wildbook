package org.ecocean.servlet.importer;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

import java.net.*;

import org.ecocean.grid.*;

import java.io.*;
import java.util.*;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;

import javax.jdo.*;

import java.lang.StringBuffer;
import java.util.Vector;
import java.util.Iterator;
import java.lang.NumberFormatException;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.media.*;
import org.ecocean.genetics.*;
import org.ecocean.tag.SatelliteTag;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StandardImport extends HttpServlet {

	// variables shared by any single import instance

	Map<String,Integer> colIndexMap = new HashMap<String, Integer>();
	Set<String> unusedColumns;
	Set<String> missingColumns; // columns we look for but don't find
	List<String> missingPhotos = new ArrayList<String>();
	List<String> foundPhotos = new ArrayList<String>();
	int numFolderRows = 0;
	boolean committing = false;
  boolean generateChildrenAssets = false;
	PrintWriter out;
	// verbose variable can be switched on/off throughout the import for debugging
	boolean verbose = false;
	String photoDirectory;
  String dataSource;

  int numAnnots=0; // for loggin'

  // these prefixes are added to any individualID, occurrenceID, or sightingID imported
  String individualPrefix="";
  String occurrencePrefix="";
  String sightingPlatformPrefix="";
  String defaultSubmitterID=null; // leave null to not set a default
  String defaultCountry=null;

  String context = "";

  String uploadDirectory = "/data/upload/";


  HttpServletRequest request;

	// just for lazy loading a var used on each row
	Integer numMediaAssets;

  Map<String,MediaAsset> myAssets = new HashMap<String,MediaAsset>();
  
  Map<String,MarkedIndividual> individualCache = new HashMap<String,MarkedIndividual>();
  
  TabularFeedback feedback;

  // need to initialize (initColIndexVariables()), this is useful to have everywhere
  int numCols;

  // indexes of columns determined to have no values for quick skipping
  List<Integer> skipCols = new ArrayList<Integer>();

  Sheet sheet = null;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException {
    
    isUserUpload = Boolean.valueOf(request.getParameter("isUserUpload"));

    System.out.println("Is user upload? ---> "+isUserUpload);
    
    // WHY ISN"T THE URL MAKING IT AAUUUGHGHHHHH
    if (isUserUpload) {
      uploadDirectory = UploadServlet.getUploadDir(request);
      System.out.println("IS USER UPLOAD! ---> uploadDirectory = "+uploadDirectory);
    }

    this.request = request; // so we can access this elsewhere without passing it around
    //String importId = Util.generateUUID();
    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }

    response.setContentType("text/html; charset=UTF-8");
    this.getServletContext().getRequestDispatcher("/header.jsp").include(request, response);
    this.getServletContext().getRequestDispatcher("/import/uploadHeader.jsp").include(request, response);

    context = ServletUtilities.getContext(request);

    myAssets = new HashMap<String,MediaAsset>();  //zero this out from previous (e.g. uncommited)

    Shepherd myShepherd = new Shepherd(context);
    myShepherd.setAction("StandardImport.java");
    myShepherd.beginDBTransaction();

    out = response.getWriter();
    AssetStore astore = getAssetStore(myShepherd);
    
    if(astore!=null){
      System.out.println("astore is OK!");
      System.out.println("Using AssetStore: "+astore.getId()+" of total "+myShepherd.getNumAssetStores());
    } else {
      System.out.println("astore is null...BOO!!");
      out.println("<p>I could not find a default AssetStore. Import cannot continue.</p>");
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      return;
    }

    User creator = AccessControl.getUser(request, myShepherd);
    ImportTask itask = new ImportTask(creator);
    itask.setPassedParameters(request);
    //this might better be set via a different configuration variable of its own
    //String filename = Util.safePath(request.getParameter("filename"));
    //if (filename == null) filename = "upload.xlsx";  //meh?
    
    //Thus MUST be full path, such as: /import/NEAQ/converted/importMe.xlsx
    String filename = request.getParameter("filename");
    
    System.out.println("Filename? = "+filename);

    if (isUserUpload&&filename!=null&&filename.length()>0) {
      filename = uploadDirectory+"/"+filename;
    }
    
    System.out.println("Filename NOW? = "+filename);

    File dataFile = new File(filename);
    
    if (filename == null) {
      System.out.println("Filename request parameter was not set in the URL.");
      out.println("<p>I could not find a filename parameter in the URL. Please specify the full path on the server file system to the Excel import file as the ?filename= parameter on the URL.</p><p>Please note: the importer assumes that all image files exist in the same folder as the Excel file or are relatively referenced in the Excel file within a subdirectory.</p><p>Example value: ?filename=/import/MyNewData/importMe.xlsx</p>");
      return;
    }
    if(!dataFile.exists()){
      out.println("<p>I found a filename parameter in the URL, but I couldn't find the file itself at the path your specified: "+filename+". We found file = "+dataFile+"</p>");
      out.println("<p>File.getAbsoluteFile = "+dataFile.getAbsoluteFile()+"</p>");
      myShepherd.rollbackDBTransaction();
      myShepherd.closeDBTransaction();
      return;
    }
    
    String uploadDir = dataFile.getParentFile().getAbsolutePath();

    //String subdir = Util.safePath(request.getParameter("subdir"));
    //if (subdir != null) uploadDir += subdir;
    photoDirectory = uploadDir+"/";
    
    boolean dataFound = dataFile.exists();

    missingColumns = new HashSet<String>();
    missingPhotos = new ArrayList<String>();
		foundPhotos = new ArrayList<String>();
		numFolderRows = 0;
    numAnnots = 0;

    committing = Util.requestParameterSet(request.getParameter("commit"));

    if (dataFound) {
      doImport(filename, dataFile, request, response, myShepherd);
    } else {
      out.println("An error occurred and your data could not be read from the file system.");
      System.out.println("No datafile found, aborting.");
    }

    this.getServletContext().getRequestDispatcher("/import/uploadFooter.jsp").include(request, response);
    this.getServletContext().getRequestDispatcher("/footer.jsp").include(request, response);

    System.out.println("Is user upload? ---> "+isUserUpload);

    myShepherd.rollbackDBTransaction();
    myShepherd.closeDBTransaction();

  }

  public void doImport(String filename, File dataFile, HttpServletRequest request, HttpServletResponse response, Shepherd myShepherd) {
    missingColumns = new HashSet<String>();
    numFolderRows = 0;
    boolean dataFound = (dataFile!=null && dataFile.exists());
    committing =  (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false")); //false by default

    if (!dataFound) return;

    Workbook wb = null;
    try {
      wb = WorkbookFactory.create(dataFile);
    } catch (org.apache.poi.openxml4j.exceptions.InvalidFormatException invalidFormat) {
      out.println("<err>InvalidFormatException on input file "+filename+". Only excel files supported.</err>");
      return;
    } catch (java.io.IOException ioEx) {
      out.println("<err>ioException on input file "+filename+". Printing error to java server logs.");
      ioEx.printStackTrace();
    }
    sheet = wb.getSheetAt(0);


    int numSheets = wb.getNumberOfSheets();
    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    int rows = sheet.getPhysicalNumberOfRows();; // No of rows
    Row firstRow = sheet.getRow(0);

    initColIndexVariables(firstRow); // IMPORTANT: this initializes the TabularFeedback

    int cols = firstRow.getPhysicalNumberOfCells(); // No of columns
    //int lastColNum = firstRow.getLastCellNum();


    int printPeriod = 1;
    if (committing) myShepherd.beginDBTransaction();
    out.println("<h2>Parsed Import Table</h2>"); 
    //System.out.println("debug0");
    System.out.println("feedback headers = "+feedback.colNames);
    feedback.printStartTable();
    //System.out.println("debug1");
    // one encounter per-row. We keep these running.
    Occurrence occ = null;
    int maxRows = 50000;
    int offset = 0;
    for (int i=1+offset; i<rows&&i<(maxRows+offset); i++) {

      MarkedIndividual mark = null;
      verbose = ((i%printPeriod)==0);
      try {

        if (committing) myShepherd.beginDBTransaction();
        Row row = sheet.getRow(i);
        if (isRowEmpty(row)) continue;

        feedback.startRow(row, i);

        ArrayList<Annotation> annotations = loadAnnotations(row, myShepherd);
        Encounter enc = loadEncounter(row, annotations, context, myShepherd);
        occ = loadOccurrence(row, occ, enc, myShepherd);
        mark = loadIndividual(row, enc, myShepherd, committing, individualCache);

        if (committing) {

          for (Annotation ann: annotations) {
            try {
              MediaAsset ma = ann.getMediaAsset();
              if (ma!=null) {
                myShepherd.storeNewAnnotation(ann);
                ma.setMetadata();
                ma.updateStandardChildren(myShepherd);
              }
            }
            catch (Exception e) {
              System.out.println("EXCEPTION on annot/ma persisting!");
              e.printStackTrace();
            }
          }

          myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
          if (!myShepherd.isOccurrence(occ))        myShepherd.storeNewOccurrence(occ);
          if (!myShepherd.isMarkedIndividual(mark)) myShepherd.storeNewMarkedIndividual(mark);
          myShepherd.commitDBTransaction();
        }

        if (verbose) {
          feedback.printRow();
          //   out.println("<td> Enc "+getEncounterDisplayString(enc)+"</td>"
          //   +"<td> individual "+mark+"</td>"
          //   +"<td> occurrence "+occ+"</td>"
          //   +"<td> dateInMillis "+enc.getDateInMilliseconds()+"</td>"
          //   +"<td> sex "+enc.getSex()+"</td>"
          //   +"<td> lifeStage "+enc.getLifeStage()+"</td>"
          //  out.println("</tr>");
        }
        
      } catch (Exception e) {
        out.println("Encountered an error while importing the file.");
        e.printStackTrace(out);
        myShepherd.rollbackDBTransaction();
      }
    }
    feedback.printEndTable();

    out.println("<div class=\"col-sm-12 col-md-6 col-lg-6 col-xl-6\">"); // half page bootstrap column

    out.println("<h2>Import Overview: </h2>");
    out.println("<ul>");
    out.println("<li>Excel File Name: "+filename+"</li>");
    out.println("<li>Excel File Successfully Found = "+dataFound+"</li>");
    out.println("<li>Excel Sheets in File = "+numSheets+"</li>");
    out.println("<li>Excel Rows = "+physicalNumberOfRows+"</li>");    
    out.println("<li>Excel Columns = "+cols+"</li>");
    //out.println("<li>Last col num = "+lastColNum+"</li>");
    out.println("<li><em>Trial Run: "+!committing+"</em></li>");
    out.println("</ul>");

    out.println("</div>"); // close column

    out.println("<div class=\"col-sm-12 col-md-6 col-lg-6 col-xl-6\">"); // half page bootstrap column

    out.println("<h2><em>NOT IMPORTED</em> Column types ("+unusedColumns.size()+"):</h2><ul>");
    for (String heading: unusedColumns) {
      out.println("<li>"+heading+"</li>");
    }
    out.println("</ul>");

    out.println("</div>"); // close column


    // List<String> usedColumns = new ArrayList<String>();
    // for (String colName: colIndexMap.keySet()) {
    //   if (!unusedColumns.contains(colName)) usedColumns.add(colName);
    // }
    // out.println("<h2><em>USED</em> Column headings ("+usedColumns.size()+"):</h2><ul>");
    // for (String heading: usedColumns) {
    //   out.println("<li>"+heading+"</li>");
    // }
    // out.println("</ul>");

    feedback.printMissingPhotos();

    feedback.printFoundPhotos();


    out.println("<h2><strong> "+numFolderRows+" </strong> Folder Rows</h2>");    

    out.println("<h2>Import completed successfully</h2>");    
    //fs.close();


  }

  public Taxonomy loadTaxonomy0(Row row, Shepherd myShepherd) {
    String sciName = getString(row, "Taxonomy.scientificName");
    if (sciName==null) return null;
    Taxonomy taxy = myShepherd.getOrCreateTaxonomy(sciName);
    String commonName = getString(row, "Taxonomy.commonName");
    if (commonName!=null) taxy.addCommonName(commonName);
    return taxy;
  }

  public Taxonomy loadTaxonomy1(Row row, Shepherd myShepherd) {
    String sciName = getString(row, "Occurrence.taxonomy1");
    if (sciName==null) return null;
    return myShepherd.getOrCreateTaxonomy(sciName);
  }
  public static boolean validCoord(Double latOrLon) {
    return (latOrLon!=null && latOrLon!=0.0);
  }

  public Occurrence loadOccurrence(Row row, Occurrence oldOcc, Encounter enc, Shepherd myShepherd) {
  	
  	Occurrence occ = getCurrentOccurrence(oldOcc, row, myShepherd);
  	// would love to have a more concise way to write following couplets, c'est la vie

  	Double seaSurfaceTemp = getDouble (row, "Occurrence.seaSurfaceTemperature");
    if (seaSurfaceTemp == null) seaSurfaceTemp = getDouble(row, "Occurrence.seaSurfaceTemp");
  	if (seaSurfaceTemp != null) occ.setSeaSurfaceTemp(seaSurfaceTemp);

  	Integer individualCount = getInteger(row, "Occurrence.individualCount");
  	if (individualCount!=null) occ.setIndividualCount(individualCount);

    // covers a typo on some decimalLatitude headers ("decimalLatitiude" note the extra i in Latitiude)
  	Double decimalLatitiude = getDouble(row, "Encounter.decimalLatitiude");
  	if (validCoord(decimalLatitiude)) occ.setDecimalLatitude(decimalLatitiude);
  	Double decimalLatitude = getDouble(row, "Encounter.decimalLatitude");
    if (validCoord(decimalLatitude)) occ.setDecimalLatitude(decimalLatitude);
  	Double decimalLongitude = getDouble(row, "Encounter.decimalLongitude");
    if (validCoord(decimalLongitude)) occ.setDecimalLongitude(decimalLongitude);

  	String fieldStudySite = getString(row, "Occurrence.fieldStudySite");
    // fieldStudySite defaults to locationID
    if (fieldStudySite==null) fieldStudySite = getString(row, "Encounter.locationID");
  	if (fieldStudySite!=null) occ.setFieldStudySite(fieldStudySite);

  	String groupComposition = getString(row, "Occurrence.groupComposition");
  	if (groupComposition!=null) occ.setGroupComposition(groupComposition);

    String groupBehavior = getString(row, "Occurrence.groupBehavior");
    // if no groupBehavior we want the behavior from an Encounter to copy over for occurrence searches
    // this makes sense semantically since many people view the world occurrence-first
    if (groupBehavior==null) groupBehavior = getString(row, "Encounter.behavior");
    if (groupBehavior!=null) occ.setGroupBehavior(groupBehavior);

  	String fieldSurveyCode = getStringOrInt(row, "Survey.id");
    if (fieldSurveyCode==null) fieldSurveyCode = getString(row, "Occurrence.fieldSurveyCode");
  	if (fieldSurveyCode!=null) occ.setFieldSurveyCode(fieldSurveyCode);

  	String sightingPlatform = getString(row, "Survey.vessel");
    if (sightingPlatform==null) sightingPlatform = getString(row, "Platform Designation");
  	if (sightingPlatform!=null) occ.setSightingPlatform(sightingPlatformPrefix+sightingPlatform);
    String surveyComments = getString(row, "Survey.comments");
    if (surveyComments!=null) occ.addComments(surveyComments);

    String comments = getString(row, "Occurrence.comments");
    if (comments!=null) occ.addComments(comments);

    Integer numAdults = getInteger(row, "Occurrence.numAdults");
    if (numAdults!=null) occ.setNumAdults(numAdults);

    Integer minGroupSize = getInteger(row, "Occurrence.minGroupSizeEstimate");
    if (minGroupSize!=null) occ.setMinGroupSizeEstimate(minGroupSize);
    Integer maxGroupSize = getInteger(row, "Occurrence.maxGroupSizeEstimate");
    if (maxGroupSize!=null) occ.setMaxGroupSizeEstimate(maxGroupSize);
    Double bestGroupSize = getDouble(row, "Occurrence.bestGroupSizeEstimate");
    if (bestGroupSize!=null) occ.setBestGroupSizeEstimate(bestGroupSize);

    Integer numCalves = getInteger(row, "Occurrence.numCalves");
    if (numCalves!=null) occ.setNumCalves(numCalves);
    Integer numJuveniles = getInteger(row, "Occurrence.numJuveniles");
    if (numJuveniles!=null) occ.setNumJuveniles(numJuveniles);


    Double bearing = getDouble(row, "Occurrence.bearing");
    if (bearing!=null) occ.setBearing(bearing);
    Double distance = getDouble(row, "Occurrence.distance");
    if (distance!=null) occ.setDistance(distance);

    Double swellHeight = getDouble(row, "Occurrence.swellHeight");
    if (swellHeight!=null) occ.setSwellHeight(swellHeight);
    String seaState = getString(row, "Occurrence.seaState");
    if (seaState==null) {
      Integer intSeaState = getInteger(row, "Occurrence.seaState");
      if (intSeaState!=null) seaState = intSeaState.toString();
    }
    if (seaState!=null) occ.setSeaState(seaState);
    Double visibilityIndex = getDouble(row, "Occurrence.visibilityIndex");
    if (visibilityIndex==null) {
      Integer visIndexInt = getIntFromMap(row, "Occurrence.visibilityIndex");
      if (visIndexInt!=null) visibilityIndex = visIndexInt.doubleValue();
    }
    if (visibilityIndex!=null) occ.setVisibilityIndex(visibilityIndex);

    Double transectBearing = getDouble(row, "Occurrence.transectBearing");
    if (transectBearing!=null) occ.setTransectBearing(transectBearing);
    String transectName = getString(row, "Occurrence.transectName");
    if (transectName!=null) occ.setTransectName(transectName);

    String initialCue = getString(row, "Occurrence.initialCue");
    String humanActivity = getString(row, "Occurrence.humanActivityNearby");
    if (humanActivity!=null) occ.setHumanActivityNearby(humanActivity);
    Double effortCode = getDouble(row, "Occurrence.effortCode");
    if (effortCode!=null) occ.setEffortCode(effortCode);

    Taxonomy taxy = loadTaxonomy0(row, myShepherd);
    if (taxy!=null) occ.addTaxonomy(taxy);

    Taxonomy taxy1 = loadTaxonomy1(row, myShepherd);
    if (taxy1!=null) occ.addTaxonomy(taxy1);

  	String surveyTrackVessel = getString(row, "SurveyTrack.vesselID");
  	if (surveyTrackVessel!=null) occ.setSightingPlatform(surveyTrackVessel);

  	Long millis = getLong(row, "Encounter.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.millis");
  	if (millis!=null) occ.setDateTimeLong(millis);

    String occurrenceRemarks = getString(row, "Encounter.occurrenceRemarks");
    if (occurrenceRemarks!=null) occ.addComments(occurrenceRemarks);

  	if (enc!=null) {
      occ.addEncounter(enc);
      // overwrite=false on following fromEncs methods
      occ.setLatLonFromEncs(false);
      occ.setSubmitterIDFromEncs(false);
    }

  	return occ;

  }

  public Encounter loadEncounter(Row row, ArrayList<Annotation> annotations, String context, Shepherd myShepherd) {

    // try to load encounter by indID and occID, make a new one if it doesn't exist.
    String individualID = getIndividualID(row);
    String occurrenceID = getOccurrenceID(row);
    Encounter enc = null;
    if (Util.stringExists(individualID) && Util.stringExists(occurrenceID)) enc = myShepherd.getEncounterByIndividualAndOccurrence(individualID, occurrenceID);

    if (enc!=null) enc.addAnnotations(annotations);
    else enc = new Encounter (annotations);

    if (occurrenceID!=null) enc.setOccurrenceID(occurrenceID);

  	// since we need access to the encounter ID
    String encID = enc.getCatalogNumber();
  	if (!Util.stringExists(encID)) {
      encID = Util.generateUUID();
  	  enc.setEncounterNumber(encID);
    }

    // Data source
    if (dataSource!=null) enc.setDataSource(dataSource);

  	// Time
  	Integer year = getInteger(row, "Encounter.year");
    if (year==null) year = getInteger(row, "Occurrence.year");
  	if (year!=null) enc.setYear(year);

  	Integer month = getInteger(row, "Encounter.month");
    if (month==null) month = getInteger(row, "Occurrence.month");
  	if (month!=null) enc.setMonth(month);

  	Integer day = getInteger(row, "Encounter.day");
    if (day==null) day = getInteger(row, "Occurrence.day");
  	if (day!=null) enc.setDay(day);

  	Integer hour = getInteger(row, "Encounter.hour");
    if (hour==null) hour = getInteger(row, "Occurrence.hour");
  	if (hour!=null) enc.setHour(hour);

  	String minutes = getIntAsString(row,"Encounter.minutes");
    if (minutes==null) minutes = getIntAsString(row, "Occurrence.minutes");
  	if (minutes!=null) enc.setMinutes(minutes);


    // setting milliseconds last means that (if provided) the exif/millis data will always take precedence
    // if we set it before, enc.setMinutes & others would reset millis
    Long millis = getLong(row, "Encounter.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.millis");
    boolean hasTimeCategories = (year!=null || month!=null || day!=null || hour!=null || minutes!=null);

    //added sanity check for millis between 1900 and 2100.. some excel was giving 0 for millis and making date stuff wierd
    if (millis!=null&&millis>-2208988800000L&&millis<4102444800000L) {
      if (hasTimeCategories) enc.setDateInMillisOnly(millis); // does not overwrite day/month/etc
      else enc.setDateInMilliseconds(millis);
    } 
    
    //depth
    Double depth = getDouble(row,"Encounter.depth");
    if(depth!=null) enc.setDepth(depth);
    


  	// Location
  	Double latitude = getDouble(row,"Encounter.latitude");
    if (latitude==null) latitude = getDouble(row,"Encounter.decimalLatitude");
    if (latitude==null) latitude = getDouble(row,"Occurrence.decimalLatitude");
    if (latitude==null) latitude = getDouble(row,"Encounter.decimalLatitiude");
    if (validCoord(latitude)) enc.setDecimalLatitude(latitude);

  	Double longitude = getDouble(row, "Encounter.longitude");
    if (longitude==null) longitude = getDouble(row,"Encounter.decimalLongitude");
    if (longitude==null) longitude = getDouble(row,"Occurrence.decimalLongitude");
    if (validCoord(longitude)) enc.setDecimalLongitude(longitude);

  	String locationID = getString(row, "Encounter.locationID");
  	if (Util.stringExists(locationID)) enc.setLocationID(locationID);

    String country = getString(row, "Encounter.country");
    if (country!=null) enc.setCountry(country);

  	// String fields
  	String otherCatalogNumbers = getStringOrInt(row, "Encounter.otherCatalogNumbers");
  	if (otherCatalogNumbers!=null) enc.setOtherCatalogNumbers(otherCatalogNumbers);

  	String sex = getString(row, "Encounter.sex");
  	if (sex!=null) enc.setSex(sex);

  	String genus = getString(row, "Encounter.genus");
  	if (genus!=null) enc.setGenus(genus);

  	String specificEpithet = getString(row, "Encounter.specificEpithet");
  	if (specificEpithet!=null) enc.setSpecificEpithet(specificEpithet);

  	String submitterOrganization = getString(row, "Encounter.submitterOrganization");
  	if (submitterOrganization!=null) enc.setSubmitterOrganization(submitterOrganization);

  	String submitterName = getString(row, "Encounter.submitterName");
  	if (submitterName!=null) enc.setSubmitterName(submitterName);

  	String patterningCode = getString(row, "Encounter.patterningCode");
  	if (patterningCode!=null) enc.setPatterningCode(patterningCode);

  	String occurrenceRemarks = getString(row, "Encounter.occurrenceRemarks");
  	if (occurrenceRemarks!=null) enc.setOccurrenceRemarks(occurrenceRemarks);


  	String submitterID = getString(row, "Encounter.submitterID");
    // don't commit this line
    if (submitterID==null) submitterID = defaultSubmitterID;
  	if (submitterID!=null) enc.setSubmitterID(submitterID);

  	String behavior = getString(row, "Encounter.behavior");
  	if (behavior!=null) enc.setBehavior(behavior);

  	String lifeStage = getString(row, "Encounter.lifeStage");
  	if (lifeStage!=null) enc.setLifeStage(lifeStage);

    String groupRole = getString(row, "Encounter.groupRole");
    if (groupRole!=null) enc.setGroupRole(groupRole);

    String researcherComments = getString(row, "Encounter.researcherComments");
    if (researcherComments!=null) enc.addComments(researcherComments);


  	String verbatimLocality = getString(row, "Encounter.verbatimLocality");
  	if (verbatimLocality!=null) enc.setVerbatimLocality(verbatimLocality);
  	

    
  	
  	
  	String nickname = getString(row, "MarkedIndividual.nickname");
    if (nickname==null) nickname = getString(row, "MarkedIndividual.nickName");
  	//if (nickname!=null) enc.setAlternateID(nickname);

    String alternateID = getString(row, "Encounter.alternateID");
    if (alternateID!=null) enc.setAlternateID(alternateID);

    /*
     * Start measurements import
     */
    List<String> measureVals=(List<String>)CommonConfiguration.getIndexedPropertyValues("measurement", context);
    List<String> measureUnits=(List<String>)CommonConfiguration.getIndexedPropertyValues("measurementUnits", context);
    int numMeasureVals=measureVals.size();
    for(int bg=0;bg<numMeasureVals;bg++){
      String colName="Encounter.measurement"+bg;
      Double val = getDouble(row, colName);
      if (val!=null) {
        Measurement valMeas = new Measurement(encID, measureVals.get(bg), val, measureUnits.get(bg), "");
        if (committing) enc.setMeasurement(valMeas, myShepherd);
        if (unusedColumns!=null) unusedColumns.remove(colName);
      }

    }
    /*
     * End measurements import
     */


    /*
     * Start Submitter imports
     */
     boolean hasSubmitters=true;
     int startIter=0;
     while(hasSubmitters){
       String colEmail="Encounter.submitter"+startIter+".emailAddress";
       String val=getString(row,colEmail);
       if(val!=null){
         if(myShepherd.getUserByEmailAddress(val.trim())!=null){
           User thisPerson=myShepherd.getUserByEmailAddress(val.trim());
           if((enc.getSubmitters()==null) || !enc.getSubmitters().contains(thisPerson)){
             if (committing) enc.addSubmitter(thisPerson);
             if (unusedColumns!=null) unusedColumns.remove(colEmail);
           }
         }
         else{
           //create a new User
           User thisPerson=new User(val.trim(),Util.generateUUID());
           if (committing) enc.addSubmitter(thisPerson);
           if (unusedColumns!=null) unusedColumns.remove(colEmail);

           String colFullName="Encounter.submitter"+startIter+".fullName";
           String val2=getString(row,colFullName);
           if(val2!=null) thisPerson.setFullName(val2.trim());
           if (unusedColumns!=null) unusedColumns.remove(colFullName);

           String colAffiliation="Encounter.submitter"+startIter+".affiliation";
           String val3=getString(row,colAffiliation);
           if(val3!=null) thisPerson.setAffiliation(val3.trim()); 
           if (unusedColumns!=null) unusedColumns.remove(colAffiliation);

         }
         startIter++;
       }
       else{
         hasSubmitters=false;
       }
     }

    /*
     * End Submitter imports
     */


     /*
      * Start Photographer imports
      */
      boolean hasPhotographers=true;
      startIter=0;
      while(hasPhotographers){
        String colEmail="Encounter.photographer"+startIter+".emailAddress";
        String val=getString(row,colEmail);
        if(val!=null){
          if(myShepherd.getUserByEmailAddress(val.trim())!=null){
            User thisPerson=myShepherd.getUserByEmailAddress(val.trim());
            if((enc.getPhotographers()==null) ||!enc.getPhotographers().contains(thisPerson)){
              if (committing) enc.addPhotographer(thisPerson);
              if (unusedColumns!=null) unusedColumns.remove(colEmail);
            }
          }
          else{
            //create a new User
            User thisPerson=new User(val.trim(),Util.generateUUID());
            if (committing) enc.addPhotographer(thisPerson);
            if (unusedColumns!=null) unusedColumns.remove(colEmail);

            String colFullName="Encounter.photographer"+startIter+".fullName";
            String val2=getString(row,colFullName);
            if(val2!=null) thisPerson.setFullName(val2.trim()); 
            if (unusedColumns!=null) unusedColumns.remove(colFullName);

            String colAffiliation="Encounter.photographer"+startIter+".affiliation";
            String val3=getString(row,colAffiliation);
            if(val3!=null) thisPerson.setAffiliation(val3.trim());
            if (unusedColumns!=null) unusedColumns.remove(colAffiliation);


          }
          startIter++;
        }
        else{
          hasPhotographers=false;
        }
      }

     /*
      * End Photographer imports
      */
     


  	String scar = getIntAsString(row, "Encounter.distinguishingScar");
  	if (scar!=null) enc.setDistinguishingScar(scar);


  	// SAMPLES
    TissueSample sample = null;
  	String tissueSampleID = getStringOrInt(row, "TissueSample.sampleID");
    // we need to make sure we have a sampleID whenever we have a microsat marker
    if (tissueSampleID==null) tissueSampleID = getStringOrInt(row, "MicrosatelliteMarkersAnalysis.analysisID");
    // same for sex analysis
    if (tissueSampleID==null) tissueSampleID = getStringOrInt(row, "SexAnalysis.processingLabTaskID");
System.out.println("tissueSampleID=(" + tissueSampleID + ")");
  	if (tissueSampleID!=null) {
      sample = myShepherd.getTissueSample(tissueSampleID, encID);
  		if (sample==null) sample = new TissueSample(enc.getCatalogNumber(), tissueSampleID);
  	}

    String markerAnalysisID = getStringOrInt(row, "MicrosatelliteMarkersAnalysis.analysisID");
    // we need to add uniqueness to the parsed string bc it's a primary key
    // but adding full encID is too long of a string.
    if (markerAnalysisID!=null) markerAnalysisID = markerAnalysisID+"-enc-"+encID.substring(0,Math.min(8,encID.length()));
    if (markerAnalysisID!=null && !myShepherd.isGeneticAnalysis(markerAnalysisID)) {
      markerAnalysisID = markerAnalysisID.replaceAll("_","-");
      MicrosatelliteMarkersAnalysis microMark = myShepherd.getMicrosatelliteMarkersAnalysis(markerAnalysisID);
      if (microMark==null) {
        microMark = new MicrosatelliteMarkersAnalysis(markerAnalysisID, tissueSampleID, encID);
        if (sample!=null) sample.addGeneticAnalysis(microMark);
      } // if microMark was grabbed from Shepherd correctly there is no further data to store.
    }

    String sexAnalID = getStringOrInt(row, "SexAnalysis.processingLabTaskID");
    String sexAnalSex = getString(row, "SexAnalysis.sex");
    if (sexAnalID!=null) {
      // we need to add uniqueness to the parsed string bc it's a primary key
      // but adding full encID is too long of a string.
      sexAnalID = sexAnalID+"-enc-"+encID.substring(0,Math.min(8,encID.length()));
      sexAnalID = sexAnalID.replaceAll("_","-");
    }
    if (sexAnalID!=null && sexAnalSex!=null && !myShepherd.isGeneticAnalysis(sexAnalID)) {
      SexAnalysis sexAnal = myShepherd.getSexAnalysis(sexAnalID);
      if (sexAnal==null) {
        sexAnal = new SexAnalysis(sexAnalID, sexAnalSex, encID, tissueSampleID);
        if (sample!=null) sample.addGeneticAnalysis(sexAnal);
      } else sexAnal.setSex(sexAnalSex);
    }

    if (sample!=null) enc.addTissueSample(sample);
    // END SAMPLES


  	String satelliteTag = getString(row, "SatelliteTag.serialNumber");
  	if (satelliteTag!=null) {
  		SatelliteTag tag = new SatelliteTag("",satelliteTag,""); //note the empty fields. sat tags are weird.
  		enc.setSatelliteTag(tag);
  	}

    String caudalType = getIntAsString(row, "Type caudale Mn");
    if (caudalType!=null) {
      enc.setDynamicProperty("caudal type",caudalType);
    }

  	enc.setState("approved");
  	return enc;
  }


  public Set<String> getColumnFieldsForClass(String className) {
  	Set<String> fieldNames = new HashSet<String>();
  	try {
  		for (String columnHeader: colIndexMap.keySet()) {
  			if (columnHeader.contains(className+".")) {
  				fieldNames.add(columnHeader.split(className+".")[1]); // for Encounter.date returns date
  			}	
  		}
  	} catch (Exception e) {}
  	return fieldNames;
  }

  public ArrayList<Annotation> loadAnnotations(Row row, Shepherd myShepherd) {

    AssetStore astore = getAssetStore(myShepherd);

  	//if (isFolderRow(row)) return loadAnnotationsFolderRow(row);
    ArrayList<Annotation> annots = new ArrayList<Annotation>();
  	for (int i=0; i<getNumMediaAssets(); i++) {
  		MediaAsset ma = getMediaAsset(row, i, astore, myShepherd);
  		if (ma==null) continue;

  		String species = getSpeciesString(row);
  		Annotation ann = new Annotation(species, ma);
  		ann.setIsExemplar(true);

      Double quality = getDouble(row, "Encounter.quality"+i);
      if (quality != null) ann.setQuality(quality);

      //ann.setMatchAgainst(true);
  		annots.add(ann);

  	}
  	if (annots.size()>0) {
      for (int i=0; i<annots.size(); i++) {
        String maName = "Encounter.mediaAsset"+i;
        String localPath = getString(row, maName);
        if (localPath!=null) foundPhotos.add(photoDirectory+"/"+localPath);
      }
  	}
    return annots;
  }

//   // for when the provided image filename is actually a folder of images
//   private ArrayList<Annotation> loadAnnotationsFolderRow(Row row, AssetStore astore, Shepherd myShepherd) {
//   	ArrayList<Annotation> annots = new ArrayList<Annotation>();
//   	String localPath = getString(row, "Encounter.mediaAsset0");
//   	if (localPath==null) return annots;
//   	localPath = localPath.substring(0,localPath.length()-1).trim(); // removes trailing asterisk
// //  	localPath = fixGlobiceFullPath(localPath)+"/";
// //  	localPath = localPath.replace(" ","\\ ");
//   	String fullPath = photoDirectory+localPath;
//   	fullPath = fullPath.replaceAll("//","/"); 
//   	System.out.println(fullPath);
//   	// Globice fix!
//   	// now fix spaces
//   	File photoDir = new File(fullPath);
//     if (!photoDir.exists()||!photoDir.isDirectory()||photoDir.listFiles()==null) {
//     	boolean itExists = photoDir.exists();
//     	boolean isDirectory = (itExists) && photoDir.isDirectory();
//     	boolean hasFiles = isDirectory && photoDir.listFiles()!=null;
//     	System.out.println("StandardImport ERROR: loadAnnotationsFolderRow called on non-directory (or empty?) path "+fullPath);
//     	System.out.println("		itExists: "+itExists);
//     	System.out.println("		isDirectory: "+isDirectory);
//     	System.out.println("		hasFiles: "+hasFiles);
      
//       feedback.addMissingPhoto(localPath);

//       return annots;
//     }


// 	  // if there are keywords we apply to all photos in encounter
// 	  String keyword0 = getString(row, "Encounter.keyword00");
// 	  Keyword key0 = (keyword0==null) ? null : myShepherd.getOrCreateKeyword(keyword0);
// 	  String keyword1 = getString(row, "Encounter.keyword01");
// 	  Keyword key1 = (keyword1==null) ? null : myShepherd.getOrCreateKeyword(keyword1);

// 	  String species = getSpeciesString(row);
// 	  for (File f: photoDir.listFiles()) {
// 	  	MediaAsset ma = null;
// 	  	try {
// 	  		JSONObject assetParams = astore.createParameters(f);
// 	  		System.out.println("		have assetParams");
// 	  		assetParams.put("_localDirect", f.toString());
// 	  		System.out.println("		about to create mediaAsset");
// 			  ma = astore.copyIn(f, assetParams);
// 	  	} catch (Exception e) {
// 	  		System.out.println("IOException creating MediaAsset for file "+f.getPath() + ": " + e.toString());
//         feedback.addMissingPhoto(localPath);

// 	  		continue; // skips the rest of loop for this file
// 	  	}
// 	  	if (ma==null) continue;
// 	  	if (key0!=null) ma.addKeyword(key0);
// 	  	if (key1!=null) ma.addKeyword(key1);
//   		Annotation ann = new Annotation(species, ma);
//   		ann.setIsExemplar(true);
//   		annots.add(ann);
// 	  }
// 	  if (annots.size()>0) foundPhotos.add(fullPath);
// 	  return annots;
//   }

  // 
  // capitolizes the final directory in path
  // private String fixGlobiceFullPath(String path) {
  // 	String fixed = capitolizeLastFilepart(path);
  // 	fixed = removeExtraGlobiceString(fixed);
  // 	return fixed;
  // }

  // private String removeExtraGlobiceString(String path) {
  // 	// we somehow got an extra instance of the word "globice" in the path string, right before a 1
  // 	return (path.replace("Globice1","1"));
  // }

  // private String capitolizeLastFilepart(String path) {
  // 	String[] parts = path.split("/");
  // 	String lastPart = parts[parts.length-1];
  // 	String firstPart = path.substring(0, path.indexOf(lastPart));
  // 	return firstPart + lastPart.toUpperCase();
  // }


  // // most rows have a single image, but some have an image folder
  // private boolean isFolderRow(Row row) {
  // 	String path = getString(row, "Encounter.mediaAsset0");
  // 	if (path==null) return false;
  // 	boolean ans = path.endsWith("*");
  // 	if (ans) numFolderRows++;
  // 	return ans;
  // }

  public String getSpeciesString(Row row) {
  	String genus = getString(row, "Encounter.genus");
  	String species = getString(row, "Encounter.specificEpithet");
		String total = genus+" "+species;
		if (total==null||total.equals(" ")) total = "unknown";
		return total;
  }

  public String getIndividualID(Row row) {
  	String indID =           getStringOrInt(row, "Encounter.individualID");
    if (indID==null) indID = getStringOrInt(row, "MarkedIndividual.individualID");
    if (!Util.stringExists(indID)) return indID;
    return individualPrefix+indID;
  }

  public MediaAsset getMediaAsset(Row row, int i, AssetStore astore, Shepherd myShepherd) {
    String localPath = getString(row, "Encounter.mediaAsset"+i);
  	if (localPath==null) return null;
  	localPath = Util.windowsFileStringToLinux(localPath);
  	//System.out.println("...localPath is: "+localPath);
  	String fullPath = photoDirectory+"/"+localPath;
  	fullPath = fullPath.replaceAll("//","/"); 
  	//System.out.println("...fullPath is: "+fullPath);
    String resolvedPath = resolveHumanEnteredFilename(fullPath);
    //System.out.println("getMediaAsset resolvedPath is: "+resolvedPath);
    if (resolvedPath==null) {
      missingPhotos.add(fullPath);
      foundPhotos.remove(fullPath);
      return null;
    }
	  File f = new File(resolvedPath);
            MediaAsset existMA = checkExistingMediaAsset(f);
            if (existMA != null) {
                if (!f.getName().equals(existMA.getFilename())) {
                    System.out.println("WARNING: got hash match, but DIFFERENT FILENAME for " + f + " with " + existMA + "; allowing new MediaAsset to be created");
                } else {
                    System.out.println("INFO: " + f + " got hash and filename match on " + existMA);
                    return existMA;
                }
            }

	  // create MediaAsset and return it
	  JSONObject assetParams = astore.createParameters(f);
	  assetParams.put("_localDirect", f.toString());
	  MediaAsset ma = null;
	  try {
	  	ma = astore.copyIn(f, assetParams);
	    // keywording

	    ArrayList<Keyword> kws = getKeywordForAsset(row, i, myShepherd);
	    if(kws!=null)ma.setKeywords(kws);
	  } 
	  catch (java.io.IOException ioEx) {
	  	System.out.println("IOException creating MediaAsset for file "+fullPath);
	  	missingPhotos.add(fullPath);
	  	foundPhotos.remove(fullPath);
                return null;
	  }
          myAssets.put(fileHash(f), ma);



	  // Keyword keyword = null;
	  // String keywordI = getString(row, "Encounter.keyword"+i);
	  // if (keywordI!=null) keyword = myShepherd.getOrCreateKeyword(keywordI);
	  // String keywordOIKey = "Encounter.keyword0"+i;
	  // String keywordOI = getString(row, keywordOIKey);
	  // if (keywordOI!=null) keyword = myShepherd.getOrCreateKeyword(keywordOI);
	  // if (keyword!=null) ma.addKeyword(keyword);
	  return ma;
  }


    //TODO in a perfect world, we would also check db for assets with same hash!!  but then we need a shepherd.  SIGH
    private MediaAsset checkExistingMediaAsset(File f) {
        String fhash = fileHash(f);
        if (fhash == null) return null;
System.out.println("use existing MA [" + fhash + "] -> " + myAssets.get(fhash));
        return myAssets.get(fhash);
    }

    // a gentle wrapper
    private String fileHash(File f) {
        if (f == null) return null;
        try {
            return Util.fileContentHash(f);
        } catch (IOException iox) {
            System.out.println("StandardImport.fileHash() ignorning " + f + " threw " + iox.toString());
        }
        return null;
    }

  private ArrayList<Keyword> getKeywordsForAsset(Row row, int n, Shepherd myShepherd) {

    ArrayList<Keyword> ans = new ArrayList<Keyword>();
    int maxAssets = getNumAssets(row);
    int maxKeywords=4;
    int stopAtKeyword = (maxAssets==(n+1)) ? maxKeywords : n; // 
    // we have up to 4 keywords per row.
    for (int i=n; i<=stopAtKeyword; i++) {
      String kwColName = "Encounter.keyword"+i;
      String kwName = getString(row, kwColName);
      if (kwName==null) {
        kwColName = "Encounter.keyword0"+i;
        kwName = getString(row, kwColName);
      }
      if (kwName==null) continue;
      Keyword kw = myShepherd.getOrCreateKeyword(kwName);
      if (kw!=null) ans.add(kw);
    }
    return ans;
  }

  private ArrayList<Keyword> getKeywordForAsset(Row row, int n, Shepherd myShepherd) {
    ArrayList<Keyword> ans = new ArrayList<Keyword>();
    String kwsName = getString(row, "Encounter.mediaAsset"+n+".keywords");
    //if keywords are just blobbed together with an underscore delimiter
    if(kwsName!=null) {
      StringTokenizer str=new StringTokenizer(kwsName,"_");
      while(str.hasMoreTokens()) {
        String kwString=str.nextToken();
        if(kwString!=null && !kwString.trim().equals("")) {
          Keyword kw = myShepherd.getOrCreateKeyword(kwString);
          if (kw!=null) ans.add(kw);
        }
        
      }
    } else {
      String kwColName = "Encounter.keyword"+n;
      String kwName = getString(row, kwColName);
      if (kwName==null) {
        kwColName = "Encounter.keyword0"+n;
        kwName = getString(row, kwColName);
      }
      if (kwName==null) return ans;
      Keyword kw = myShepherd.getOrCreateKeyword(kwName);
      if (kw!=null) ans.add(kw);
    }
    
    return ans;
  }

  // private int getNumAssets(Row row) {
  //   int n=0;
  //   while(getString(row,"Encounter.mediaAsset"+n)!=null) {n++;}
  //   return n;
  // }

  // Checks common human errors in inputing filenames
  // and returns the most similar filename that actually exists on the server
  // returns null if it cannot find a good string
  private String resolveHumanEnteredFilename(String fullPath) {
    if (Util.fileExists(fullPath))      return fullPath;

    String candidatePath = uppercaseJpg(fullPath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    candidatePath = noExtension(candidatePath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    String candidatePath2 = uppercaseBeforeJpg(candidatePath);
    if (Util.fileExists(candidatePath2)) return candidatePath2;

    candidatePath = lowercaseJpg(fullPath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    candidatePath = fixSpaceBeforeJpg(candidatePath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    candidatePath = fixSpaceBeforeDotJpg(candidatePath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    candidatePath = removeSpaceDashSpaceBeforeDot(candidatePath);
    if (Util.fileExists(candidatePath)) return candidatePath;

    return null;
  }

    //not sure how cool this is.  but probably same can be said about all this!
    private String noExtension(String filename) {
        if (filename.matches(".*\\.[^\\./]+$")) return filename;
        return filename + ".jpg";  // :(
    }

  private String uppercaseBeforeJpg(String filename) {
  	// uppercases the section between final slash and .jpg
  	if (filename==null) return null;
  	int indexOfDotJpg = filename.indexOf(".jpg");
  	if (indexOfDotJpg == -1) indexOfDotJpg = filename.indexOf(".JPG");
  	int indexOfLastSlash = filename.lastIndexOf("/");
  	if (indexOfDotJpg==-1 || indexOfLastSlash==-1) return filename;

  	String beforePart      = filename.substring(0,indexOfLastSlash+1);
  	String capitolizedPart = filename.substring(indexOfLastSlash+1,indexOfDotJpg).toUpperCase();
  	String afterPart       = filename.substring(indexOfDotJpg);

  	return (beforePart+capitolizedPart+afterPart);
  }

  private String lowercaseJpg(String filename) {
  	if (filename==null) return null;
  	return (filename.replace(".JPG",".jpg"));
  }
  private String uppercaseJpg(String filename) {
    if (filename==null) return null;
    return (filename.replace(".jpg",".JPG"));
  }
  private String fixSpaceBeforeJpg(String filename) {
    if (filename==null) return null;
    return (filename.replace(" jpg",".jpg"));
  }
  private String fixSpaceBeforeDotJpg(String filename) {
    if (filename==null) return null;
    return (filename.replace(" .jpg",".jpg"));
  }
  // private String removeTailingSpace(String filename) {
  // 	if (filename==null) return null;
  // 	return (filename.replace(" .jpg", ".jpg"));
  // }

  private String removeSpaceDashSpaceBeforeDot(String filename) {
    if (filename==null) return null;
    return (filename.replace(" - .", "."));
  }

	private int getNumMediaAssets() {
		setNumMediaAssets();
		return numMediaAssets.intValue();
	}
	private void setNumMediaAssets() {
		int numAssets = 0;
		for (String col: colIndexMap.keySet()) {
			if ((col != null) && (col.indexOf("mediaAsset")>-1)) numAssets++;
		}
		numMediaAssets=numAssets;
	}




  public MarkedIndividual loadIndividual(Row row, Encounter enc, Shepherd myShepherd, boolean committing, Map<String,MarkedIndividual> individualCache) {

  	boolean newIndividual = false;
  	String individualID = getIndividualID(row);
  	if (individualID==null) return null;

    
  	MarkedIndividual mark = individualCache.get(individualID);
    if (mark==null) mark = MarkedIndividual.withName(myShepherd, individualID, enc.getGenus(),enc.getSpecificEpithet());
  	if (mark==null) { // new individual
	    mark = new MarkedIndividual(enc);
	    if(committing) {
	      myShepherd.getPM().makePersistent(mark);
	       myShepherd.commitDBTransaction();
	       myShepherd.beginDBTransaction();
                mark.refreshNamesCache();
	       //out.println("persisting new individual");
	   }
	    newIndividual = true;
	  }
  	else {
  	  //out.println("Found a pre-existing Individual: "+mark.toString());
  	}
    // add the entered name, make sure it's attached to either the labelled organization, or fallback to the logged-in user
    Organization org = getOrganization(row, myShepherd);
    if (org!=null) mark.addName(individualID);
    //else mark.addName(request, individualID);
    else mark.addName(individualID);

	  if (mark==null) {
      out.println("StandardImport WARNING: weird behavior. Just made an individual but it's still null.");
      return mark;
    }

	  if (!newIndividual) {
	    mark.addEncounter(enc);
	    enc.setIndividual(mark);
	    System.out.println("loadIndividual notnew individual: "+mark.getDisplayName());
	  }
	  else {
	    enc.setIndividual(mark);
	  }
	  if(committing) {
	    myShepherd.commitDBTransaction();
	    myShepherd.beginDBTransaction();
	  }

    //String alternateID = getString(row, "Encounter.alternateID");
    //if (alternateID!=null) mark.setAlternateID(alternateID);

  	String nickname = getString(row, "MarkedIndividual.nickname");
    if (nickname==null) nickname = getString(row, "MarkedIndividual.nickName");
  	if (nickname!=null) mark.setNickName(nickname);

  	return mark;

  }


  // check if oldOcc is the same occurrence as the occurrence on this row
  // if so, return oldOcc. If not, return parseOccurrence(row)
  public Occurrence getCurrentOccurrence(Occurrence oldOcc, Row row, Shepherd myShepherd) {
  	String occID = getOccurrenceID(row);
  	if (oldOcc!=null && oldOcc.getOccurrenceID()!=null && oldOcc.getOccurrenceID().equals(occID)) return oldOcc;
  	Occurrence occ = myShepherd.getOrCreateOccurrence(occID);

    return occ;
    // if (isOccurrenceOnRow(oldOcc, row)) return oldOcc;
    // return parseOccurrence(row);
  }




  private void initColIndexVariables(Row firstRow) {
    colIndexMap = makeColIndexMap(firstRow);
    
  	unusedColumns = new HashSet<String>();
    //Set<String> col = colIndexMap.keySet();
  	// have to manually copy-in like this because keySet returns a POINTER (!!!)
  	for (String colName: colIndexMap.keySet()) {
      // length restriction removes colnames like "F21"
  		if (colName!=null && colName.length()>3) {
        unusedColumns.add(colName);
      } 
  	}
  }

  // Returns a map from each column header to the integer col number

  // i need to ensure we have all unused columns added to visible list
  private Map<String,Integer> makeColIndexMap(Row firstRow) {
  	Map<String,Integer> colMap = new HashMap<String, Integer>();
    numCols = firstRow.getLastCellNum();
    String[] headers = new String[numCols];
    System.out.println("We're making colIndexMap!");
  	for (int i=0; i<numCols; i++) {
      String colName = getStringNoLog(firstRow, i);
      System.out.println("Are there any values in this colum? "+i);
      if (colName==null || colName.length()<4 || !anyValuesInColumn(i)) {
        System.out.println("skipCols adding column named: "+colName+" with index "+i);
        skipCols.add(i);
        continue;
      }
      System.out.println("yes, "+colName+" has at least one value"); 
      headers[i] = colName;
  		colMap.put(colName, i);
    }

    feedback = new TabularFeedback(headers);
    System.out.println("headers = "+headers);
    System.out.println("feedback headers = "+feedback.colNames);
  	return colMap;
  }

  private boolean anyValuesInColumn(int colIndex) {
    int numRows = sheet.getLastRowNum();
    System.out.println("gimme that rowNum: "+numRows);
    for (int i=1; i<=numRows; i++) {
      System.out.println("checking row "+i+" for value presence");
      try {
        //String anyVal = sheet.getRow(i).getCell(colIndex).toString();

        Row row = sheet.getRow(i);
        //System.out.println("get ROW? "+row);
        Cell cell = row.getCell(colIndex);
        System.out.println("get CELL? "+cell);
        if (cell!=null) {
          String anyVal = cell.toString();
          System.out.println("get anyVal "+anyVal);
          if (anyVal!=null&&anyVal.length()>0&&!"".equals(anyVal)) {
            System.out.println("hey, anyValue! look at you. ---> "+anyVal);
            return true;
          }
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    return false;
  }

  public String getOccurrenceID(Row row) {
    // some custom data-fixing just for our aswn data blend
    String occID = getStringOrInt(row,"Occurrence.occurrenceID");
    if (!Util.stringExists(occID)) occID = getStringOrInt(row, "Encounter.occurrenceID");
    if (!Util.stringExists(occID)) return occID;
    occID = occID.replace("LiveVesselSighting","");
    return (occurrencePrefix+occID);
  }

  public boolean isOccurrenceOnRow(Occurrence occ, Row row) {
    return (occ!=null && !occ.getOccurrenceID().equals(getOccurrenceID(row)));
  }


  private static final Map<String, Integer> qualityMap = new HashMap<String, Integer>(){
    { // whoah, DOUBLE brackets! Java, you so crazy!
      put("No Data", null);
      put("Bad", 2);
      put("Fair", 3);
      put("Good", 4);
      put("Excellent", 5);
    }
  };

  public Integer getIntFromMap(Row row, String colName) {
    String key = getString(row, colName);
    if (key == null || !qualityMap.containsKey(key)) return null;
    return qualityMap.get(key);
  }

  // following 'get' functions swallow errors
  public Integer getInteger(Row row, int i) {
    try {
      int val = (int) row.getCell(i).getNumericCellValue();
      System.out.println("extracted int for line "+i);
      feedback.logParseValue(i, val, row);
      return new Integer( val );
    } catch (Exception e){
      // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading apostrophe in its stored value that prevents us from parsing it as a number.
      //e.printStackTrace();
      try {
        String str = getString(row, i); 
        System.out.println("Trying to get INTEGER????? ------> "+str);
        if (str==null) return null;
        try {
          Integer ans = Integer.parseInt(str);
          feedback.logParseValue(i, ans, row);
          return ans;
        } catch (Exception pe) {
          str = str.substring(1);
          Integer ans2 = Integer.parseInt(str);
          System.out.println("      getInteger SUBSTRINGED and got ans "+ans2);
          feedback.logParseValue(i, ans2, row);
          return ans2;
        }
      } catch (Exception e2) {e2.printStackTrace();}
    }
    feedback.logParseNoValue(i);
    return null;
  }

  public Long getLong(Row row, int i) {
    try {
      long val = (long) row.getCell(i).getNumericCellValue();
      System.out.println("extracted long for line "+i);
      feedback.logParseValue(i, val, row);
      return new Long( val );
    } catch (Exception e){
      //e.printStackTrace();      
      try {
        String str = getString(row, i);
        System.out.println("Did you get a long for this thing??? ----> "+str+" found on column "+i);
        if (str==null) return null;
        try {
          Long ans = Long.parseLong(str);
          feedback.logParseValue(i, ans, row);
          System.out.println("How about now????? ----> "+str);
          return ans;
        } catch (Exception pe) {
          str = str.substring(1);
          Long ans2 = Long.parseLong(str);
          System.out.println("      getLong SUBSTRINGED and got ans "+ans2);
          feedback.logParseValue(i, ans2, row);
          return ans2;
        }
      } catch (Exception e2) {e2.printStackTrace();}
    }
    feedback.logParseNoValue(i);
    return null;
  }

  public Double getDouble(Row row, int i) {
    String originalString = null; // i'd like to make a copy of what actually resides in the field for feedback before i try to crush it into a double
    try {
      // maybe things will just be perfect
      double val = row.getCell(i).getNumericCellValue();
      System.out.println("extracted double for line "+i);
      feedback.logParseValue(i, val, row);
      return new Double( val );
    } catch (Exception e) {
      // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading apostrophe in its stored value that prevents us from parsing it as a number.
      String str = null;
      try {
        originalString = getString(row, i);
        if (originalString==null||originalString.isEmpty()) {
          feedback.logParseNoValue(i);
          return null;
        }

        System.out.println("Trying to get a DOUBLE, removing non numeric characters ----> "+str+" on column "+i);
        str = removeNonNumeric(originalString);
        System.out.println("now have this: "+str);

        Double ans = Double.parseDouble(str);
        System.out.println("      getDouble string conversion got ans "+ans);
        feedback.logParseValue(i, ans, row);
        return ans;
      } catch (Exception pe) {
        pe.printStackTrace();
        System.out.println("failed parsing double: "+str);
        if (!stringIsDouble(str)) System.out.println("I'm sooo sure this isn't a double.");
        feedback.logParseError(i, originalString, row);
        return null;
      }
    }
  }

  public String getString(final Row row, final int i) {
    System.out.println("Calling getString on row "+i+" with cell "+String.valueOf(row.getCell(i)));
    final Cell cell = row.getCell(i);
    String str = null;
    try {
      if (cell!=null&&cell.getCellType()==Cell.CELL_TYPE_STRING) {
        System.out.println("Current cell: "+cell.toString()+" Current row: "+cell.getRowIndex()+" Current col: "+cell.getColumnIndex());
        str = cell.getStringCellValue();
      } else {
        // not ideal, but maybe get something 
        str = cell.toString();
      }
    } catch (Exception e) {
      // it should be basically impossible to get here. this is not a challenge.  
      feedback.logParseError(i, cell.toString(), row);
      e.printStackTrace();
    }
    if ("".equals(str) || str == null) {
      feedback.logParseNoValue(i);
      return null;
    }
    feedback.logParseValue(i, str, row); //todo: figure out why this line breaks the import
    return str;
  }

  public Boolean getBooleanFromString(Row row, int i) {
    try {
      String boolStr = getString(row, i).trim().toLowerCase();
      if (boolStr==null || boolStr.equals("")) {
        return null;
      } else {
        feedback.logParseValue(i, boolStr, row);
      }
      if (boolStr.equals("yes")) return new Boolean(true);
      if (boolStr.equals("no")) return new Boolean(false);
    } catch (Exception e) {}
    feedback.logParseNoValue(i);
    return null;
  }

  public Date getDate(Row row, int i) {
    try {
      Date date = row.getCell(i).getDateCellValue();
      feedback.logParseValue(i, date, row);
      return date;
    } catch (Exception e) {}
    feedback.logParseNoValue(i);
    return null;
  }

  public DateTime getDateTime(Row row, int i) {
    Date date = getDate(row, i);
    if (date == null) return null;
    return new DateTime(date);
  }

  // Below methods are *not* static and work from column names rather than column numbers
  // IMPORTANT: ONLY WORKS IF colIndexMap HAS BEEN INITIALIZED
  public String getString(Row row, String colName) {
    if (!colIndexMap.containsKey(colName)) {
      if (verbose) missingColumns.add(colName);
      return null;
    }
    System.out.println("getString colName = "+colName);
    String ans = getString(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public String getIntAsString(Row row, String colName) {
  	Integer i = getInteger(row,colName);
    if (i==null) return null;
    System.out.println("getIntAsString colName = "+colName);
  	return i.toString();
  }

  public String getStringOrInt(Row row, String colName) {
    if (!colIndexMap.containsKey(colName)) {
      if (verbose) missingColumns.add(colName);
      return null;
    }
    System.out.println("getStringOrInt colName = "+colName);
    String ans = getStringOrInt(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public String getStringOrInt(Row row, int i) {
    String ans = null;
    try {
      ans = getString(row, i);
      if (ans==null) {
        Integer inty = getInteger(row,i);
        if (inty!=null) ans = String.valueOf(inty);
      } 
    } catch (IllegalStateException ise) {}
    return ans;
  }
  
  public Organization getOrganization(Row row, Shepherd myShepherd) {
    String orgID = getString(row, "Encounter.submitterOrganization");
    if (orgID==null) return null;
    Organization org = myShepherd.getOrCreateOrganizationByName(orgID, committing);
    return org;
  }

  // public Integer getInteger(Row row, String colName) {
  //   Integer ans = null;
  //   if (colIndexMap.containsKey(colName)) {
  //     int i = colIndexMap.get(colName);
  //     if (isCellBlank(row, i)) {
  //       feedback.logParseNoValue(i);
  //       return null;
  //     }  
  //     ans = getInteger(row, i);
  //     if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
  //   } else {      
  //     if (verbose) missingColumns.add(colName);
  //     return null;
  //   }
  //   return ans;
  // }


  public Integer getInteger(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
    }
    System.out.println("getInteger colName = "+colName);
    Integer ans = getInteger(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public Long getLong(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
    }
    System.out.println("getLong colName = "+colName);
    Long ans = getLong(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public Double getDouble(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
    }
    System.out.println("getDouble colName = "+colName);
    Double ans = getDouble(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public Date getDate(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
    }
    System.out.println("getDate colName = "+colName);
    Date ans = getDate(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  public DateTime getDateTime(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
    }
    System.out.println("getDateTime colName = "+colName);
    DateTime ans = getDateTime(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }

  // PARSING UTILITIES


  private static boolean stringIsDouble(String str) {
    if (str.contains(".")&&stringIsNumber(str)) {
      return true;
    }
    return false;
  }

  private static boolean stringIsNumber(String str) {
    if (str==null||str.trim().length()<1) return false;
    str = str.trim();
    if (str.matches(".*\\d.*")) {
      return true;
    }
    return false;
  }

  private static String removeNonNumeric(String str) throws NullPointerException {
    str = str.trim();
    return str.replaceAll("[^\\d.]", "");
  }


  // Apache POI, shame on you for making me write this. Shame! Shame! Shame! SHAME!
  // (as if I actually wrote this. thanks stackoverflow!)
  public static boolean isRowEmpty(Row row) {
    if (row == null) return true;
    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
        Cell cell = row.getCell(c);
        if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK)
            return false;
    }
    return true;
	}


  public static boolean isBlank(Cell cell) {
    return (
       cell == null ||
       cell.getCellType() == Cell.CELL_TYPE_BLANK ||
      (cell.getCellType() == Cell.CELL_TYPE_STRING && cell.getStringCellValue().isEmpty())
    );
  }

  public static boolean isCellBlank(Row row, int i) {
    return (row==null || isBlank(row.getCell(i)));
  }


  // PARSING UTILITIES

	// This would be cool to put in Encounter or something.
	// tho I'm not immediately sure how we'd get the url context, or determine if we want to include /encounters/ or not
	public static String getEncounterURL(Encounter enc) {
		if (enc==null || enc.getCatalogNumber()==null) return null;
		return "encounters/encounter.jsp?number="+enc.getCatalogNumber();
	}

	// gives us a nice link if we're
	public String getEncounterDisplayString(Encounter enc) {
		if (enc==null) return null;
		if (committing) {
			return "<a href=\""+getEncounterURL(enc)+"\" >"+enc.toString()+"</a>";
		}
		return enc.toString();
	}


  private AssetStore  getAssetStore(Shepherd myShepherd) {

    return AssetStore.getDefault(myShepherd);
    //return AssetStore.get(myShepherd, 1);

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


    //returns file so you can use .getName() or .lastModified() etc
    public static File importXlsFile(String rootDir) {
        File dir = new File(rootDir, "import");
        try {
            for (final File f : dir.listFiles()) {
                if (f.isFile() && f.getName().matches("WildbookStandardFormat.*\\.xlsx")) return f;
            }
        } catch (Exception ex) {
            System.out.println("ERROR: importXlsFile() rootDir=" + rootDir + " threw " + ex.toString());
            return null;
        }
        System.out.println("WARNING: importXlsFile() could not find 'WildbookStandardFormat*.xlsx' in " + dir);
        return null;
    }



    // FEEDBACK CLASSES - May break out -- 


    private class TabularFeedback {

      //Set<String> unusedColumns;
      //Set<String> missingColumns; // columns we look for but don't find
      List<String> missingPhotos = new ArrayList<String>();
      List<String> foundPhotos = new ArrayList<String>();
  
      String[] colNames;
  
      RowFeedback currentRow;
  
      public TabularFeedback(String[] colNames) {
        this.colNames = colNames;
        missingPhotos = new ArrayList<String>();
        foundPhotos = new ArrayList<String>();
        currentRow=null; // must be manually initialized during row loop with startRow
      }
  
      public void startRow(Row row, int i) {
        currentRow = new RowFeedback(row, i);
        System.out.println("StartRow called for i="+i);
      }
  
      public void addMissingPhoto(String localPath) {
        missingPhotos.add(localPath);
      }

      public void addFoundPhoto(String localPath) {
        foundPhotos.add(localPath);
      }
  
      public void printMissingPhotos() {
        if (!isUserUpload) {
          out.println("<h2><em>Missing photos</em>("+missingPhotos.size()+"):</h2><ul>");
          for (String photo: missingPhotos) {
            out.println("<li>"+photo+"</li>");
          }
          out.println("</ul>");
        } 
      }
  
      public void printRow() {
        System.out.println("Starting to printRow");
        out.println(currentRow);
        //System.out.println(currentRow);
        System.out.println("Done with printRow");
      }
    
      public void printFoundPhotos() {
        if (!isUserUpload) {
          out.println("<h2><em>Found photos</em>("+foundPhotos.size()+"):</h2><ul>");
          for (String photo: foundPhotos) {
            out.println("<li>"+photo+"</li>");
          }
          out.println("</ul>");
        }
      }
  
      public void printStartTable() {
        out.println("<div class=\"tableFeedbackWrapper\"><table class=\"tableFeedback\">");
        out.println("<tr class=\"headerRow\"><th class=\"rotate\"><div><span><span></div></th>"); // empty header cell for row # column
        System.out.println("HEY YOU! You damn well better see a line below this");
        boolean isNull = (colNames==null);
        System.out.println("colNames isNull "+isNull);
        System.out.println("starting to print table. num colNames="+colNames.length+" and the array itself = "+colNames);
        for (int i=0;i<colNames.length;i++) {
          if (skipCols.contains(i)) continue; 
          out.println("<th class=\"rotate\"><div><span class=\"tableFeedbackColumnHeader\">"+colNames[i]+"</span></div></th>");
        }
        System.out.println("done printing start table");
        out.println("</tr>");
      }
      public void printEndTable() {
        out.println("</table></div>");
      }
  
      public void logParseValue(int colNum, Object value, Row row) {
        System.out.println("TabularFeedback.logParseValue called on object: "+value+" and colNum "+colNum);
        if (value==null||String.valueOf(value)=="") {
          this.currentRow.logParseNoValue(colNum);
        }
        this.currentRow.logParseValue(colNum, value, row);
      }

      public void logParseError(int colNum, Object value, Row row) {
        this.currentRow.logParseError(colNum, value, row);
      }

      public void logParseNoValue(int colNum) {
        this.currentRow.logParseNoValue(colNum);
      }
  
      public String toString() {
        return "Tabular feedback with "+colNames.length+" columns, on row "+currentRow.num;
      }
  
    }
  
    private class RowFeedback {
      CellFeedback[] cells;
      public int num;
  
      //String checkingInheritance = uploadDirectory;
  
      public RowFeedback(Row row, int num) {
        this.num=num;
        this.cells = new CellFeedback[numCols];
      }
  
      public String toString() {
        StringBuffer str = new StringBuffer();
        str.append("<tr>");
        str.append("<td>"+num+"</td>");

        for (int i=0;i<cells.length; i++) {
          if (skipCols.contains(i)) {
            System.out.println("skipping this col for feedback, index "+i+" was present in skipCols");
            continue; 
          }
          CellFeedback cell = cells[i];
          if (cell==null) str.append(nullCellHtml());
          else str.append(cell.html());
        }
        str.append("</tr>"); 
        return str.toString();
      }
  
      public void logParseValue(int colNum, Object value, Row row) {
        System.out.println("RowFeedback.logParseValue on an object: "+value+" with colNum "+colNum);
        if (value==null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
          System.out.println("RowFeedback.logParseValue on a NULL OBJECT: trying to recover a value, or log empty");
          String valueString = getCellValueAsString(row, colNum);
          if (valueString==null||"".equals(valueString.trim())) {
            logParseNoValue(colNum);
            return;
          } 
          logParseError(colNum, valueString, row);
          return;
        }
        this.cells[colNum] = new CellFeedback(value, true, false);
      }


      public void logParseError(int colNum, Object value, Row row) {
        this.cells[colNum] = new CellFeedback(value, false, false);
      }
      public void logParseNoValue(int colNum) {
        this.cells[colNum] = new CellFeedback(null, true, true);
      }

    }

    public String getStringNoLog(Row row, int i) {
      String str = null;
      try {
        str = row.getCell(i).getStringCellValue().trim();
        if (str.equals("")) return null;
      }
      catch (Exception e) {}
      return str;
    }
  
  
    // cannot put this inside CellFeedback bc java inner classes are not allowed static methods or vars (this is stupid).
    static String nullCellHtml() {
      return "<td class=\"cellFeedback null\" title=\"The importer was unable to retrieve this cell, or it did not exist. This is possible if it is a duplicate column, it relies on another column, or only some rows contain the cell. You may proceed if this cell OK to ignore.\"><span></span></td>";
    }
  
    class CellFeedback {
  
      // These two booleans cover the 3 possible states of a cell:
      // 1: successful parse (T,F), 2:no value provided (T,T), 3: unsuccessful parse with a value provided (F,F).
      public boolean success;
      public boolean isBlank;
      String valueStr;
  
  
      public CellFeedback(Object value, boolean success, boolean isBlank) {
        System.out.println("about to create cellFeedback for value "+value);
        if (value == null) valueStr = null;
        else valueStr = value.toString();
        this.success = success;
        this.isBlank = isBlank;
        System.out.println("new cellFeedback: got valueStr "+valueStr+" success: "+success+" and isBlank: "+isBlank);
      }
      public String html() { // here's where we add the excel value string on errors
        StringBuffer str = new StringBuffer();
        str.append("<td class=\"cellFeedback "+classStr()+"\" title=\""+titleStr()+"\"><span>");
        if (Util.stringExists(valueStr)) {
          str.append(valueStr);
        }
        str.append("</span></td>");
        return str.toString();
      }
  
      public String classStr() {
        if (isBlank) return "blank";
        if (!success) return "error";
        return "success";
      }
  
      public String titleStr() {
        if (isBlank) return "Cell was blank in excel file.";
        if (!success) return "ERROR: The import was unable to parse this cell. Please ensure that there are not letters or special characters in number fields (ex. lat/lon text or degree mark), formulas where there should be values or other data inconsistencies.";
        return "Successfully parsed value from excel file.";    
      }
  
  
    }

      /**
     * h/t http://www.java-connect.com/apache-poi-tutorials/read-all-type-of-excel-cell-value-as-string-using-poi/
     * gripe: apache POI is a shit excel library if GETTING A STRING FROM A CELL TAKES 30 $@(%# LINES OF CODE
     */
  public static String getCellValueAsString(Cell cell) {
    String strCellValue = null;
    if (cell != null) {
      try {
        switch (cell.getCellType()) {
        case Cell.CELL_TYPE_STRING:
          strCellValue = cell.toString();
          break;
        case Cell.CELL_TYPE_NUMERIC:
          if (DateUtil.isCellDateFormatted(cell)) {
            SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy");
            strCellValue = dateFormat.format(cell.getDateCellValue());
          } else {
            Double value = cell.getNumericCellValue();
            Long longValue = value.longValue();
            strCellValue = new String(longValue.toString());
          }
          break;
        case Cell.CELL_TYPE_BOOLEAN:
          strCellValue = new String(new Boolean(
                  cell.getBooleanCellValue()).toString());
          break;
        case Cell.CELL_TYPE_BLANK:
          strCellValue = "";
          break;
        }
    } catch (Exception parseError) {
      strCellValue = "<em>parse error</em>";
    }
  }
  return strCellValue;
}

public static String getCellValueAsString(Row row, int num) {
  if (row==null || row.getCell(num)==null) return "";
  return getCellValueAsString(row.getCell(num));
}


  // END FEEDBACK CLASSES
  public String fileInDir(String filename, String directoryPath) {
    if (directoryPath.endsWith("/")) return (directoryPath+filename);
    return (directoryPath+"/"+filename); 
  }

}
