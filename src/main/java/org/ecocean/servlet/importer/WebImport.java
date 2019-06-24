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
import java.text.SimpleDateFormat;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.media.*;
import org.ecocean.genetics.*;
import org.ecocean.tag.SatelliteTag;
import org.ecocean.resumableupload.UploadServlet;

import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DateUtil;
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

public class WebImport extends HttpServlet {

	// variables shared by any single import instance
	Shepherd myShepherd;
	Map<String,Integer> colIndexMap = new HashMap<String, Integer>();
	int numFolderRows = 0;
	boolean committing = false;
	PrintWriter out;
	// verbose variable can be switched on/off throughout the import for debugging
	boolean verbose = false;
	String uploadDirectory = "/data/upload/";

  Set<String> unusedColumns;
  Set<String> missingColumns; // columns we look for but don't find
  List<String> foundPhotos = new ArrayList<String>();

  TabularFeedback feedback;

  // need to initialize (initColIndexVariables()), this is useful to have everywhere
  int numCols;

	private AssetStore astore;

	// just for lazy loading a var used on each row
	Integer numMediaAssets;

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }

  public String fileInDir(String filename, String directoryPath) {
    if (directoryPath.endsWith("/")) return (directoryPath+filename);
    return (directoryPath+"/"+filename); 
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException {
    System.out.println("WEB-IMPORT: beginning doPost");
    String context="context0";
    context=ServletUtilities.getContext(request);

    uploadDirectory = UploadServlet.getUploadDir(request);

    myShepherd = new Shepherd(context);
    out = response.getWriter();
    astore = getAssetStore(myShepherd);

    if (request.getCharacterEncoding() == null) {
      request.setCharacterEncoding("utf-8");
    }
    response.setContentType("text/html; charset=UTF-8");
    this.getServletContext().getRequestDispatcher("/header.jsp").include(request, response);
    this.getServletContext().getRequestDispatcher("/import/uploadHeader.jsp").include(request, response);

    // uploadDirectory = "/data/oman_import/photos/";
    // String filename = "/data/oman_import/ASWN_secondExport.xlsx";
    String subdir = ServletUtilities.getParameterOrAttribute("subdir", request);
    System.out.println("WebImport got subdir "+subdir);
    if (Util.stringExists(subdir)) uploadDirectory = fileInDir(subdir, uploadDirectory); // need slash
    String filename = request.getParameter("filename");
    System.out.println("WebImport got uploadDirectory "+uploadDirectory);
    String fullPath = fileInDir(filename, uploadDirectory);
    File dataFile = new File(fullPath);
    boolean dataFound = (dataFile!=null && dataFile.exists());
    System.out.println("WEB-IMPORT: dataFound="+dataFound+" and full path = "+fullPath);
    if (dataFound) doImport(filename, dataFile, request, response);
    else out.println("No data file was found at file "+filename);

    this.getServletContext().getRequestDispatcher("/import/uploadFooter.jsp").include(request, response);
    this.getServletContext().getRequestDispatcher("/footer.jsp").include(request, response);


  }

  public void doImport(String filename, File dataFile, HttpServletRequest request, HttpServletResponse response) {
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
    Sheet sheet = wb.getSheetAt(0);


    int numSheets = wb.getNumberOfSheets();
    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    int rows = sheet.getPhysicalNumberOfRows();; // No of rows
    Row firstRow = sheet.getRow(0);

    initColIndexVariables(firstRow); // IMPORTANT: this initializes the TabularFeedback

    int cols = firstRow.getPhysicalNumberOfCells(); // No of columns
    int lastColNum = firstRow.getLastCellNum();


    int printPeriod = 1;
    if (committing) myShepherd.beginDBTransaction();
    out.println("<h2>Parsed Import Table</h2>"); 
    System.out.println("debug0");
    System.out.println("feedback headers = "+feedback.colNames);
    feedback.printStartTable();
    System.out.println("debug1");
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

        // here's the central logic
        ArrayList<Annotation> annotations = loadAnnotations(row);
        Encounter enc = loadEncounter(row, annotations);
        occ = loadOccurrence(row, occ, enc);
        mark = loadIndividual(row, enc);

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
        
      }
      catch (Exception e) {
        out.println("Encountered an error while importing the file.");
        e.printStackTrace(out);
        myShepherd.rollbackDBTransaction();
      }
    }
    feedback.printEndTable();

    out.println("<h2>File Overview: </h2>");
    out.println("<ul>");
    out.println("<li>Filename: "+filename+"</li>");
    out.println("<li>File found = "+dataFound+"</li>");
    out.println("<li>Num Sheets = "+numSheets+"</li>");
    out.println("<li>Num Rows = "+physicalNumberOfRows+"</li>");    
    out.println("<li>Num Cols = "+cols+"</li>");
    out.println("<li>Last col num = "+lastColNum+"</li>");
    out.println("<li><em>committing = "+committing+"</em></li>");
    out.println("</ul>");




    out.println("<h2><em>UNUSED</em> Column headings ("+unusedColumns.size()+"):</h2><ul>");
    for (String heading: unusedColumns) {
      out.println("<li>"+heading+"</li>");
    }
    out.println("</ul>");


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

  public Taxonomy loadTaxonomy0(Row row) {
    String sciName = getString(row, "Taxonomy.scientificName");
    if (sciName==null) return null;
    Taxonomy taxy = myShepherd.getOrCreateTaxonomy(sciName);
    String commonName = getString(row, "Taxonomy.commonName");
    if (commonName!=null) taxy.addCommonName(commonName);
    return taxy;
  }

  public Taxonomy loadTaxonomy1(Row row) {
    String sciName = getString(row, "Occurrence.taxonomy1");
    if (sciName==null) return null;
    return myShepherd.getOrCreateTaxonomy(sciName);
  }

  public static boolean validLatLon(Double lat, Double lon) {
    return ((lat!=null) && (lon!=null) && ((lat!=0.0)&&(lon!=0.0)) );
  }

  public Occurrence loadOccurrence(Row row, Occurrence oldOcc, Encounter enc) {
  	
  	Occurrence occ = getCurrentOccurrence(oldOcc, row);
  	// would love to have a more concise way to write following couplets, c'est la vie

  	Double seaSurfaceTemp = getDouble (row, "Occurrence.seaSurfaceTemperature");
  	if (seaSurfaceTemp != null) occ.setSeaSurfaceTemp(seaSurfaceTemp);

  	Integer individualCount = getInteger(row, "Occurrence.individualCount");
  	if (individualCount!=null) occ.setIndividualCount(individualCount);

  	Double decimalLatitiude = getDouble(row, "Encounter.decimalLatitiude");
  	if (decimalLatitiude!=null) occ.setDecimalLatitude(decimalLatitiude);

  	Double decimalLatitude = getDouble(row, "Encounter.decimalLatitude");
  	Double decimalLongitude = getDouble(row, "Encounter.decimalLongitude");

    if (validLatLon(decimalLatitude,decimalLongitude)) {
      occ.setDecimalLatitude(decimalLatitude);
      occ.setDecimalLongitude(decimalLongitude);
    }

  	String fieldStudySite = getString(row, "Occurrence.fieldStudySite");
  	if (fieldStudySite!=null) occ.setFieldStudySite(fieldStudySite);

  	String groupComposition = getString(row, "Occurrence.groupComposition");
  	if (groupComposition!=null) occ.setGroupComposition(groupComposition);

  	String fieldSurveyCode = getString(row, "Survey.id");
    if (fieldSurveyCode==null) fieldSurveyCode = getString(row, "Occurrence.fieldSurveyCode");
  	if (fieldSurveyCode!=null) occ.setFieldSurveyCode(fieldSurveyCode);

  	String sightingPlatform = getString(row, "Survey.vessel");
    if (sightingPlatform==null) sightingPlatform = getString(row, "Platform Designation");
  	if (sightingPlatform!=null) occ.setSightingPlatform(sightingPlatform);
    String surveyComments = getString(row, "Survey.comments");
    if (surveyComments!=null) occ.addComments(surveyComments);

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
    if (seaState!=null) occ.setSeaState(seaState);
    Double visibilityIndex = getDouble(row, "Occurrence.visibilityIndex");
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

    Taxonomy taxy = loadTaxonomy0(row);
    if (taxy!=null) occ.addTaxonomy(taxy);

    Taxonomy taxy1 = loadTaxonomy1(row);
    if (taxy1!=null) occ.addTaxonomy(taxy1);

  	String surveyTrackVessel = getString(row, "SurveyTrack.vesselID");
  	if (surveyTrackVessel!=null) occ.setSightingPlatform(surveyTrackVessel);

  	Long millis = getLong(row, "Encounter.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.dateInMilliseconds");
    if (millis==null) millis = getLong(row, "Occurrence.millis");
  	if (millis!=null) occ.setDateTimeLong(millis);

  	if (enc!=null) {
      occ.addEncounter(enc);
      // overwrite=false on following fromEncs methods
      occ.setLatLonFromEncs(false);
      occ.setSubmitterIDFromEncs(false);
    }

  	return occ;

  }

  public Encounter loadEncounter(Row row, ArrayList<Annotation> annotations) {
  	Encounter enc = new Encounter (annotations);

  	// since we need access to the encounter ID
  	String encID = Util.generateUUID();
  	enc.setEncounterNumber(encID);

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
    if (millis!=null) {
      if (hasTimeCategories) enc.setDateInMillisOnly(millis); // does not overwrite day/month/etc
      else enc.setDateInMilliseconds(millis);
    } 


  	// Location
  	Double latitude = getDouble(row,"Encounter.latitude");
    if (latitude==null) latitude = getDouble(row,"Encounter.decimalLatitude");
    if (latitude==null) latitude = getDouble(row,"Occurrence.decimalLatitude");

  	Double longitude = getDouble(row, "Encounter.longitude");
    if (longitude==null) longitude = getDouble(row,"Encounter.decimalLongitude");
    if (longitude==null) longitude = getDouble(row,"Occurrence.decimalLongitude");

    if (validLatLon(latitude,longitude)) {
      enc.setDecimalLatitude(latitude);
      enc.setDecimalLongitude(longitude);
    }


  	String locationID = getString(row, "Encounter.locationID");
  	if (locationID!=null) enc.setLocationID(locationID);

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

  	Integer patterningCode = getInteger(row, "Encounter.patterningCode");
  	if (patterningCode!=null) enc.setFlukeType(patterningCode);

  	String occurrenceRemarks = getString(row, "Encounter.occurrenceRemarks");
  	if (occurrenceRemarks!=null) enc.setOccurrenceRemarks(occurrenceRemarks);

  	String occurrenceID = getString(row, "Encounter.occurrenceID");
  	if (occurrenceID==null) occurrenceID = getString(row, "Occurrence.occurrenceID");
  	if (occurrenceID!=null) enc.setOccurrenceID(occurrenceID);

  	String submitterID = getString(row, "Encounter.submitterID");
  	if (submitterID!=null) enc.setSubmitterID(submitterID);

  	String behavior = getString(row, "Encounter.behavior");
  	if (behavior!=null) enc.setBehavior(behavior);

/*  should not need this now, as (MarkedIndividual)mark has encounter added
  	String individualID = getIndividualID(row);
  	if (individualID!=null) enc.setIndividualID(individualID);
*/

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
  	if (nickname!=null) enc.setAlternateID(nickname);

    String alternateID = getString(row, "Encounter.alternateID");
    if (alternateID!=null) enc.setAlternateID(alternateID);

  	Double length = getDouble(row, "Encounter.measurement.length");
  	if (length!=null) {
  		Measurement lengthMeas = new Measurement(encID, "length", length, "m", "");
  		if (committing) enc.setMeasurement(lengthMeas, myShepherd);
  	}

  	Double weight = getDouble(row, "Encounter.measurement.weight");
  	if (weight!=null) {
  		Measurement weightMeas = new Measurement(encID, "weight", weight, "kg", "");
  		if (committing) enc.setMeasurement(weightMeas, myShepherd);
  	}

  	Double depth = getDouble(row, "Encounter.depth");
  	if (depth!=null) enc.setDepth(depth);

  	String scar = getIntAsString(row, "Encounter.distinguishingScar");
  	if (scar!=null) enc.setDistinguishingScar(scar);


  	// SAMPLES
    TissueSample sample = null;
  	String tissueSampleID = getStringOrInt(row, "TissueSample.sampleID");
    // we need to make sure we have a sampleID whenever we have a microsat marker
    if (tissueSampleID==null) tissueSampleID = getStringOrInt(row, "MicrosatelliteMarkersAnalysis.analysisID");
    // same for sex analysis
    if (tissueSampleID==null) tissueSampleID = getStringOrInt(row, "SexAnalysis.processingLabTaskID");
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

  public ArrayList<Annotation> loadAnnotations(Row row) {

  	if (isFolderRow(row)) return loadAnnotationsFolderRow(row);
  	ArrayList<Annotation> annots = new ArrayList<Annotation>();
  	for (int i=0; i<getNumMediaAssets(); i++) {
  		MediaAsset ma = getMediaAsset(row, i);
  		if (ma==null) continue;

  		String species = getSpeciesString(row);
  		Annotation ann = new Annotation(species, ma);
  		ann.setIsExemplar(true);
  		annots.add(ann);
  		//if (ma!=null && ma.localPath()!=null) foundPhotos.add(ma.localPath().toString());
  	}
  	if (annots.size()>0) {
      for (int i=0; i<annots.size(); i++) {
        String maName = "Encounter.mediaAsset"+i;
        String localPath = getString(row, maName);
        if (localPath!=null) feedback.addFoundPhoto(fileInDir(localPath, uploadDirectory));
      }
  	}
  	return annots;
  }

  // for when the provided image filename is actually a folder of images
  private ArrayList<Annotation> loadAnnotationsFolderRow(Row row) {
  	ArrayList<Annotation> annots = new ArrayList<Annotation>();
  	String localPath = getString(row, "Encounter.mediaAsset0");
  	if (localPath==null) return annots;
  	localPath = localPath.substring(0,localPath.length()-1); // removes trailing asterisk
  	localPath = fixGlobiceFullPath(localPath)+"/";
//  	localPath = localPath.replace(" ","\\ ");
  	String fullPath = fileInDir(localPath, uploadDirectory);
  	// Globice fix!
  	// now fix spaces
  	File uploadDir = new File(fullPath);
    if (!uploadDir.exists()||!uploadDir.isDirectory()||uploadDir.listFiles()==null) {
    	boolean itExists = uploadDir.exists();
    	boolean isDirectory = (itExists) && uploadDir.isDirectory();
    	boolean hasFiles = isDirectory && uploadDir.listFiles()!=null;
    	System.out.println("StandardImport ERROR: loadAnnotationsFolderRow called on non-directory (or empty?) path "+fullPath);
    	System.out.println("		itExists: "+itExists);
    	System.out.println("		isDirectory: "+isDirectory);
    	System.out.println("		hasFiles: "+hasFiles);
      feedback.addMissingPhoto(localPath);
      return annots;
    }


	  // if there are keywords we apply to all photos in encounter
	  String keyword0 = getString(row, "Encounter.keyword00");
	  Keyword key0 = (keyword0==null) ? null : myShepherd.getOrCreateKeyword(keyword0);
	  String keyword1 = getString(row, "Encounter.keyword01");
	  Keyword key1 = (keyword1==null) ? null : myShepherd.getOrCreateKeyword(keyword1);

	  String species = getSpeciesString(row);
	  for (File f: uploadDir.listFiles()) {
	  	MediaAsset ma = null;
	  	try {
	  		JSONObject assetParams = astore.createParameters(f);
	  		System.out.println("		have assetParams");
	  		assetParams.put("_localDirect", f.toString());
	  		System.out.println("		about to create mediaAsset");
			  ma = astore.copyIn(f, assetParams);
	  	} catch (Exception e) {
	  		System.out.println("IOException creating MediaAsset for file "+f.getPath());
	  		feedback.addMissingPhoto(f.getPath());
	  		continue; // skips the rest of loop for this file
	  	}
	  	if (ma==null) continue;
	  	if (key0!=null) ma.addKeyword(key0);
	  	if (key1!=null) ma.addKeyword(key1);
  		Annotation ann = new Annotation(species, ma);
  		ann.setIsExemplar(true);
  		annots.add(ann);
	  }
	  if (annots.size()>0) feedback.addFoundPhoto(fullPath);
	  return annots;
  }

  // capitolizes the final directory in path
  private String fixGlobiceFullPath(String path) {
  	String fixed = capitolizeLastFilepart(path);
  	fixed = removeExtraGlobiceString(fixed);
  	return fixed;
  }

  private String removeExtraGlobiceString(String path) {
  	// we somehow got an extra instance of the word "globice" in the path string, right before a 1
  	return (path.replace("Globice1","1"));
  }

  private String capitolizeLastFilepart(String path) {
  	String[] parts = path.split("/");
  	String lastPart = parts[parts.length-1];
  	String firstPart = path.substring(0, path.indexOf(lastPart));
  	return firstPart + lastPart.toUpperCase();
  }


  // most rows have a single image, but some have an image folder
  private boolean isFolderRow(Row row) {
  	String path = getString(row, "Encounter.mediaAsset0");
  	if (path==null) return false;
  	boolean ans = path.endsWith("*");
  	if (ans) numFolderRows++;
  	return ans;
  }

  public String getSpeciesString(Row row) {
  	String genus = getString(row, "Encounter.genus");
  	String species = getString(row, "Encounter.species");
		String total = genus+" "+species;
		if (total==null||total.equals(" ")) total = "unknown";
		return total;
  }

  public String getIndividualID(Row row) {
  	String indID = getString(row, "Encounter.individualID");
    if (indID==null) indID = getString(row, "MarkedIndividual.individualID");
  	// Cetamada uses single letter names like A
  	if (indID!=null && indID.length()==1) return "Cetamada-"+indID;
  	return indID;
  }

  public MediaAsset getMediaAsset(Row row, int i) {
  	String localPath = getString(row, "Encounter.mediaAsset"+i);
  	if (localPath==null) return null;
  	localPath = Util.windowsFileStringToLinux(localPath);
  	String fullPath = fileInDir(localPath, uploadDirectory);
    String resolvedPath = resolveHumanEnteredFilename(fullPath);
    if (resolvedPath==null) {
      feedback.addMissingPhoto(fullPath);
      return null;
    }
	  File f = new File(resolvedPath);

	  // create MediaAsset and return it
	  JSONObject assetParams = astore.createParameters(f);
	  assetParams.put("_localDirect", f.toString());
	  MediaAsset ma = null;
	  try {
	  	ma = astore.copyIn(f, assetParams);
	  } catch (java.io.IOException ioEx) {
	  	System.out.println("IOException creating MediaAsset for file "+fullPath);
	  	feedback.addMissingPhoto(fullPath);
	  }

	  // keywording

    ArrayList<Keyword> kws = getKeywordsForAsset(row, i);
    ma.setKeywords(kws);

	  // Keyword keyword = null;
	  // String keywordI = getString(row, "Encounter.keyword"+i);
	  // if (keywordI!=null) keyword = myShepherd.getOrCreateKeyword(keywordI);
	  // String keywordOIKey = "Encounter.keyword0"+i;
	  // String keywordOI = getString(row, keywordOIKey);
	  // if (keywordOI!=null) keyword = myShepherd.getOrCreateKeyword(keywordOI);
	  // if (keyword!=null) ma.addKeyword(keyword);

	  return ma;
  }

  private ArrayList<Keyword> getKeywordsForAsset(Row row, int n) {
    ArrayList<Keyword> ans = new ArrayList<Keyword>();
    int maxAssets = getNumAssets(row);
    int maxKeywords=2;
    int stopAtKeyword = (maxAssets==(n+1)) ? maxKeywords : n; // 
    // we have up to two keywords per row.
    for (int i=n; i<stopAtKeyword; i++) {
      String kwColName = "Encounter.keyword0"+i;
      String kwName = getString(row, kwColName);
      if (kwName==null) continue;
      Keyword kw = myShepherd.getOrCreateKeyword(kwName);
      if (kw!=null) ans.add(kw);
    }
    return ans;
  }

  private int getNumAssets(Row row) {
    int n=0;
    while(getString(row,"Encounter.mediaAsset"+n)!=null) {n++;}
    return n;
  }

  // Checks common human errors in inputing filenames
  // and returns the most similar filename that actually exists on the server
  // returns null if it cannot find a good string
  private String resolveHumanEnteredFilename(String fullPath) {
    if (Util.fileExists(fullPath))      return fullPath;

    String candidatePath = uppercaseJpg(fullPath);
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
  private String removeTailingSpace(String filename) {
  	if (filename==null) return null;
  	return (filename.replace(" .jpg", ".jpg"));
  }
  private String removeSpaceDashSpaceBeforeDot(String filename) {
    if (filename==null) return null;
    return (filename.replace(" - .", "."));
  }

	private int getNumMediaAssets() {
		if (numMediaAssets==null) setNumMediaAssets();
		return numMediaAssets.intValue();
	}
	private void setNumMediaAssets() {
		int numAssets = 0;
		for (String col: colIndexMap.keySet()) {
			if (col.indexOf("mediaAsset")>-1) numAssets++;
		}
		numMediaAssets=numAssets;
	}




  public MarkedIndividual loadIndividual(Row row, Encounter enc) {

  	boolean newIndividual = false;
  	String individualID = getIndividualID(row);
  	if (individualID==null) return null;

    MarkedIndividual mark = myShepherd.getMarkedIndividualQuiet(individualID);
    if (mark==null) { // new individual
	    mark = new MarkedIndividual(individualID, enc);
	    newIndividual = true;
	  }
	  if (mark==null) {
      System.out.println("StandardImport WARNING: weird behavior. Just made an individual but it's still null.");
      return mark;
    }

	  if (!newIndividual) mark.addEncounterNoCommit(enc);

    String alternateID = getString(row, "Encounter.alternateID");
    if (alternateID!=null) mark.setAlternateID(alternateID);

  	String nickname = getString(row, "MarkedIndividual.nickname");
    if (nickname==null) nickname = getString(row, "MarkedIndividual.nickName");
  	if (nickname!=null) mark.setNickName(nickname);

    String genus = getString(row, "Encounter.genus");
    if (genus!=null) mark.setGenus(genus);

    String specificEpithet = getString(row, "Encounter.specificEpithet");
    if (specificEpithet!=null) mark.setSpecificEpithet(specificEpithet);


  	return mark;

  }


  // check if oldOcc is the same occurrence as the occurrence on this row
  // if so, return oldOcc. If not, return parseOccurrence(row)
  public Occurrence getCurrentOccurrence(Occurrence oldOcc, Row row) {
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
    Set<String> col = colIndexMap.keySet();
  	// have to manually copy-in like this because keySet returns a POINTER (!!!)
  	for (String colName: colIndexMap.keySet()) {
      // length restriction removes colnames like "F21"
  		if (colName!=null && colName.length()>3)unusedColumns.add(colName);
  	}
  }

  // Returns a map from each column header to the integer col number
  private Map<String,Integer> makeColIndexMap(Row firstRow) {
  	Map<String,Integer> colMap = new HashMap<String, Integer>();
    numCols = firstRow.getLastCellNum();
    String[] headers = new String[numCols];
    System.out.println("We're making colIndexMap!");
  	for (int i=0; i<numCols; i++) {
  		String colName = getStringNoLog(firstRow, i);
      if (colName==null || colName.length()<4) continue;
      headers[i] = colName;
  		colMap.put(colName, i);
  	}
    feedback = new TabularFeedback(headers);
    System.out.println("headers = "+headers);
    System.out.println("feedback headers = "+feedback.colNames);
  	return colMap;
  }

  public String getOccurrenceID(Row row) {
  	return (getString(row,"Occurrence.occurrenceID"));
  }

  public boolean isOccurrenceOnRow(Occurrence occ, Row row) {
    return (occ!=null && !occ.getOccurrenceID().equals(getOccurrenceID(row)));
  }




//   // following 'get' functions swallow errors
  public Integer getInteger(Row row, int i) {
    Integer ans = null;
    if (isCellBlank(row, i)) {
      feedback.logParseNoValue(i);
      return null;
    }
    try {
      double val = row.getCell(i).getNumericCellValue();
      ans = new Integer( (int) val );
    }
    catch (Exception e) {
      // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading apostrophe in its stored value that prevents us from parsing it as a number.
      try {
        String str = getStringNoLog(row, i);
        if (str==null) ans = null;
        else {try {
          ans = Integer.parseInt(str);
        } catch (Exception badParse) {
          str = str.substring(1);
          ans = Integer.parseInt(str);
          System.out.println("      getInteger SUBSTRINGED and got ans "+ans);
        }}
      }
      catch (Exception ex) {}
    }
    feedback.logParseValue(i, ans, row);
    System.out.println("      getInteger returning "+ans);
    return ans;
  }

  public Long getLong(Row row, int i) {
    Long ans = null;
    try {
      double val = row.getCell(i).getNumericCellValue();
      ans = new Long( (long) val );
    }
    catch (Exception e){      
      try {
        String str = getStringNoLog(row, i);
        if (str==null) ans = null;
        else {
          try {
            ans = Long.parseLong(str);
          } catch (Exception badParse) {
            str = str.substring(1);
            ans = Long.parseLong(str);
            System.out.println("      getLong SUBSTRINGED and got ans "+ans);
          }
        }
      }
      catch (Exception ex) {}
    }
    feedback.logParseValue(i, ans, row);
    return ans;
  }
  public Double getDouble(Row row, int i) {
    if (isCellBlank(row, i)) {
      feedback.logParseNoValue(i);
      return null;
    }
    Double ans = null;
    try {
      double val = row.getCell(i).getNumericCellValue();
      ans = new Double(val);
    }
    catch (Exception e){
      // case for when we have a weird String-Double, which looks like a double in the excel sheet, yet is cast as a String, AND has a leading apostrophe in its stored value that prevents us from parsing it as a number.
      String str = getString(row, i);
      try {
        ans = Double.parseDouble(str);
        System.out.println("      getDouble string conversion got ans "+ans);
      } catch (Exception badParse) {
        str = str.substring(1);
        ans = Double.parseDouble(str);
        System.out.println("      getDouble SUBSTRINGED and got ans "+ans);
      }
    }
    feedback.logParseValue(i, ans, row);
    return ans;
  }

  public String getString(Row row, int i) {
    if (isCellBlank(row, i)) {
      feedback.logParseNoValue(i);
      return null;
    }
    String str = getStringNoLog(row, i);
    System.out.println("about to logParseValue on a string, row "+i+", feedback "+feedback);
    feedback.logParseValue(i, str, row); //todo: figure out why this line breaks the import
    System.out.println("done with logParseValue on a string");
    return str;
  }

  public String getStringNoLog(Row row, int i) {
    String str = null;
    try {
      str = row.getCell(i).getStringCellValue();
      if (str.equals("")) return null;
    }
    catch (Exception e) {}
    return str;
  }



  public Boolean getBooleanFromString(Row row, int i) {
    Boolean ans = null;
    try {
      String boolStr = getStringNoLog(row, i).trim().toLowerCase();
      if (boolStr==null || boolStr.equals("")) {
        ans = null;
      } else {
        if (boolStr.equals("yes") || boolStr.equals("true")) ans = new Boolean(true);
        else if (boolStr.equals("no") || boolStr.equals("false")) ans = new Boolean(false);
      }
    }
    catch (Exception e) {}
    feedback.logParseValue(i, ans, row);
    return ans;
  }

  public Date getDate(Row row, int i) {
    Date ans = null;
    try {
      ans = row.getCell(i).getDateCellValue();
    }
    catch (Exception e) {}
    feedback.logParseValue(i, ans, row);
    return ans;
  }

  public DateTime getDateTime(Row row, int i) {
    Date date = getDate(row, i);
    if (date == null) return null;
    return new DateTime(date);
    // no need to log as getDate takes care of that
  }

  // Below methods are *not* static and work from column names rather than column numbers
  // IMPORTANT: ONLY WORKS IF colIndexMap HAS BEEN INITIALIZED
  public String getString(Row row, String colName) {
    if (!colIndexMap.containsKey(colName)) {
      if (verbose) missingColumns.add(colName);
      return null;
    }
    String ans = getString(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public String getIntAsString(Row row, String colName) {
  	Integer i = getInteger(row,colName);
  	if (i==null) return null;
  	return i.toString();
  }
  public String getStringOrInt(Row row, String colName) {
    if (!colIndexMap.containsKey(colName)) {
      if (verbose) missingColumns.add(colName);
      return null;
    }
    String ans = getStringOrInt(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public String getStringOrInt(Row row, int i) {
    String ans = getString(row, i);
    if (ans==null) {
      Integer inty = getInteger(row,i);
      if (inty!=null) ans = inty.toString();
    }
    return ans;
  }



  public Integer getInteger(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
  	}
    Integer ans = getInteger(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public Long getLong(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
  	}
    Long ans = getLong(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public Double getDouble(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
  	}
    Double ans = getDouble(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public Date getDate(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
  	}
    Date ans = getDate(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }
  public DateTime getDateTime(Row row, String colName) {
  	if (!colIndexMap.containsKey(colName)) {
  		if (verbose) missingColumns.add(colName);
  		return null;
  	}
    DateTime ans = getDateTime(row, colIndexMap.get(colName));
    if (ans!=null && unusedColumns!=null) unusedColumns.remove(colName);
    return ans;
  }


  // Apache POI, shame on you for making me write this. Shame! Shame! Shame! SHAME!
  // (as if I actually wrote this. thanks stackoverflow!)
  public static boolean isRowEmpty(Row row) {
    for (int c = row.getFirstCellNum(); c < row.getLastCellNum(); c++) {
        Cell cell = row.getCell(c);
        if (cell != null && cell.getCellType() != Cell.CELL_TYPE_BLANK)
            return false;
    }
    return true;
	}


	// This would be cool to put in Encounter or something.
	// tho I'm not immediately sure how we'd get the url context, or determine if we want to include /encounters/ or not
	public static String getEncounterURL(Encounter enc) {
		if (enc==null || enc.getCatalogNumber()==null) return null;
		return "/encounters/encounter.jsp?number="+enc.getCatalogNumber();
	}

	// gives us a nice link if we're
	public String getEncounterDisplayString(Encounter enc) {
		if (enc==null) return null;
		if (committing) {
			return "<a href=\""+getEncounterURL(enc)+"\" >"+enc.getCatalogNumber()+"</a>";
		}
		return enc.getCatalogNumber();
	}


  private AssetStore  getAssetStore(Shepherd myShepherd) {

    //return AssetStore.getDefault(myShepherd);
    return AssetStore.get(myShepherd, 5);

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


  private class TabularFeedback {

    Set<String> unusedColumns;
    Set<String> missingColumns; // columns we look for but don't find
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
      out.println("<h2><em>Missing photos</em>("+missingPhotos.size()+"):</h2><ul>");
      for (String photo: missingPhotos) {
        out.println("<li>"+photo+"</li>");
      }
      out.println("</ul>");
    }


    // all UI methods must begin with print (convention)
    public void printRow() {
      System.out.println("Starting to printRow");
      out.println(currentRow);
      System.out.println("Done with printRow");
    }

    public void printFoundPhotos() {
      out.println("<h2><em>Found photos</em>("+foundPhotos.size()+"):</h2><ul>");
      for (String photo: foundPhotos) {
        out.println("<li>"+photo+"</li>");
      }
      out.println("</ul>");
    }

    public void printStartTable() {
      out.println("<div class=\"tableFeedbackWrapper\"><table class=\"tableFeedback\">");
      out.println("<tr class=\"headerRow\"><th class=\"rotate\"><div><span><span></div></th>"); // empty header cell for row # column
      System.out.println("HEY YOU! You damn well better see a line below this");
      boolean isNull = (colNames==null);
      System.out.println("colNames isNull "+isNull);
      System.out.println("starting to print table. num colNames="+colNames.length+" and the array itself = "+colNames);
      for (int i=0;i<colNames.length;i++) {
        out.println("<th class=\"rotate\"><div><span>"+colNames[i]+"</span></div></th>");
      }
      System.out.println("done printing start table");
      out.println("</tr>");
    }
    public void printEndTable() {
      out.println("</table></div>");
    }

    public void logParseValue(int colNum, Object value, Row row) {
      System.out.println("TabularFeedback.logParseValue called on object: "+value+" and colNum "+colNum);
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

    String checkingInheritance = uploadDirectory;

    public RowFeedback(Row row, int num) {
      this.num=num;
      this.cells = new CellFeedback[numCols];
    }

    public String toString() {
      StringBuffer str = new StringBuffer();
      str.append("<tr>");
      str.append("<td>"+num+"</td>");
      for (CellFeedback cell: cells) {
        if (cell==null) str.append(nullCellHtml());
        else str.append(cell.html());
      }
      str.append("</tr>"); 
      return str.toString();
    }

    public void logParseValue(int colNum, Object value, Row row) {
      System.out.println("RowFeedback.logParseValue on an object: "+value);
      if (value==null) { // a tad experimental here. this means we don't have to check the parseSuccess in each getWhatever method
        System.out.println("RowFeedback.logParseValue on a NULL object. Calling logParseError.");
        String valueString = getCellValueAsString(row, colNum);
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


  // cannot put this inside CellFeedback bc java inner classes are not allowed static methods or vars (this is stupid).
  static String nullCellHtml() {
    return "<td class=\"cellFeedback null\" title=\"The importer did not attempt to parse this cell. This is possible if it is a duplicate column or relies on another column that is not present. You may proceed if this cell OK to ignore.\"><span></span></td>";
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
      System.out.println("done creating cellFeedback. got valueStr "+valueStr);
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
      if (!success) return "ERROR: The import was unable to parse this cell. Please verify that your value conforms to the Wildbook Standard Format and re-import.";
      return "Successfully parsed value from excel file.";    
    }


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



}
