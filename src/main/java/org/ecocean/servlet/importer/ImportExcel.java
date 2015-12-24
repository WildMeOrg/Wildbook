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
    ArrayList<SuperSpot> out = new ArrayList<SuperSpot>();
    for (int i=0; i<n; i++) {
      double x = data.readDouble();
      double y = data.readDouble();
      if (Double.isNaN(x) || Double.isNaN(y)) {
        throw new IOException("Caught IOException on parsing fgp file: a spot coordinate was NaN.");
      };
      out.add(new SuperSpot(x,y));
    }
    return out;
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

          // in all the sheets (TODO: double check), the relevant data is in the second sheet.
          //Get first/desired sheet from the workbook
          XSSFSheet sheet = workbook.getSheetAt(1);

          //Iterate through each rows one by one
          Iterator<Row> rowIterator = sheet.iterator();
          
          // Little temporary memory-saver
          int maxRows = 40;
          
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
                enc.setOccurrenceID("-1");
                newEncounter=false;
                System.out.println("\tEncounter already in db.");
              }
              else{
                enc.setCatalogNumber(encID);
                enc.setState("approved");
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

            
            
            Cell locationCell = row.getCell(3);
            String locationID = locationCell.getStringCellValue();
            if ( (locationID!=null) && !locationID.equals("") ) {
              enc.setLocationID(locationID);
              enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set location ID to " + locationID + ".</p>"); 
              System.out.println("\tlocation ID: "+locationID);
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
            
            /* disabled for now bc we don't support dynamic numeric properties; I recall jason saying these fields not needed.
            try {
              // # sharks at cave
              Cell sharksCaveCell = row.getCell(19);
              String sharksCave = sharksCaveCell.getStringCellValue();
              if(sharksCave!=null && !sharksCave.equals("")) {
                enc.setDynamicProperty("# sharks in cave", sharksCave);
                System.out.println("\t# sharks in cave: "+sharksCave);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set #SharksInCave to " + sharksCave + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\t# sharks in cave: COULD NOT PARSE");
            }
            
            try {
              // # sharks at overhang
              Cell sharksOverhangCell = row.getCell(20);
              String sharksOverhang = sharksOverhangCell.getStringCellValue();
              if(sharksOverhang!=null && !sharksOverhang.equals("")) {
                enc.setDynamicProperty("# sharks in overhang", sharksOverhang);
                System.out.println("\t# sharks in overhang: "+sharksOverhang);
                enc.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "ImportExcel process set #SharksInOverhang to " + sharksOverhang + ".</p>");
              }
            }
            catch (Exception e) {
              System.out.println("\t# sharks in overhang: COULD NOT PARSE");
            }
            */

                                    
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
                  ArrayList<SuperSpot> reference_spots = loadFgpSpots(spotData, 3);
                  // The next value in the fgp file encodes the number of points.
                  int nPoints = spotData.readInt();
                  ArrayList<SuperSpot> spots = loadFgpSpots(spotData, nPoints);
                  // are normed_spots needed for anything?
                  // ArrayList<SuperSpot> normed_spots = loadFgpSpots(spotData, nPoints);
                  // Now load spots into encounter object; defaults to left side if no flank info
                  if ((flank!=null)&&(flank.equals("R"))){
                    enc.setRightReferenceSpots(reference_spots);
                    enc.setRightSpots(spots);
                    enc.hasRightSpotImage = true;
                  }
                  else {
                    enc.setLeftReferenceSpots(reference_spots);
                    enc.setSpots(spots);
                    enc.hasSpotImage = true;
                  }
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
            if(newEncounter){
              myShepherd.storeNewEncounter(enc, enc.getCatalogNumber());
            }
            if(loadPicture){
              String baseDir = getEncDBFolder(shepherdDataDir,encID).getCanonicalPath();
              System.out.println("\tRefreshing asset formats with baseDir = "+baseDir);
              enc.refreshAssetFormats(context, baseDir, picture, false, true);
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
      out.close();
    }


  }


