package org.ecocean.servlet.importer;

import org.joda.time.LocalDateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.json.JSONObject;

import java.net.*;
import java.sql.Time;
import java.text.Normalizer;

import org.ecocean.grid.*;
import java.io.*;
import java.util.*;
import java.io.FileInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.media.*;
import javax.jdo.*;
import java.lang.StringBuffer;
import java.util.Vector;
import java.util.Iterator;
import java.lang.NumberFormatException;
//import org.apache.poi.hssf.usermodel.*;
//import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class ImportExcel extends HttpServlet {

  static PrintWriter out;
  static String context; 
  static String baseURL;
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException {
    out = response.getWriter();
    context = ServletUtilities.getContext(request);
    baseURL = getBaseURL(request); 
    
    out.println("Importer usage in browser: https://yourhost/ImportExcel?exceldir=yourexceldirectory&imgdir=yourimagedirectory&commit='trueorfalse' ");
    out.println("The default directories are /opt/excel_imports and /opt/image_imports. commit=false to test data parsing, true to actually save.");
    // set up a Shepherd
    // He will make sure that the default asset store is created if Wildbook hasn't made one for whatever reason.
    Shepherd startShepherd=null;
    startShepherd=new Shepherd(context);
    startShepherd.setAction("ImportExcel.class");
    
    if (!CommonConfiguration.isWildbookInitialized(startShepherd)) {
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, startShepherd);
    }
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false")); //false by default
    
    String exceldir = "/opt/excel_imports";
    if (request.getParameter("exceldir") != null) exceldir = request.getParameter("exceldir");
    File[] excelFileList = getFiles(exceldir);
    boolean excelFound = excelFileList.length > 0;
    for (File file : excelFileList) {
      out.println("++ Processing Excel File: "+file.getName()+" at "+ file.getAbsolutePath());
      processExcel(file, response, committing);
    }
    
    String imagedir = "/opt/image_imports/";
    if (request.getParameter("imagedir") != null) imagedir = request.getParameter("imagedir");
    File[] imageFileList = getFiles(imagedir);
    boolean imagesFound = imageFileList.length > 0;
    for (File file : imageFileList) {
      out.println("++ Processing Image File: "+file.getName()+" at "+ file.getAbsolutePath());
      processImage(file, response, committing, imagedir);
    }
    
    out.println("Excel File(s) found = "+String.valueOf(excelFound)+" at "+excelFileList[0].getAbsolutePath());
    //out.println("Image File(s) found = "+String.valueOf(imagesFound)+" at "+imageFileList[0].getAbsolutePath());
    
    //Create Workbook instance holding reference to .xls file 
    // XSSF is used for .xlxs files
    // NPOIFSFileSystem fs = new NPOIFSFileSystem(dataFile);  
    // HSSFWorkbook wb = new HSSFWorkbook(fs.getRoot(), true);

    // CHECK if local media store exist by calling static function in startupwildbook
    // get the code from index.jsp ~line 30-38
    // persist images in asset store, then associate with encounter
    // be careful of committing one and not the other, if you do it will contain a reference to 
    // that object in RAM, but not the persisted one if at all. 
  }
  
  public void processImage(File imageFile, HttpServletResponse response, boolean committing, String imagedir) throws IOException {
    
    Shepherd myShepherd = new Shepherd(context);
    String rootDir = getServletContext().getRealPath("/");
    String assetStorePath="/data/wildbook_data_dir";
    String assetStoreURL= baseURL + "/wildbook_data_dir";
    LocalAssetStore assetStore = null;
    try {
      if (committing) myShepherd.beginDBTransaction();
      assetStore = new LocalAssetStore("Turtle-Conservancy-Asset-Store", new File(assetStorePath).toPath(), assetStoreURL, true);
      if (committing) myShepherd.getPM().makePersistent(assetStore);
      if (committing) myShepherd.commitDBTransaction();
      out.println("++++ Success creating asset store at "+assetStoreURL+" ++++");
    } catch (Exception e) {
      out.println("++++ Failed to create and persist local asset store at "+assetStoreURL+" ++++");
      e.printStackTrace();      
    }
        
    String photoFileName = null;
    String photoNumber = null;
    boolean isValid = false;
    try {
      if (committing) myShepherd.beginDBTransaction();
      photoFileName = imageFile.getName();
      photoNumber = photoFileName.substring(4,7);
      if (Character.isDigit(photoFileName.substring(7).charAt(0))) {
        isValid = false;
        myShepherd.closeDBTransaction();
        out.println("Image rejected. Image filename contains invalid characters for asset ID.");
      }
      out.println("++++ Photo number "+photoNumber+" Identified! ++++");
    } catch (Exception e) {
      out.println("!!!! Error grabbing image number to search for matching encounter. !!!!");
      e.printStackTrace();
    }
    
    if (isValid == true) {
      File photo = new File(imagedir, photoFileName);
      JSONObject params = assetStore.createParameters(photo);
      MediaAsset ma = null;
      if (committing && isValid == true) {
        try {
          ma = new MediaAsset(assetStore, params);
          out.println("++++ Created a media asset with params: "+ma.getParameters().toString()+" ++++");
        } catch (Exception e) {
          out.println("!!!! Error creating media asset for image number "+photoNumber+" !!!!");
          e.printStackTrace();
        }
      }
      
      if (committing) {
        try {
          ma.copyIn(photo);
          myShepherd.getPM().makePersistent(ma);
          ma.updateStandardChildren(myShepherd);
          ma.updateMinimalMetadata();
        } catch (Exception e) {
          out.println("!!!! Error persisting media asset !!!!");
          e.printStackTrace(); 
        }
      }
      
      Annotation ann = new Annotation("Psammobates geometricus", ma);
      if (committing) {
        try {
          myShepherd.storeNewAnnotation(ann);
        } catch (Exception e) {
          out.println("!!!! Error storing new annotation for media asset !!!!");
          e.printStackTrace(); 
        }
      }
      // Hashmap --> annotations to id of enc
      // Then add the map to the enc when creating it
      // enc.addAnnotations
      Encounter match = null;
      Iterator<Encounter> allEncs = myShepherd.getAllEncounters();
      while (allEncs.hasNext() == true) {
        Encounter enc = allEncs.next();
        if (enc.getDynamicPropertyValue("imageNum") == photoNumber) {
          // Associate after commiting
        }
      }    
    }
    
    // You are trying to create media assets for each image file. Each image has a photo number
    // at the 4-6 character of it's file name.
    // Each encounter should have a photo number as well. Create the media assets, then create the encounters.
    // Then associate each encounter with the appropriate media assets. 
    // SAVE ASSET --> GET NUMBER --> SEARCH ENCOUNTER NUMBERS -->>> SAVE PAIR 
    
    
    // THE END
    myShepherd.closeDBTransaction();
  }
  
  public void processExcel(File dataFile, HttpServletResponse response, boolean committing) throws IOException {  
    
    Shepherd myShepherd = new Shepherd(context);
    
    FileInputStream fs = new FileInputStream(dataFile);
    XSSFWorkbook wb = new XSSFWorkbook(fs);
    XSSFSheet sheet;
    XSSFRow row;
    
    sheet = wb.getSheetAt(0);
    
    if (wb.getNumberOfSheets() < 1) {  
      out.println("!!! XSSFWorkbook did not find any sheets !!!");
    } else if (sheet.getClass() == null) {
      out.println("!!! Sheet was not successfully extracted !!!");
    } else {
      out.println("+++ Success creating FileInputStream and XSSF Worksheet +++");
    }
    
    int numSheets = wb.getNumberOfSheets();
    out.println("Num Sheets = "+numSheets);

    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    out.println("Num Rows = "+physicalNumberOfRows);

    int rows = sheet.getPhysicalNumberOfRows();; // No of rows
    int cols = sheet.getRow(0).getPhysicalNumberOfCells(); // No of columns
    // int tmp = 0;
    out.println("Num Cols = "+cols);
    out.println("committing = "+committing);

    int printPeriod = 25;

    if (committing) myShepherd.beginDBTransaction();
    out.println("+++++ LOOPING THROUGH FILE +++++");
    for (int i=1; i<rows; i++) {
      try {
        if (committing) myShepherd.beginDBTransaction();
        row = sheet.getRow(i);
        
        Encounter enc = parseEncounter(row);
        String indID = enc.getIndividualID();
        MarkedIndividual ind = null;
        boolean needToAddEncToInd = false;
        if (indID!=null) {
          ind = myShepherd.getMarkedIndividualQuiet(indID);
          if (ind==null) {
            ind = new MarkedIndividual(indID,enc);
          } else {
            needToAddEncToInd = true;
          }
        }
        try {
          enc.setState("approved");
          if (committing) myShepherd.storeNewEncounter(enc, Util.generateUUID());
        } catch (Exception e) {
          e.printStackTrace();
          out.println("!!! Failed to Store New Encounter !!!");
        }
        if (needToAddEncToInd) ind.addEncounter(enc, context);
        if (committing && indID!=null  && !myShepherd.isMarkedIndividual(indID)) myShepherd.storeNewMarkedIndividual(ind);
        if (committing) myShepherd.commitDBTransaction();
        if (i%printPeriod==0) {
          out.println("Parsed row ("+i+"), containing Enc "+enc.getEncounterNumber()
          +" with Latitude "+enc.getDecimalLatitude()
          +" and Longitude "+enc.getDecimalLongitude()
          +" with country "+enc.getCountry()
          +", dateInMillis "+enc.getDateInMilliseconds()
          +", individualID "+enc.getIndividualID()
          +", sex "+enc.getSex()
          +", living status "+enc.getLivingStatus()
          +", identification notes "+enc.getIdentificationRemarks()
          );
        }
      }
      catch (Exception e) {
        fs.close();
        out.println("!!! Encountered an error while Iterating through rows !!!");
        e.printStackTrace(out);
        myShepherd.rollbackDBTransaction();
      }
    }
    fs.close();
    wb.close();
  }

  public File[] getFiles(String path) {
    File[] arr;
    try {
      File folder = new File(path);
      System.out.println("+++++ "+folder.toString()+" FOLDER STRING +++++");
      arr = folder.listFiles();
      System.out.println(Arrays.toString(arr) + "  ARRAY STRING");
      for (File file : arr) {
        if (file.isFile()) {
          System.out.println("++ FOUND FILE: "+file.getName()+" at "+ file.getAbsolutePath());
        }
      }
      return arr;
    } catch (Exception e) {
      System.out.println("+++++ ERROR: Failed to get list of files from folder. +++++");
      e.printStackTrace();
      return null;
    }
  }
  
  public static String getBaseURL(HttpServletRequest request) {
    String scheme = request.getScheme() + "://";
    String name = request.getServerName();
    String port = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
    String path = request.getContextPath();
    return scheme + name + port + path;
  }
  
  public Encounter parseEncounter(XSSFRow row) {
    // The individual ID should be the picture number, which should be the same as the ross number.
    // Ross number is primary ID, but of it doesn't include photo discard. If no Ross number.
    String indID = stripAccents(getString(row, 13)); 
    Encounter enc = new Encounter();
    
    enc.setIndividualID(indID);
   
    enc.setDecimalLatitude(getDouble(row,4));
    enc.setDecimalLongitude(getDouble(row,5));
    enc.setSex(parseSex(getString(row, 9)));
    enc.setCountry("South Africa");
    enc.setVerbatimLocality("South Africa");
    
    Date rossDate = getDate(row,8);
    Date vickiDate = getDate(row,2); 

    Date encDate = resolveDate(rossDate, vickiDate);
    DateTime dt = new DateTime(encDate);
    if (dt!=null) enc.setDateInMilliseconds(dt.getMillis());
    
    // This has some random info. I'll try to grab a few things out of it. 
    String notes = getString(row, 14);
    enc.setLivingStatus(getLiving(notes));
    enc.setIdentificationRemarks(notes);
 
    parseDynProp(enc, "rossNum", row, 7);
    parseDynProp(enc, "vickiNum", row, 6);
    parseDynProp(enc, "encounterTime", row, 3);
    parseDynProp(enc, "imageNum", row, 13);
    
    // Weight Commmon configuration.labels.properties <-- define measurments in language folder
    // Measurement object  
    
    // Constructor for encounter takes annotation list - maybe useful
    
    // setAnnotations takes array list
    
    enc.setDWCDateAdded();
    enc.setDWCDateLastModified();
    
    // Probably always going to be Psammobates geometricus.
    enc.setSubmitterID("Bulk Import");
    enc.setGenus("Psammobates geometricus");
    enc.setSpecificEpithet("geometricus");

    return enc;
  }

  private String stripAccents(String s) {
        try {
      s = Normalizer.normalize(s, Normalizer.Form.NFD);
      s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
      return s;
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  

  private void parseDynProp(Encounter enc, String name, XSSFRow row, int i) {
    String val = getString(row, i);
    if (val == null) return;
    enc.setDynamicProperty(name, val);
  }


  // following 'get' functions swallow errors
  public Integer getInteger(XSSFRow row, int i) {
    try {
      double val = row.getCell(i).getNumericCellValue();
      return new Integer( (int) val );
    }
    catch (Exception e){}
    return null;
  }
  
  public Double getDouble(XSSFRow row, int i ) {
    try {
      double val = row.getCell(i).getNumericCellValue();
      return new Double( (double) val );
    } catch (Exception e) {
      return null;
    }
  }

  public String getString(XSSFRow row, int i) {
    try {
      String str = row.getCell(i).getStringCellValue();
      if (str.equals("")) return null;
      return str;
    }
    catch (Exception e) {}
    return null;
  }

  public String getStringOrIntString(XSSFRow row, int i) {
    try {
      String str = row.getCell(i).getStringCellValue();
      if (str.equals("")) return null;
      return str;
    }
    catch (Exception e) { try {
      return getInteger(row, i).toString();
    }
    catch (Exception e2) {} }

    return null;
  }
  
  public Boolean getBooleanFromString(XSSFRow row, int i) {
    try {
      String boolStr = getString(row, i).trim().toLowerCase();
      if (boolStr==null || boolStr.equals("")) return null;
      else if (boolStr.equals("yes")) return new Boolean(true);
      else if (boolStr.equals("no")) return new Boolean(false);
    }
    catch (Exception e) {}
    return null;
  }

  public Date getDate(XSSFRow row, int i) {
    try {
      Date date = row.getCell(i).getDateCellValue();
      return date;
    }
    catch (Exception e) {}
    return null;
  }
  
  public DateTime getDateTime(XSSFRow row, int i) {
    Date date = getDate(row, i);
    if (date == null) return null;
    return new DateTime(date);
  }
  
  public Date resolveDate(Date r, Date v) {
    try {
      if (r != null) {
        return r;
      } else if (v != null) {
        return v;
      } else {
        return null;
      }
    } catch (Exception e) {
      return null;
    }
  }
  
  public String parseSex(String s ) {
    try {
      String sex = s.substring(0, 1).toLowerCase();
      if ( sex.equals("m" )) {
        return "Male";
      } else if (sex.equals("f")) {
        return "Female";
      } else {
        return "Unknown";
      }
    } catch (Exception e) {
      e.printStackTrace();
      return null;
    }
  }
  
  public String getLiving(String notes) {
    try {
      notes = notes.toLowerCase().replaceAll("[^a-zA-Z\\s ]", "");
      if (notes.equals("dead")) {
        return "dead";
      }
    } catch (Exception e) {
     return null;
    }
    return null;
  }
}