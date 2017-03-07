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


public class ImportHumpback extends HttpServlet {
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
    
    out.println("Importer usage in browser: https://yourhost/HumpbackImporter?commit='trueorfalse' ");
    out.println("The default directories are /opt/excel_imports/ and /opt/image_imports/. commit=false to test data parsing, true to actually save.");
    
    Shepherd myShepherd=null;
    myShepherd=new Shepherd(context);
    myShepherd.setAction("ImportHumpback.class");
    if (!CommonConfiguration.isWildbookInitialized(myShepherd)) {
      myShepherd.beginDBTransaction();
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, myShepherd);
      myShepherd.commitDBTransaction();
    }
    
    myShepherd.beginDBTransaction();
    AssetStore assetStore = AssetStore.getDefault(myShepherd);
    myShepherd.commitDBTransaction();
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    
    String exceldir = "/opt/excel_humpback/";
    if (request.getParameter("exceldir") != null) exceldir = request.getParameter("exceldir");
    File[] excelFileList = null;
    try {
      excelFileList = getFiles(exceldir);
      for (File file : excelFileList) {
        out.println("\n++ Processing Excel File: "+file.getName()+" at "+ file.getAbsolutePath());
        processExcel(file, response, request, committing, myShepherd, assetStore);
      }      
    } catch (Exception e) {
      e.printStackTrace();
      out.println("!!!! Exception While Grabbing File List from "+exceldir+" !!!!");
    }
    myShepherd.closeDBTransaction();
    out.close();
  }
  
  
  public void processExcel(File dataFile, HttpServletResponse response, HttpServletRequest request, boolean committing, Shepherd myShepherd, AssetStore assetStore) throws IOException { 
    FileInputStream fs = new FileInputStream(dataFile);
    XSSFWorkbook wb = new XSSFWorkbook(fs);
    XSSFSheet sheet = wb.getSheetAt(0);
    XSSFRow row;
    
    if (wb.getNumberOfSheets() < 1) {  
      out.println("!!! XSSFWorkbook did not find any sheets. !!!");
    } else if (sheet.getClass() == null) {
      out.println("!!! Sheet was not successfully extracted. !!!");
    } else {
      out.println("+++ Success creating FileInputStream and XSSF Worksheet +++");
    }
    int numSheets = wb.getNumberOfSheets();
    int rows = sheet.getPhysicalNumberOfRows();
    int cols = sheet.getRow(0).getPhysicalNumberOfCells(); 
    
    out.println("Num Sheets = "+numSheets);
    out.println("Num Rows = "+rows);
    out.println("Num Columns = "+cols);
    out.println("Committing ? =");
    
    out.println("++++ PROCESSING EXCEL FILE, NOM NOM ++++");
    
    Encounter enc = null;
    for (int i=1; i<rows; i++) { 
      if (committing) {
        myShepherd.beginDBTransaction();
      }      
      row = sheet.getRow(i);
      enc = parseEncounter(row, myShepherd);
      
      String imageFile = getString(row, 0);
      ArrayList<Keyword> keywords = generateKeywords(row);
      
      enc = attachAsset(enc, imageFile, request, myShepherd, assetStore, keywords);
      // Continue to process encounter.  
      // Get indy, make indy.
    }
    wb.close();
  }
  
  public ArrayList<Keyword> generateKeywords(XSSFRow row) {
    ArrayList<Keyword> keys = new ArrayList<Keyword>(2);
    Keyword imf = new Keyword(getString(row, 0));
    Keyword col = new Keyword(getString(row, 1));
    keys.add(imf);
    keys.add(col);
    return keys;
  }
  
  public Encounter attachAsset(Encounter enc, String imageName, HttpServletRequest request, Shepherd myShepherd, AssetStore assetStore, ArrayList<Keyword> keywords ) {
    MediaAsset  ma = null;
    File photo = null;
    
    JSONObject params = new JSONObject();
    String imagedir = "/opt/image_humpback/";
    if (request.getParameter("imagedir") != null) imagedir = request.getParameter("imagedir");
    File[] imageFileList = getFiles(imagedir);
    
    for (File imageFile : imageFileList) {
      
      if (imageFile.getName().equals(imageName)) {
        out.println("\nImage Filename : "+imageFile.getName()+" = Image I'm looking for : "+imageName);
        out.println("!!!GOT A MATCH!!!");
        try {
          myShepherd.beginDBTransaction();
          photo = new File(imagedir, imageName);
          params = assetStore.createParameters(photo);
          ma = new MediaAsset(assetStore, params);
          myShepherd.commitDBTransaction();          
        } catch (Exception e) {
          out.println("!!!! Error Trying to Create Media Asset!!!!");
          e.printStackTrace();
        }
        try {
          myShepherd.beginDBTransaction();
          ma.addDerivationMethod("HumpbackImporter", System.currentTimeMillis());
          ma.addLabel("_original");
          ma.copyIn(photo);
          ma.setAccessControl(request);
          ma.updateMetadata();
          ma.updateStandardChildren(myShepherd);
          myShepherd.commitDBTransaction();
        } catch (Exception e) {
          out.println("!!!! Error Trying to Save Media Asset Properties !!!!");
          e.printStackTrace();
        }
        try {
          out.println("++++ Persisting Media Asset ++++");
          myShepherd.beginDBTransaction();
          myShepherd.getPM().makePersistent(ma);
          myShepherd.commitDBTransaction();
        } catch (Exception e) {
          out.println("!!!! Could not Persist Media Asset !!!!");
          e.printStackTrace();
        }
        
        try {
          myShepherd.beginDBTransaction();
          ma.setKeywords(keywords);
          myShepherd.commitDBTransaction();
        } catch (Exception e) {
          out.println("!!!! Error Adding Keywords to Encounter !!!!");
          e.printStackTrace();
        }
        
        try {
          out.println("Media Asset Parameters : "+ma.getParametersAsString()+" !!!!");
          myShepherd.beginDBTransaction();
          enc.addMediaAsset(ma);
          myShepherd.commitDBTransaction();
          out.println("Encounter As String : "+enc.toString()+" !!!!");
          out.println("++++ Adding Media Asset ++++");
        } catch (Exception e) {
          out.println("!!!! Error Adding Media Asset to Encounter !!!!");
          e.printStackTrace();
        }       
      }
    } 
    return enc;
  }
  
  public Encounter parseEncounter(XSSFRow row, Shepherd myShepherd) {  
    String indyId = getStringOrIntString(row, 1);
    Encounter enc = new Encounter();
    enc.setCatalogNumber(Util.generateUUID());
    enc.setGenus("Megaptera");
    enc.setSpecificEpithet("novaeangliae");
    enc.setState("approved");
    enc.setDWCDateAdded();
    enc.setDWCDateLastModified();
    enc.setSubmitterID("Bulk Import");
    enc.setIndividualID(indyId); 
    myShepherd.beginDBTransaction();
    myShepherd.getPM().makePersistent(enc);
    myShepherd.commitDBTransaction();
    out.println("Here's the ID for this Encounter : "+indyId);
    checkIndyExistence(indyId, enc, myShepherd);
    return enc;
  }
  
  public void checkIndyExistence(String indyId, Encounter enc, Shepherd myShepherd) {
    MarkedIndividual mi = null;
    try {
      if (indyId != null && indyId != "") {
        myShepherd.beginDBTransaction();
        mi = myShepherd.getMarkedIndividual(indyId);      
        myShepherd.commitDBTransaction();        
      } else {
        indyId = null;
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (mi == null) {
      out.println("No Individual with ID : "+indyId+" exists. Creating.");
      myShepherd.beginDBTransaction();
      mi = new MarkedIndividual(indyId, enc);
      myShepherd.storeNewMarkedIndividual(mi);
      myShepherd.commitDBTransaction();
    }
  }
  
  public File[] getFiles(String path) {
    File[] arr;
    try {
      File folder = new File(path);
      arr = folder.listFiles();
      return arr;
    } catch (Exception e) {
      System.out.println("+++++ ERROR: Failed to get list of files from folder. +++++");
      e.printStackTrace();
      return null;
    }
  }
  
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
  
  public static String getBaseURL(HttpServletRequest request) {
    String scheme = request.getScheme() + "://";
    String name = request.getServerName();
    String port = (request.getServerPort() == 80) ? "" : ":" + request.getServerPort();
    String path = request.getContextPath();
    return scheme + name + port + path;
  }
  
  
  
  
}