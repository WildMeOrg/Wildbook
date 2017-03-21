/*
 * The Shepherd Project - A Mark-Recapture Framework
 * Copyright (C) 2011 Jason Holmberg
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.ecocean.servlet.importer;

import com.oreilly.servlet.multipart.*;

import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.mmutil.FileUtilities;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;

import java.util.Iterator;

import org.joda.time.*;
import org.joda.time.format.*;

import java.lang.IllegalArgumentException;

import org.ecocean.genetics.*;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.HashMap;
import java.nio.file.Files;

/* imports for dealing with spreadsheets and .xlsx files */
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;


/**
 * Uploads an Excel file for data import
 * Built initially from a copy of Jason Holmberg's org.ecocean.servlet.importer.ImportSRGD
 * @author drewblount
 */
public class ImportExcel extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    doPost(request, response);
  }

  // the image file is in a folder whose name is somewhat difficult to derive
  // this will return a File f, and f.exists() might be true or false depending on the success of the search
  static File getEncDataFolder(File imgDir, Encounter enc, StringBuffer messages) {
    String fName = "";
    String imgName = enc.getCatalogNumber();
    String photographer = enc.getPhotographerName();    
    if (imgName != null && photographer != null) {
      // the data folder naming convention used in our data is:
      fName = imgName.substring(0,9) + photographer;
    }
    File dataFolder = new File(imgDir, fName);
    if (!dataFolder.exists()) {
      // oddly, some folder names just have underscores at the beginning
      dataFolder = new File(imgDir, '_'+fName);
      System.out.println("\tfName = _"+fName);
    } else {
      System.out.println("\tfName = "+fName); 
    }
    return dataFolder;
  }
  
  //should find some folders that #1 misses
  // defaults to the nameless empty dir, in the parent imgDir you supplied
  static File getEncDataFolder2(File imgDir, Encounter enc, StringBuffer messages) {
    String imgName = enc.getCatalogNumber()+".jpg";
    String dirPrefix = imgName.substring(0,9);    

    // generate list of all possible folder names (concatenating two lists of possibilities)
    String[] possFolders1 = imgDir.list( new PrefixFileFilter(dirPrefix));
    String[] possFolders2 = imgDir.list( new PrefixFileFilter("_"+dirPrefix));
    String[] possibleFolders = new String[possFolders1.length + possFolders2.length];
    System.arraycopy(possFolders1, 0, possibleFolders, 0, possFolders1.length);
    System.arraycopy(possFolders2, 0, possibleFolders, possFolders1.length, possFolders2.length);

    // Check each possible folder, and return whichever one has the right image in it
    for (String fName: possibleFolders) {
      File testF = new File(imgDir, fName);
      if (testF.exists() && testF.isDirectory()) {
        File outF = new File(testF, imgName);
        if (outF.exists() && outF.isFile()) {
          return testF;
        }
      } 
    }
    return new File(imgDir, "");
  }
  
  // returns the folder containing an encounter's db data.
  // for encounter abc123, returns dataDir/encounters/a/b/abc123
  // for encounters whose name cannot be parsed, returns dataDir/encounters/
  static File getEncDBFolder (File dataDir, String encID) {
    String subDir = "encounters/";
    if (encID!=null && encID.length()>1) {
      //subDir += encID.charAt(0) + "/" + encID.charAt(1) + "/";
    }
    subDir += encID;
    File out = new File(dataDir, subDir);
    out.mkdirs();
    return out;
  }
  static File getEncDBFolder (File dataDir, Encounter enc) {
    return getEncDBFolder(dataDir, enc.getCatalogNumber());
  }
  
  // Somewhat tedious; parses a string of the type "151° 15’ 50 E" and returns the signed decimal repres
  static Double degStrToDouble(String DMS) {
    int i=0;
    String deg = "";
    while (Character.isDigit(DMS.charAt(i))) {
      deg += DMS.charAt(i);
      i += 1;
    };
    while (!Character.isDigit(DMS.charAt(i))) {
      i += 1;
    }
    String min = "";
    while (Character.isDigit(DMS.charAt(i))) {
      min += DMS.charAt(i);
      i += 1;
    };
    while (!Character.isDigit(DMS.charAt(i))) {
      i += 1;
    }
    String sec = "";
    while (Character.isDigit(DMS.charAt(i))) {
      sec += DMS.charAt(i);
      i += 1;
    };
    int D = Integer.parseInt(deg);
    int M = Integer.parseInt(min);
    int S = Integer.parseInt(sec);
    Double mag = D + (M/60.0) + (S/3600.0);
    int sign = 1;
    while (!Character.isLetter(DMS.charAt(i))) {
      i += 1;
    }
    if (Character.isLetter(DMS.charAt(i))) {
      char c = DMS.charAt(i);
      if ( c=='S' || c=='s' || c=='W' || c=='w' ) sign = -1;
    }
    return mag*sign;
  }
  
  
  
  static File getEncPicture(File imgDir, Encounter enc, StringBuffer messages) {
    File dataFolder = getEncDataFolder(imgDir, enc, messages);
    File pFile = new File(dataFolder, enc.getCatalogNumber()+".jpg");
    return pFile;
  }
  
  static File getEncFGP(File imgDir, Encounter enc, StringBuffer messages) {
    File dataFolder = getEncDataFolder(imgDir, enc, messages);
    File fgpFile = new File(dataFolder, enc.getCatalogNumber()+".fgp");
    return fgpFile;
  }
  
  // I'll let this function live here until I can download the latest version of com.reijns.I3S
  static ArrayList<SuperSpot> loadFgpSpots(DataInputStream data, int n) throws IOException {
    return loadFgpSpots(data, n, false, false);
  }
  // if spacingZeroes, there is a 0.0 between each pair of doubles
  static ArrayList<SuperSpot> loadFgpSpots(DataInputStream data, int n, boolean verbose, boolean spacingZeroes) throws IOException {
    ArrayList<SuperSpot> out = new ArrayList<SuperSpot>();
    for (int i=0; i<n; i++) {
      double x = data.readDouble();
      double y = data.readDouble();
      if (spacingZeroes) data.readDouble();
      if (Double.isNaN(x) || Double.isNaN(y)) {
        throw new IOException("Caught IOException on parsing fgp file: a spot coordinate was NaN.");
      };
      if (verbose) System.out.println("\t\tSpot: ("+x+", "+y+")");
      out.add(new SuperSpot(x,y));
    }
    return out;
  }
  
  static String printSpotList(ArrayList<SuperSpot> spotList) {
    String out = "";
    for (SuperSpot s : spotList) {
      out += "\n("+s.getCentroidX()+", "+s.getCentroidY()+")";
    }
    return out;
  }

  
  
  private boolean checkFileType(DataInputStream data) throws IOException {
    byte[] b = new byte[4];
    // read in first 4 bytes, and check file type.
    data.read(b, 0, 4);
    if (((char) b[0] == 'I' && (char) b[1] == 'f' && (char) b[2] == '0' && (char) b[3] == '1') == false) {
      return false;
    }
    return true;
  }

  
  /**
   * Closes the FGP opened as any InputStream descendant.
   *
   * copied from com.reijns.I3S.FgpOpenFile.java but made static
   */
  static void closeFile(InputStream data) {
    try {
      if (data != null) {
        data.close();
      }
    } catch (IOException e) {
      System.err.println("Caught IOException closing file: " + e.getMessage());
    }
  }
  
  
  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    
    //create the Hookmark keyword if it does not exist yet
    /* 
    myShepherd.beginDBTransaction();
    if(!myShepherd.isKeyword("Hookmark")){
      myShepherd.rollbackDBTransaction();
      Keyword kw=new Keyword("Hookmark");
      myShepherd.storeNewKeyword(kw);
    }
    else{
      myShepherd.rollbackDBTransaction();
    }
    */
    

    System.out.println("\n\n"+new java.util.Date().toString()+": Starting ImportExcel servlet.");

    //setup data dir
    System.out.println("Beginning directory creation...");
    String rootWebappPath = getServletContext().getRealPath("/");
    System.out.println("\twebapp path:\t"+rootWebappPath);
    File webappsDir = new File(rootWebappPath).getParentFile();
    System.out.println("\twebapps dir:\t"+webappsDir.getAbsolutePath());
    String dataDirName = CommonConfiguration.getDataDirectoryName(context);
    System.out.println("\tdata dir name:\t"+dataDirName);
    //File shepherdDataDir = new File("/data/wildbook_data_dir");
    File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
    if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
    System.out.println("\tdata dir absolute:\t"+shepherdDataDir.getAbsolutePath());
    System.out.println("\tdata dir canonical:\t"+shepherdDataDir.getCanonicalPath());    
    File tempSubdir = new File(webappsDir, "temp");
    if(!tempSubdir.exists()){tempSubdir.mkdirs();}
    System.out.println("\ttemp subdir:\t"+tempSubdir.getAbsolutePath());
    System.out.println("Finished directory creation.\n");


    //set up for response
    response.setContentType("text/html");
    PrintWriter out = response.getWriter();
    boolean locked = false;

    String fileName = "None";

    StringBuffer messages=new StringBuffer();

    boolean successfullyWroteFile=false;

    File finalFile=new File(tempSubdir,"temp.csv");
    

    try {
      MultipartParser mp = new MultipartParser(request, (CommonConfiguration.getMaxMediaSizeInMegabytes(context) * 1048576));
      Part part;
      while ((part = mp.readNextPart()) != null) {
        String name = part.getName();
        if (part.isParam()) {


          // it's a parameter part
          ParamPart paramPart = (ParamPart) part;
          String value = paramPart.getStringValue();


        }

        if (part.isFile()) {
          FilePart filePart = (FilePart) part;
          fileName = ServletUtilities.cleanFileName(filePart.getFileName());
          if (fileName != null) {
            System.out.println("     Trying to upload file: "+fileName);
            //File thisSharkDir = new File(encountersDir.getAbsolutePath() +"/"+ encounterNumber);
            //if(!thisSharkDir.exists()){thisSharkDir.mkdirs();}
            finalFile=new File(tempSubdir, fileName);
            filePart.writeTo(finalFile);
            successfullyWroteFile=true;
            System.out.println("\n\n     I successfully uploaded the file!");
          }
        }
      }      

      try {
        if (successfullyWroteFile) {

          System.out.println("\n\n     Starting file content import...");

          // this line assumes there is an .xlsx file, but a better version would inspect the extension of the uploaded file and handle accordingly 
          FileInputStream excelInput = new FileInputStream(finalFile);
          //Create Workbook instance holding reference to .xlsx file
          XSSFWorkbook workbook = new XSSFWorkbook(excelInput);
          
          // this code block parses a separate tab of the workbook, which holds some temperature info
          XSSFSheet temperatureSheet = workbook.getSheetAt(7);
          HashMap<String,HashMap<String,Integer>> infoLookup = new HashMap<String,HashMap<String,Integer>>();
          Iterator<Row> infoRows = temperatureSheet.iterator();
          // skip one row
          if (infoRows.hasNext()) infoRows.next();
          System.out.println("parsing sheet 7 for temperature and other info...");
          int infoRowNum = 1;
          while (infoRows.hasNext()) {
            Row row = infoRows.next();            
            // the row object will now be parsed to make each event
            Cell idStringCell = row.getCell(0);
            if (idStringCell==null) continue;
            String idString = idStringCell.getStringCellValue();
            System.out.println("Row "+infoRowNum+":");
            System.out.println("\tID: "+idString);
            HashMap<String,Integer> thisInfo = new HashMap<String,Integer>();
            // get temperature
            Cell tempCell = row.getCell(1);
            try {
              int temp = (int) tempCell.getNumericCellValue();
              thisInfo.put("temp", temp);
              System.out.println("\ttemp: "+temp);
            }
            catch (Exception e) {
              System.out.println("\ttemp: not parsed");
            }
            Cell caveCell = row.getCell(2);
            try {
              int sharksCave = (int) caveCell.getNumericCellValue();
              thisInfo.put("sharksCave",sharksCave);
              System.out.println("\tsharksCave: "+sharksCave);
            }
            catch (Exception e) {
              System.out.println("\tsharksCave: not parsed");
            }
            Cell overhangCell = row.getCell(3);
            try {
              int sharksOverhang = (int) overhangCell.getNumericCellValue();
              thisInfo.put("sharksOverhang",sharksOverhang);
              System.out.println("\tsharksOverhang: "+sharksOverhang);
            }
            catch (Exception e) {
              System.out.println("\tsharksOverhang: not parsed");
            }
            infoLookup.put(idString, thisInfo);
            infoRowNum++;
          }
                    

          // in all the sheets (TODO: double check), the relevant data is in the second sheet.
          //Get first/desired sheet from the workbook
          XSSFSheet sheet = workbook.getSheetAt(1);

          //Iterate through each rows one by one
          Iterator<Row> rowIterator = sheet.iterator();
          
          // Little temporary memory/time-saver
          int maxRows = 40000;
          
          // how many blank excel lines it reads before it decides the file is empty
          int endSheetSensitivity=3;
          int blankRows=0;
          
          int rowNum = 1;
          // eat non-data rows
          while (rowIterator.hasNext() && rowNum < 3) {
            rowIterator.next();
            rowNum++;
          }
          
          // Keeps track of some upload metadata
          int nNewSharks=0;
          int nNewSharksAccordingSheet=0;
          ArrayList<String> missingData = new ArrayList<String>();
          
          // objects for getting images
          String imageDirName = "/data/shark_imgs/";
          File imageDir = new File(imageDirName);
          if (!imageDir.exists()) {
            String warn = "Image directory was not found!";
            System.out.println(warn);
            messages.append("<li>"+warn+"</li>");
          }

          // handles singlephotovideo persistence
          SinglePhotoVideo picture = new SinglePhotoVideo();
          boolean loadPicture = false;
          
          while (rowIterator.hasNext() && rowNum < maxRows && blankRows < endSheetSensitivity)
          {
            System.out.println("Processing row "+rowNum+". Data combed:");
            boolean newEncounter=true;
            boolean newShark=true;
            loadPicture=false;
            boolean ok2import=true;
            Encounter enc=new Encounter();            
            myShepherd.beginDBTransaction();

            Row row = rowIterator.next();            
            
            // the row object will now be parsed to make each event
            Cell newSharkSheetCell = row.getCell(0);
            if (newSharkSheetCell==null){
              blankRows+=1;
              continue;
            }
            String newSharkSheet = newSharkSheetCell.getStringCellValue();
            
            // TODO: make this a lookup rather than trusting the sheet;
            boolean newSharkInSheet = ( newSharkSheet.equals("New") );
            
            
            // Because there is one unique photo per encounter, sample_ID is the image name in row 3
            Cell imageNameCell = row.getCell(2);
            if (imageNameCell==null){
              blankRows += 1;
              continue;
            }
            String encID = imageNameCell.getStringCellValue();
            if( (encID!=null) && !encID.equals("") ) {
              blankRows = 0;
              System.out.println("\tEncounter ID: "+ encID);
              
              if(myShepherd.isEncounter(encID)){
                enc=myShepherd.getEncounter(encID);
                //enc.setOccurrenceID("-1");
                newEncounter=false;
                System.out.println("\tEncounter already in db.");
              }
              else{
                enc.setCatalogNumber(encID);
                enc.setState("approved");
                
                //persist it
                myShepherd.rollbackDBTransaction();
                myShepherd.storeNewEncounter(enc, encID);
                myShepherd.beginDBTransaction();
                
                
                System.out.println("\tEncounter added to DB.");
              }
            }
            else {
              blankRows += 1;
              ok2import = false;
              // messages.append("<li>Row "+rowNum+": could not find sample/encounter ID in the first column of row "+rowNum+".</li>");
              System.out.println("          Could not find sample/encounter ID in the first column of row "+rowNum+".");
              // don't do any more parsing if there's no encID
              continue;
            }
            
            //let's create co-occurrences
            int encIDLength=encID.length();
            String occurrenceID=encID.substring(0,encIDLength-5);
            Occurrence occur=new Occurrence();
            if(myShepherd.isOccurrence(occurrenceID)){
              occur=myShepherd.getOccurrence(occurrenceID);
              occur.addEncounter(enc);
            }
            else{
              occur=new Occurrence(occurrenceID, enc);
              myShepherd.commitDBTransaction();
              myShepherd.storeNewOccurrence(occur);
              myShepherd.beginDBTransaction();
            }
            //end occurrence creation
            
            
            Cell sharkIDCell = row.getCell(1);
            String individualID = sharkIDCell.getStringCellValue();
            if ( (individualID!=null) && !individualID.equals("") ) {
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set marked individual to " + individualID + ".</p>");
              enc.setIndividualID(individualID);
              System.out.println("\tIndividual ID: "+individualID);
              
              newShark = !myShepherd.isMarkedIndividual(individualID);
              
              if (newShark) {nNewSharks++;}
              if (newSharkInSheet) {nNewSharksAccordingSheet++;}
              
              if (newShark && !newSharkInSheet) {
                String warn = "DATA WARNING: Incongruity on row " +rowNum+"; Sheet says \'this shark is already in the DB\', but the database has no individual with id " + individualID;
                System.out.println(warn);
                messages.append("<li>"+warn+"</li>");
              } else if (!newShark && newSharkInSheet) {
                // These warnings seemed superfluous
                // String warn = "OVERWRITE NOTE: On row " +rowNum+"; Sheet says \'this shark is not already in the DB\', but the database already has an individual with id "+individualID+".";
                // System.out.println(warn);
                // messages.append("<li>"+warn+"</li>");
              }
            } 
            else {
              enc.setIndividualID("Unassigned");
            }

            enc.setGenus("Carcharius");
            enc.setSpecificEpithet("taurus");
            
            Cell locationCell = row.getCell(3);
            String locationID = locationCell.getStringCellValue();
            if ( (locationID!=null) && !locationID.equals("") ) {
              enc.setLocationID(locationID);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set location ID to " + locationID + ".</p>"); 
              System.out.println("\tlocation ID: "+locationID);
              
              
              
              

              
              //let's also set location description based on locationID
              if(locationID.equals("BH")){enc.setLocation("Boat Harbour");}
              else if(locationID.equals("BI")){enc.setLocation("Broughton Island");}
              else if(locationID.equals("BS")){enc.setLocation("Big Seal");}
              else if(locationID.equals("BT")){enc.setLocation("Bait Grounds, Forster");}
              else if(locationID.equals("CC")){enc.setLocation("Cherubs Cave");}
              else if(locationID.equals("CG")){enc.setLocation("Cod Grounds, Laurieton");}
              else if(locationID.equals("CH")){enc.setLocation("Coffs Harbour");}
              else if(locationID.equals("DD")){enc.setLocation("Drum & Drumsticks");}
              else if(locationID.equals("DP")){enc.setLocation("Dalmeny Point");}
              else if(locationID.equals("FB")){enc.setLocation("Fairy Bower");}
              else if(locationID.equals("FC")){enc.setLocation("Foggy Cave, Terrigal");}
              else if(locationID.equals("FL")){enc.setLocation("Flinders Reef");}
              else if(locationID.equals("FO")){enc.setLocation("Forster");}
              else if(locationID.equals("FR")){enc.setLocation("Fish Rock");}
              else if(locationID.equals("FT")){enc.setLocation("Flat Rock");}
              else if(locationID.equals("GI")){enc.setLocation("Green Island");}
              else if(locationID.equals("GS")){enc.setLocation("The Grotto Seal Rocks");}
              else if(locationID.equals("HE")){enc.setLocation("Hendersons");}
              else if(locationID.equals("HU")){enc.setLocation("Hutchisons");}
              else if(locationID.equals("JR")){enc.setLocation("Julian Rocks");}
              else if(locationID.equals("LF")){enc.setLocation("Long Reef, Sydney");}
              else if(locationID.equals("LR")){enc.setLocation("Latitude Reef, Forster");}
              else if(locationID.equals("LS")){enc.setLocation("Little Seal");}
              else if(locationID.equals("MI")){enc.setLocation("Montague Island");}
              else if(locationID.equals("MP")){enc.setLocation("Magic Point");}
              else if(locationID.equals("MR")){enc.setLocation("Mermaid Reef");}
              else if(locationID.equals("NB")){enc.setLocation("North Bondi");}
              else if(locationID.equals("NM")){enc.setLocation("Nine Mile Tweed");}
              else if(locationID.equals("NS")){enc.setLocation("North Solitary");}
              else if(locationID.equals("PF")){enc.setLocation("Pinnacle Forster");}
              else if(locationID.equals("SF")){enc.setLocation("Status Reef, Forster");}
              else if(locationID.equals("SK")){enc.setLocation("Skeleton Reef, Forster");}
              else if(locationID.equals("SR")){enc.setLocation("Seal Rocks");}
              else if(locationID.equals("SS")){enc.setLocation("South Solitary Island");}
              else if(locationID.equals("ST")){enc.setLocation("Sawtooth");}
              else if(locationID.equals("TB")){enc.setLocation("The Barge");}
              else if(locationID.equals("TG")){enc.setLocation("Tollgate");}
              else if(locationID.equals("WR")){enc.setLocation("Wolf Rock");}
              

              
              
              
            }

            
            try {
              Cell yearCell = row.getCell(4);
              int year = (int) yearCell.getNumericCellValue();
              // accounts for them only writing the last two year digits
              if (year<100) year += 2000;
              enc.setYear(year);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set year to " + year + ".</p>"); 
              System.out.println("\tyear: "+year);

            }
            
            catch (Exception e) {
              String warn = "DATA WARNING: did not successfully parse year info for encounter " + enc.getCatalogNumber();
              System.out.println(warn);
              messages.append("<li>"+warn+"</li>");
            }
            
            try {
              Cell monthCell = row.getCell(5);
              String monthStr = monthCell.getStringCellValue();
              int month = -1;
              switch (monthStr) {
                case "JAN": month = 1;
                case "FEB": month = 2;
                case "MAR": month = 3;
                case "APR": month = 4;
                case "MAY": month = 5;
                case "JUN": month = 6;
                case "JUL": month = 7;
                case "AUG": month = 8;
                case "SEP": month = 9;
                case "OCT": month = 10;
                case "NOV": month = 11;
                case "DEC": month = 12;
              }
              enc.setMonth(month);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set month to " + month + ".</p>"); 
              System.out.println("\tmonth: "+monthStr+", "+month);
              
              
              
              
            }
            catch (Exception e) {
              String warn = "DATA WARNING: did not successfully parse month info for encounter " + enc.getCatalogNumber();
              System.out.println(warn);
              messages.append("<li>"+warn+"</li>");
              
            }
            
            //refresh date properties
            enc.resetDateInMilliseconds();
            
            Cell sexCell = row.getCell(6);
            String sex = sexCell.getStringCellValue();
            if((sex!=null)&&(!sex.equals(""))){
              
              if (sex.equals("M")) { enc.setSex("male"); }
              else if (sex.equals("F")) { enc.setSex("female"); }
              else { enc.setSex("unknown"); }
              
              System.out.println("\tsex: "+sex);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set sex to " + sex + ".</p>");
              
            }

            
            Cell flankCell = row.getCell(7);
            String flank = flankCell.getStringCellValue();
            if((flank!=null)&&(!flank.equals(""))){
              enc.setDynamicProperty("flank", flank);
              System.out.println("\tflank: "+flank);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set flank to " + flank + ".</p>");
            }

            Cell photographerCell = row.getCell(8);
            String photographer = photographerCell.getStringCellValue();
            if(photographer!=null && !photographer.equals("")) {
              enc.setPhotographerName(photographer);
              System.out.println("\tphotographer: "+photographer);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "ImportExcel process set flank to "
                  + photographer + ".</p>");
            }
            
            //laser
            Cell laserCell = row.getCell(12);
            String laser = laserCell.getStringCellValue();
            if(laser!=null && !laser.equals("")) {
              enc.setDynamicProperty("Laser", laser);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "ImportExcel process set laser to "
                  + laser + ".</p>");
            }
            
            
            //migration
            Cell migCell = row.getCell(10);
            String mig = migCell.getStringCellValue();
            if(mig!=null && !mig.equals("")) {
              enc.setDynamicProperty("Migration", mig);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "ImportExcel process set migration to "
                  + mig + ".</p>");
            }
            //end migration
            
            
            
            //lifestage
            Cell lifeCell = row.getCell(15);
            String life = lifeCell.getStringCellValue();
            if(life!=null && !life.equals("")) {
              enc.setDynamicProperty("Age", life);
              
              if(life.toLowerCase().equals("a")){enc.setLifeStage("adult");}
              else if(life.toLowerCase().equals("j")){enc.setLifeStage("juvenile");}
              else if(life.toLowerCase().equals("s")){enc.setLifeStage("sub-adult");}
              
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "ImportExcel process set Lifestage to "
                  + life + ".</p>");
            }
            //lifestage
            
            
            //hook mark
            Cell hookCell = row.getCell(11);
            String hook = hookCell.getStringCellValue();
            if(hook!=null && !hook.equals("")) {
              enc.setDynamicProperty("Hookmark", hook);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                  + (new java.util.Date()).toString() + "</em><br>"
                  + "ImportExcel process set hookmark to "
                  + hook + ".</p>");
            }
            
            
            //Keyword hookmarkKW=myShepherd.getKeyword("Hookmark");
            //picture.addKeyword(hookmarkKW);
            //end hookmark
            
            //precaudal length
            try{
              Cell precaudalCell = row.getCell(13);


              if(precaudalCell!=null) {
                Double pc = new Double(precaudalCell.getNumericCellValue());
                //need new measurement
                if(pc>0.0){
                  Measurement pcmeasurement=new Measurement();

                  if(enc.getMeasurement("precaudallength")!=null){
                    pcmeasurement=enc.getMeasurement("precaudallength");
                    pcmeasurement.setValue(pc);
                  }
                  else{
                    pcmeasurement = new Measurement(encID, "precaudallength", pc, "cm", "directly measured");
                  }
                  enc.setMeasurement(pcmeasurement, myShepherd);

                  enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                      + (new java.util.Date()).toString() + "</em><br>"
                      + "ImportExcel process set precaudal length to "
                      + pc + " cm.</p>");
                }
              }


            }
            catch(NumberFormatException nfe){
              System.out.println("\tPrecaudal length: could not parse");
              nfe.printStackTrace();
            }
            catch(Exception e) {
              System.out.println("\tPrecaudal length: could not parse");
            }
            //end precaudal length
            

            
            //length
            try{
              Cell lengthCell = row.getCell(14);
              
              if(lengthCell!=null) {
                Double pc = new Double(lengthCell.getNumericCellValue());
                //need new measurement
                if(pc>0.0){
                  Measurement pcmeasurement=new Measurement();
                  if(enc.getMeasurement("length")!=null){
                    pcmeasurement=enc.getMeasurement("length");
                    pcmeasurement.setValue(pc);
                  }
                  else{
                    pcmeasurement = new Measurement(encID, "length", pc, "cm", "directly measured");
                  }
                  
                  enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                      + (new java.util.Date()).toString() + "</em><br>"
                      + "ImportExcel process set length to "
                      + pc + " cm.</p>");
                }
            }
            }
            catch(NumberFormatException nfe){
              System.out.println("I could not format the precaudal length for:"+encID);
              nfe.printStackTrace();
            }
            //end length
                        
            try {
              // # sharks at cave
              Cell sharksCaveCell = row.getCell(19);
              String sharksCave = "";
              int i = (int)sharksCaveCell.getNumericCellValue(); 
              sharksCave = String.valueOf(i); 
              if(sharksCave!=null && !sharksCave.equals("")) {
                enc.setDynamicProperty("# sharks in cave", sharksCave);
                System.out.println("\t# sharks in cave: "+sharksCave);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set #SharksInCave to " + sharksCave + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\t# sharks in cave: none parsed");
            }
            
            try {
              // # sharks at overhang
              Cell sharksOverhangCell = row.getCell(20);
              String sharksOverhang = "";
              int i = (int)sharksOverhangCell.getNumericCellValue(); 
              sharksOverhang = String.valueOf(i); 
              if(sharksOverhang!=null && !sharksOverhang.equals("")) {
                enc.setDynamicProperty("# sharks at overhang", sharksOverhang);
                System.out.println("\t# sharks at overhang: "+sharksOverhang);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set #SharksNearOverhang to " + sharksOverhang + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\t# sharks at overhang: none parsed");
            }
            
            // comments
            try {
              Cell commentCell = row.getCell(21);
              String comment = commentCell.getStringCellValue();
              if(comment!=null && !comment.equals("")) {
                enc.setComments(comment);
                System.out.println("\tcomment: "+comment);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set occurenceRemarks to " + comment + ".</p>");
              }
              else {
                System.out.println("\tcomment: empty field");
              }
            }
            catch (Exception e) {
              System.out.println("\tcomments: none parsed");
            }
            
            try {
              System.out.println("\tparsing temp...");
              Cell tempCell = row.getCell(16);
              double temperature = -1.0;
              
              if (tempCell!=null) {
                System.out.println("\tparsing tempCell...");
                try {
                temperature = tempCell.getNumericCellValue();
                System.out.println("\ttempCell parsed as "+temperature);
                } catch (Exception e) {
                  System.out.println("\ttempCell parse leads to ERROR");
                }
              }
              if(Double.isNaN(temperature) || temperature == -1.0) {
                // try looking up in the temperature
                System.out.println("\t\tlooking up in temp table...");
                System.out.println("\t\tSubstring = "+encID.substring(0,9));
                HashMap<String,Integer> thisInfo = infoLookup.get(encID.substring(0,9));
                System.out.println("\t\tthis encounter in temp table: "+(thisInfo!=null));
                if (thisInfo!=null) temperature = thisInfo.get("temp");
              }
              if(!Double.isNaN(temperature) && temperature != -1.0 && temperature !=0.0) {
                Measurement tempMeasurement = new Measurement();
                if (enc.getMeasurement("Temp.")!=null){
                  tempMeasurement = enc.getMeasurement("Temp.");
                  tempMeasurement.setValue(temperature);
                }
                else {
                  tempMeasurement = new Measurement(encID, "Temp.", temperature, "Celsius", "directly measured");
                }
                enc.setMeasurement(tempMeasurement, myShepherd);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                    + (new java.util.Date()).toString() + "</em><br>"
                    + "ImportExcel process set temperature to "
                    + temperature + " cm.</p>");
                System.out.println("\ttemperature: "+temperature);
              }
            }
            catch (Exception e) {
                System.out.println("\ttemperature: none parsed");
            }
            
            

                                    
            // lat/long section
            try {
              Cell latCell = row.getCell(17);
              String latString = latCell.getStringCellValue();
              if(latString!=null && !latString.equals("")) {
                System.out.println("\tlatitude string: "+latString);
                Double lat = degStrToDouble(latString);
                enc.setDecimalLatitude(lat);
                System.out.println("\tlatitude double: "+lat);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                    + (new java.util.Date()).toString() + "</em><br>"
                    + "ImportExcel process set latitude to "
                    + lat + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\tlatitude string: COULD NOT PARSE");
            }
            try {
              Cell longCell = row.getCell(18);
              String longString = longCell.getStringCellValue();
              if(longString!=null && !longString.equals("")) {
                System.out.println("\tlongitude string: "+longString);
                Double longit = degStrToDouble(longString);
                enc.setDecimalLongitude(longit);
                System.out.println("\tlongitude double: "+longit);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on "
                    + (new java.util.Date()).toString() + "</em><br>"
                    + "ImportExcel process set longitude to "
                    + longit + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\tlongitude string: COULD NOT PARSE");
            }
            
            String strOutputDateTime = ServletUtilities.getDate();
            enc.setDWCDateLastModified(strOutputDateTime);
            enc.setDWCDateAdded(strOutputDateTime);
            
            // DATA FINDING SECTION
            if (imageDir.exists() && encID!=null) {
              File dataFolder = getEncDataFolder2(imageDir, enc, messages);
              System.out.println("\tdata folder: "+dataFolder.getAbsolutePath());
              
              // Find and load shark pic:
              File pFile = new File(dataFolder, encID+".jpg");
              if (pFile.exists()) {
                String fname = "noExtract"+encID+".jpg";
                if ((flank!=null)&&(flank.equals("R"))){
                  fname = "extractRight"+encID+".jpg";
                }
                else {
                  fname = "extract"+encID+".jpg";
                }
                String contFolder = pFile.getParent();
                // changing this to look in the dataDir/encounters/a/b/abc123 (for encounter abc123)
                /*String fullFname = contFolder+"/"+fname;
                 *File cpFile = new File(fullFname);
                 */
                File encounterFolder = getEncDBFolder(shepherdDataDir, encID);
                File cpFile = new File(encounterFolder, pFile.getName());
                File extractFile = new File(encounterFolder, fname);
                // pFile.renameTo(cpFile);
                // catches the case where we've already copied this file
                
                //copy original file by its original name
                if (!cpFile.exists()) {
                  FileUtilities.copyFile(pFile, cpFile);
                  System.out.println("\timage copied to "+cpFile.getCanonicalPath()+".");
                } else {
                  System.out.println("\timage copy already found in "+cpFile.getCanonicalPath()+".");
                }
                
                //also copy over the extract file assuming spot processing has occurred
                if (!extractFile.exists()) {
                  FileUtilities.copyFile(pFile, extractFile);
                }
                
                picture = new SinglePhotoVideo(encID, cpFile);
                // check to make sure this encounter isn't already linked to this picture
                //if (!enc.getRightSpotImageFileName().equals(picture.getFilename()) & !enc.spotImageFileName.equals(picture.getFilename())){
                  // link enc->pic
                  if ((flank!=null)&&(flank.equals("R"))){
                    enc.setRightSpotImageFileName(extractFile.getName());
                  } else {
                    enc.setSpotImageFileName(extractFile.getName());
                  }
                  enc.addSinglePhotoVideo(picture);
                  loadPicture = true;
                  System.out.println("\timage: "+picture.getFilename());
                  enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process added photo " + picture.getFilename() + ".</p>");
               // }
              
              }
              else {
                System.out.println("\timage: NOT FOUND");
                missingData.add(encID);
              }
              
              // Find and load shark .FGP
              File fgpFile = new File(dataFolder, encID+".fgp");
              if (fgpFile.exists() && fgpFile.canRead()) {
                System.out.println("\tSpot File: "+encID+".fgp");
                // inspired by com.reijns.I3S.FgpFileOpen.OpenFgpFile
                FileInputStream fStream = new FileInputStream(fgpFile);
 
                DataInputStream spotData = new DataInputStream(new BufferedInputStream(fStream));
                
                try {
                  // load reference_spots, which are the first three points in the FGP;
                  System.out.println("\tFGP file type check: " + checkFileType(spotData));
                  ArrayList<SuperSpot> reference_spots = loadFgpSpots(spotData, 3, false, false);
                  // The next value in the fgp file encodes the number of points.
                  int nPoints = spotData.readInt();
                  // seems like, from catalina.out, there are spacing zeroes after parsing N
                  ArrayList<SuperSpot> spots = loadFgpSpots(spotData, nPoints, false, true);
                  // are normed_spots needed for anything?
                  // ArrayList<SuperSpot> normed_spots = loadFgpSpots(spotData, nPoints);
                  // Now load spots into encounter object; defaults to left side if no flank info
                  if ((flank!=null)&&(flank.equals("R"))){
                    //enc.setNumRightSpots(nPoints);
                    //System.out.println("\tn R-spots: " + nPoints);
                    enc.setRightReferenceSpots(reference_spots);
                    //System.out.println("\tR-reference spots:"+ printSpotList(reference_spots));
                    enc.setRightSpots(spots);
                    //System.out.println("\tR-spots:"+ printSpotList(spots));
                    //enc.hasRightSpotImage = true;
                  }
                  else {
                    //enc.setNumLeftSpots(nPoints);
                    //System.out.println("\tn spots: " + nPoints);
                    enc.setLeftReferenceSpots(reference_spots);
                    //System.out.println("\treference spots:"+ printSpotList(reference_spots));
                    enc.setSpots(spots);
                    //System.out.println("\tspots:"+ printSpotList(spots));
                    //enc.hasSpotImage = true;
                  }
                  System.out.println("FGP file parsed and added to encounter.");
                } catch (IOException e) {
                  System.out.println("\t\tIOERROR reading FGP file for encounter "+encID+" on row "+rowNum+".");
                } finally {
                  closeFile(spotData);
                }
                closeFile(fStream);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process added spots from " + encID+".fgp.</p>");
              }
              else {
                System.out.println("\tFGP file: NOT FOUND");
              }
              
              
            } else {
              System.out.println("\t Data Folder: NOT FOUND");
              missingData.add(encID);
            }
            
            
            
                        
          // commit the encounter
          
          if(ok2import){
            System.out.println("\tOK to import, storing encounter.");
            myShepherd.commitDBTransaction();
            //if(newEncounter){
            //  myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
            //}
            if(loadPicture){
              String baseDir = shepherdDataDir.getCanonicalPath();
              System.out.println("\tRefreshing asset formats with baseDir = "+baseDir);
              //enc.refreshAssetFormats(context, baseDir, picture, true);
            }
              
              

          // upload/update the MarkedIndividual object
          if (!individualID.equals("")) {
            MarkedIndividual indie=new MarkedIndividual();
            myShepherd.beginDBTransaction();
          
            Encounter enc2=myShepherd.getEncounter(encID);
          
            if (!newShark) {
              indie=myShepherd.getMarkedIndividual(individualID);
            }
            else {
              indie.setIndividualID(individualID);
            }
            
            //OK to generically add it as the addEncounter() method will ignore it if already added to marked individual
            indie.addEncounter(enc2, context);

            if((indie.getSex()==null)||((enc2.getSex()!=null)&&(indie.getSex()!=enc2.getSex()))){
              indie.setSex(enc2.getSex());
              indie.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set sex to " + enc2.getSex() + ".</p>");
              
            }
                        
            indie.refreshDependentProperties(context);
            indie.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process added encounter " + enc2.getCatalogNumber() + ".</p>");
            
            myShepherd.commitDBTransaction();
            if(newShark){ myShepherd.storeNewMarkedIndividual(indie);}
            myShepherd.beginDBTransaction();

          } // endif (!individualID.equals(""))
          } // endif (ok2import)
          else {myShepherd.rollbackDBTransaction();}
          
            rowNum++;
            
          } // endwhile (rowIterator.hasNext() && rowNum < maxRows)
          workbook.close();
          excelInput.close();
          System.out.println("The excel file has been closed.");
          
          // just a check to see if this excel file has been uploaded before
          if ((nNewSharksAccordingSheet-nNewSharks)>(nNewSharksAccordingSheet)/2) {
            out.println("OVERWRITE ALERT:\tThe uploaded spreadsheet overwrote data already in the DB.");
          }
          
          // add message for missing data
          if (!missingData.isEmpty()) {
            String dataWarn = "("+fileName+"): A number of encounters were uploaded whose data appear to be missing. Missing filenames are:";
            messages.append("<p>"+dataWarn+"<ul><li>");
            System.out.println(dataWarn+"\n\t");
            for (String n: missingData) {
              messages.append(n+", ");
              System.out.print(n+", ");
            }
            messages.append("</li></ul></p>");
          }
          
          
        } // endif (successfullyWroteFile)
        
        else {
          locked = true;
          System.out.println("ImportExcel: For some reason the import failed without exception.");
        }


        } // endtry above if (successfullyWroteFile)
        catch (Exception le) {
          System.out.println("ImportExcel: There was an exception caught during the import");
          locked = true;
          myShepherd.rollbackDBTransaction();
          myShepherd.closeDBTransaction();
          le.printStackTrace();
        }


        if (!locked) {
          System.out.println("ImportExcel: Completed without lock; closing transaction");
          myShepherd.commitDBTransaction();
          myShepherd.closeDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          
          
          out.println("<p><strong>Success!</strong> I have successfully uploaded and imported "+fileName+".</p>");

          if(messages.toString().equals("")){messages.append("None");}
                    
          out.println("<p>The following error messages were reported during the import process:<br /><ul>"+messages+"</ul></p>" );
                     
          
          
          out.println("<p><a href=\"appadmin/import.jsp\">Return to the import page</a></p>" );

          out.println(ServletUtilities.getFooter(context));
        } 

      } 
      catch (IOException lEx) {
        lEx.printStackTrace();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to upload your Excel file. Please contact the webmaster about this message.");
        out.println(ServletUtilities.getFooter(context));
      } 
      catch (NullPointerException npe) {
        npe.printStackTrace();
        out.println(ServletUtilities.getHeader(request));
        out.println("<strong>Error:</strong> I was unable to import Excel data as no file was specified.");
        out.println(ServletUtilities.getFooter(context));
      }
      finally{myShepherd.closeDBTransaction();}
    
      out.close();
      }


  }


