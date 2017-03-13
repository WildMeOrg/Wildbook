package org.ecocean.servlet.importer;

import org.json.JSONObject;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.joda.time.DateTime;
import org.ecocean.media.*;

//import org.apache.poi.hssf.usermodel.*;
//import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


public class ImportHumpback extends HttpServlet {
  private static PrintWriter out;
  private static String context; 
  // Values for Reference catalog are the alternates.
  private static int idColumn = 2;
  private static int colorColumn = 1;
  private static ArrayList<String> unmatchedFiles = new ArrayList<String>(); 
  
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException { 
    out = response.getWriter();
    context = ServletUtilities.getContext(request);
    
    out.println("Importer usage in browser: https://yourhost/HumpbackImporter?commit='trueorfalse' ");
    out.println("The default directories are /opt/excel_imports/ and /opt/image_imports/. commit=false to test data parsing, true to actually save.");
    
    Shepherd initShepherd=null;
    initShepherd=new Shepherd(context);
    initShepherd.beginDBTransaction();
    initShepherd.setAction("ImportHumpback.class");
    if (!CommonConfiguration.isWildbookInitialized(initShepherd)) {
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, initShepherd);
    }
    initShepherd.commitDBTransaction();
    initShepherd.closeDBTransaction();
    
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    
    String exceldir = "/opt/excel_humpback/";
    if (request.getParameter("exceldir") != null) exceldir = request.getParameter("exceldir");
    File[] excelFileList = null;
    try {
      excelFileList = getFiles(exceldir);
      try {
        for (File file : excelFileList) {
          out.println("\n++ Processing Excel File: "+file.getName()+" at "+ file.getAbsolutePath());
          processExcel(file, response, request, committing);
        }              
      } catch (Exception e) {
        e.printStackTrace();
        out.println("Choked on the next file.");
      }
    } catch (Exception e) {
      e.printStackTrace();
      out.println("!!!! Exception While Grabbing File List from "+exceldir+" !!!!");
    }
    unmatchedFilenames("", "get");
    out.close();
  }
  
  
  public void processExcel(File dataFile, HttpServletResponse response, HttpServletRequest request, boolean committing) throws IOException { 
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
    out.println("Committing ? = "+String.valueOf(committing));
    
    if (dataFile.getName().equals("CRC XRefCat.xlsx")) {
      out.println("HACK: File is Reference Catalog. ID Column = 1, Color Column = 2");
      idColumn = 1;
      colorColumn = 2;
    } else {
      idColumn = 2;
      colorColumn = 1;
    }
    
    out.println("++++ PROCESSING EXCEL FILE, NOM NOM ++++");
    Shepherd myShepherd = null;
    myShepherd = new Shepherd(context);
    Encounter enc = null;
    myShepherd.beginDBTransaction();
    AssetStore assetStore = AssetStore.getDefault(myShepherd);
    myShepherd.commitDBTransaction();
    
    for (int i=1; i<rows; i++) {     
      
      row = sheet.getRow(i);
      String imageFile = getString(row, 0);
      
      enc = parseEncounter(row, myShepherd);  
      
      
      ArrayList<Keyword> keywords = generateKeywords(row, dataFile, myShepherd);    
      attachAsset(enc, imageFile, request, myShepherd, assetStore, keywords);
    }
    
    
    
    // Lets just pull out the encounter/individual association to try to get this gnarly thread pile-up fixed.
    // This is totally a hack. Associating in the main loop overloaded postgres connections.
    Iterator<Encounter> allEncs = myShepherd.getAllEncounters();
    Encounter encToAssociate = null;
    while (allEncs.hasNext()) {
      encToAssociate = allEncs.next();
      if (encToAssociate.getIndividualID() != null && !encToAssociate.getIndividualID().equals("0")) {
        findOrCreateIndy(encToAssociate.getIndividualID(), encToAssociate, myShepherd);   
        encToAssociate.refreshAssetFormats(myShepherd);
      }
    }
    myShepherd.closeDBTransaction();
    wb.close();
  }
  
  public ArrayList<Keyword> generateKeywords(XSSFRow row, File dataFile, Shepherd myShepherd) {
    
    ArrayList<Keyword> keys = new ArrayList<Keyword>(); 
    
    myShepherd.beginDBTransaction();
    Keyword imf = myShepherd.getKeyword(dataFile.getName());
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    Keyword col = myShepherd.getKeyword(getStringOrIntString(row, colorColumn));
    myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    Keyword pq = myShepherd.getKeyword("PQ - Poor Quality");
    myShepherd.commitDBTransaction();
    
    if (myShepherd.getKeyword("PQ - Poor Quality") == null) {
      pq = new Keyword("PQ - Poor Quality");
      myShepherd.beginDBTransaction();
      myShepherd.getPM().makePersistent(pq);
      myShepherd.commitDBTransaction();
    }
    
    
    if (imf == null && dataFile.getName() != null) {
      imf = new Keyword(dataFile.getName().toUpperCase()); 
      myShepherd.beginDBTransaction();
      myShepherd.getPM().makePersistent(imf);
      myShepherd.commitDBTransaction();
    }
    if (imf != null) {
      keys.add(imf);
    }
    
    try {
      if (col == null) {
        if (getStringOrIntString(row, colorColumn).length() < 5) {
          col = new Keyword(getStringOrIntString(row, colorColumn).toUpperCase());
          myShepherd.beginDBTransaction();
          myShepherd.getPM().makePersistent(col);
          myShepherd.commitDBTransaction();          
        }
      }      
    } catch (Exception e) {
      out.println("Can't create color keyword!");
    }
    
    if (col != null) {
      keys.add(col);
    }
    
    //Handle poor quality image tags.
    out.println("Checking quality.");
    if (getStringOrIntString(row, idColumn).equals("0") || getStringOrIntString(row, idColumn).equals("PQ")) {           
      keys.add(pq);
      out.println("Poor Quality Image! Tagging.");
    }
    
    return keys;
  }
  
  public void attachAsset(Encounter enc, String imageName, HttpServletRequest request, Shepherd myShepherd, AssetStore assetStore, ArrayList<Keyword> keywords ) {
    MediaAsset  ma = null;
    File photo = null;
    
    JSONObject params = new JSONObject();
    String imagedir = "/opt/image_humpback/";
    if (request.getParameter("imagedir") != null) imagedir = request.getParameter("imagedir");
    File[] imageFileList = getFiles(imagedir);
    
    boolean match = false;
    
    for (File imageFile : imageFileList) {  
      if (imageFile.getName().equals(imageName)) {
        match = true;
        out.println("\n MATCH! Image Filename : "+imageFile.getName()+" = Image I'm looking for : "+imageName);
        try {
          photo = new File(imagedir, imageName);
          params = assetStore.createParameters(photo);
          ma = new MediaAsset(assetStore, params);         
        } catch (Exception e) {
          out.println("!!!! Error Trying to Create Media Asset!!!!");
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
          ma.addDerivationMethod("HumpbackImporter", System.currentTimeMillis());
          ma.addLabel("_original");
          ma.copyIn(photo);
          ma.setAccessControl(request);
          ma.updateMetadata();
          myShepherd.beginDBTransaction();
          ma.updateStandardChildren(myShepherd);
          myShepherd.commitDBTransaction();
        } catch (Exception e) {
          out.println("!!!! Error Trying to Save Media Asset Properties !!!!");
          e.printStackTrace(out);
        }
        
        try {
          ma.setKeywords(keywords);
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
    if (match == false) {
      unmatchedFilenames(imageName, "store");
    } else {      
      match = false;
    }
  }
  
  public void unmatchedFilenames(String name, String flag) {
    if (flag.equals("store")) {
      unmatchedFiles.add(name);
    }
    if (flag.equals("get")) {
      for (String file : unmatchedFiles) {
        out.println("Unmatched File : "+file);
      }
      out.println("Total of "+Integer.toString(unmatchedFiles.size())+" Files Not Matched.");
    } 
  }
  
  public Encounter parseEncounter(XSSFRow row, Shepherd myShepherd) {  
    String indyId = null;
    
    try {
      indyId = getStringOrIntString(row, idColumn);      
    } catch (Exception e) {
      e.printStackTrace();
      out.println("Error Getting Indy Id!");
    }
    
    Encounter enc = new Encounter();
    enc.setCatalogNumber(Util.generateUUID());
    enc.setGenus("Megaptera");
    enc.setSpecificEpithet("novaeangliae");
    enc.setState("approved");
    enc.setDWCDateAdded();   
    enc.setDWCDateLastModified();
    enc.setSubmitterID("Bulk Import");
    if (indyId != null && !indyId.equals("0") && !indyId.equals("PQ")) {
      enc.setIndividualID(indyId); 
    }
    
    myShepherd.beginDBTransaction();
    myShepherd.getPM().makePersistent(enc);
    myShepherd.commitDBTransaction();
    
    out.println("Here's the ID for this Individual : "+indyId);
    return enc;
  }
  
  public void findOrCreateIndy(String indyId, Encounter enc, Shepherd myShepherd) {
    MarkedIndividual mi = null;
    
    try {
      if (!indyId.equals("0")) {
        myShepherd.beginDBTransaction();
        mi = myShepherd.getMarkedIndividualQuiet(indyId);      
        myShepherd.commitDBTransaction();        
      } 

    } catch (Exception e) {
      e.printStackTrace();
    }
    
    if (mi == null && !indyId.equals("0")) {
      out.println("No Individual with ID : "+indyId+" exists. Creating.");
      mi = new MarkedIndividual(indyId, enc);
      myShepherd.beginDBTransaction();
      myShepherd.storeNewMarkedIndividual(mi);
      myShepherd.commitDBTransaction();    
    }
    if (mi != null && !indyId.equals("0")) {
      try {
        myShepherd.beginDBTransaction();
        mi.addEncounter(enc, indyId);
        myShepherd.commitDBTransaction();      
      } catch (Exception e) {
        e.printStackTrace(out);
        out.println("Choked while saving Indy to Enc");
      }      
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
  
}
