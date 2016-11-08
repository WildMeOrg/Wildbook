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
import org.ecocean.*;
import org.ecocean.servlet.*;
import org.ecocean.media.*;
import javax.jdo.*;
import java.lang.StringBuffer;
import java.util.Vector;
import java.util.Iterator;
import java.lang.NumberFormatException;
import org.apache.poi.hssf.usermodel.*;
import org.apache.poi.poifs.filesystem.NPOIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.commons.lang.StringUtils;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class
ImportExcel extends HttpServlet {

  public void init(ServletConfig config) throws ServletException {
    super.init(config);
  }

  public void doGet(HttpServletRequest request,  HttpServletResponse response) throws ServletException,  IOException {
    doPost(request,  response);
  }

  public void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException,  IOException {

    String context="context0";
    context=ServletUtilities.getContext(request);
    Shepherd myShepherd = new Shepherd(context);
    PrintWriter out = response.getWriter();

    String filename = "/home/ubuntu/Documents/dataTo2012.xls";
    if (request.getParameter("filename") != null) filename = request.getParameter("filename");
    File dataFile = new File(filename);
    boolean dataFound = dataFile.exists();

    out.println("</br><p>File found="+String.valueOf(dataFound)+" at "+dataFile.getAbsolutePath()+"</p>");
    //FileInputStream dataFIStream = new FileInputStream(dataFile);
    //Create Workbook instance holding reference to .xlsx file
    //XSSFWorkbook workbook = new XSSFWorkbook(dataFIStream);

    boolean committing =  (request.getParameter("commit")!=null && !request.getParameter("commit").toLowerCase().equals("false")); //false by default


    NPOIFSFileSystem fs = new NPOIFSFileSystem(dataFile);
    HSSFWorkbook wb = new HSSFWorkbook(fs.getRoot(), true);
    //POIFSFileSystem fs = new POIFSFileSystem(dataFIStream);
    //HSSFWorkbook wb = new HSSFWorkbook(fs);
    HSSFSheet sheet = wb.getSheetAt(0);
    HSSFRow row;
    HSSFCell cell;

    int numSheets = wb.getNumberOfSheets();
    out.println("<p>Num Sheets = "+numSheets+"</p>");

    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    out.println("<p>Num Rows = "+physicalNumberOfRows+"</p>");

    int rows = sheet.getPhysicalNumberOfRows();; // No of rows
    int cols = sheet.getRow(0).getPhysicalNumberOfCells(); // No of columns
    int tmp = 0;
    out.println("<p>Num Cols = "+cols+"</p>");
    Occurrence occ = null;

    if (committing) myShepherd.beginDBTransaction();
    out.println("<h2>BEGINNING THE EXCEL LOOP</h2>");
    for (int i=1; i<rows; i++) {
      try {
        if (committing) myShepherd.beginDBTransaction();
        row = sheet.getRow(i);

        occ = getCurrentOccurrence(occ, row);
        out.println("row "+i+": "+occ.getComments());

        Encounter enc = parseEncounter(row, occ);

        String indID = enc.getIndividualID();
        if (indID==null) continue;
        MarkedIndividual ind = myShepherd.getMarkedIndividual(indID);
        boolean needToAddEncToInd = false;
        if (ind==null) {
          ind = new MarkedIndividual(indID,enc);
        } else {
          needToAddEncToInd = true;
        }
        if (committing) myShepherd.storeNewEncounter(enc, Util.generateUUID());
        occ.addEncounter(enc);
        if (needToAddEncToInd) ind.addEncounter(enc, context);
        if (committing && !myShepherd.isMarkedIndividual(indID)) myShepherd.storeNewMarkedIndividual(ind);
        if (committing) myShepherd.commitDBTransaction();
        if (i%10==0) {
          out.println("<p>Parsed row ("+i+"), containing Enc "+enc.getEncounterNumber()+"</p>");
        }
      }
      catch (Exception e) {
        fs.close();
        out.println("Encountered an error while importing the file.");
        e.printStackTrace(out);
        myShepherd.rollbackDBTransaction();
      }
    }
    fs.close();
  }


  // check if oldOcc is the same occurrence as the occurrence on this row
  // if so, return oldOcc. If not, return parseOccurrence(row)
  public Occurrence getCurrentOccurrence(Occurrence oldOcc, HSSFRow row) {
    if (isOccurrenceOnRow(oldOcc, row)) return oldOcc;
    return parseOccurrence(row);
  }

  public boolean isOccurrenceOnRow(Occurrence occ, HSSFRow row) {
    if (occ!=null && !occ.getComments().equals("None")) {
      return (occ.getComments().equals(makeOccurrenceComment(row)));
    }
    return false;
  }

  public String makeOccurrenceComment(HSSFRow row) {
    // just want a deterministic string
    String encDate = getString(row, 7);
    String coordX  = getString(row, 3);
    String coordY  = getString(row, 4);
    String individuals = getString(row, 8);
    return (encDate+": "+"("+coordX+", "+coordY+") "+individuals);
  }

  private boolean needToParse(String rowOccID, Shepherd myShepherd) {
    return (!myShepherd.isOccurrence(rowOccID));
  }

  public Occurrence parseOccurrence(HSSFRow row) {

    String comment = makeOccurrenceComment(row);
    Occurrence occ = new Occurrence(comment);
    return occ;

  }

  public Encounter parseEncounter(HSSFRow row, Occurrence occ) {
    String id = getString(row, 9);
    Encounter enc = new Encounter(occ, id);
    // enc.setAgeClass(getString(row,128));
    return enc;
  }

  // following 'get' functions swallow errors
  public Integer getInteger(HSSFRow row, int i) {
    try {
      double val = row.getCell(i).getNumericCellValue();
      return new Integer( (int) val );
    }
    catch (Exception e){}
    return null;
  }

  public String getString(HSSFRow row, int i) {
    try {
      String str = row.getCell(i).getStringCellValue();
      if (str==null || str.equals("")) return null;
      else return str;
    }
    catch (Exception e) {}
    return null;
  }

  public Boolean getBooleanFromString(HSSFRow row, int i) {
    try {
      String boolStr = getString(row, i).trim().toLowerCase();
      if (boolStr==null || boolStr.equals("")) return null;
      else if (boolStr.equals("yes")) return new Boolean(true);
      else if (boolStr.equals("no")) return new Boolean(false);
    }
    catch (Exception e) {}
    return null;
  }

  public DateTime getDateTime(HSSFRow row) {
    int year = 0;
    int month = 0;
    int day = 0;
    int hour = 0;
    int minute = 0;
    int second = 0;
    try {
      Date dateWithTimeOnly = row.getCell(7).getDateCellValue();
      hour = dateWithTimeOnly.getHours();
      minute = dateWithTimeOnly.getMinutes();
      second = dateWithTimeOnly.getSeconds();
    }
    catch (Exception e) {}
    /*try {
      Date dateWithDateOnly = row.getCell(3).getDateCellValue();
      year = dateWithDateOnly.getYear();
      month = dateWithDateOnly.getMonth();
      day = dateWithDateOnly.getDay();
    }
    catch (Exception e) {
    */
      try {
        year = getInteger(row, 6).intValue();
        month = getInteger(row, 5).intValue();
        day = getInteger(row, 4).intValue();
    }
      catch (Exception ex) {}  /*
    }*/
    return new DateTime(year, month, day, hour, minute, second, DateTimeZone.forID("Africa/Nairobi"));

  }
}
