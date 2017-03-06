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
    String dataURL = getDataURL(request);
    
    out.println("Importer usage in browser: https://yourhost/ImportExcel?commit='trueorfalse' ");
    out.println("The default directories are /opt/excel_imports/ and /opt/image_imports/. commit=false to test data parsing, true to actually save.");
    // set up a Shepherd
    // He will make sure that the default asset store is created if Wildbook hasn't made one for whatever reason.
    Shepherd myShepherd=null;
    myShepherd=new Shepherd(context);
    myShepherd.setAction("ImportExcel.class");
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      myShepherd.beginDBTransaction();
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, myShepherd);
      myShepherd.commitDBTransaction();
    }
    String assetStorePath="/data/wildbook_data_dir";
     
    String assetStoreURL= dataURL + "/wildbook_data_dir";
    
    //myShepherd.beginDBTransaction();
    //AssetStore assetStore = AssetStore.getDefault(myShepherd);
    //myShepherd.commitDBTransaction();
    //try {
    //  if (assetStore.toString() == null || assetStore.toString() == "") {
    myShepherd.beginDBTransaction();
    AssetStore assetStore = new LocalAssetStore("Turtle-Asset-Store", new File(assetStorePath).toPath(), assetStoreURL, true);
    myShepherd.getPM().makePersistent(assetStore);
    myShepherd.commitDBTransaction();        
    //  }
    //} catch (Exception e) {
    //  out.println("!!!! Failed to find or create a local asset store at "+assetStoreURL+" !!!!");
    //  e.printStackTrace();      
    //}    
    
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false")); //false by default
    
    Hashtable<String,MediaAsset> assetIds = new Hashtable<String,MediaAsset>(); 
    String imagedir = "/opt/image_imports/";
    if (request.getParameter("imagedir") != null) imagedir = request.getParameter("imagedir");
    File[] imageFileList = getFiles(imagedir);
    boolean imagesFound = imageFileList.length > 0;
    Hashtable<String,MediaAsset> assetHash = new Hashtable<String,MediaAsset>();
    for (File file : imageFileList) {
      out.println("\n++ Processing Image File: "+file.getName()+" at "+ file.getAbsolutePath());
      assetHash = processImage(file, response, request, committing, imagedir, assetStore, myShepherd);
      if (assetHash != null) {
        assetIds.putAll(assetHash);  
      }
    }       
    
    String assetString = StringUtils.join(assetIds.keySet(), " ");
    System.out.println("All saved values in assetIds : " + assetString);
    
    String exceldir = "/opt/excel_imports/";
    if (request.getParameter("exceldir") != null) exceldir = request.getParameter("exceldir");
    boolean excelFound = false;
    File[] excelFileList = null;
    try {
      excelFileList = getFiles(exceldir);
      excelFound = excelFileList.length > 0;
      for (File file : excelFileList) {
        out.println("\n++ Processing Excel File: "+file.getName()+" at "+ file.getAbsolutePath());
        processExcel(file, response, committing, assetIds, myShepherd);
      }      
    } catch (Exception e) {
      e.printStackTrace();
      out.println("!!!! Exception While Grabbing File List from "+exceldir+" !!!!");
    }
    
    out.println("Excel File(s) found = "+String.valueOf(excelFound)+" at "+excelFileList[0].getAbsolutePath());
    out.println("Image File(s) found = "+String.valueOf(imagesFound)+" at "+imageFileList[0].getAbsolutePath());
    
    //Create Workbook instance holding reference to .xls file 
    // XSSF is used for .xlxs files
    // NPOIFSFileSystem fs = new NPOIFSFileSystem(dataFile);  
    // HSSFWorkbook wb = new HSSFWorkbook(fs.getRoot(), true);

    // persist images in asset store, then associate with encounter
    // be careful of committing one and not the other, if you do it will contain a reference to 
    // that object in RAM, but not the persisted one if at all. 
    myShepherd.closeDBTransaction();
    out.close();
  }
  
  public Hashtable<String,MediaAsset> processImage(File imageFile, HttpServletResponse response, HttpServletRequest request,  boolean committing, String imagedir, AssetStore assetStore, Shepherd myShepherd) throws IOException {
    
    MediaAsset ma = null;   
    String photoFileName = null;
    String photoNumber = null;
    File photo = null;
    JSONObject params = null;
    String photoId = null;
    boolean isValid = false;
    try {
      photoFileName = imageFile.getName();
      photoNumber = photoFileName.substring(4,7);
      char x = 'x';
      if (photoNumber.substring(2).charAt(0) == x) {
        isValid = false;
        out.println("Image rejected. Image filename contains invalid characters for asset ID.");
      } else {
        isValid = true;
        out.println("++ Image Valid ++");
      }
      char z = '0';
      if (photoNumber.charAt(0) == z) {
        photoNumber = photoNumber.substring(1);
      }
      out.println("++++ Photo number "+photoNumber+" Identified! ++++");
    } catch (Exception e) {
      out.println("!!!! Error grabbing image number to search for matching encounter. !!!!");
      e.printStackTrace();
    }
    
    if (isValid == true) {
      try {
        photo = new File(imagedir, photoFileName);
        out.println("++ Image Dir : "+ imagedir+" ++");
        out.println("!!! photoFileName : "+photoFileName+" !!!");
        out.println("!!! AssetStore : "+assetStore.toString()+" !!!");
      } catch (Exception e) {
        e.printStackTrace();
        out.println("!!! Error Creating photo File !!!");
      }
      try {
        myShepherd.beginDBTransaction();
        params = assetStore.createParameters(photo);
        params.put("url", photoFileName+photoNumber);
        myShepherd.commitDBTransaction();

      } catch (Exception e) {
        e.printStackTrace();
        out.println("!!! Error Creating params in assetStore !!!");
      }
      if (committing && isValid == true) {
        try {
          out.println("++++ Creating Media Asset ++++");
          myShepherd.beginDBTransaction();
          photoId = (photoNumber+photoFileName.substring(photoFileName.length()-5).charAt(0)); 
          ma = new MediaAsset(assetStore, params);
          ma.addDerivationMethod("createEncounter", System.currentTimeMillis());
          ma.addLabel("_original");
          ma.copyIn(photo);
          ma.setAccessControl(request);
          ma.updateMetadata();
          ma.updateStandardChildren(myShepherd);
          ma.generateUUIDFromId();
          myShepherd.commitDBTransaction();
        } catch (Exception e) {
          out.println("!!!! Error creating media asset for image ID "+photoId+" !!!!");
          e.printStackTrace();
        }
      }
      
      if (committing && isValid == true) {
        try {
          out.println("++++ Persisting Media Asset ++++");
          myShepherd.beginDBTransaction();
          myShepherd.getPM().makePersistent(ma);
          myShepherd.commitDBTransaction();
          //myShepherd.beginDBTransaction();
        } catch (Exception e) {
          myShepherd.rollbackDBTransaction();
          out.println("!!!! Error persisting media asset !!!!");
          e.printStackTrace(); 
        }
      }
      
      //Annotation ann = new Annotation("Psammobates geometricus", ma);
      //if (committing && isValid == true) {
      //  try {
      //    myShepherd.storeNewAnnotation(ann);
      //    myShepherd.getPM().makePersistent(ann);
      //    myShepherd.commitDBTransaction();
      //  } catch (Exception e) {
      //    out.println("!!!! Error storing new annotation for media asset !!!!");
      //    e.printStackTrace(); 
      //  }
      //}
      // enc.addAnnotations  
    }
    // You are trying to create media assets for each image file. Each image has a photo number
    // at the 4-6 character of it's file name.
    // Each encounter should have a photo number as well. Create the media assets, then create the encounters.
    // Then associate each encounter with the appropriate media assets. 
    // SAVE ASSET --> GET NUMBER --> SEARCH ENCOUNTER NUMBERS -->>> SAVE PAIR 
    Hashtable<String,MediaAsset> hashValues = new Hashtable<String, MediaAsset>();
    if (committing && isValid == true) {
      System.out.println("Photo ID : " + photoId);
      hashValues.put(photoId, ma);
    }
    isValid = false;
    if (ma != null && photoId != null) {
      return hashValues; 
    } else {
      return null;
    }
  }
  
  public void processExcel(File dataFile, HttpServletResponse response, boolean committing, Hashtable<String,MediaAsset> assetIds, Shepherd myShepherd) throws IOException {  
    
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
    out.println("Num Cols = "+cols);
    out.println("committing = "+committing);

    int printPeriod = 25;
    out.println("+++++ LOOPING THROUGH FILE +++++");
    String encId = null;
    boolean isValid = true;
    for (int i=1; i<rows; i++) {
      try {
        if (committing) myShepherd.beginDBTransaction();
        row = sheet.getRow(i);
        if (getInteger(row, 7) != null) {
          encId = String.valueOf(getInteger(row, 7));          
        }
        out.println("---- CURRENT ID: "+encId+" ----");
        if (assetIds.get(encId + "l") == null && assetIds.get(encId + "r") == null && assetIds.get(encId + "c") == null && assetIds.get(encId + "p") == null) {
          isValid = false;
          out.println("!!! ID Not Found in Asset List !!!");
        }
        Encounter enc = null;
        if (committing && isValid == true) {
          enc = parseEncounter(row, myShepherd);
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
            out.println("Adding media asset : "+encId);
            enc.setState("approved");
            if (committing && isValid == true) myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
            String encIdS = String.valueOf(encId);
            MediaAsset mal = assetIds.get(encIdS + "l");
            MediaAsset mar = assetIds.get(encIdS + "r");
            MediaAsset mac = assetIds.get(encIdS + "c");
            MediaAsset map = assetIds.get(encIdS + "p");
            try {
              if (mal != null) {
                enc.addMediaAsset(mal);      
                out.println("MAL : "+mal.toString());
              }
              if (mac != null) {
                enc.addMediaAsset(mac); 
                out.println("MAC : "+mac.toString());
              }
              if (map != null) {
                enc.addMediaAsset(map);     
                out.println("MAP : "+map.toString());
              }
              if (mar != null) {
                enc.addMediaAsset(mar);    
                out.println("MAR : "+mar.toString());
              }
              out.println("ENC TO STRING : "+enc.toString());
            } catch (Exception npe) {
              npe.printStackTrace();
              out.println("!!! Failed to Add Media asset to Encounter  !!!");
            }
          } catch (Exception e) {
            e.printStackTrace();
            out.println("!!! Failed to Store New Encounter  !!!");
          }
          
          if (needToAddEncToInd) ind.addEncounter(enc, context);
          if (committing && indID!=null && isValid == true && !myShepherd.isMarkedIndividual(indID)) myShepherd.storeNewMarkedIndividual(ind);
          if (committing && isValid == true) myShepherd.commitDBTransaction();
          // New Close it.
          if (i%printPeriod==0) {
            out.println("Parsed row ("+i+"), containing Enc "+enc.getEncounterNumber()
            +" with Latitude "+enc.getDecimalLatitude()
            +" and Longitude "+enc.getDecimalLongitude()
            +", dateInMillis "+enc.getDateInMilliseconds()
            +", individualID "+enc.getIndividualID()
            +", sex "+enc.getSex()
            +", living status "+enc.getLivingStatus()
            +", identification notes "+enc.getIdentificationRemarks()
                );
          }
        }
      } catch (Exception e) {
        fs.close();
        out.println("!!! Encountered an error while Iterating through rows !!!");
        e.printStackTrace(out);
        myShepherd.rollbackDBTransaction();
      }
      isValid = true;
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
  
  public static String getDataURL(HttpServletRequest request) {
    String scheme = request.getScheme() + "://";
    String name = request.getServerName();
    String port = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
    String path = request.getContextPath();
    return scheme + name + port;
  }
  
  
  public Encounter parseEncounter(XSSFRow row, Shepherd myShepherd) {
    
    Encounter enc = new Encounter();
    Integer encNum = getInteger(row, 7);
    String encNumString = String.valueOf(encNum);
    String indID = null;
    if (getString(row, 12) != null) {
      indID = getString(row, 12);
      enc.setIndividualID(indID);
    }
    
    out.println("Processing encounter : "+encNumString);
    
    String vickiNum = getString(row, 6);
    
    enc.setEncounterNumber(encNumString);
    if (vickiNum != null) {
      enc.setAlternateID(vickiNum);
    }
   
    enc.setDecimalLatitude(getDouble(row,4));
    enc.setDecimalLongitude(getDouble(row,5));
    enc.setSex(parseSex(getString(row, 9)));
    enc.setCountry("South Africa");
    enc.setVerbatimLocality("South Africa");
    
    Date rossDate = getDate(row,8);
    Date vickiDate = getDate(row,2); 
    
    Double weightD = getDouble(row, 10);
    Double lengthD = getDouble(row, 11);
    
    Measurement weight = new Measurement(encNumString,"Weight", weightD, "Gram", "directly measured");
    Measurement length = new Measurement(encNumString,"Length", lengthD, "Millimeter", "directly measured");
    enc.setMeasurement(weight, myShepherd);
    enc.setMeasurement(length, myShepherd);

    Date encDate = resolveDate(rossDate, vickiDate);
    DateTime dt = new DateTime(encDate);
    if (dt!=null) enc.setDateInMilliseconds(dt.getMillis());
    
    // This has some random info. I'll try to grab a few things out of it. 
    String notes = getString(row, 14);
    enc.setLivingStatus(getLiving(notes));
    enc.setIdentificationRemarks(notes);
 
    parseDynProp(enc, "encounterTime", row, 3);
    // Constructor for encounter takes annotation list - maybe useful
    // setAnnotations takes array list
    enc.setDWCDateAdded();
    enc.setDWCDateLastModified();
    enc.setSubmitterID("Bulk Import");
    enc.setGenus("Psammobates geometricus");
    enc.setSpecificEpithet("geometricus");

    return enc;
  }

  //private String stripAccents(String s) {
  //      try {
  //    s = Normalizer.normalize(s, Normalizer.Form.NFD);
  //    s = s.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
  //    return s;
  //  } catch (Exception e) {
  //    e.printStackTrace();
  //    return null;
  //  }
  //}
  
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