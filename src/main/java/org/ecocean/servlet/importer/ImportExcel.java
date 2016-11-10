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
    out.println("Num Sheets = "+numSheets);

    int physicalNumberOfRows = sheet.getPhysicalNumberOfRows();
    out.println("Num Rows = "+physicalNumberOfRows);

    int rows = sheet.getPhysicalNumberOfRows();; // No of rows
    int cols = sheet.getRow(0).getPhysicalNumberOfCells(); // No of columns
    int tmp = 0;
    out.println("Num Cols = "+cols);
    out.println("committing = "+committing);

    Occurrence occ = null;

    int printPeriod = 50;

    if (committing) myShepherd.beginDBTransaction();
    out.println("<h2>BEGINNING THE EXCEL LOOP</h2>");
    for (int i=1; i<rows; i++) {
      try {
        if (committing) myShepherd.beginDBTransaction();
        row = sheet.getRow(i);

        occ = getCurrentOccurrence(occ, row, out);
        if (i%printPeriod==0)  out.println("row "+i+": "+occ.getComments());

        Encounter enc = parseEncounter(row, occ);
        String indID = enc.getIndividualID();
        MarkedIndividual ind = null;
        boolean needToAddEncToInd = false;
        if (indID!=null) {
          ind = myShepherd.getMarkedIndividual(indID);
          if (ind==null) {
            ind = new MarkedIndividual(indID,enc);
          } else {
            needToAddEncToInd = true;
          }

        }
        // TODO



        if (committing) myShepherd.storeNewEncounter(enc, Util.generateUUID());
        enc.setState("approved");
        occ.addEncounter(enc);
        if (committing) myShepherd.storeNewOccurrence(occ);
        enc.setOccurrenceID(occ.getOccurrenceID());

        enc.setDWCDateAdded();
        enc.setDWCDateLastModified();
        enc.setSubmitterID("Bulk Import");
        enc.setVerbatimLocality(enc.getCountry());



        if (needToAddEncToInd) ind.addEncounter(enc, context);
        if (committing && indID!=null  && !myShepherd.isMarkedIndividual(indID)) myShepherd.storeNewMarkedIndividual(ind);
        if (committing) myShepherd.commitDBTransaction();
        if (i%printPeriod==0) {
          out.println("Parsed row ("+i+"), containing Enc "+enc.getEncounterNumber()
          +" with individualID "+enc.getIndividualID()
          +" and occurrenceID "+enc.getOccurrenceID()
          +" with country "+enc.getCountry()
          +", dateInMillis "+enc.getDateInMilliseconds()
          +", individualID "+enc.getIndividualID()
          +", sex "+enc.getSex()
          +", lifeStage "+enc.getLifeStage()
          +", and dynamic properties "+enc.getDynamicProperties()
          );
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
  public Occurrence getCurrentOccurrence(Occurrence oldOcc, HSSFRow row, PrintWriter out) {
    if (isOccurrenceOnRow(oldOcc, row)) return oldOcc;
    return parseOccurrence(row);
  }

  public boolean isOccurrenceOnRow(Occurrence occ, HSSFRow row) {
    if (occ!=null && !occ.getComments().equals("None")) {
      boolean res = occ.getComments().equals(makeOccurrenceComment(row));
    }
    return false;
  }

  public String makeOccurrenceComment(HSSFRow row) {
    // just want a deterministic string
    DateTime encDate = getDateTime(row, 7);
    Integer coordX  = getInteger(row, 3);
    Integer coordY  = getInteger(row, 4);
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

    enc.setCountry(getString(row,0));
    enc.setLocationID(getString(row,1));
    // col 2-4: coordinates in old system. TODO
    // col 5-6: info re: camera trap. TODO

    DateTime encDate = getDateTime(row, 7);
    if (encDate!=null) enc.setDateInMilliseconds(encDate.getMillis());
    // col 8 is parsed by the occurrence
    enc.setIndividualID(getString(row, 9));
    enc.setLifeStage(getStringOrIntString(row, 10));
    enc.setSex(getString(row, 11));
    // col 12: genealogi (mother) TODO
    // col 13 photos per encounter TODO
    parseDynProp(enc, "lure type", row, 14);
    parseDynProp(enc, "camera type", row, 15);

    // enc.setAgeClass(getString(row,128));
    return enc;
  }

  private void parseDynProp(Encounter enc, String name, HSSFRow row, int i) {
    String val = getString(row, i);
    if (val == null) return;
    enc.setDynamicProperty(name, val);
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
      if (str.equals("")) return null;
      return str;
    }
    catch (Exception e) {}
    return null;
  }

  public String getStringOrIntString(HSSFRow row, int i) {
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

  public Date getDate(HSSFRow row, int i) {
    try {
      Date date = row.getCell(i).getDateCellValue();
      return date;
    }
    catch (Exception e) {}
    return null;
  }


  public DateTime getDateTime(HSSFRow row, int i) {
    Date date = getDate(row, i);
    if (date == null) return null;
    return new DateTime(date);
  }
}
