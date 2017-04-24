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


public class ImportBass extends HttpServlet {
  private static PrintWriter out;
  private static String context; 
  // Values for Reference catalog are the alternates.
  private static int idColumn = 1;
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
    
    out.println("Importer usage in browser: https://yourhost/ImportBass?commit='trueorfalse' ");
    out.println("The default directories are /opt/excel_imports/ and /opt/image_imports/. commit=false to test data parsing, true to actually save.");
    
    Shepherd initShepherd=null;
    initShepherd=new Shepherd(context);
    initShepherd.beginDBTransaction();
    initShepherd.setAction("ImportBass.class");
    if (!CommonConfiguration.isWildbookInitialized(initShepherd)) {
      System.out.println("WARNING: Wildbook not initialized. Starting Wildbook");    
      StartupWildbook.initializeWildbook(request, initShepherd);
    }
    initShepherd.commitDBTransaction();
    initShepherd.closeDBTransaction();
    
    
    boolean committing = (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false"));
    
    String exceldir = "/opt/bass_excel/";
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
        out.println(" !!!! Choked on the file, Processing Excel Failed. !!!! ");
      }
    } catch (Exception e) {
      e.printStackTrace();
      out.println("!!!! Exception While Grabbing File List from "+exceldir+" !!!!");
    }
    unmatchedFilenames("", "get");
    out.close();
    
    File testingRootDir = new File("");
    out.println("Here is the root file dir for Tomcat! : "+testingRootDir.getAbsolutePath());
    
    
  }
  
  
  public void processExcel(File dataFile, HttpServletResponse response, HttpServletRequest request, boolean committing) throws IOException { 
    FileInputStream fs = new FileInputStream(dataFile);
    XSSFWorkbook wb = new XSSFWorkbook(fs);
    XSSFSheet sheet = wb.getSheetAt(0);
    XSSFRow row = null;
    
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
    
    out.println("++++ PROCESSING EXCEL FILE, NOM NOM ++++");
    Shepherd myShepherd = null;
    myShepherd = new Shepherd(context);
    Encounter enc = null;
    myShepherd.beginDBTransaction();
    AssetStore assetStore = AssetStore.getDefault(myShepherd);
    myShepherd.commitDBTransaction();
    
    HashMap<String,String> nicks = new HashMap<String,String>();
    for (int i=1; i<rows; i++) {     
      
      row = sheet.getRow(i);
      String imageFile = getString(row, 4);
      try {
        if (imageFile != null) {
          imageFile = imageFile.trim();
        }         
      } catch (Exception e) {
        e.printStackTrace();
      }
      out.println("Original Image File name for finding whitespace: |"+imageFile+"|");
      
      enc = parseEncounter(row, myShepherd);  
      
      
      ArrayList<Keyword> keywords = generateKeywords(row, dataFile, myShepherd);    
      attachAsset(enc, imageFile, request, myShepherd, assetStore, keywords);
      
      nicks.put(enc.getCatalogNumber(), getString(row, 2));
      //Gonna try to make a hacky nickname handover here.
    }
    
    
    
    // Lets just pull out the encounter/individual association to try to get this gnarly thread pile-up fixed.
    // This is totally a hack. Associating in the main loop overloaded postgres connections.
    
    // There is a problem here where we still need to add data from each row to the individual, but this only iterates 
    // through encounters. Either fon a way store what you need on the encounter, or find a better way to feed it in.
    Iterator<Encounter> allEncs = myShepherd.getAllEncounters();
    Encounter encToAssociate = null;
    while (allEncs.hasNext()) {
      encToAssociate = allEncs.next();
      if (encToAssociate.getIndividualID() != null) {
        out.println("Trying to process "+encToAssociate.getIndividualID());
        findOrCreateIndy(encToAssociate.getIndividualID(), encToAssociate, myShepherd, nicks);    
      }
    }
    myShepherd.closeDBTransaction();
    wb.close();
  }
  
  public ArrayList<Keyword> generateKeywords(XSSFRow row, File dataFile, Shepherd myShepherd) {
    
    ArrayList<Keyword> keys = new ArrayList<Keyword>(); 
    
    // Get dis keyword workin
    String sideString = getStringOrIntString(row, 9);
    if (sideString != null) {
      sideString = sideString.trim().toLowerCase();
      out.println("sideString : "+sideString);
      if (sideString.contains("l")) {
        sideString = "Has Left Side Image(s)";        
      }
      if (sideString.contains("r")) {
        sideString = "Has Right Side Image(s)";
      }
      Keyword side = null;
      try {
        myShepherd.beginDBTransaction();
        side = myShepherd.getKeyword(sideString);
        myShepherd.commitDBTransaction();
      } catch (Exception e) {
        e.printStackTrace();
      }
      if (side == null) {
        side = new Keyword(sideString);
      }
      if (side != null) {
        keys.add(side);
      }
    }
    
    String mediaType = getStringOrIntString(row, 10);
    if (mediaType != null) {
      if (mediaType.toLowerCase().contains("v")) {
        mediaType = "Video";
      }
      if (mediaType.toLowerCase().contains("p")) {
        mediaType = "Photograph";
      }
      mediaType = "Media Type : "+mediaType;
    }
    
    String downloaded = getStringOrIntString(row, 12);
    if (downloaded != null) {
      downloaded = "Downloaded File : "+downloaded.toLowerCase();
    }
    
    myShepherd.beginDBTransaction();
    Keyword dl = myShepherd.getKeyword(downloaded);
    myShepherd.commitDBTransaction();
    //myShepherd.beginDBTransaction();
    //Keyword side = myShepherd.getKeyword(sideString);
    //if (side!=null) {out.println("Found Side! 1");}
    //myShepherd.commitDBTransaction();
    myShepherd.beginDBTransaction();
    Keyword med = myShepherd.getKeyword(mediaType);
    myShepherd.commitDBTransaction();
    
    if (myShepherd.getKeyword(mediaType) == null && mediaType != null) {
      med = new Keyword(mediaType);
      myShepherd.beginDBTransaction();
      myShepherd.getPM().makePersistent(med);
      myShepherd.commitDBTransaction();
    }
    if (med != null) {
      keys.add(med);
    }
    
    if (myShepherd.getKeyword(downloaded) == null && downloaded != null) {
      dl = new Keyword(downloaded); 
      myShepherd.beginDBTransaction();
      myShepherd.getPM().makePersistent(dl);
      myShepherd.commitDBTransaction();
    }
    if (dl != null) {
      keys.add(dl);
    }
    
    //if (myShepherd.getKeyword(sideString) == null && side != null) {
    //  side = new Keyword(sideString);
    //  myShepherd.beginDBTransaction();
    //  myShepherd.getPM().makePersistent(side);
    //  myShepherd.commitDBTransaction();     
    //  if (side!=null) {out.println("Persisted Side! 2");}
    //}      
    //if (side != null) {
    //  keys.add(side);
    //}  
    return keys;
  }
  
  public void attachAsset(Encounter enc, String imageName, HttpServletRequest request, Shepherd myShepherd, AssetStore assetStore, ArrayList<Keyword> keywords ) {
    MediaAsset  ma = null;
    File photo = null;
    
    JSONObject params = new JSONObject();
    String imagedir = "/opt/bass_image/";
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
          ma.addDerivationMethod("Bass Importer", System.currentTimeMillis());
          ma.addLabel("_original");
          ma.copyIn(photo);
          //ma.setAccessControl(request);
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
    
    DateTime dt = processDate(row);
    if (dt!=null) {
      try {
        out.println("CURRENT DATE LINE: "+getStringOrIntString(row, 8));
        enc.setDateInMilliseconds(dt.getMillis());
        enc.setVerbatimEventDate(dt.toString());        
      } catch (Exception e) {
        out.println("Barfed on date.");
        e.printStackTrace();
      }
    } else {
      try {
        if (getStringOrIntString(row, 8)!= null) {
          enc.setVerbatimEventDate(getStringOrIntString(row, 8));          
        }
      } catch (Exception e) {
        e.printStackTrace();
      }
    }
    
    enc.setCatalogNumber(Util.generateUUID());
    enc.setGenus("Stereolepis");
    enc.setSpecificEpithet("gigas");
    enc.setState("approved");
    enc.setDWCDateAdded();   
    enc.setDWCDateLastModified();
    enc.setSubmitterID("Bulk Import");
    enc.setVerbatimLocality(getString(row, 5));
    
    if (getString(row, 17) != null) {
      enc.setComments(getString(row, 17));      
    }
    if (getString(row, 14) != null) {
      enc.setPhotographerName(getString(row, 14));      
    }
    if (getString(row, 15) != null) {
      String email = getString(row, 15);
      String[] emails = email.split(",");
      enc.setPhotographerEmail(emails[0].trim());
    }
    
    double lat = 0;
    double lon = 0;
    if (getStringOrIntString(row,6) != null && getStringOrIntString(row,7) != null) {
      lat = getLatLon(getString(row, 6));
      lon = getLatLon(getString(row, 7));
      out.println("LAT : "+lat);
      out.println("LON : "+lon);
    }
    if (lat != 0 && lon != 0) {
      enc.setDecimalLatitude(lat);
      enc.setDecimalLongitude(lon);      
    }
    
    if (indyId != null ) {
      enc.setIndividualID(indyId); 
    }
    
    parseDynProp(enc, "Original File Name ", row, 3);
    parseDynProp(enc, "Video File Name ", row, 11);
    parseDynProp(enc, "Video URL ", row, 13);
    parseDynProp(enc, "Photographer ", row, 14);
    parseDynProp(enc, "Contact Info ", row, 15);
    parseDynProp(enc, "Previous Invalid Name ", row, 16);
    
    myShepherd.beginDBTransaction();
    myShepherd.getPM().makePersistent(enc);
    myShepherd.commitDBTransaction();
    
    out.println("Here's the ID for this Individual : "+indyId);
    return enc;
  }
  
  public void findOrCreateIndy(String indyId, Encounter enc, Shepherd myShepherd, HashMap<String,String> nicks) {
    MarkedIndividual mi = null;
    
    try {
      if (indyId != null) {
        myShepherd.beginDBTransaction();
        mi = myShepherd.getMarkedIndividualQuiet(indyId);      
        myShepherd.commitDBTransaction();
        out.println("Looking for "+indyId);
      } 

    } catch (Exception e) {
      e.printStackTrace();
    }
    
    if (mi == null) {
      out.println("No Individual with ID : "+indyId+" exists. Creating.");
      mi = new MarkedIndividual(indyId, enc);
      myShepherd.beginDBTransaction();
      myShepherd.storeNewMarkedIndividual(mi);
      myShepherd.commitDBTransaction();    
    }
    if (mi != null) {
      try {
        myShepherd.beginDBTransaction();
        mi.addEncounter(enc, context);
        mi.setNickName(nicks.get(enc.getCatalogNumber()));
        //mi.setAlternateID(getString(row, 0));
        myShepherd.commitDBTransaction();      
      } catch (Exception e) {
        e.printStackTrace(out);
        out.println("Choked while saving Indy to Enc");
      }      
    }  
  }
  
  public DateTime processDate(XSSFRow row) {
    DateTime dateTime = null;
    String dateRow = getStringOrIntString(row, 8);
    Date date = getDate(row, 8);
    // Hackety hackety. Handling all the little date format differences. 
    if (date != null&&!dateRow.substring(0,2).equals("20")) {
      try {
        dateTime = new DateTime(date);
        if (dateTime != null) {
          out.println("New dateTime from getDate: "+dateTime);
          return dateTime;
        }
      } catch (Exception e) {
        out.println("Value from getDate was wack. Continuing.");
      }
    }
    int year = 0;
    int month = 0;
    int day = 0;
    // There is something super fishy (hah) going on here.
    out.println("DATEROW SAYS : "+dateRow);
    if (dateRow != null) {
      if (dateRow.contains("/")) {
        //Split on character instead.
        String[] arr = dateRow.split("/");
        for (String i : arr) {
          if (i.length() < 2) {
            i = "0" + i;
          }
        }
        try {
          year = Integer.parseInt(arr[2]);
          month = Integer.parseInt(arr[0]);
          day = Integer.parseInt(arr[1]);          
        } catch (Exception e) {}
      } 
      if (dateRow.length() > 7 && !dateRow.contains("/")) {
        try {
          year = Integer.parseInt(dateRow.substring(0,4));
          month = Integer.parseInt(dateRow.substring(4,6));
          day = Integer.parseInt(dateRow.substring(6));          
        } catch (Exception e) {}
      }       
      
    }
    try {
      if (year != 0 && month != 0 && day != 0) {
        dateTime = new DateTime(year, month, day, 0, 0);        
      }
    } catch (Exception e) {
      out.println("You can't make a date out of that! --BARF--");
    }
    out.println("New dateTime from string: "+dateTime);
    return dateTime;
  }
  
  public double getLatLon(String inputString) {
    double degrees = 0;
    double minutes = 0;
    double result = 0;
    try {
      String[] arr = inputString.split("\u00b0");
      arr[1] = arr[1].substring(0, arr[1].length()-1);
      degrees = Double.parseDouble(arr[0]);
      minutes = Double.parseDouble(arr[1]) / 60;
      result = degrees + minutes;      
    } catch (Exception e) {
      e.printStackTrace();
      out.println("Could not parse Lat/Lon value into double for storage.");
    }
    return result;
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
  
  private void parseDynProp(Encounter enc, String name, XSSFRow row, int i) {
    String val = getString(row, i);
    if (val == null) return;
    enc.setDynamicProperty(name, val);
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
  
  public Date getDate(XSSFRow row, int i) {
    Date date = null;        
    try {
      date = row.getCell(i).getDateCellValue();
      if (date != null) {
        return date;
      }
    } catch (Exception e) {}    
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